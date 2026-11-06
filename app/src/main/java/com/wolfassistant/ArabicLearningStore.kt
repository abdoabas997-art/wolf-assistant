
package com.wolfassistant

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class ArabicLearningStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "wolf_arabic_learning",
        Context.MODE_PRIVATE
    )

    private val correctionsKey = "arabic_corrections"
    private val vocabularyKey = "personal_vocabulary"

    private fun normalize(text: String): String {
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    fun learnCorrection(
        recognizedText: String,
        correctText: String
    ): Boolean {
        val wrong = normalize(recognizedText)
        val correct = correctText.trim()

        if (wrong.isBlank() || correct.isBlank()) return false

        val data = JSONObject(
            preferences.getString(correctionsKey, "{}") ?: "{}"
        )

        data.put(wrong, correct)

        preferences.edit()
            .putString(correctionsKey, data.toString())
            .apply()

        return true
    }

    fun applyCorrections(text: String): String {
        val data = JSONObject(
            preferences.getString(correctionsKey, "{}") ?: "{}"
        )

        var result = text

        val keys = data.keys().asSequence()
            .toList()
            .sortedByDescending { it.length }

        for (key in keys) {
            val correct = data.optString(key, "")
            if (correct.isBlank()) continue

            val pattern = Regex(
                Regex.escape(key),
                RegexOption.IGNORE_CASE
            )

            result = pattern.replace(result) { correct }
        }

        return result
    }

    fun saveVocabulary(word: String) {
        val value = word.trim()
        if (value.isBlank()) return

        val data = JSONObject(
            preferences.getString(vocabularyKey, "{}") ?: "{}"
        )

        data.put(normalize(value), value)

        preferences.edit()
            .putString(vocabularyKey, data.toString())
            .apply()
    }

    fun getVocabulary(): List<String> {
        val data = JSONObject(
            preferences.getString(vocabularyKey, "{}") ?: "{}"
        )

        return data.keys().asSequence()
            .map { data.optString(it) }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun getCorrectionCount(): Int {
        val data = JSONObject(
            preferences.getString(correctionsKey, "{}") ?: "{}"
        )

        return data.length()
    }

    fun clearLearning() {
        preferences.edit()
            .remove(correctionsKey)
            .remove(vocabularyKey)
            .apply()
    }
}
