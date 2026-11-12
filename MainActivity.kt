
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        private const val REQUEST_MIC = 100
        private const val REQUEST_SPEECH = 101
        private const val JARVIS_API_URL =
            "https://delicate-haze-a0fa.dizsergey525.workers.dev"
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
                "Русский голос недоступен: установите русский голос"
        } else {
            statusText.text = "Ошибка голосовой системы"
        }
    }

    private fun checkMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MIC
            )
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

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
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            val command = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()

            if (command != null) {
                responseText.text = "Вы: $command"
                processCommand(command)
            } else {
                say("Я не расслышал команду.")
            }
        } else {
            statusText.text = "Система готова"
        }
    }

    private fun processCommand(command: String) {
        val normalized = command.lowercase(Locale.ROOT)

        when {
            normalized.contains("настройки") -> {
                say("Открываю настройки телефона.")
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }

            normalized.contains("открой") ||
            normalized.contains("запусти") ||
            normalized.contains("открывай") ||
            normalized.contains("включи приложение") -> {
                openInstalledApp(command)
            }

            else -> askJarvis(command)
        }
    }

    private fun openInstalledApp(command: String) {
        var appName = command.lowercase(Locale.ROOT)

        val wordsToRemove = listOf(
            "джарвис", "открой", "открывай", "запусти",
            "запуск", "приложение", "приложения",
            "пожалуйста", "мне", "включи"
        )

        for (word in wordsToRemove) {
            appName = appName.replace(word, " ")
        }

        appName = appName
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (appName.isBlank()) {
            say("Какое приложение открыть, сэр?")
            return
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(packageManager)
                .toString()
                .lowercase(Locale.ROOT)

            label == appName
        } ?: apps.firstOrNull { info ->
            val label = info.loadLabel(packageManager)
                .toString()
                .lowercase(Locale.ROOT)

            label.contains(appName) || appName.contains(label)
        }

        if (match == null) {
            say("Не нашёл приложение $appName на вашем телефоне.")
            return
        }

        val packageName = match.activityInfo.packageName
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            say("Открываю ${match.loadLabel(packageManager)}.")
            startActivity(launchIntent)
        } else {
            say("Не удалось запустить это приложение.")
        }
    }

    private fun askJarvis(message: String) {
        statusText.text = "Джарвис думает..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = (URL(JARVIS_API_URL).openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                }

                val requestBody = JSONObject()
                    .put("message", message)
                    .toString()

                connection.outputStream.use {
                    it.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream.bufferedReader().use {
                    it.readText()
                }

                val json = JSONObject(response)

                if (code in 200..299) {
                    val answer = json.optString(
                        "answer",
                        "Не удалось получить ответ."
                    )

                    runOnUiThread {
                        say(answer)
                    }
                } else {
                    val error = json.optString(
                        "error",
                        "Ошибка сервера: $code"
                    )

                    runOnUiThread {
                        say("Не удалось получить ответ. $error")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    say("Не удалось связаться с сервером. Проверьте интернет.")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun say(message: String) {
        responseText.text = message
        statusText.text = "Джарвис отвечает"

        if (ttsReady) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "JARVIS_RESPONSE"
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
