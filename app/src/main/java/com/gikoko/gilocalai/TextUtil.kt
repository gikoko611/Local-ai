package com.gikoko.gilocalai

import java.util.Locale
import java.util.regex.Pattern

object TextUtil {
    private val stop = setOf(
        "the", "a", "an", "is", "are", "of", "to", "and", "in", "on", "for", "what", "how", "why", "who",
        "where", "when", "do", "does", "you", "me", "my", "it", "this", "that", "with", "be", "can", "about", "tell"
    )

    /** Scripts written without spaces between words (Burmese, CJK...) are indexed as character bigrams. */
    private fun isSpaceless(cp: Int): Boolean =
        (cp in 0x1000..0x109F && cp != 0x104A && cp != 0x104B) ||
            cp in 0xAA60..0xAA7F || cp in 0xA9E0..0xA9FF ||
            cp in 0x4E00..0x9FFF || cp in 0x3040..0x30FF || cp in 0xAC00..0xD7AF

    fun hasMyanmar(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp in 0x1000..0x109F) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun grams(text: String): List<String> {
        val out = ArrayList<String>()
        val s = text.lowercase(Locale.ROOT)
        val word = StringBuilder()
        val run = ArrayList<Int>()

        fun flushWord() {
            if (word.length >= 2) {
                val w = word.toString()
                if (w !in stop) out.add(w)
            }
            word.setLength(0)
        }

        fun flushRun() {
            if (run.size == 1) {
                out.add(String(Character.toChars(run[0])))
            } else {
                for (i in 0 until run.size - 1) {
                    val sb = StringBuilder()
                    sb.appendCodePoint(run[i])
                    sb.appendCodePoint(run[i + 1])
                    out.add(sb.toString())
                }
            }
            run.clear()
        }

        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isSpaceless(cp) -> { flushWord(); run.add(cp) }
                Character.isLetterOrDigit(cp) -> { flushRun(); word.appendCodePoint(cp) }
                else -> { flushWord(); flushRun() }
            }
        }
        flushWord()
        flushRun()
        return out
    }

    private val sentenceSplit: Pattern = Pattern.compile("(?<=\u104B)|(?<=[.!?])\\s+|\\n+")

    /** Splits long text into ~maxLen sized pieces on sentence boundaries (Burmese "။" aware). */
    fun chunk(raw: String, maxLen: Int = 600, minLen: Int = 20): List<String> {
        val text = raw.replace("\r", "")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (text.isEmpty()) return emptyList()
        val sentences = ArrayList<String>()
        for (part in sentenceSplit.split(text)) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if (p.length <= maxLen) {
                sentences.add(p)
            } else {
                var k = 0
                while (k < p.length) {
                    sentences.add(p.substring(k, minOf(p.length, k + maxLen)))
                    k += maxLen
                }
            }
        }
        val chunks = ArrayList<String>()
        val cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && cur.length + s.length + 1 > maxLen) {
                chunks.add(cur.toString())
                cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotEmpty()) chunks.add(cur.toString())
        return chunks.filter { it.length >= minLen }
    }

    private fun decodeEntities(t: String): String {
        var s = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
        s = Regex("&#(\\d+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.let { code ->
                runCatching { String(Character.toChars(code)) }.getOrNull()
            } ?: ""
        }
        return s
    }

    fun htmlToText(html: String): String {
        var s = html
        s = Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>|</tr>").replace(s, "\n")
        s = Regex("<[^>]+>").replace(s, " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[ \t\u00A0]+"), " ").replace(Regex("\\s*\n\\s*"), "\n")
        return s.trim()
    }
}
