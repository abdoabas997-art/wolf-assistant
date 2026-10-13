package com.wolfassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.Manifest
import android.util.Log

class VoiceAssistantService : Service() {

    companion object {
        const val ACTION_START =
            "com.wolfassistant.action.START_VOICE"

        const val ACTION_STOP =
            "com.wolfassistant.action.STOP_VOICE"

        private const val CHANNEL_ID = "wolf_voice_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "WolfVoiceService"
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceService()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                stopping = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                val notification = buildNotification()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification
                    )
                }

                startRecognition()
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الاستماع الصوتي لمساعد الذئب",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "إشعار ظاهر أثناء تشغيل الاستماع الصوتي"
                setShowBadge(false)
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, VoiceAssistantService::class.java)
            .setAction(ACTION_STOP)

        val stopPendingIntent =
            android.app.PendingIntent.getService(
                this,
                1002,
                stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    else 0
            )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("🐺 مساعد الذئب")
            .setContentText("خدمة الاستماع الصوتي تعمل")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "إيقاف الاستماع",
                stopPendingIntent
            )
            .build()
    }

    private fun startRecognition() {
        if (stopping || listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition is unavailable")
            stopVoiceService()
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        listening = true
                    }

                    override fun onBeginningOfSpeech() {
                        listening = true
                    }

                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onResults(
                        results: android.os.Bundle?
                    ) {
                        listening = false

                        val spoken = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()

                        if (!spoken.isNullOrBlank()) {
                            Log.i(TAG, "Recognized speech: $spoken")

                            // سنربط النص بالمحادثة في الخطوة التالية.
                            val event = Intent(
                                "com.wolfassistant.VOICE_RESULT"
                            ).setPackage(packageName)

                            event.putExtra("spoken_text", spoken)
                            sendBroadcast(event)
                        }

                        restartRecognition()
                    }

                    override fun onError(error: Int) {
                        listening = false
                        restartRecognition()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(
                        partialResults: android.os.Bundle?
                    ) {}
                    override fun onEvent(
                        eventType: Int,
                        params: android.os.Bundle?
                    ) {}
                }
            )

            val speechIntent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "ar-EG"
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ar-EG"
                )
            }

            recognizer?.startListening(speechIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Unable to start speech recognition", e)
            stopVoiceService()
        }
    }

    private fun restartRecognition() {
        if (stopping) return

        android.os.Handler(mainLooper).postDelayed({
            if (!stopping) {
                startRecognition()
            }
        }, 1000)
    }

    private fun stopVoiceService() {
        stopping = true
        listening = false

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }

        recognizer = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {
        stopping = true

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
