package com.gikoko.gilocalai

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

/** Online learning: reads web pages and Wikipedia (Burmese + English) into the local knowledge base. */
class WebLearner(private val db: KnowledgeDb) {

    private fun get(urlStr: String, maxBytes: Int = 1_500_000): Pair<String, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "GILocalAI/1.0 (Android; personal offline assistant)")
        conn.setRequestProperty("Accept-Language", "my,en;q=0.8")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val ct = conn.contentType ?: ""
            val charsetName = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)
            val cs: Charset = try {
                if (charsetName != null) Charset.forName(charsetName) else Charsets.UTF_8
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            val bytes = conn.inputStream.use { ins ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val r = ins.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    total += r
                    if (total >= maxBytes) break
                }
                out.toByteArray()
            }
            return Pair(String(bytes, cs), ct)
        } finally {
            conn.disconnect()
        }
    }

    /** Reads one web page and stores it as knowledge. Returns number of chunks added. */
    fun learnUrl(rawUrl: String): Int {
        var url = rawUrl.trim()
        if (url.isEmpty()) return 0
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val (body, ct) = get(url)
        val text = if (ct.contains("html", true) || body.trimStart().startsWith("<")) TextUtil.htmlToText(body) else body
        val chunks = TextUtil.chunk(text.take(1_200_000))
        db.deleteBySource(url)
        return db.addKnowledgeBatch("web", url, chunks.take(1500))
    }

    /**
     * Looks a topic up on Wikipedia (language chosen from the query) and stores the intro text.
     * Also saves Burmese <-> English title pairs into the glossary, so the app slowly learns vocabulary.
     * Returns number of chunks added.
     */
    fun learnTopic(query: String, maxPages: Int = 2): Int {
        val q = query.trim().take(150)
        if (q.isEmpty()) return 0
        val langs = if (TextUtil.hasMyanmar(q)) listOf("my", "en") else listOf("en", "my")
        var added = 0
        for (lang in langs) {
            val other = if (lang == "my") "en" else "my"
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&generator=search&gsrsearch=${URLEncoder.encode(q, "UTF-8")}&gsrlimit=$maxPages" +
                "&prop=extracts%7Clanglinks&exintro=1&explaintext=1&exlimit=$maxPages&exchars=1500" +
                "&lllang=$other&lllimit=5"
            val body = try {
                get(url, 600_000).first
            } catch (e: Exception) {
                continue
            }
            val pages = try {
                JSONObject(body).getJSONObject("query").getJSONArray("pages")
            } catch (e: Exception) {
                continue
            }
            for (i in 0 until pages.length()) {
                val p = pages.getJSONObject(i)
                val title = p.optString("title")
                val extract = p.optString("extract").trim()
                if (extract.length < 40) continue
                val source = "wikipedia:$lang:$title"
                db.deleteBySource(source)
                added += db.addKnowledgeBatch("web", source, TextUtil.chunk("$title — $extract", 700))
                val ll = p.optJSONArray("langlinks")
                if (ll != null && ll.length() > 0) {
                    val t2 = ll.getJSONObject(0).optString("title")
                    if (t2.isNotBlank()) {
                        if (lang == "my") db.addGlossary(title, t2) else db.addGlossary(t2, title)
                    }
                }
            }
            if (added > 0) break
        }
        return added
    }
}
