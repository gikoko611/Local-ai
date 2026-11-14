package com.gikoko.gilocalai

/**
 * Learns from the conversation itself, without touching the model weights:
 *  - "remember ..." / "my name is ..." style messages become permanent facts
 *  - user corrections are stored so the next answer can take them into account
 */
object ChatLearner {
    private val rememberKeys = listOf(
        "remember", "don't forget", "dont forget", "my name is", "call me", "i live in", "i prefer",
        "i like ", "i work ", "from now on", "မှတ်ထား", "မှတ်ပါ", "မမေ့", "နာမည်", "ခေါ်ပါ"
    )
    private val correctionStarts = listOf(
        "no,", "no ", "wrong", "actually", "that's wrong", "that is wrong", "incorrect",
        "မဟုတ်ဘူး", "မှားတယ်", "မမှန်"
    )

    /** Returns "fact", "correction" or null if nothing was learned. */
    fun learn(db: KnowledgeDb, userText: String, prevUser: String?, prevAssistant: String?): String? {
        val t = userText.trim()
        if (t.length < 6 || t.length > 400) return null
        val low = t.lowercase()

        if (prevAssistant != null && correctionStarts.any { low.startsWith(it) }) {
            val about = prevUser?.let { " (about: ${it.take(80)})" } ?: ""
            return if (db.addKnowledge("fact", "correction", "Correction from user$about: $t")) "correction" else null
        }

        val isQuestion = t.endsWith("?") || t.endsWith("လား") || t.endsWith("လဲ") || t.endsWith("သလဲ")
        val wantsRemember = rememberKeys.any { low.contains(it) }
        if (wantsRemember && (!isQuestion || low.startsWith("remember") || low.contains("မှတ်ထား"))) {
            return if (db.addKnowledge("fact", "chat", t)) "fact" else null
        }
        return null
    }
}
