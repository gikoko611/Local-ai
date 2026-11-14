package com.gikoko.gilocalai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads text out of books/documents: txt, md, csv, json, html, pdf, epub, docx. */
object DocReader {
    private const val MAX_CHARS = 1_500_000

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun readLimited(ins: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var total = 0
        while (total < limit) {
            val r = ins.read(buf)
            if (r < 0) break
            out.write(buf, 0, r)
            total += r
        }
        return out.toByteArray()
    }

    fun read(ctx: Context, uri: Uri, name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val text = when (ext) {
            "pdf" -> readPdf(ctx, uri)
            "epub" -> readZip(ctx, uri) { entry, bytes ->
                val n = entry.lowercase()
                if (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) TextUtil.htmlToText(String(bytes, Charsets.UTF_8)) else null
            }
            "docx" -> readZip(ctx, uri) { entry, bytes ->
                if (entry == "word/document.xml") {
                    String(bytes, Charsets.UTF_8).replace("</w:p>", "\n").replace(Regex("<[^>]+>"), "")
                } else null
            }
            "html", "htm" -> TextUtil.htmlToText(readPlain(ctx, uri))
            else -> readPlain(ctx, uri)
        }
        return text.take(MAX_CHARS)
    }

    private fun readPlain(ctx: Context, uri: Uri): String {
        val ins = ctx.contentResolver.openInputStream(uri) ?: return ""
        return ins.use { String(readLimited(it, 6_000_000), Charsets.UTF_8) }
    }

    private fun readPdf(ctx: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        val ins = ctx.contentResolver.openInputStream(uri) ?: return ""
        return ins.use { stream ->
            PDDocument.load(stream).use { doc ->
                val stripper = PDFTextStripper()
                val n = doc.numberOfPages
                val sb = StringBuilder()
                var p = 1
                while (p <= n && sb.length < MAX_CHARS) {
                    stripper.startPage = p
                    stripper.endPage = minOf(n, p + 19)
                    sb.append(stripper.getText(doc)).append('\n')
                    p += 20
                }
                sb.toString()
            }
        }
    }

    private fun readZip(ctx: Context, uri: Uri, pick: (String, ByteArray) -> String?): String {
        val ins = ctx.contentResolver.openInputStream(uri) ?: return ""
        val sb = StringBuilder()
        ins.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (sb.length < MAX_CHARS) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val bytes = readLimited(zip, 3_000_000)
                    val t = pick(e.name, bytes)
                    if (t != null) sb.append(t).append('\n')
                }
            }
        }
        return sb.toString()
    }
}
