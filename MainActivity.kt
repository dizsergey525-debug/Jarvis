package com.filko.jarvis

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        private const val REQUEST_MIC = 100
        private const val REQUEST_SPEECH = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        responseText = findViewById(R.id.responseText)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            checkMicrophone()
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            tts?.stop()
            statusText.text = "Голос остановлен"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            statusText.text = if (ttsReady) "Система готова" else
                "Русский голос недоступен: установите русский голос в настройках"
        } else {
            statusText.text = "Ошибка голосовой системы"
        }
    }

    private fun checkMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                say("Мне необходимо разрешение на микрофон.")
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Слушаю вас")
        }
        try {
            statusText.text = "Слушаю..."
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (e: ActivityNotFoundException) {
            say("На телефоне не найден сервис распознавания речи.")
        }
    }

    @Deprecated("Используется системное распознавание речи")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            val command = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            if (command != null) {
                responseText.text = "Вы: $command"
                processCommand(command.lowercase(Locale.ROOT))
            } else {
                say("Я не расслышал команду.")
            }
        } else {
            statusText.text = "Система готова"
        }
    }

    private fun processCommand(command: String) {
        when {
            command.contains("настройки") -> {
                say("Открываю настройки телефона.")
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            command.contains("хром") || command.contains("браузер") -> {
                openApp("com.android.chrome", "Открываю браузер.")
            }
            command.contains("ютуб") || command.contains("youtube") -> {
                openApp("com.google.android.youtube", "Открываю YouTube.")
            }
            command.contains("привет") -> {
                say("Здравствуйте. Рад вас слышать.")
            }
            command.contains("как дела") -> {
                say("Все системы работают штатно. Чем займемся?")
            }
            command.contains("кто ты") -> {
                say("Я Джарвис, ваш персональный помощник FILKO.")
            }
            else -> {
                say("Команду принял. Пока мои возможности ограничены. "
                    + "Позже подключим искусственный интеллект.")
            }
        }
    }

    private fun openApp(packageName: String, message: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            say(message)
            startActivity(launchIntent)
        } else {
            say("Это приложение не найдено на вашем телефоне.")
        }
    }

    private fun say(message: String) {
        responseText.text = message
        statusText.text = "Джарвис отвечает"
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_RESPONSE")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
