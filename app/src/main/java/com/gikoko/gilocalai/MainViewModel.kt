package com.gikoko.gilocalai

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class UiMsg(val id: Long, val role: String, val text: String, val info: String = "")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app.getSharedPreferences("gi_settings", Context.MODE_PRIVATE))

    private val db = KnowledgeDb(app)
    private val retriever = Retriever(db)
    private val web = WebLearner(db)
    private val bridge = LlamaBridge()
    private var handle = 0L
    private var heldPfd: ParcelFileDescriptor? = null
    private val genMutex = Mutex()
    @Volatile private var stopRequested = false

    val messages = mutableStateListOf<UiMsg>()
    val knowledgeList = mutableStateListOf<KRow>()
    val glossaryList = mutableStateListOf<GRow>()
    val ggufFiles = mutableStateListOf<File>()

    var generating by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var modelName by mutableStateOf<String?>(null)
        private set
    var modelInfo by mutableStateOf("")
        private set
    var status by mutableStateOf("")
        private set
    var kindCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var glossaryCount by mutableStateOf(0)
        private set

    private var glossaryCache: List<GRow> = emptyList()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { retriever.reload() }
            val hist = withContext(Dispatchers.IO) { db.recentMessages(100) }
            messages.addAll(hist.map { UiMsg(it.id, it.role, it.content, it.info) })
            refreshLists()
            if (settings.autoLoad) autoLoadModel()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bridge.nativeStop()
        if (handle != 0L) {
            bridge.nativeFree(handle)
            handle = 0L
        }
        try { heldPfd?.close() } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ model loading

    private fun unloadInternal() {
        if (handle != 0L) {
            bridge.nativeFree(handle)
            handle = 0L
        }
        try { heldPfd?.close() } catch (_: Exception) {}
        heldPfd = null
        modelName = null
        modelInfo = ""
    }

    fun unloadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            genMutex.withLock { unloadInternal() }
            status = "Model unloaded"
        }
    }

    private fun finishLoad(name: String) {
        modelName = name
        modelInfo = try { bridge.nativeInfo(handle) } catch (e: Exception) { "" }
        status = "✅ Model ready: $name"
    }

    fun loadFromPath(path: String) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            status = "Loading model…"
            val f = File(path)
            val ok = withContext(Dispatchers.IO) {
                genMutex.withLock {
                    unloadInternal()
                    if (!f.exists()) {
                        status = "❌ File not found: $path"
                        false
                    } else {
                        handle = bridge.nativeLoad(path, settings.nCtx, settings.threads)
                        if (handle != 0L) {
                            settings.modelPath = path
                            settings.modelUri = ""
                            settings.save()
                            finishLoad(f.name)
                            true
                        } else {
                            status = "❌ Could not load model (bad GGUF, unsupported architecture or not enough RAM)"
                            false
                        }
                    }
                }
            }
            busy = false
            if (ok) refreshLists()
        }
    }

    fun loadFromUri(uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            status = "Loading model…"
            val ctx = getApplication<Application>()
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = DocReader.displayName(ctx, uri)
            withContext(Dispatchers.IO) {
                genMutex.withLock {
                    unloadInternal()
                    var loaded = false
                    val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (e: Exception) { null }
                    if (pfd != null) {
                        // Fast path: llama.cpp opens the file through /proc/self/fd (no copy)
                        handle = bridge.nativeLoad("/proc/self/fd/${pfd.fd}", settings.nCtx, settings.threads)
                        if (handle != 0L) {
                            heldPfd = pfd
                            settings.modelPath = ""
                            settings.modelUri = uri.toString()
                            loaded = true
                        } else {
                            try { pfd.close() } catch (_: Exception) {}
                        }
                    }
                    if (!loaded) {
                        // Fallback: copy into app storage, then load from there
                        status = "Copying model into app storage (one time)…"
                        val dst = copyToPrivate(ctx, uri, name)
                        if (dst != null) {
                            handle = bridge.nativeLoad(dst.path, settings.nCtx, settings.threads)
                            if (handle != 0L) {
                                settings.modelPath = dst.path
                                settings.modelUri = ""
                                loaded = true
                            }
                        }
                    }
                    if (loaded) {
                        settings.save()
                        finishLoad(name)
                    } else {
                        status = "❌ Could not load model (bad GGUF, unsupported architecture or not enough RAM)"
                    }
                }
            }
            busy = false
            refreshLists()
        }
    }

    private fun copyToPrivate(ctx: Context, uri: Uri, name: String): File? {
        return try {
            val dir = File(ctx.filesDir, "models").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dst = File(dir, safe)
            val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (dst.exists() && size > 0 && dst.length() == size) return dst
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
            } ?: return null
            dst
        } catch (e: Exception) {
            null
        }
    }

    private fun autoLoadModel() {
        val p = settings.modelPath
        if (p.isNotEmpty() && File(p).exists()) {
            loadFromPath(p)
        } else if (settings.modelUri.isNotEmpty()) {
            loadFromUri(Uri.parse(settings.modelUri))
        }
    }

    /** Re-load the current model (after changing context size or threads). */
    fun reloadModel() {
        if (settings.modelPath.isNotEmpty()) loadFromPath(settings.modelPath)
        else if (settings.modelUri.isNotEmpty()) loadFromUri(Uri.parse(settings.modelUri))
        else status = "No model selected yet"
    }

    fun scanModels() {
        viewModelScope.launch(Dispatchers.IO) {
            status = "Searching for .gguf files…"
            val found = ArrayList<File>()
            try {
                @Suppress("DEPRECATION")
                val root = Environment.getExternalStorageDirectory()
                root.walkTopDown().maxDepth(4).onEnter { it.name != "Android" }.forEach { f ->
                    if (f.isFile && f.extension.equals("gguf", true) && found.size < 60) found.add(f)
                }
            } catch (_: Exception) {
            }
            File(getApplication<Application>().filesDir, "models").listFiles()?.forEach { f ->
                if (f.extension.equals("gguf", true)) found.add(f)
            }
            withContext(Dispatchers.Main) {
                ggufFiles.clear()
                ggufFiles.addAll(found.distinctBy { it.absolutePath })
                status = if (found.isEmpty()) "No .gguf files found (check storage permission)" else "Found ${found.size} model file(s)"
            }
        }
    }

    // ------------------------------------------------------------------ chat

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return false
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun glossaryHints(query: String): List<String> {
        val low = query.lowercase()
        val out = ArrayList<String>()
        for (g in glossaryCache) {
            val hit = (g.my.isNotEmpty() && query.contains(g.my)) || (g.en.length >= 3 && low.contains(g.en.lowercase()))
            if (hit) out.add("${g.my} = ${g.en}")
            if (out.size >= 8) break
        }
        return out
    }

    private fun updateAssistant(id: Long, text: String, info: String) {
        try {
            val i = messages.indexOfFirst { it.id == id }
            if (i >= 0) messages[i] = messages[i].copy(text = text, info = info)
        } catch (_: Exception) {
        }
    }

    fun send(text: String) {
        val userText = text.trim()
        if (userText.isEmpty() || generating) return
        if (handle == 0L) {
            status = "⚠️ Load a GGUF model first (Settings tab)"
            return
        }
        generating = true
        stopRequested = false
        val history = messages.toList()
        val prevAssistant = history.lastOrNull { it.role == "assistant" }?.text
        val prevUser = history.lastOrNull { it.role == "user" }?.text
        val userId = System.nanoTime()
        val assistantId = userId + 1
        messages.add(UiMsg(userId, "user", userText))
        messages.add(UiMsg(assistantId, "assistant", ""))

        viewModelScope.launch(Dispatchers.Default) {
            val info = StringBuilder()
            try {
                db.addMessage("user", userText, "")

                // 1) learn from the conversation
                if (settings.learnFromChat) {
                    val learned = ChatLearner.learn(db, userText, prevUser, prevAssistant)
                    if (learned != null) {
                        info.append("🧠 learned $learned · ")
                        retriever.reload()
                        refreshLists()
                    }
                }

                // 2) retrieve from local knowledge, 3) learn online if nothing relevant is known
                fun lookup(): List<Hit> =
                    if (settings.useKnowledge) retriever.search(userText, settings.topHits).filter { it.coverage >= 0.25 } else emptyList()

                var hits = lookup()
                val best = hits.maxOfOrNull { it.coverage } ?: 0.0
                if (settings.autoOnline && userText.length >= 6 && best < 0.5 && isOnline()) {
                    status = "🌐 Learning online…"
                    val added = try { web.learnTopic(userText) } catch (e: Exception) { 0 }
                    if (added > 0) {
                        retriever.reload()
                        hits = lookup()
                        info.append("🌐 +$added web · ")
                        refreshLists()
                    }
                    status = ""
                }
                if (hits.isNotEmpty()) info.append("📚 ${hits.size} notes · ")

                val facts = db.recentByKind("fact", 6).map { it.text }
                val gloss = glossaryHints(userText)

                // 4) build the prompt so it fits the context window
                val tpl = PromptBuilder.tplOf(settings.template)
                val contextLimit = if (settings.nCtx <= 0) 512 else settings.nCtx
                val budget = contextLimit - settings.maxTokens - 48
                val hist = history.filter { it.text.isNotBlank() }
                var histStart = maxOf(0, hist.size - settings.historyTurns * 2)
                var hitCount = hits.size
                var factList = facts
                var prompt = ""
                while (true) {
                    val sys = PromptBuilder.systemText(settings.systemPrompt, factList, gloss, hits.take(hitCount))
                    val turns = hist.drop(histStart).map { PromptBuilder.Turn(it.role, it.text.take(1200)) } +
                        PromptBuilder.Turn("user", userText)
                    prompt = PromptBuilder.build(tpl, sys, turns)
                    val n = bridge.nativeCount(handle, prompt.toByteArray(Charsets.UTF_8))
                    if (n <= budget) break
                    when {
                        histStart < hist.size -> histStart++
                        hitCount > 0 -> hitCount--
                        factList.isNotEmpty() -> factList = factList.drop(1)
                        else -> break
                    }
                }

                // 5) generate
                val sb = StringBuilder()
                var lastUi = 0L
                val infoStr = info.toString()
                val rc = genMutex.withLock {
                    bridge.nativeGenerate(
                        handle,
                        prompt.toByteArray(Charsets.UTF_8),
                        settings.maxTokens,
                        settings.temperature,
                        settings.topP,
                        settings.topK,
                        settings.repeatPenalty,
                        TokenCallback { bytes ->
                            sb.append(String(bytes, Charsets.UTF_8))
                            val now = SystemClock.uptimeMillis()
                            if (now - lastUi > 60) {
                                lastUi = now
                                updateAssistant(assistantId, sb.toString(), infoStr)
                            }
                            !stopRequested
                        }
                    )
                }
                var finalText = sb.toString().trim()
                if (rc < 0) {
                    val err = when (rc) {
                        -3 -> "⚠️ Prompt is too long for the context window. Increase Context size in Settings or shorten the message."
                        -4 -> "⚠️ Not enough RAM to create the context. Lower Context size in Settings."
                        else -> "⚠️ Generation failed (code $rc)."
                    }
                    finalText = if (finalText.isEmpty()) err else finalText + "\n\n" + err
                } else if (finalText.isEmpty()) {
                    finalText = "…"
                }
                updateAssistant(assistantId, finalText, infoStr)
                db.addMessage("assistant", finalText, infoStr)
            } catch (e: Throwable) {
                updateAssistant(assistantId, "⚠️ Error: ${e.message}", "")
            } finally {
                generating = false
            }
        }
    }

    fun stop() {
        stopRequested = true
        bridge.nativeStop()
    }

    fun newChat() {
        if (generating) stop()
        messages.clear()
        viewModelScope.launch(Dispatchers.IO) { db.clearMessages() }
    }

    fun saveAnswer(msg: UiMsg) {
        val i = messages.indexOfFirst { it.id == msg.id }
        val q = if (i > 0) messages.subList(0, i).lastOrNull { it.role == "user" }?.text ?: "" else ""
        viewModelScope.launch(Dispatchers.IO) {
            val ok = db.addKnowledge("saved", "answer", if (q.isNotEmpty()) "Q: $q\nA: ${msg.text}" else msg.text)
            retriever.reload()
            refreshLists()
            status = if (ok) "💾 Saved to knowledge" else "Already saved"
        }
    }

    // ------------------------------------------------------------------ learning screen

    fun refreshLists() {
        viewModelScope.launch(Dispatchers.IO) {
            val k = db.listKnowledge(40)
            val g = db.allGlossary()
            val counts = db.countsByKind()
            glossaryCache = g
            withContext(Dispatchers.Main) {
                knowledgeList.clear(); knowledgeList.addAll(k)
                glossaryList.clear(); glossaryList.addAll(g.take(100))
                kindCounts = counts
                glossaryCount = g.size
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val ctx = getApplication<Application>()
            var total = 0
            for (u in uris) {
                val name = DocReader.displayName(ctx, u)
                status = "📖 Reading $name …"
                val added = withContext(Dispatchers.IO) {
                    try {
                        val text = DocReader.read(ctx, u, name)
                        val chunks = TextUtil.chunk(text)
                        db.deleteBySource(name)
                        db.addKnowledgeBatch("book", name, chunks.take(4000))
                    } catch (e: Throwable) {
                        status = "❌ $name: ${e.message}"
                        0
                    }
                }
                total += added
            }
            withContext(Dispatchers.IO) { retriever.reload() }
            status = "✅ Learned $total passages from ${uris.size} file(s)"
            busy = false
            refreshLists()
        }
    }

    fun learnUrl(url: String) {
        if (busy || url.isBlank()) return
        viewModelScope.launch {
            busy = true
            status = "🌐 Reading page…"
            val n = withContext(Dispatchers.IO) {
                try { web.learnUrl(url) } catch (e: Exception) { status = "❌ ${e.message}"; -1 }
            }
            if (n >= 0) {
                withContext(Dispatchers.IO) { retriever.reload() }
                status = "✅ Learned $n passages from the page"
            }
            busy = false
            refreshLists()
        }
    }

    fun learnTopic(topic: String) {
        if (busy || topic.isBlank()) return
        viewModelScope.launch {
            busy = true
            status = "🌐 Searching Wikipedia…"
            val n = withContext(Dispatchers.IO) {
                try { web.learnTopic(topic) } catch (e: Exception) { status = "❌ ${e.message}"; -1 }
            }
            if (n >= 0) {
                withContext(Dispatchers.IO) { retriever.reload() }
                status = if (n > 0) "✅ Learned $n passages about \"$topic\"" else "Nothing found for \"$topic\""
            }
            busy = false
            refreshLists()
        }
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.addKnowledge("fact", "note", text.trim())
            retriever.reload()
            status = "✅ Note saved"
            refreshLists()
        }
    }

    fun deleteKnowledge(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteKnowledge(id)
            retriever.reload()
            refreshLists()
        }
    }

    fun clearKnowledge() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearKnowledge(null)
            retriever.reload()
            status = "Knowledge cleared"
            refreshLists()
        }
    }

    fun addGlossary(my: String, en: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addGlossary(my, en)
            refreshLists()
        }
    }

    fun deleteGlossary(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteGlossary(id)
            refreshLists()
        }
    }
}
