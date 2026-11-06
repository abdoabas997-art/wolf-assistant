package com.wolfassistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
class MainActivity : Activity() {

    private val bg = Color.rgb(15, 20, 32)
    private val panel = Color.rgb(27, 35, 51)
    private val accent = Color.rgb(55, 130, 235)
    private val border = Color.rgb(90, 120, 160)

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView

    private var continuousButton: Button? = null
    private var readButton: Button? = null
    private var voiceControls: View? = null

    private val messageTexts = ArrayList<String>()
    private val messageOwners = ArrayList<Boolean>()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var continuousListening = false
    private var readReplies = false
    private var lastAssistantReply = ""
    private var chatScreenOpen = false
    private var receiverRegistered = false
private val networkExecutor = Executors.newSingleThreadExecutor()
private val mainHandler = Handler(Looper.getMainLooper())

private val backendUrl =
    "https://wolf-assistant-d6dur.faable.link/chat"
    // معالجة زر الرجوع في إصدارات أندرويد الحديثة.
  private var requestInProgress = false
    private var platformBackCallback:
        android.window.OnBackInvokedCallback? = null

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.wolfassistant.VOICE_RESULT") return

            val spoken = intent.getStringExtra("spoken_text")
            if (!spoken.isNullOrBlank() && chatScreenOpen) {
    if (handleLocalVoiceCommand(spoken)) {
        return
    }

    input.setText(spoken)
    input.setSelection(input.text.length)
    sendMessage()
}
    
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        savedInstanceState?.let { state ->
            state.getStringArrayList("chat_texts")?.let {
                messageTexts.addAll(it)
            }

            state.getBooleanArray("chat_owners")?.let { owners ->
                owners.forEach { messageOwners.add(it) }
            }

            readReplies = state.getBoolean("read_replies", false)
            lastAssistantReply = state.getString("last_reply", "") ?: ""
            continuousListening =
                state.getBoolean("continuous_listening", false)
        }

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val languageResult = tts?.setLanguage(Locale("ar", "EG"))

                if (
                    languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }

        registerVoiceReceiver()

        chatScreenOpen =
            savedInstanceState?.getBoolean("chat_screen_open", false) == true

        // تسجيل معالج الرجوع في Android 13 وما بعده.
        if (Build.VERSION.SDK_INT >= 33) {
            platformBackCallback = android.window.OnBackInvokedCallback {
                handleBackPress()
            }

            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                platformBackCallback!!
            )
        }

        if (chatScreenOpen) showChat() else showHome()
    }

    private fun registerVoiceReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter("com.wolfassistant.VOICE_RESULT")

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(voiceReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(voiceReceiver, filter)
        }

        receiverRegistered = true
    }

private fun sendWolfAccessibilityCommand(action: String) {
    val intent = Intent(action).setPackage(packageName)
    sendBroadcast(intent)
}

private fun parseGridNumber(value: String): Int? {
    val numbers = mapOf(
        "واحد" to 1, "الأول" to 1, "الاول" to 1, "الأولى" to 1,
        "اثنين" to 2, "اتنين" to 2, "الثاني" to 2, "الثانى" to 2,
        "ثلاثة" to 3, "تلاتة" to 3, "الثالث" to 3, "الثالثة" to 3,
        "أربعة" to 4, "اربعة" to 4, "أربعه" to 4, "الرابع" to 4,
        "خمسة" to 5, "خمسه" to 5, "الخامس" to 5
    )

    return value.toIntOrNull() ?: numbers[value]
}

private fun handleLocalVoiceCommand(spokenText: String): Boolean {
    val text = spokenText
        .trim()
        .lowercase(Locale("ar", "EG"))
        .replace('٠', '0')
        .replace('١', '1')
        .replace('٢', '2')
        .replace('٣', '3')
        .replace('٤', '4')
        .replace('٥', '5')
        .replace('٦', '6')
        .replace('٧', '7')
        .replace('٨', '8')
        .replace('٩', '9')

    when {
        text.contains("أظهر الشبكة") ||
        text.contains("اظهر الشبكة") ||
        text.contains("اعرض الشبكة") ||
        text.contains("افتح الشبكة") -> {
            sendWolfAccessibilityCommand(
                WolfAccessibilityService.ACTION_SHOW_GRID
            )
            addMessage("🐺 تم إرسال أمر إظهار شبكة الشاشة.", false)
            return true
        }

        text.contains("أخف الشبكة") ||
        text.contains("اخف الشبكة") ||
        text.contains("إخفاء الشبكة") ||
        text.contains("اخفاء الشبكة") -> {
            sendWolfAccessibilityCommand(
                WolfAccessibilityService.ACTION_HIDE_GRID
            )
            addMessage("🐺 تم إرسال أمر إخفاء الشبكة.", false)
            return true
        }

        text.contains("التقط صورة للشاشة") ||
        text.contains("صور الشاشة") ||
        text.contains("التقط صورة الشاشة") -> {
            sendWolfAccessibilityCommand(
                WolfAccessibilityService.ACTION_CAPTURE
            )
            addMessage("🐺 تم إرسال أمر التقاط صورة الشاشة.", false)
            return true
        }
    }

    val rowMatch = Regex(
        "(?:الصف|صف)\\s*(\\d+|واحد|اثنين|اتنين|ثلاثة|تلاتة|أربعة|اربعة|خمسة|الأول|الاول|الثاني|الثانى|الثالث|الرابع|الخامس)"
    ).find(text)

    val columnMatch = Regex(
        "(?:العمود|عمود)\\s*(\\d+|واحد|اثنين|اتنين|ثلاثة|تلاتة|أربعة|اربعة|خمسة|الأول|الاول|الثاني|الثانى|الثالث|الرابع|الخامس)"
    ).find(text)

    if (rowMatch != null && columnMatch != null) {
        val row = parseGridNumber(rowMatch.groupValues[1])
        val column = parseGridNumber(columnMatch.groupValues[1])

        if (row != null && column != null &&
            row in 1..5 && column in 1..5
        ) {
            val intent = Intent(
                WolfAccessibilityService.ACTION_TAP_CELL
            ).setPackage(packageName).apply {
                putExtra(WolfAccessibilityService.EXTRA_ROW, row)
                putExtra(WolfAccessibilityService.EXTRA_COLUMN, column)
            }

            sendBroadcast(intent)
            addMessage(
                "🐺 تم إرسال أمر النقر على الصف $row والعمود $column.",
                false
            )
            return true
        }
    }

    return false
}

    private fun createRoot(): LinearLayout {
        val horizontal = dp(16)
        val vertical = dp(10)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(horizontal, vertical, horizontal, vertical)
        }

        root.setOnApplyWindowInsetsListener { view, insets ->
            var left = 0
            var top = 0
            var right = 0
            var bottom = 0
            var keyboardVisible = false

            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                )
                val ime = insets.getInsets(
                    android.view.WindowInsets.Type.ime()
                )

                left = bars.left
                top = bars.top
                right = bars.right
                bottom = maxOf(bars.bottom, ime.bottom)

                keyboardVisible = insets.isVisible(
                    android.view.WindowInsets.Type.ime()
                )
            } else {
                @Suppress("DEPRECATION")
                run {
                    left = insets.systemWindowInsetLeft
                    top = insets.systemWindowInsetTop
                    right = insets.systemWindowInsetRight
                    bottom = insets.systemWindowInsetBottom
                }
            }

            view.setPadding(
                horizontal + left,
                vertical + top,
                horizontal + right,
                vertical + bottom
            )

            // إبقاء حقل الرسالة وزر الإرسال ظاهرين عند فتح لوحة المفاتيح.
            voiceControls?.visibility =
                if (keyboardVisible) View.GONE else View.VISIBLE
            readButton?.visibility =
                if (keyboardVisible) View.GONE else View.VISIBLE

            insets
        }

        root.post { root.requestApplyInsets() }
        return root
    }

    private fun makeText(
        text: String,
        size: Float,
        color: Int = Color.WHITE
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(4), dp(3), dp(4), dp(3))
            backgroundTintList = ColorStateList.valueOf(accent)
        }
    }

    private fun makeMenuButton(
        emoji: String,
        title: String,
        description: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = "$emoji  $title\n\n$description"
            textSize = 14f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(6), dp(6), dp(6), dp(6))

            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), border)
            }

            setOnClickListener { action() }
        }
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(
            View(this).apply {
                setBackgroundColor(Color.rgb(55, 70, 95))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )
    }

    private fun addMenuRow(
        parent: LinearLayout,
        first: Button,
        second: Button
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        row.addView(
            first,
            LinearLayout.LayoutParams(0, dp(118), 1f).apply {
                marginEnd = dp(5)
                bottomMargin = dp(8)
            }
        )

        row.addView(
            second,
            LinearLayout.LayoutParams(0, dp(118), 1f).apply {
                marginStart = dp(5)
                bottomMargin = dp(8)
            }
        )

        parent.addView(row)
    }

    private fun showHome() {
        chatScreenOpen = false
        voiceControls = null
        readButton = null
        continuousButton = null

        val root = createRoot()

        root.addView(
            makeText("🐺 مساعد الذئب", 28f),
            LinearLayout.LayoutParams(-1, dp(58))
        )

        root.addView(
            makeText(
                "مساعدك الشخصي الذكي",
                15f,
                Color.rgb(160, 190, 225)
            ),
            LinearLayout.LayoutParams(-1, dp(35))
        )

        addDivider(root)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }

        content.addView(
            makeText("أهلًا بك! 🐺\nماذا تريد أن أفعل اليوم؟", 19f),
            LinearLayout.LayoutParams(-1, dp(90))
        )

        addMenuRow(
            content,
            makeMenuButton(
                "💬🎙️",
                "المحادثة الذكية",
                "كتابة وصوت واستماع",
                ::showChat
            ),
            makeMenuButton(
                "🧠",
                "الذاكرة",
                "إدارة المعلومات"
            ) {
                showInfo(
                    "الذاكرة",
                    "الذاكرة الدائمة المحلية ستكون مرحلة التطوير التالية."
                )
            }
        )

        addMenuRow(
            content,
            makeMenuButton(
                "📱",
                "التحكم بالهاتف",
                "بصلاحياتك"
            ) {
                showInfo(
                    "التحكم بالهاتف",
                    "سنضيف أوامر الهاتف تدريجيًا، مع طلب الإذن قبل تنفيذ الإجراءات."
                )
            },
            makeMenuButton(
                "⚙️",
                "الإعدادات",
                "الصوت والتطبيق",
                ::showSettings
            )
        )

        content.addView(
            makeText(
                "نسخة تجريبية • التطوير مستمر",
                12f,
                Color.rgb(155, 170, 195)
            ),
            LinearLayout.LayoutParams(-1, dp(36))
        )

        val pageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }

        root.addView(
            pageScroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf(
            "تشغيل أو إيقاف قراءة الردود الصوتية",
            "اختبار الصوت",
            "معلومات عن التطبيق"
        )

        AlertDialog.Builder(this)
            .setTitle("إعدادات مساعد الذئب")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        readReplies = !readReplies

                        if (!readReplies) {
                            tts?.stop()
                        } else if (lastAssistantReply.isNotEmpty()) {
                            speak(lastAssistantReply)
                        }

                        showInfo(
                            "الصوت",
                            if (readReplies) {
                                "قراءة الردود مفعّلة."
                            } else {
                                "قراءة الردود متوقفة."
                            }
                        )
                    }

                    1 -> speak("أهلًا بك، أنا مساعد الذئب.")

                    2 -> showInfo(
                        "مساعد الذئب",
                        "هذه نسخة تجريبية. الردود الحالية محلية، " +
                            "والذكاء الاصطناعي المحلي سيكون مرحلة لاحقة."
                    )
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showChat() {
        chatScreenOpen = true

        val root = createRoot()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            makeButton("‹ الرئيسية").apply {
                setOnClickListener {
                    stopSingleListening()
                    tts?.stop()
                    hideKeyboard()
                    showHome()
                }
            },
            LinearLayout.LayoutParams(dp(105), dp(46))
        )

        header.addView(
            makeText("🐺 المحادثة الذكية", 20f),
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )

        root.addView(header)

        status = makeText(
            "● جاهز للمساعدة",
            13f,
            Color.rgb(100, 220, 160)
        ).apply {
            setPadding(0, dp(5), 0, dp(8))
        }

        root.addView(status)
        addDivider(root)

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(messages)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
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
                marginEnd = dp(7)
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

        continuousButton = makeButton(
            if (continuousListening) {
                "👂 الاستماع المستمر: يعمل"
            } else {
                "👂 الاستماع المستمر: متوقف"
            }
        )

        val stopButton = makeButton("⏹ إيقاف")

        voiceRow.addView(
            micButton,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(4)
            }
        )

        voiceRow.addView(
            continuousButton!!,
            LinearLayout.LayoutParams(0, dp(46), 1.45f).apply {
                marginEnd = dp(4)
            }
        )

        voiceRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(46), .8f)
        )

        voiceControls = voiceRow
        root.addView(voiceRow)

        readButton = makeButton(
            if (readReplies) {
                "🔊 قراءة الردود: مفعّلة"
            } else {
                "🔊 قراءة الردود: متوقفة"
            }
        )

        root.addView(
            readButton!!,
            LinearLayout.LayoutParams(-1, dp(43)).apply {
                topMargin = dp(4)
            }
        )

        setContentView(root)
        root.requestApplyInsets()

        if (messageTexts.isEmpty()) {
            addMessage(
                "أهلًا بك! أنا مساعد الذئب 🐺\n\n" +
                    "اكتب رسالتك أو اضغط «تحدث». " +
                    "ويمكنك تشغيل الاستماع المستمر بإذنك.",
                false
            )
        } else {
            for (i in messageTexts.indices) {
                renderMessage(
                    messageTexts[i],
                    messageOwners.getOrElse(i) { false }
                )
            }
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (
                actionId == EditorInfo.IME_ACTION_SEND ||
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

        continuousButton?.setOnClickListener {
            if (continuousListening) {
                stopContinuousListening()
            } else {
                requestContinuousListening()
            }
        }

        stopButton.setOnClickListener {
            stopSingleListening()
            stopContinuousListening()
            tts?.stop()
            setStatus("تم إيقاف الاستماع والصوت")
        }

        readButton?.setOnClickListener {
            readReplies = !readReplies

            readButton?.text = if (readReplies) {
                "🔊 قراءة الردود: مفعّلة"
            } else {
                "🔊 قراءة الردود: متوقفة"
            }

            if (readReplies && lastAssistantReply.isNotEmpty()) {
                speak(lastAssistantReply)
            } else if (!readReplies) {
                tts?.stop()
            }
        }
    }

    private fun hideKeyboard() {
        if (!::input.isInitialized) return

        val imm = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun requestContinuousListening() {
        AlertDialog.Builder(this)
            .setTitle("الاستماع المستمر")
            .setMessage(
                "سيستخدم مساعد الذئب الميكروفون أثناء تشغيل الخدمة، " +
                    "وسيظهر إشعار دائم به زر لإيقافها. هل توافق؟"
            )
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("أوافق") { _, _ ->
                startContinuousWithPermission()
            }
            .show()
    }

    private fun startContinuousWithPermission() {
        if (
            Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
            return
        }

        startContinuousService()
    }

    private fun startContinuousService() {
        try {
            val intent = Intent(
                this,
                VoiceAssistantService::class.java
            ).setAction(VoiceAssistantService.ACTION_START)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            continuousListening = true
            updateContinuousButton()
            setStatus("الاستماع المستمر قيد التشغيل")
        } catch (_: Exception) {
            continuousListening = false
            updateContinuousButton()
            setStatus(
                "تعذر تشغيل الاستماع المستمر. " +
                    "افتح التطبيق وراجع إذن الميكروفون."
            )
        }
    }

    private fun stopContinuousListening() {
        continuousListening = false

        try {
            startService(
                Intent(
                    this,
                    VoiceAssistantService::class.java
                ).setAction(VoiceAssistantService.ACTION_STOP)
            )
        } catch (_: Exception) {
        }

        updateContinuousButton()
        setStatus("الاستماع المستمر متوقف")
    }

    private fun startVoiceWithPermission() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("خدمة التعرف على الصوت غير متاحة")
            return
        }

        if (
            Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                102
            )
            return
        }

        startSingleListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            101 -> {
                if (granted) {
                    startContinuousService()
                } else {
                    setStatus("لم يتم السماح باستخدام الميكروفون")
                }
            }

            102 -> {
                if (granted) {
                    startSingleListening()
                } else {
                    setStatus("لم يتم السماح باستخدام الميكروفون")
                }
            }
        }
    }

    private fun startSingleListening() {
        if (continuousListening) {
            setStatus(
                "أوقف الاستماع المستمر أولًا لاستخدام التحدث المنفرد"
            )
            return
        }

        if (
            Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("خدمة التعرف على الصوت غير متاحة")
            return
        }

        try {
            stopSingleListening()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        setStatus("🎙️ أستمع إليك الآن...")
                    }

                    override fun onBeginningOfSpeech() {
                        setStatus("تفضل، أنا أستمع")
                    }

                    override fun onEndOfSpeech() {
                        setStatus("جارٍ فهم كلامك...")
                    }

                     override fun onResults(results: Bundle?) {
    val spoken = results
        ?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )
        ?.firstOrNull()

    if (!spoken.isNullOrBlank() && chatScreenOpen) {
        if (!handleLocalVoiceCommand(spoken)) {
            input.setText(spoken)
            input.setSelection(input.text.length)
            sendMessage()
        }
    } else {
        setStatus("لم أتعرف على الكلام")
    }

    stopSingleListening()
}

override fun onError(error: Int) {
    setStatus("لم أتعرف على الكلام، حاول مرة أخرى")
    stopSingleListening()
}

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )

            val speechIntent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ar-EG"
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            setStatus("تعذر تشغيل الميكروفون")
            stopSingleListening()
        }
    }

    private fun stopSingleListening() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
    }

    private fun updateContinuousButton() {
        continuousButton?.text = if (continuousListening) {
            "👂 الاستماع المستمر: يعمل"
        } else {
            "👂 الاستماع المستمر: متوقف"
        }
    }

    private fun sendMessage() {
    val text = input.text.toString().trim()
    if (text.isEmpty()) return
if (requestInProgress) {
    setStatus("انتظر حتى يصل الرد السابق...")
    return
}

requestInProgress = true
    addMessage("أنت:\n$text", true)

    input.setText("")
    hideKeyboard()

    setStatus("🐺 جارٍ إرسال رسالتك إلى مساعد الذئب...")

    // تجهيز آخر 12 رسالة سابقة لإرسال سياق المحادثة.
    val history = JSONArray()
    val endIndex = messageTexts.size - 1
    val startIndex = maxOf(0, endIndex - 12)

    for (i in startIndex until endIndex) {
        val original = messageTexts[i]

        val content = when {
            original.startsWith("أنت:\n") ->
                original.removePrefix("أنت:\n")

            original.startsWith("🐺 مساعد الذئب:\n") ->
                original.removePrefix("🐺 مساعد الذئب:\n")

            else -> original
        }

        val item = JSONObject()
        item.put(
            "role",
            if (messageOwners.getOrElse(i) { false }) {
                "user"
            } else {
                "assistant"
            }
        )
        item.put("content", content)

        history.put(item)
    }

    networkExecutor.execute {
        var reply: String

        try {
            val payload = JSONObject().apply {
                put("message", text)
                put("history", history)
            }

            val connection =
                URL(backendUrl).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 45000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.outputStream.use { output ->
                    output.write(
                        payload.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode = connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseText = responseStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

                if (responseCode !in 200..299) {
                    throw Exception("Server error")
                }

                val response = JSONObject(responseText)

                reply = response.optString("reply", "").trim()

                if (reply.isEmpty()) {
                    throw Exception("Empty response")
                }

            } finally {
                connection.disconnect()
            }

        } catch (_: Exception) {
            reply =
                "تعذر الاتصال بخادم الذكاء الاصطناعي حاليًا. " +
                "تأكد من اتصال الإنترنت وحالة الخادم، ثم حاول مرة أخرى."
        }

        mainHandler.post {
    requestInProgress = false

    if (isFinishing || isDestroyed) return@post

            lastAssistantReply = reply

            addMessage(
                "🐺 مساعد الذئب:\n$reply",
                false
            )

            setStatus("● جاهز للمساعدة")

            if (readReplies) {
                speak(reply)
            }
        }
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
        if (chatScreenOpen && ::status.isInitialized) {
            status.text = text
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messageTexts.add(text)
        messageOwners.add(isUser)

        if (chatScreenOpen && ::messages.isInitialized) {
            renderMessage(text, isUser)
        }
    }

    private fun renderMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            setPadding(dp(16), dp(12), dp(16), dp(12))

            background = GradientDrawable().apply {
                setColor(
                    if (isUser) {
                        Color.rgb(35, 75, 125)
                    } else {
                        panel
                    }
                )

                cornerRadius = dp(14).toFloat()

                setStroke(
                    dp(1),
                    if (isUser) {
                        Color.rgb(65, 115, 175)
                    } else {
                        border
                    }
                )
            }
        }

        messages.addView(
            bubble,
            LinearLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )

        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            "chat_texts",
            ArrayList(messageTexts)
        )

        outState.putBooleanArray(
            "chat_owners",
            BooleanArray(messageOwners.size) {
                messageOwners[it]
            }
        )

        outState.putBoolean("read_replies", readReplies)
        outState.putString("last_reply", lastAssistantReply)
        outState.putBoolean("chat_screen_open", chatScreenOpen)
        outState.putBoolean(
            "continuous_listening",
            continuousListening
        )

        super.onSaveInstanceState(outState)
    }

    // تنفيذ سلوك الرجوع نفسه على إصدارات أندرويد المختلفة.
    private fun handleBackPress() {
        if (chatScreenOpen) {
            stopSingleListening()
            tts?.stop()
            hideKeyboard()
            showHome()
        } else {
            AlertDialog.Builder(this)
                .setTitle("الخروج من مساعد الذئب")
                .setMessage("هل تريد الخروج من التطبيق؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("خروج") { _, _ ->
                    finish()
                }
                .show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackPress()
    }

    override fun onDestroy() {
        // إزالة معالج الرجوع قبل إنهاء النشاط.
        if (Build.VERSION.SDK_INT >= 33) {
            platformBackCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            platformBackCallback = null
        }

        stopSingleListening()
        tts?.stop()
        tts?.shutdown()

        if (receiverRegistered) {
            try {
                unregisterReceiver(voiceReceiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }
networkExecutor.shutdownNow()
        super.onDestroy()
    }
}
