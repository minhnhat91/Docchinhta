package com.example.docchinhta

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var textToSpeech: TextToSpeech? = null
    private var sentenceIndex = 0
    private lateinit var sentences: List<String>
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sentenceInfoTextView: TextView
    private var timewait = 500
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        // Initialize SharedPreferences
        sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        // Restore user settings
        restoreSettings()

        val editText = findViewById<EditText>(R.id.editText)
        val button = findViewById<Button>(R.id.button)
        val speedSeekBar = findViewById<SeekBar>(R.id.speedSeekBar)
        sentenceInfoTextView = findViewById(R.id.sentenceInfoTextView)

        textToSpeech = TextToSpeech(this, this)
        textToSpeech?.setSpeechRate(sharedPreferences.getFloat("speechRate", 0.4F))

        button.setOnClickListener {
            val inputText = editText.text.toString()
            stopSpeaking()
            sentenceIndex = 0
            sentences = splitIntoSentences(inputText)
            speakNextSentence()
        }

        val speedTextView = findViewById<TextView>(R.id.speedTextView)

        speedSeekBar.progress = (sharedPreferences.getFloat("speechRate", 0.4F) * 10).toInt()

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update speed of text-to-speech
                val speed = (progress.toFloat() / 10) // Speed range: 0.1 to 2.0
                textToSpeech?.setSpeechRate(speed)
                // Save user setting
                editor.putFloat("speechRate", speed).apply()
                // Update speed text view
                speedTextView.text = "Speed: ${String.format("%.1f", speed)}x"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Timewait SeekBar
        val timewaitSeekBar = findViewById<SeekBar>(R.id.timewaitSeekBar)
        timewaitSeekBar.progress = sharedPreferences.getInt("timewait", 500)

        timewaitSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update timewait
                timewait = progress // Update timewait (range from 0 to 2000)
                updateTimewait()
                // Save user setting
                editor.putInt("timewait", timewait).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val resumePauseButton = findViewById<ImageButton>(R.id.resumePauseButton)
        resumePauseButton.setImageResource(if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)

        resumePauseButton.setOnClickListener {
            isPaused = !isPaused
            resumePauseButton.setImageResource(if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        }

        val prevButton = findViewById<ImageButton>(R.id.prevButton)
        val nextButton = findViewById<ImageButton>(R.id.nextButton)

        prevButton.setOnClickListener {
            if (sentenceIndex > 0) {
                sentenceIndex--
                stopSpeaking()
                speakNextSentence()
            }
        }

        nextButton.setOnClickListener {
            if (sentenceIndex < sentences.size - 1) {
                sentenceIndex++
                stopSpeaking()
                speakNextSentence()
            }
        }
    }

    private fun restoreSettings() {
        timewait = sharedPreferences.getInt("timewait", 500)
        val repeatCount = sharedPreferences.getInt("repeatCount", 3)
        isPaused = sharedPreferences.getBoolean("isPaused", false)
        sentenceIndex = sharedPreferences.getInt("sentenceIndex", 0)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("vi", "VN")
            val result = textToSpeech?.setLanguage(locale)
            val voice = Voice("vi-VN-language", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, null)
            textToSpeech?.voice = voice
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakNextSentence() {
        if (sentenceIndex < sentences.size && !isPaused) {
            val sentence = sentences[sentenceIndex]
            val sentenceLength = sentence.trim().length
            speak(sentence, sentenceLength) // Đọc câu 3 lần
        }
    }

    private fun speak(text: String, length: Int) {
        var repeatIndex = 0
        val repeatCountEditText = findViewById<EditText>(R.id.repeatCountEditText)
        val repeatCount = if (repeatCountEditText.text.toString().isNotEmpty()) {
            repeatCountEditText.text.toString().toInt()
        } else {
            3 // Giá trị mặc định
        }

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (repeatIndex < repeatCount) {
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                    repeatIndex++
                    updateSentenceInfo("Câu ${sentenceIndex + 1}: $text (Độ dài: $length)")
                    handler.postDelayed(this, (timewait * length).toLong())
                } else {
                    sentenceIndex++
                    speakNextSentence()
                }
            }
        }, 0)
    }

    private fun updateSentenceInfo(info: String) {
        sentenceInfoTextView.text = info
    }

    private fun updateTimewait() {
        // Update timewait text view
        val timewaitTextView = findViewById<TextView>(R.id.timewaitTextView)
        timewaitTextView.text = "Timewait: ${timewait}ms"
    }

    private fun stopSpeaking() {
        textToSpeech?.stop()
        handler.removeCallbacksAndMessages(null)
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var currentSentence = ""
        for (char in text) {
            currentSentence += char
            if (char in ".") { // Thêm các dấu phẩy, chấm phẩy vào đây
                sentences.add(currentSentence.trim() + " chấm")
                currentSentence = ""
            } else if (char == ',') { // Thêm dấu xuống dòng vào đây
                sentences.add(currentSentence.trim() + " phẩy")
                currentSentence = ""
            } else if (char == '?') { // Thêm dấu xuống dòng vào đây
                sentences.add(currentSentence.trim() + " hỏi")
                currentSentence = ""
            } else if (char == ';') { // Thêm dấu xuống dòng vào đây
                sentences.add(currentSentence.trim() + " chấm phẩy")
                currentSentence = ""
            } else if (char == '!') { // Thêm dấu xuống dòng vào đây
                sentences.add(currentSentence.trim() + " chấm than")
                currentSentence = ""
            } else if (char == '\n') { // Thêm dấu xuống dòng vào đây
                sentences.add(currentSentence.trim())
                currentSentence = ""
            }
        }
        if (currentSentence.isNotBlank()) {
            sentences.add(currentSentence.trim())
        }
        return sentences
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}

