package com.ralph.quizlocker

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat.getSystemService
import android.view.WindowManager
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_quiz_locker.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class QuizLockerActivity : AppCompatActivity() {

    var quiz:JSONObject? = null

    // 정답횟수 저장 SharedPreference
    val wrongAnswerPref by lazy { getSharedPreferences("wrongAnswer", Context.MODE_PRIVATE)}
    // 오답횟수 저장 SharedPreference
    val correctAnswerPref by lazy { getSharedPreferences("correctAnswer", Context.MODE_PRIVATE)}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금화면보다 상단에 위치하기 위한 설정 조정. 버전별로 사용법이 다르기 때문에 상이하게 적용
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // 잠금화면에서 보여지도록 설정
            setShowWhenLocked(true)

            // 잠금해제
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // 잠금화면에서 보여지도록 설정
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

            // 기본잠금화면을 해제
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        // 화면을 켜진 상태로 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_quiz_locker)

        // 퀴즈 데이터를 가져온다.
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val category = pref.getStringSet("category", setOf<String>("수도"))

        var jsonFiles: MutableList<String> = mutableListOf()

        // 선택한 퀴즈 종류를 저장
        if(category.contains("일반상식")) jsonFiles.add("knowledge.json")
        if(category.contains("역사")) jsonFiles.add("history.json")
        if(category.contains("수도")) jsonFiles.add("capital.json")

        // 선택한 퀴즈 종류 중 랜덤하게 한가지 선택
        val jsonFileIndex = Random().nextInt(jsonFiles.size)
        val json = assets.open(jsonFiles[jsonFileIndex]).reader().readText()
        val quizArray = JSONArray(json)

        // 선택된 종류에서 랜덤하게 퀴즈 선택
        quiz = quizArray.getJSONObject(Random().nextInt(quizArray.length()))

        // 퀴즈를 보여준다.
        quizLabel.text = quiz?.getString("question")
        choice1.text = quiz?.getString("choice1")
        choice2.text = quiz?.getString("choice2")

        // 정답횟수를 보여준다.
        val id = quiz?.getInt("id").toString() ?: ""
        correctCountLabel.text = "정답횟수:${correctAnswerPref.getInt(id, 0)}"
        wrongCountLabel.text = "오답횟수:${wrongAnswerPref.getInt(id, 0)}"


        // SeekBar의 값이 변경될 때 불리는 리스너
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when {
                    // SeeBar의 우측 끝으로 가면 choice2를 선택한 것으로 간주한다.
                    progress > 95 -> {
                        leftImageView.setImageResource(R.drawable.padlock)
                        // 우측 이미지뷰의 자물쇠 아이콘을 열림 아이콘으로 변경
                        rightImageView.setImageResource(R.drawable.unlock)
                    }
                    // SeekBar의 좌측 끝으로 가면 choice1을 선택한 것으로 간주한다.
                    progress < 5 -> {
                        // 좌측 이미지뷰의 자물쇠 아이콘을 열림 아이콘으로 변경
                        leftImageView.setImageResource(R.drawable.unlock)
                        rightImageView.setImageResource(R.drawable.padlock)
                    }
                    // 양쪽 끝이 아닌 경우
                    else -> {
                        // 양쪽 이미지를 모두 잠금 아이콘으로 변경
                        leftImageView.setImageResource(R.drawable.padlock)
                        rightImageView.setImageResource(R.drawable.padlock)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 50
                when {
                    // 우측 끝의 답을 선택한 경우
                    progress > 95 -> checkChoice(quiz?.getString("choice2") ?: "")
                    // 좌측 끝의 답을 선택한 경우
                    progress < 5 -> checkChoice(quiz?.getString("choice1") ?: "")

                    else -> seekBar?.progress = 50
                }
            }
        })
    }

    // 정답 체크 함수
    fun checkChoice(choice: String) {
        quiz?.let {
            when {
                // choice의 텍스트가 정답 텍스트와 같으면 Activity 종료
                choice == it.getString("answer") -> {
                    // 정답인 경우 정답횟수 증가
                    val id= it.getInt("id").toString()
                    var count = correctAnswerPref.getInt(id, 0)
                    count++
                    correctAnswerPref.edit().putInt(id, count).apply()
                    correctCountLabel.text = "정답횟수:${count}"

                    // Activity 종료
                    finish()
                }

                else -> {
                    // 오답횟수 증가
                    val id= it.getInt("id").toString()
                    var count = wrongAnswerPref.getInt(id, 0)
                    count++
                    wrongAnswerPref.edit().putInt(id, count).apply()
                    wrongCountLabel.text = "오답횟수:${count}"

                    // 정답이 아닌 경우 UI 초기화
                    leftImageView.setImageResource(R.drawable.padlock)
                    rightImageView.setImageResource(R.drawable.unlock)
                    seekBar?.progress = 50

                    // 정답이 아닌 경우 진동알림 추가
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {    // 26
                        // 1초동안 100의 세기(최고 255)로 1회 진동
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, 100))
                    } else {
                        // 1초동안 진동
                        vibrator.vibrate(1000)
                    }
                }
            }
        }
    }
}
