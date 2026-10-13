
package com.wolfassistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.rgb(15, 20, 32)
    private val panel = Color.rgb(27, 35, 51)
    private val accent = Color.rgb(55, 130, 235)
    private val border = Color.rgb(90, 120, 160)

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var continuousButton: Button
    private lateinit var readButton: Button

    private val messageTexts = ArrayList<String>()
    private val messageOwners = ArrayList<Boolean>()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var continuousListening = false
    private var listening = false
    private var readReplies = false
    private var lastAssistantReply = ""

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        readReplies = savedInstanceState?.getBoolean(
            "read_replies", false
        ) ?: false

        lastAssistantReply = savedInstanceState?.getString(
            "last_reply", ""
        ) ?: ""

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "EG")
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars()
                )
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    top = insets.systemWindowInsetTop
                    bottom = insets.systemWindowInsetBottom
                }
            }

            view.setPadding(
                dp(16),
                top + dp(8),
                dp(16),
                bottom + dp(8)
            )
            insets
        }

        val header = TextView(this).apply {
            text = "🐺 مساعد الذئب"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        root.addView(header)

        status = TextView(this).apply {
            text = "● جاهز للمساعدة"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 220, 160))
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        root.addView(status)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(55, 70, 95))
        }
        root.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
        )

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        // إصلاح خطأ البناء: نضيف حاوية الرسائل مباشرة إلى ScrollView
        scroll.addView(messages)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        input = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(190, 200, 215))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setSingleLine(true)
            minHeight = dp(52)
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), border)
            }
        }

        val sendRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        sendRow.addView(
            input,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginEnd = dp(8)
            }
        )

        val sendButton = makeButton("إرسال")
        sendRow.addView(
            sendButton,
            LinearLayout.LayoutParams(dp(82), dp(54))
        )
        root.addView(sendRow)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        val micButton = makeButton("🎙️ تحدث")
        continuousButton = makeButton("👂 استماع مستمر: متوقف")
        val stopButton = makeButton("⏹ إيقاف")

        voiceRow.addView(
            micButton,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(4)
            }
        )
        voiceRow.addView(
            continuousButton,
            LinearLayout.LayoutParams(0, dp(46), 1.45f).apply {
                marginEnd = dp(4)
            }
        )
        voiceRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(46), 0.8f)
        )
        root.addView(voiceRow)

        readButton = makeButton(
            if (readReplies) "🔊 قراءة الردود: مفعّلة"
            else "🔊 قراءة الردود: متوقفة"
        )
        root.addView(
            readButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            ).apply {
                topMargin = dp(4)
            }
        )

        setContentView(root)
        root.requestApplyInsets()

        val oldTexts =
            savedInstanceState?.getStringArrayList("chat_texts")
        val oldOwners =
            savedInstanceState?.getBooleanArray("chat_owners")

        if (oldTexts != null && oldOwners != null &&
            oldTexts.isNotEmpty()
        ) {
            val count = minOf(oldTexts.size, oldOwners.size)
            for (i in 0 until count) {
                addMessage(oldTexts[i], oldOwners[i])
            }
        } else {
            addMessage(
                "أهلًا بك! أنا مساعد الذئب 🐺\n\n" +
                    "يمكنك الكتابة أو استخدام الميكروفون للتحدث معي.",
                false
            )
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                sendMessage()
                true
            } else {
                false
            }
        }

        micButton.setOnClickListener {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            "chat_texts", ArrayList(messageTexts)
        )
        outState.putBooleanArray(
            "chat_owners",
            BooleanArray(messageOwners.size) { messageOwners[it] }
        )
        outState.putBoolean("read_replies", readReplies)
        outState.putString("last_reply", lastAssistantReply)
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(4), 0, dp(4), 0)
            setBackgroundTintList(
                ColorStateList.valueOf(accent)
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
                arrayOf(Manifest.permission.RECORD_AUDIO), 101
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
        ) return

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

                        val spoken = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()

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
                            setStatus(
                                "لم أتعرف على الكلام، حاول مرة أخرى"
                            )
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG"
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            recognizer?.startListening(intent)
        } catch (_: Exception) {
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
                    SimpleDateFormat(
                        "hh:mm a", Locale.getDefault()
                    ).format(Date())

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
        messageTexts.add(text)
        messageOwners.add(isUser)

        val bubble = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(
                    if (isUser) Color.rgb(35, 75, 125) else panel
                )
                cornerRadius = dp(14).toFloat()
                setStroke(
                    dp(1),
                    if (isUser) Color.rgb(65, 115, 175) else border
                )
            }
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(5), 0, dp(5))
        }

        messages.addView(bubble, params)

        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
