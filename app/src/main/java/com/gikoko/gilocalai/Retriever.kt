package com.gikoko.gilocalai

data class Hit(
    val id: Long,
    val kind: String,
    val source: String,
    val text: String,
    val score: Double,
    val coverage: Double
)

/**
 * In-memory BM25-style index over the knowledge table.
 * Works for English words and for Burmese (character bigrams), no embeddings model needed.
 */
class Retriever(private val db: KnowledgeDb) {
    private class Doc(val id: Long, val kind: String, val source: String, val text: String, val len: Int)

    @Volatile private var docs: List<Doc> = emptyList()
    @Volatile private var index: HashMap<String, IntArray> = HashMap()
    @Volatile private var avgLen = 1.0

    @Synchronized
    fun reload() {
        val rows = db.allKnowledge()
        val list = ArrayList<Doc>(rows.size)
        val tmp = HashMap<String, ArrayList<Int>>()
        var total = 0L
        for ((i, r) in rows.withIndex()) {
            val g = TextUtil.grams(r.text)
            val tf = HashMap<String, Int>()
            for (x in g) tf[x] = (tf[x] ?: 0) + 1
            for ((k, v) in tf) {
                val l = tmp.getOrPut(k) { ArrayList() }
                l.add(i)
                l.add(v)
            }
            list.add(Doc(r.id, r.kind, r.source, r.text, maxOf(1, g.size)))
            total += g.size
        }
        val idx = HashMap<String, IntArray>(tmp.size * 2)
        for ((k, v) in tmp) idx[k] = v.toIntArray()
        docs = list
        index = idx
        avgLen = if (list.isEmpty()) 1.0 else maxOf(1.0, total.toDouble() / list.size)
    }

    fun search(query: String, k: Int): List<Hit> {
        val d = docs
        val idx = index
        if (d.isEmpty() || k <= 0) return emptyList()
        val qg = TextUtil.grams(query).distinct()
        if (qg.isEmpty()) return emptyList()
        val n = d.size
        val scores = DoubleArray(n)
        val matched = IntArray(n)
        val k1 = 1.2
        val b = 0.75
        for (g in qg) {
            val p = idx[g] ?: continue
            val df = p.size / 2
            val idf = Math.log(1.0 + (n - df + 0.5) / (df + 0.5))
            var j = 0
            while (j < p.size) {
                val di = p[j]
                val tf = p[j + 1]
                val norm = tf * (k1 + 1) / (tf + k1 * (1 - b + b * d[di].len / avgLen))
                scores[di] += idf * norm
                matched[di]++
                j += 2
            }
        }
        val order = (0 until n).filter { matched[it] > 0 }.sortedByDescending { scores[it] }.take(k)
        return order.map {
            val doc = d[it]
            Hit(doc.id, doc.kind, doc.source, doc.text, scores[it], matched[it].toDouble() / qg.size)
        }
    }
}
