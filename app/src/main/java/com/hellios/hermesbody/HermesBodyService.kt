/*
 * HermesBodyService.kt
 * Foreground Service + SpeechRecognizer (pl-PL) + TTS + WebSocket → Termux
 * 
 * "Ciało dla agentów"
 */

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
import android.widget.Toast
import okhttp3.*
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class HermesBodyService : Service() {

    // ── State ──
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var client: OkHttpClient? = null
    private var isListening = false
    private var isSpeaking = false
    private var state = "IDLE"  // IDLE | LISTENING | THINKING | SPEAKING | WAITING

    // ── Agent routing ──
    private val agents = mapOf(
        "pi"     to 8420,
        "coder"  to 8421,
        "hermes" to 8422,
        "polar"  to 8423,
        "ghost"  to 8424
    )

    companion object {
        const val CHANNEL_ID = "hermes_body"
        const val NOTIFICATION_ID = 1
        const val TAG = "HermesBody"
    }

    // ═══════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Czekam... ●"))

        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        initTTS()
        initSpeech()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LISTEN" -> startListening()
            "STOP_LISTEN"  -> stopListening()
            "SPEAK"        -> speak(intent.getStringExtra("text") ?: "")
            "WAKE"         -> handleWakeWord(intent.getStringExtra("word") ?: "pi")
            else           -> startListening()  // default: zacznij słuchać
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speech?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    // ═══════════════════════════════════════
    // TTS — TextToSpeech
    // ═══════════════════════════════════════

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pl", "PL")
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        setState("SPEAKING")
                    }
                    override fun onDone(utteranceId: String?) {
                        // "Oddech" — 1.5s nasłuchu po TTS
                        setState("WAITING")
                        updateNotification("Słucham... ◉")
                        android.os.Handler(mainLooper).postDelayed({
                            if (state == "WAITING") {
                                stopListening()
                                setState("IDLE")
                                updateNotification("Czekam... ●")
                            }
                        }, 3000)  // 3s oddech
                    }
                    @Deprecated("")
                    override fun onError(utteranceId: String?) {
                        setState("IDLE")
                    }
                })
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    // ═══════════════════════════════════════
    // STT — SpeechRecognizer
    // ═══════════════════════════════════════

    private fun initSpeech() {
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            
            var partialResult = ""

            override fun onReadyForSpeech(params: Bundle?) {
                setState("LISTENING")
                updateNotification("Słucham... 🟢")
            }

            override fun onBeginningOfSpeech() {
                partialResult = ""
            }

            override fun onRmsChanged(rmsdB: Float) {
                // poziom głośności — można użyć do wizualizacji
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                setState("THINKING")
                updateNotification("Myślę... 🟡")
            }

            override fun onError(error: Int) {
                setState("IDLE")
                updateNotification("Błąd ●")
                // Restart nasłuchu po błędzie
                android.os.Handler(mainLooper).postDelayed({
                    if (state == "IDLE") startListening()
                }, 2000)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    routeToAgent(text)
                }
                partialResult = ""
            }

            override fun onPartialResults(partial: Bundle?) {
                val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partialResult = matches?.firstOrNull() ?: ""
                updateNotification("Słucham... 🟢 — $partialResult")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speech?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speech?.cancel()
    }

    // ═══════════════════════════════════════
    // ROUTING
    // ═══════════════════════════════════════

    private fun handleWakeWord(word: String) {
        val agent = word.lowercase().removePrefix("ok ").trim()
        if (agent in agents) {
            speak("Słucham. Co potrzebujesz?")
            startListening()
        } else {
            // Przekaż do Google Assistant
            val assistIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            assistIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(assistIntent)
        }
    }

    private fun routeToAgent(text: String) {
        // Sprawdź czy tekst zaczyna się od wake word
        val lower = text.lowercase().trim()
        for ((name, port) in agents) {
            if (lower.startsWith("ok $name") || lower == name) {
                val cleanText = text.removePrefix("ok $name").removePrefix("OK $name").trim()
                sendToAgent(port, cleanText.ifBlank { text })
                return
            }
        }
        // Default: wyślij do pi
        sendToAgent(8420, text)
    }

    private fun sendToAgent(port: Int, text: String) {
        val json = JSONObject().apply {
            put("text", text)
            put("source", "voice")
            put("timestamp", System.currentTimeMillis())
        }

        val body = RequestBody.create(
            MediaType.parse("application/json"), json.toString()
        )

        val request = Request.Builder()
            .url("http://127.0.0.1:$port/")
            .post(body)
            .build()

        client?.newCall(request)?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                speak("Nie mogę połączyć się z agentem.")
                setState("IDLE")
            }

            override fun onResponse(call: Call, response: Response) {
                val respText = response.body()?.string()
                try {
                    val obj = JSONObject(respText ?: "{}")
                    val agentResponse = obj.optString("response", "OK")
                    speak(agentResponse)
                } catch (e: Exception) {
                    setState("IDLE")
                }
            }
        })

        // Nasłuch po odpowiedzi - kontynuuj "Oddech"
        startListening()
    }

    // ═══════════════════════════════════════
    // STATE + NOTIFICATION
    // ═══════════════════════════════════════

    private fun setState(newState: String) {
        state = newState
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Hermes Body")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Hermes Body")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hermes Body", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ciało dla agentów — zawsze aktywne"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
