package com.inkagent.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The user's own study library: folders, tags and items, persisted in filesDir/userlib/index.json.
 * Built-in lecture notes / exercise chapters / CMC papers are seeded in as items (id = their
 * built-in id) so they can be moved, retitled, tagged or hidden like anything imported.
 * Imported files live in filesDir/userlib/files/.
 */
class UserLibrary(private val ctx: Context, private val builtin: Library) {
    class Folder(val id: String, var name: String, var parent: String)
    class Item(
        val id: String, var title: String, val type: String, // note | set | file | text
        var folder: String, val tags: MutableList<String>, val added: Long,
        var source: String, var mime: String, var file: String, var url: String,
    ) {
        fun json() = JSONObject().put("id", id).put("title", title).put("type", type).put("folder", folder)
            .put("tags", JSONArray(tags)).put("added", added).put("source", source).put("mime", mime)
            .put("file", file).put("url", url)
    }

    val root = File(ctx.filesDir, "userlib")
    val filesDir = File(root, "files")
    private val indexFile = File(root, "index.json")
    val folders = LinkedHashMap<String, Folder>()
    val items = LinkedHashMap<String, Item>()
    private val removedBuiltins = HashSet<String>()
    private var loaded = false
    /** Bumped on every change, so open screens can refresh. */
    @Volatile var version = 0L; private set
    var onChange: (() -> Unit)? = null

    @Synchronized fun ensure() {
        if (loaded) return
        loaded = true
        filesDir.mkdirs()
        runCatching {
            if (indexFile.exists()) {
                val o = JSONObject(indexFile.readText())
                o.optJSONArray("folders")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { f -> folders[f.getString("id")] = Folder(f.getString("id"), f.optString("name"), f.optString("parent")) } }
                o.optJSONArray("items")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { j ->
                    items[j.getString("id")] = Item(j.getString("id"), j.optString("title"), j.optString("type"), j.optString("folder"),
                        j.optJSONArray("tags")?.let { t -> MutableList(t.length()) { k -> t.optString(k) } } ?: mutableListOf(),
                        j.optLong("added"), j.optString("source"), j.optString("mime"), j.optString("file"), j.optString("url"))
                } }
                o.optJSONArray("removedBuiltins")?.let { a -> for (i in 0 until a.length()) removedBuiltins.add(a.optString(i)) }
            }
        }.onFailure { com.inkagent.AppLog.w("UserLib", "index unreadable, reseeding", it) }
        seed()
    }

    private fun folderPath(vararg names: String): String {
        var parent = ""
        for (n in names) {
            val f = folders.values.firstOrNull { it.parent == parent && it.name == n } ?: Folder(newId("f"), n, parent).also { folders[it.id] = it }
            parent = f.id
        }
        return parent
    }

    /** Add any built-in the index has not seen yet (first run, or new content in an update). */
    private fun seed() {
        builtin.ensure()
        var changed = false
        if (folders.isEmpty()) { folderPath("收件箱"); changed = true }
        val subj = mapOf("analysis" to "数学分析", "algebra" to "高等代数")
        for (n in builtin.notes) if (n.id !in items && n.id !in removedBuiltins) {
            val s = subj[n.subject] ?: n.subject
            items[n.id] = Item(n.id, n.title, "note", folderPath("讲义", s), mutableListOf(s, "讲义"), 0, "内置", "text/markdown", "", "")
            changed = true
        }
        for (set in builtin.sets.values) if (set.id !in items && set.id !in removedBuiltins) {
            val (folder, tags) = if (set.kind == "cmc") {
                val track = set.meta.optString("track").ifBlank { "其他" }
                folderPath("CMC 真题", track) to mutableListOf("竞赛", track)
            } else {
                val s = subj[set.meta.optString("subjectKey")] ?: "习题"
                folderPath("分章习题", s) to mutableListOf(s, "习题")
            }
            items[set.id] = Item(set.id, set.title, "set", folder, tags, 0, "内置", "application/json", "", "")
            changed = true
        }
        if (changed) save()
    }

    private fun newId(p: String) = p + "-" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (1000..9999).random()

    @Synchronized private fun save() {
        root.mkdirs()
        val o = JSONObject()
            .put("folders", JSONArray(folders.values.map { JSONObject().put("id", it.id).put("name", it.name).put("parent", it.parent) }))
            .put("items", JSONArray(items.values.map { it.json() }))
            .put("removedBuiltins", JSONArray(removedBuiltins.toList()))
        val tmp = File(root, "index.json.tmp"); tmp.writeText(o.toString()); tmp.renameTo(indexFile)
        version++
        onChange?.invoke()
    }

    // ---------- queries ----------

    @Synchronized fun folderName(id: String): String =
        if (id.isBlank()) "（根目录）" else generateSequence(folders[id]) { folders[it.parent] }.map { it.name }.toList().reversed().joinToString(" / ")

    /** Resolve a folder given as id or as a "a/b" path (created when [create]). */
    @Synchronized fun resolveFolder(spec: String?, create: Boolean = false): String? {
        ensure()
        val s = spec?.trim().orEmpty()
        if (s.isEmpty() || s == "/" || s == "根目录") return ""
        if (s in folders) return s
        val parts = s.split('/', '／').map { it.trim() }.filter { it.isNotEmpty() }
        var parent = ""
        for (p in parts) {
            val f = folders.values.firstOrNull { it.parent == parent && it.name == p }
                ?: if (create) Folder(newId("f"), p, parent).also { folders[it.id] = it } else return null
            parent = f.id
        }
        if (create) save()
        return parent
    }

    @Synchronized fun subtree(id: String): Set<String> {
        val out = HashSet<String>(); out.add(id)
        var grew = true
        while (grew) { grew = false; for (f in folders.values) if (f.parent in out && out.add(f.id)) grew = true }
        return out
    }

    @Synchronized fun allTags(): List<Pair<String, Int>> {
        ensure()
        val m = LinkedHashMap<String, Int>()
        items.values.forEach { it.tags.forEach { t -> m[t] = (m[t] ?: 0) + 1 } }
        return m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    @Synchronized fun list(folder: String?, recursive: Boolean, tag: String?, query: String?): List<Item> {
        ensure()
        val fset = folder?.let { if (recursive) subtree(it) else setOf(it) }
        val terms = query?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        return items.values.filter { it ->
            (fset == null || it.folder in fset) && (tag.isNullOrBlank() || tag in it.tags) &&
                (terms.isEmpty() || (it.title + " " + it.tags.joinToString(" ") + " " + it.source).lowercase().let { h -> terms.all { t -> t in h } })
        }
    }

    @Synchronized fun catalogJson(): String {
        ensure()
        return JSONObject()
            .put("folders", JSONArray(folders.values.map { JSONObject().put("id", it.id).put("name", it.name).put("parent", it.parent) }))
            .put("items", JSONArray(items.values.map { it.json() }))
            .put("tags", JSONArray(allTags().map { JSONObject().put("tag", it.first).put("n", it.second) }))
            .toString()
    }

    // ---------- edits ----------

    @Synchronized fun createFolder(name: String, parent: String): Folder {
        ensure()
        val clean = name.trim().ifEmpty { "新文件夹" }
        folders.values.firstOrNull { it.parent == parent && it.name == clean }?.let { return it }
        return Folder(newId("f"), clean, parent).also { folders[it.id] = it; save() }
    }

    @Synchronized fun renameFolder(id: String, name: String) { folders[id]?.let { it.name = name.trim().ifEmpty { it.name }; save() } }

    @Synchronized fun moveFolder(id: String, parent: String): Boolean {
        val f = folders[id] ?: return false
        if (parent in subtree(id)) return false
        f.parent = parent; save(); return true
    }

    /** Delete a folder; its items and subfolders move up to its parent. */
    @Synchronized fun deleteFolder(id: String) {
        val f = folders.remove(id) ?: return
        folders.values.filter { it.parent == id }.forEach { it.parent = f.parent }
        items.values.filter { it.folder == id }.forEach { it.folder = f.parent }
        save()
    }

    @Synchronized fun rename(id: String, title: String) { items[id]?.let { it.title = title.trim().ifEmpty { it.title }; save() } }

    @Synchronized fun move(ids: List<String>, folder: String): Int { var n = 0; ids.forEach { items[it]?.let { i -> i.folder = folder; n++ } }; if (n > 0) save(); return n }

    @Synchronized fun tag(ids: List<String>, add: List<String>, remove: List<String>): Int {
        var n = 0
        ids.forEach { id -> items[id]?.let { i ->
            i.tags.removeAll(remove.toSet())
            add.map { it.trim().trimStart('#') }.filter { it.isNotEmpty() && it !in i.tags }.forEach { i.tags.add(it) }
            n++
        } }
        if (n > 0) save()
        return n
    }

    @Synchronized fun delete(ids: List<String>): Int {
        var n = 0
        ids.forEach { id -> items.remove(id)?.let { i ->
            if (i.type == "note" || i.type == "set") removedBuiltins.add(id)
            if (i.file.isNotBlank()) File(filesDir, i.file).delete()
            n++
        } }
        if (n > 0) save()
        return n
    }

    /** Bring hidden built-ins back. */
    @Synchronized fun restoreBuiltins(): Int { val n = removedBuiltins.size; removedBuiltins.clear(); seed(); save(); return n }

    // ---------- import ----------

    @Synchronized fun addText(title: String, markdown: String, folder: String, tags: List<String>, source: String, url: String = ""): Item {
        ensure()
        val id = newId("u")
        val fn = "$id.md"
        File(filesDir, fn).writeText(markdown)
        return Item(id, title.trim().ifEmpty { "未命名" }, "text", folder, tags.toMutableList(), System.currentTimeMillis(), source, "text/markdown", fn, url)
            .also { items[id] = it; save() }
    }

    fun addBytes(name: String, mime: String, bytes: ByteArray, folder: String, tags: List<String>, source: String, url: String = ""): Item =
        addStream(name, mime, bytes.inputStream(), folder, tags, source, url)

    fun addStream(name: String, mimeIn: String, input: java.io.InputStream, folder: String, tags: List<String>, source: String, url: String = ""): Item {
        ensure()
        val id = newId("u")
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 } ?: extFor(mimeIn)
        val fn = if (ext.isEmpty()) id else "$id.$ext"
        File(filesDir, fn).outputStream().use { o -> input.use { it.copyTo(o) } }
        val mime = mimeIn.ifBlank { mimeFor(ext) }
        val isText = mime.startsWith("text/") || ext in setOf("md", "txt", "markdown", "tex")
        val title = name.substringBeforeLast('.').ifBlank { name }.ifBlank { "导入的文件" }
        return synchronized(this) {
            Item(id, title, if (isText) "text" else "file", folder, tags.toMutableList(), System.currentTimeMillis(), source, mime, fn, url)
                .also { items[id] = it; save() }
        }
    }

    fun importUri(uri: Uri, folder: String, tags: List<String>, source: String): Item {
        val cr = ctx.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入的文件"
        runCatching { cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } } }
        val mime = cr.getType(uri) ?: ""
        val input = cr.openInputStream(uri) ?: throw IllegalStateException("无法读取 $name")
        return addStream(name, mime, input, folder, tags, source)
    }

    private fun extFor(mime: String) = when {
        mime == "application/pdf" -> "pdf"
        mime == "image/png" -> "png"
        mime == "image/jpeg" -> "jpg"
        mime == "image/webp" -> "webp"
        mime == "text/markdown" -> "md"
        mime.startsWith("text/") -> "txt"
        mime.contains("wordprocessingml") -> "docx"
        mime.contains("presentationml") -> "pptx"
        else -> ""
    }

    private fun mimeFor(ext: String) = when (ext) {
        "pdf" -> "application/pdf"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
        "md", "markdown" -> "text/markdown"; "txt", "tex" -> "text/plain"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }

    // ---------- reading ----------

    fun fileOf(id: String): File? = synchronized(this) { items[id] }?.file?.takeIf { it.isNotBlank() }?.let { File(filesDir, it) }?.takeIf { it.exists() }

    fun isImage(id: String) = synchronized(this) { items[id] }?.mime?.startsWith("image/") == true

    /** Text content of an item for reading or for the agent; null when it has none (an image, an unknown binary). */
    fun text(id: String, maxChars: Int = 60_000): String? {
        ensure()
        val it = synchronized(this) { items[id] } ?: return builtinText(id)
        return when (it.type) {
            "note", "set" -> builtinText(id)
            else -> {
                val f = fileOf(id) ?: return null
                val ext = f.extension.lowercase()
                when {
                    it.type == "text" -> f.readText()
                    it.mime == "application/pdf" || ext == "pdf" -> pdfText(f, maxChars)
                    ext == "docx" -> zipXmlText(f, Regex("word/document\\.xml"), "</w:p>")
                    ext == "pptx" -> zipXmlText(f, Regex("ppt/slides/slide\\d+\\.xml"), "</a:p>")
                    else -> null
                }
            }
        }?.let { t -> if (t.length > maxChars) t.take(maxChars) + "\n…（已截断，共 ${t.length} 字）" else t }
    }

    private fun builtinText(id: String): String? = when {
        id.startsWith("note:") -> builtin.noteText(id)
        builtin.sets[id] != null -> builtin.sets[id]!!.let { s -> s.title + "\n\n" + s.problems.joinToString("\n\n") { "${it.no}. ${it.statement}" } }
        else -> null
    }

    private fun pdfText(f: File, maxChars: Int): String? = runCatching {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
            val s = com.tom_roush.pdfbox.text.PDFTextStripper()
            s.endPage = minOf(doc.numberOfPages, 60)
            "（PDF 共 ${doc.numberOfPages} 页）\n" + s.getText(doc).take(maxChars)
        }
    }.getOrElse { com.inkagent.AppLog.w("UserLib", "pdf text failed", it); null }

    private fun zipXmlText(f: File, entry: Regex, para: String): String? = runCatching {
        val parts = ArrayList<Pair<String, String>>()
        ZipInputStream(f.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (entry.matches(e.name)) parts.add(e.name to z.readBytes().toString(Charsets.UTF_8))
                e = z.nextEntry
            }
        }
        parts.sortedBy { it.first.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }.joinToString("\n\n") { (_, xml) ->
            xml.replace(para, "\n").replace(Regex("<[^>]+>"), "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"")
        }.trim()
    }.getOrNull()
}
