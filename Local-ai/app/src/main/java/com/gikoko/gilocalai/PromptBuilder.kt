package com.gikoko.gilocalai

object PromptBuilder {
    enum class Tpl(val label: String) {
        CHATML("ChatML (Qwen and many others)"),
        LLAMA3("Llama 3"),
        GEMMA("Gemma"),
        PLAIN("Plain text")
    }

    data class Turn(val role: String, val text: String)

    fun tplOf(name: String): Tpl = try {
        Tpl.valueOf(name)
    } catch (e: Exception) {
        Tpl.CHATML
    }

    /** System text = base prompt + remembered facts + glossary hints + retrieved knowledge. */
    fun systemText(base: String, facts: List<String>, gloss: List<String>, hits: List<Hit>): String {
        val sb = StringBuilder(base.trim())
        if (facts.isNotEmpty()) {
            sb.append("\n\nThings to remember about the user:\n")
            for (f in facts) sb.append("- ").append(f.take(300)).append('\n')
        }
        if (gloss.isNotEmpty()) {
            sb.append("\nBurmese-English word hints:\n")
            for (g in gloss) sb.append("- ").append(g).append('\n')
        }
        if (hits.isNotEmpty()) {
            sb.append("\nKnowledge notes (use them if relevant, ignore them if not):\n")
            hits.forEachIndexed { i, h -> sb.append('[').append(i + 1).append("] ").append(h.text.take(700)).append('\n') }
        }
        return sb.toString().trimEnd()
    }

    fun build(tpl: Tpl, system: String, turns: List<Turn>): String {
        val sb = StringBuilder()
        when (tpl) {
            Tpl.CHATML -> {
                sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                for (t in turns) sb.append("<|im_start|>").append(t.role).append('\n').append(t.text).append("<|im_end|>\n")
                sb.append("<|im_start|>assistant\n")
            }
            Tpl.LLAMA3 -> {
                // BOS is added by the tokenizer
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(system).append("<|eot_id|>")
                for (t in turns) sb.append("<|start_header_id|>").append(t.role).append("<|end_header_id|>\n\n").append(t.text).append("<|eot_id|>")
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            Tpl.GEMMA -> {
                var first = true
                for (t in turns) {
                    val role = if (t.role == "assistant") "model" else "user"
                    sb.append("<start_of_turn>").append(role).append('\n')
                    if (first && role == "user") {
                        sb.append(system).append("\n\n")
                        first = false
                    }
                    sb.append(t.text).append("<end_of_turn>\n")
                }
                sb.append("<start_of_turn>model\n")
            }
            Tpl.PLAIN -> {
                sb.append(system).append("\n\n")
                for (t in turns) {
                    sb.append(if (t.role == "assistant") "Assistant: " else "User: ").append(t.text).append('\n')
                }
                sb.append("Assistant: ")
            }
        }
        return sb.toString()
    }
}
