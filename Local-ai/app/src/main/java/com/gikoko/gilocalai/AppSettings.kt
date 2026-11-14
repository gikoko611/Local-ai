package com.gikoko.gilocalai

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val DEFAULT_SYSTEM_PROMPT =
    "You are GI Local AI, a helpful assistant running fully offline on the user's phone. " +
    "Answer clearly and briefly. Reply in the same language the user writes in (Burmese or English). " +
    "If you are not sure about something, say so instead of guessing."

class AppSettings(private val sp: SharedPreferences) {
    var modelPath by mutableStateOf(sp.getString("modelPath", "") ?: "")
    var modelUri by mutableStateOf(sp.getString("modelUri", "") ?: "")

    var temperature by mutableStateOf(sp.getFloat("temperature", 0.7f))
    var topP by mutableStateOf(sp.getFloat("topP", 0.9f))
    var topK by mutableStateOf(sp.getInt("topK", 40))
    var repeatPenalty by mutableStateOf(sp.getFloat("repeatPenalty", 1.1f))
    var maxTokens by mutableStateOf(sp.getInt("maxTokens", 256))
    var nCtx by mutableStateOf(sp.getInt("nCtx", 2048))
    var threads by mutableStateOf(sp.getInt("threads", 4))
    var template by mutableStateOf(sp.getString("template", "CHATML") ?: "CHATML")
    var systemPrompt by mutableStateOf(sp.getString("systemPrompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT)
    var historyTurns by mutableStateOf(sp.getInt("historyTurns", 4))

    // Auto-learn
    var learnFromChat by mutableStateOf(sp.getBoolean("learnFromChat", true))
    var autoOnline by mutableStateOf(sp.getBoolean("autoOnline", true))
    var useKnowledge by mutableStateOf(sp.getBoolean("useKnowledge", true))
    var topHits by mutableStateOf(sp.getInt("topHits", 3))
    var autoLoad by mutableStateOf(sp.getBoolean("autoLoad", true))

    fun save() {
        sp.edit()
            .putString("modelPath", modelPath)
            .putString("modelUri", modelUri)
            .putFloat("temperature", temperature)
            .putFloat("topP", topP)
            .putInt("topK", topK)
            .putFloat("repeatPenalty", repeatPenalty)
            .putInt("maxTokens", maxTokens)
            .putInt("nCtx", nCtx)
            .putInt("threads", threads)
            .putString("template", template)
            .putString("systemPrompt", systemPrompt)
            .putInt("historyTurns", historyTurns)
            .putBoolean("learnFromChat", learnFromChat)
            .putBoolean("autoOnline", autoOnline)
            .putBoolean("useKnowledge", useKnowledge)
            .putInt("topHits", topHits)
            .putBoolean("autoLoad", autoLoad)
            .apply()
    }
}
