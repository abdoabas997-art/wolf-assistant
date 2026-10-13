
package com.wolfassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.*
import android.app.AlertDialog
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.rgb(15, 20, 32)
    private val panel = Color.rgb(27, 35, 51)
    private val accent = Color.rgb(70, 150, 255)

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var continuousButton: Button

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var continuousListening = false
    private var listening = false
    private var readReplies = false
    private var pendingAction = ""
    private var lastAssistantReply = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "EG")
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(16, 10, 16, 10)
        }

        val header = TextView(this).apply {
            text = "🐺 مساعد الذئب"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 6)
        }
        root.addView(header)

        status = TextView(this).apply {
            text = "● جاهز للمساعدة"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 220, 160))
            setPadding(8, 0, 8, 12)
        }
        root.addView(status)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(55, 70, 95))
        }
        root.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            )
        )

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        scroll.addView(messages)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        addMessage(
            "أهلًا بك! أنا مساعد الذئب 🐺\n\n" +
                "يمكنك الكتابة أو استخدام الميكروفون للتحدث معي.",
            false
        )

        input = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(14, 8, 14, 8)
            setSingleLine(true)
            setBackgroundColor(panel)
        }

        val sendRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        sendRow.addView(
            input,
            LinearLayout.LayoutParams(0, 52, 1f)
        )

        val sendButton = makeButton("إرسال")
        sendRow.addView(sendButton)
        root.addView(sendRow)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val micButton = makeButton("🎙️ تحدث")
        continuousButton = makeButton("👂 استماع مستمر: متوقف")
        val stopButton = makeButton("⏹ إيقاف")

        voiceRow.addView(
            micButton,
            LinearLayout.LayoutParams(0, 48, 1f)
        )
        voiceRow.addView(
            continuousButton,
            LinearLayout.LayoutParams(0, 48, 1.4f)
        )
        voiceRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, 48, 0.8f)
        )
        root.addView(voiceRow)

        val speechRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val readButton = makeButton("🔊 قراءة الردود: متوقفة")
        speechRow.addView(
            readButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 46
            )
        )
        root.addView(speechRow)

        setContentView(root)

        sendButton.setOnClickListener { sendMessage() }

        input.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        micButton.setOnClickListener {
            pendingAction = "push"
            startVoiceWithPermission()
        }

        continuousButton.setOnClickListener {
            if (continuousListening) {
                stopListening()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("الاستماع المستمر")
                    .setMessage(
                        "هل تسمح لمساعد الذئب بالاستماع المتكرر " +
                            "عبر الميكروفون؟ يمكنك إيقافه في أي وقت."
                    )
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("أوافق") { _, _ ->
                        continuousListening = true
                        updateContinuousButton()
                        pendingAction = "continuous"
                        startVoiceWithPermission()
                    }
                    .show()
            }
        }

        stopButton.setOnClickListener {
            stopListening()
            tts?.stop()
            setStatus("تم إيقاف الاستماع والصوت")
        }

        readButton.setOnClickListener {
            readReplies = !readReplies
            readButton.text = if (readReplies) {
                "🔊 قراءة الردود: مفعّلة"
            } else {
                tts?.stop()
                "🔊 قراءة الردود: متوقفة"
            }

            if (readReplies && lastAssistantReply.isNotEmpty()) {
                speak(lastAssistantReply)
            }
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(accent)
            )
        }
    }

    private fun startVoiceWithPermission() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("خدمة التعرف على الصوت غير متاحة على جهازك")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
            return
        }

        startListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 101) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                continuousListening = false
                updateContinuousButton()
                setStatus("لم يتم السماح باستخدام الميكروفون")
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        setStatus("🎙️ أستمع إليك الآن...")
                    }

                    override fun onBeginningOfSpeech() {
                        setStatus("🎙️ تفضل، أنا أستمع")
                    }

                    override fun onEndOfSpeech() {
                        listening = false
                        setStatus("جارٍ فهم كلامك...")
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false
                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                        if (!spoken.isNullOrBlank()) {
                            input.setText(spoken)
                            sendMessage()
                        }

                        restartContinuousIfNeeded()
                    }

                    override fun onError(error: Int) {
                        listening = false

                        if (continuousListening) {
                            setStatus("الاستماع مستمر...")
                            restartContinuousIfNeeded()
                        } else {
                            setStatus("لم أتعرف على الكلام، حاول مرة أخرى")
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )

            val intent = Intent(
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
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )
            }

            recognizer?.startListening(intent)
        } catch (e: Exception) {
            setStatus("تعذر تشغيل الميكروفون")
        }
    }

    private fun restartContinuousIfNeeded() {
        if (continuousListening) {
            window.decorView.postDelayed({
                if (continuousListening && !listening) {
                    startListening()
                }
            }, 800)
        } else {
            setStatus("● جاهز للمساعدة")
        }
    }

    private fun stopListening() {
        continuousListening = false
        listening = false
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        updateContinuousButton()
        setStatus("● جاهز للمساعدة")
    }

    private fun updateContinuousButton() {
        continuousButton.text = if (continuousListening) {
            "👂 الاستماع المستمر: يعمل"
        } else {
            "👂 استماع مستمر: متوقف"
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        addMessage("أنت: $text", true)
        input.setText("")

        val imm = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        val normalized = text.lowercase(Locale("ar", "EG"))

        val reply = when {
            normalized.contains("اسمك") ->
                "أنا مساعد الذئب 🐺، مساعدك الذكي."

            normalized.contains("مرحبا") ||
                normalized.contains("أهلا") ||
                normalized.contains("السلام") ->
                "أهلًا بك! كيف أقدر أساعدك؟"

            normalized.contains("الوقت") ->
                "الوقت الحالي على جهازك: " +
                    java.text.SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(java.util.Date())

            else ->
                "وصلتني رسالتك. الواجهة تعمل بنجاح، " +
                    "لكن الردود الذكية تحتاج إلى ربط التطبيق " +
                    "بخادم الذكاء الاصطناعي."
        }

        lastAssistantReply = reply
        addMessage("🐺 مساعد الذئب:\n$reply", false)

        if (readReplies) {
            speak(reply)
        }
    }

    private fun speak(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "wolf-reply"
        )
    }

    private fun setStatus(text: String) {
        if (::status.isInitialized) {
            status.text = text
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 14, 16, 14)
            setBackgroundColor(
                if (isUser) Color.rgb(35, 75, 125) else panel
            )
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 6, 0, 6)
        messages.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
