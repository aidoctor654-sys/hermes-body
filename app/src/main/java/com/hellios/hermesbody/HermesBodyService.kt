package com.hellios.hermesbody

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class HermesBodyService : Service() {

    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var state = "IDLE"

    private val agents = mapOf(
        "pi" to 8420, "hermes" to 8422, "polar" to 8423, "ghost" to 8424
    )

    companion object {
        const val CHANNEL_ID = "hermes_body"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Hermes Body ●"))
        initTTS()
        initSpeech()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { speech?.destroy(); tts?.shutdown(); super.onDestroy() }

    // ── TTS ──
    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pl", "PL")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { state = "SPEAKING" }
                    override fun onDone(id: String?) {
                        state = "WAITING"
                        android.os.Handler(mainLooper).postDelayed({
                            if (state == "WAITING") { state = "IDLE" }
                        }, 3000)
                    }
                    @Deprecated("") override fun onError(id: String?) { state = "IDLE" }
                })
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis())
    }

    // ── STT ──
    private fun initSpeech() {
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { state = "LISTENING" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { state = "THINKING" }
            override fun onError(e: Int) {
                state = "IDLE"
                android.os.Handler(mainLooper).postDelayed({ startListening() }, 2000)
            }
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) routeToAgent(text)
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speech?.startListening(intent)
    }

    // ── ROUTING ──
    private fun routeToAgent(text: String) {
        val lower = text.lowercase().trim()
        var targetPort = 8420 // default pi
        for ((name, port) in agents) {
            if (lower.startsWith("ok $name") || lower == name) {
                targetPort = port
                break
            }
        }
        sendToAgent(targetPort, text)
    }

    private fun sendToAgent(port: Int, text: String) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                    put("source", "voice")
                }
                val url = URL("http://127.0.0.1:$port/")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(json.toString().toByteArray())
                conn.connectTimeout = 5000
                conn.readTimeout = 10000

                val resp = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(resp)
                val msg = obj.optString("response", obj.optString("status", "OK"))
                speak(msg)
                conn.disconnect()
            } catch (e: Exception) {
                speak("Agent offline.")
            }
        }.start()
        startListening()
    }

    // ── NOTIFICATION ──
    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Body")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Hermes Body", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
