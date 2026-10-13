package com.wolfassistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

private val bg = Color.rgb(15, 20, 32)
private val panel = Color.rgb(27, 35, 51)
private val accent = Color.rgb(70, 150, 255)
private lateinit var messages: LinearLayout
private lateinit var input: EditText
private lateinit var scroll: ScrollView

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        setPadding(20, 16, 20, 12)
    }

    val header = TextView(this).apply {
        text = "🐺 مساعد الذئب"
        textSize = 27f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(8, 18, 8, 8)
    }
    root.addView(header)

    val status = TextView(this).apply {
        text = "● جاهز للمساعدة"
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(100, 220, 160))
        setPadding(8, 0, 8, 16)
    }
    root.addView(status)

    val divider = View(this).apply {
        setBackgroundColor(Color.rgb(55, 70, 95))
    }
    root.addView(divider, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 1
    ))

    scroll = ScrollView(this)
    messages = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 12, 0, 12)
    }
    scroll.addView(messages)
    root.addView(scroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
    ))

    val welcome = TextView(this).apply {
        text = "أهلًا بك! أنا مساعد الذئب 🐺\n\nأنا جاهز لمساعدتك في الأسئلة والأفكار والبرمجة. اكتب رسالتك بالأسفل لنبدأ."
        textSize = 17f
        setTextColor(Color.WHITE)
        setPadding(16, 18, 16, 18)
        setBackgroundColor(panel)
    }
    messages.addView(welcome)

    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 12, 0, 4)
    }

    input = EditText(this).apply {
        hint = "اكتب رسالتك هنا..."
        textSize = 16f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.LTGRAY)
        setPadding(14, 8, 14, 8)
        setSingleLine(true)
        setBackgroundColor(panel)
    }
    row.addView(input, LinearLayout.LayoutParams(0, 54, 1f))

    val send = Button(this).apply {
        text = "إرسال"
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(accent)
        )
    }
    row.addView(send)

    root.addView(row)
    setContentView(root)

    send.setOnClickListener { sendMessage() }
    input.setOnEditorActionListener { _, _, _ ->
        sendMessage()
        true
    }
}

private fun sendMessage() {
    val text = input.text.toString().trim()
    if (text.isEmpty()) return

    addMessage("أنت: $text", true)
    input.setText("")

    val reply = when {
        text.contains("اسمك") ->
            "أنا مساعد الذئب 🐺، مساعدك الذكي."
        text.contains("مرحبا") || text.contains("أهلا") ||
            text.contains("السلام") ->
            "أهلًا بك! كيف أقدر أساعدك؟"
        text.contains("الوقت") ->
            "الوقت الحالي على جهازك: " +
                java.text.SimpleDateFormat(
                    "hh:mm a", java.util.Locale.getDefault()
                ).format(java.util.Date())
        else ->
            "وصلتني رسالتك. الواجهة تعمل بنجاح، لكن الردود الذكية تحتاج إلى ربط التطبيق بخادم الذكاء الاصطناعي."
    }

    addMessage("🐺 مساعد الذئب:\n$reply", false)
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

}
