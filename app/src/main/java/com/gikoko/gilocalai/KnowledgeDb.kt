package com.gikoko.gilocalai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class KRow(val id: Long, val kind: String, val source: String, val text: String, val created: Long)
data class GRow(val id: Long, val my: String, val en: String)
data class MsgRow(val id: Long, val role: String, val content: String, val info: String)

/** Local store for everything the app "learns": knowledge chunks, Burmese-English glossary, chat history. */
class KnowledgeDb(ctx: Context) : SQLiteOpenHelper(ctx, "gi_knowledge.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE knowledge(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, " +
                "source TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_k_kind ON knowledge(kind)")
        db.execSQL("CREATE INDEX idx_k_source ON knowledge(source)")
        db.execSQL(
            "CREATE TABLE glossary(id INTEGER PRIMARY KEY AUTOINCREMENT, my TEXT NOT NULL, " +
                "en TEXT NOT NULL, created INTEGER NOT NULL, UNIQUE(my, en))"
        )
        db.execSQL(
            "CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, " +
                "content TEXT NOT NULL, info TEXT, created INTEGER NOT NULL)"
        )
        val seed = listOf(
            "မင်္ဂလာပါ" to "hello", "ကျေးဇူးတင်ပါတယ်" to "thank you", "ရေ" to "water", "ထမင်း" to "rice",
            "စာအုပ်" to "book", "ကျောင်း" to "school", "ဆရာ" to "teacher", "ကျောင်းသား" to "student",
            "အိမ်" to "house", "မြို့" to "city", "နိုင်ငံ" to "country", "မြန်မာ" to "Myanmar",
            "ရန်ကုန်" to "Yangon", "မန္တလေး" to "Mandalay", "ဘုရား" to "pagoda", "ကွန်ပျူတာ" to "computer",
            "ဖုန်း" to "phone", "အင်တာနက်" to "internet", "ငွေ" to "money", "အချိန်" to "time",
            "ဒီနေ့" to "today", "မနက်ဖြန်" to "tomorrow", "မနေ့က" to "yesterday", "ကူညီပါ" to "help",
            "ဘာလဲ" to "what is", "ဘယ်လို" to "how", "ဘာကြောင့်" to "why", "ဘယ်မှာ" to "where",
            "ဘယ်သူ" to "who", "ကျန်းမာရေး" to "health", "အလုပ်" to "work", "မိသားစု" to "family",
            "သူငယ်ချင်း" to "friend", "အမေ" to "mother", "အဖေ" to "father"
        )
        val now = System.currentTimeMillis()
        for ((my, en) in seed) {
            val cv = ContentValues()
            cv.put("my", my); cv.put("en", en); cv.put("created", now)
            db.insertWithOnConflict("glossary", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- knowledge ----------
    fun addKnowledge(kind: String, source: String, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val db = writableDatabase
        db.rawQuery("SELECT 1 FROM knowledge WHERE kind=? AND text=? LIMIT 1", arrayOf(kind, t)).use {
            if (it.moveToFirst()) return false
        }
        val cv = ContentValues()
        cv.put("kind", kind); cv.put("source", source); cv.put("text", t)
        cv.put("created", System.currentTimeMillis())
        return db.insert("knowledge", null, cv) > 0
    }

    fun addKnowledgeBatch(kind: String, source: String, chunks: List<String>): Int {
        if (chunks.isEmpty()) return 0
        val db = writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (c in chunks) {
                val cv = ContentValues()
                cv.put("kind", kind); cv.put("source", source); cv.put("text", c); cv.put("created", now)
                if (db.insert("knowledge", null, cv) > 0) n++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    fun deleteBySource(source: String) {
        writableDatabase.delete("knowledge", "source=?", arrayOf(source))
    }

    fun deleteKnowledge(id: Long) {
        writableDatabase.delete("knowledge", "id=?", arrayOf(id.toString()))
    }

    fun clearKnowledge(kind: String? = null) {
        if (kind == null) writableDatabase.delete("knowledge", null, null)
        else writableDatabase.delete("knowledge", "kind=?", arrayOf(kind))
    }

    private fun readRows(sql: String, args: Array<String>?): List<KRow> {
        val out = ArrayList<KRow>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(KRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
        }
        return out
    }

    fun allKnowledge(): List<KRow> =
        readRows("SELECT id, kind, source, text, created FROM knowledge ORDER BY id", null)

    fun listKnowledge(limit: Int): List<KRow> =
        readRows("SELECT id, kind, source, text, created FROM knowledge ORDER BY id DESC LIMIT $limit", null)

    fun recentByKind(kind: String, limit: Int): List<KRow> =
        readRows("SELECT id, kind, source, text, created FROM knowledge WHERE kind=? ORDER BY id DESC LIMIT $limit", arrayOf(kind))

    fun countsByKind(): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        readableDatabase.rawQuery("SELECT kind, COUNT(*) FROM knowledge GROUP BY kind", null).use { c ->
            while (c.moveToNext()) m[c.getString(0)] = c.getInt(1)
        }
        return m
    }

    // ---------- glossary ----------
    fun addGlossary(my: String, en: String): Boolean {
        val a = my.trim(); val b = en.trim()
        if (a.isEmpty() || b.isEmpty() || a == b) return false
        val cv = ContentValues()
        cv.put("my", a); cv.put("en", b); cv.put("created", System.currentTimeMillis())
        return writableDatabase.insertWithOnConflict("glossary", null, cv, SQLiteDatabase.CONFLICT_IGNORE) > 0
    }

    fun deleteGlossary(id: Long) {
        writableDatabase.delete("glossary", "id=?", arrayOf(id.toString()))
    }

    fun allGlossary(): List<GRow> {
        val out = ArrayList<GRow>()
        readableDatabase.rawQuery("SELECT id, my, en FROM glossary ORDER BY id DESC LIMIT 3000", null).use { c ->
            while (c.moveToNext()) out.add(GRow(c.getLong(0), c.getString(1), c.getString(2)))
        }
        return out
    }

    // ---------- chat history ----------
    fun addMessage(role: String, content: String, info: String): Long {
        val cv = ContentValues()
        cv.put("role", role); cv.put("content", content); cv.put("info", info)
        cv.put("created", System.currentTimeMillis())
        return writableDatabase.insert("messages", null, cv)
    }

    fun recentMessages(limit: Int): List<MsgRow> {
        val out = ArrayList<MsgRow>()
        readableDatabase.rawQuery(
            "SELECT id, role, content, COALESCE(info,'') FROM messages ORDER BY id DESC LIMIT $limit", null
        ).use { c ->
            while (c.moveToNext()) out.add(MsgRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return out.reversed()
    }

    fun clearMessages() {
        writableDatabase.delete("messages", null, null)
    }
}
