package com.wolfassistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var entry: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("wolf_memory", MODE_PRIVATE) }
    private val history = mutableListOf<JSONObject>()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "🐺 مساعد الذئب"; textSize = 24f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(6))
        }
        val settings = Button(this).apply { text = "⚙"; contentDescription = "إعدادات الاتصال"; setOnClickListener { showSettings() } }
        heading.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(settings)
        val subtitle = TextView(this).apply {
            text = "محادثة خاصة • ذاكرة محلية • وضع هجين"; textSize = 13f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        }
        status = TextView(this).apply {
            textSize = 12f; setTextColor(Color.rgb(120, 200, 160)); gravity = Gravity.CENTER
            text = if (backendUrl().isBlank()) "الوضع المحلي — الخادم غير مضبوط" else "عنوان الخادم محفوظ"
            setPadding(0, 0, 0, dp(8))
        }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(messages) }
        entry = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setSingleLine(false); minLines = 1; maxLines = 4
            setBackgroundColor(Color.rgb(35, 45, 55)); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        send = Button(this).apply { text = "إرسال"; setOnClickListener { sendMessage() } }
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(entry, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val clear = Button(this).apply {
            text = "مسح الذاكرة المحلية"; setOnClickListener { clearMemory() }
        }
        root.addView(heading); root.addView(subtitle); root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(composer); root.addView(clear)
        setContentView(root)
        loadHistory()
        if (history.isEmpty()) addMessage("مساعد الذئب", "أهلًا بك! أستطيع حفظ سجل محادثتك على هذا الهاتف. لضمان الردود الذكية عبر الإنترنت، اضبط عنوان الخادم من زر الإعدادات. عند عدم الاتصال سأستخدم ردودًا محلية بسيطة.", false)
    }

    private fun sendMessage() {
        val text = entry.text.toString().trim()
        if (text.isEmpty()) return
        addMessage("أنت", text, true)
        entry.text.clear()
        val endpoint = backendUrl()
        if (endpoint.isBlank()) {
            addMessage("مساعد الذئب • محلي", localReply(text), true)
            status.text = "الوضع المحلي — الخادم غير مضبوط"
            return
        }
        send.isEnabled = false
        status.text = "جارٍ الاتصال بالخادم..."
        val requestHistory = JSONArray()
        history.takeLast(12).forEach { item ->
            val role = item.optString("role")
            val content = item.optString("content")
            if (role == "user" || role == "assistant") requestHistory.put(JSONObject().put("role", role).put("content", content))
        }
        executor.execute {
            var reply: String? = null
            var error = "تعذّر الاتصال بالخادم. تحقق من الإنترنت وعنوان الخادم."
            try {
                val url = URL(endpoint.trimEnd('/') + "/chat")
                require(url.protocol.equals("https", true)) { "يجب استخدام عنوان خادم آمن يبدأ بـ https://" }
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 12000; readTimeout = 45000
                    doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject().put("message", text).put("history", requestHistory).toString()
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = JSONObject(response.ifBlank { "{}" })
                if (code in 200..299) reply = json.optString("reply").takeIf { it.isNotBlank() }
                else error = when (code) {
                    503 -> "الخادم يعمل، لكن مزوّد الذكاء الاصطناعي لم يُضبط بعد."
                    429 -> "طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حاول مجددًا."
                    else -> "الخادم أعاد خطأ ($code). تحقق من إعداداته."
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message?.takeIf { it.contains("https") } ?: "تعذّر الاتصال بالخادم. تحقق من العنوان والإنترنت."
            }
            mainHandler.post {
                send.isEnabled = true
                if (reply != null) {
                    addMessage("مساعد الذئب", reply!!, true)
                    status.text = "متصل بالخادم"
                } else {
                    addMessage("مساعد الذئب", "$error\nيمكنك مواصلة المهام الأساسية محليًا.", true)
                    status.text = "الخادم غير متاح — الذاكرة المحلية محفوظة"
                }
            }
        }
    }

    private fun localReply(text: String): String {
        val q = text.lowercase()
        return when {
            q.contains("مرحبا") || q.contains("مرحبًا") || q.contains("السلام") || q.contains("اهلا") || q.contains("أهلا") -> "أهلًا بك! أنا الذئب. وضع المحادثة المحلي يعمل، لكن الإجابات المتقدمة تحتاج إلى إعداد الخادم."
            q.contains("اسمك") -> "أنا مساعد الذئب، مساعدك على الهاتف."
            q.contains("تذكر") || q.contains("ذاكرة") -> "أحفظ سجل المحادثة على هذا الجهاز. يمكنك مسحه من زر «مسح الذاكرة المحلية»."
            q.contains("الوقت") -> "لا أملك ساعة مدمجة في الوضع المحلي حاليًا."
            else -> "استلمت رسالتك وحفظتها محليًا. للرد على الأسئلة بذكاء، افتح ⚙ وأدخل عنوان خادم الذئب الآمن (HTTPS)."
        }
    }

    private fun addMessage(who: String, body: String, save: Boolean) {
        val view = TextView(this).apply {
            text = "$who\n$body"; textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(if (who == "أنت") Color.rgb(39, 70, 92) else Color.rgb(35, 45, 55))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, dp(10)); messages.addView(view, params)
        if (save && (who == "أنت" || who.startsWith("مساعد الذئب"))) {
            val role = if (who == "أنت") "user" else "assistant"
            history.add(JSONObject().put("role", role).put("content", body).put("who", who))
            while (history.size > 60) history.removeAt(0)
            persistHistory()
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadHistory() {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val who = item.optString("who", if (item.optString("role") == "user") "أنت" else "مساعد الذئب")
                val body = item.optString("content")
                history.add(item)
                val view = TextView(this).apply {
                    text = "$who\n$body"; textSize = 16f; setTextColor(Color.WHITE)
                    setBackgroundColor(if (who == "أنت") Color.rgb(39, 70, 92) else Color.rgb(35, 45, 55))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, dp(10)); messages.addView(view, params)
            }
        } catch (_: Exception) { history.clear() }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun persistHistory() { prefs.edit().putString("messages", JSONArray(history).toString()).apply() }
    private fun backendUrl() = prefs.getString("backend_url", "")?.trim().orEmpty()

    private fun showSettings() {
        val input = EditText(this).apply {
            hint = "https://your-server.example.com"; setSingleLine(true); setText(backendUrl())
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("إعدادات اتصال الذئب")
            .setMessage("أدخل عنوان الخادم الآمن فقط، دون /chat. لا تضع أي مفتاح سري هنا. اتركه فارغًا لاستخدام الوضع المحلي.")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val value = input.text.toString().trim().trimEnd('/')
                if (value.isNotBlank() && !value.startsWith("https://", true)) {
                    status.text = "العنوان غير صالح: يجب أن يبدأ بـ https://"
                } else {
                    prefs.edit().putString("backend_url", value).apply()
                    status.text = if (value.isBlank()) "الوضع المحلي — الخادم غير مضبوط" else "تم حفظ عنوان الخادم"
                }
            }
            .setNeutralButton("مسح العنوان") { _, _ -> prefs.edit().remove("backend_url").apply(); status.text = "الوضع المحلي — الخادم غير مضبوط" }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun clearMemory() {
        AlertDialog.Builder(this).setTitle("مسح الذاكرة المحلية؟")
            .setMessage("سيتم حذف سجل المحادثة المحفوظ على هذا الهاتف.")
            .setPositiveButton("مسح") { _, _ ->
                history.clear(); prefs.edit().remove("messages").apply(); messages.removeAllViews()
                addMessage("مساعد الذئب", "تم مسح الذاكرة المحلية. كيف أساعدك؟", false)
            }.setNegativeButton("إلغاء", null).show()
    }

    override fun onDestroy() { super.onDestroy(); executor.shutdownNow() }
}
