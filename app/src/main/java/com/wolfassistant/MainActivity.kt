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

    // معالجة زر الرجوع في إصدارات أندرويد الحديثة.
    private var platformBackCallback:
        android.window.OnBackInvokedCallback? = null

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.wolfassistant.VOICE_RESULT") return

            val spoken = intent.getStringExtra("spoken_text")
            if (!spoken.isNullOrBlank() && chatScreenOpen) {
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
