package com.inkagent.agent

import android.app.Activity
import android.content.Intent
import com.inkagent.library.UserLibrary
import com.inkagent.llm.CancelSignal
import com.xnotes.ui.BrowseEntry
import com.xnotes.ui.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * App-level powers for the agent: organise the user's library (folders, tags, imports, saving
 * web finds) and manage notes (list, open, create, rename, move to trash). Anything that throws
 * the user's data away asks first through [confirm].
 */
class AppTools(
    private val activity: Activity,
    private val editor: () -> Editor?,
    private val lib: () -> UserLibrary,
    private val confirm: (String, List<Pair<String, String>>) -> Boolean,
) {
    private fun strs(a: JSONArray?): List<String> = a?.let { List(it.length()) { i -> it.optString(i) } }?.filter { it.isNotBlank() } ?: emptyList()
    private fun idsOf(args: JSONObject): List<String> = strs(args.optJSONArray("ids")) + listOfNotNull(args.optString("id").takeIf { it.isNotBlank() })

    fun register(reg: ToolRegistry) {
        reg.register(object : Tool {
            override val name = "library_manage"
            override val description = "管理用户的资料库（用户自己的资料、导入的文件，以及内置讲义/习题/真题都在里面，可以自由整理）。action：" +
                "tree 看文件夹结构和标签；list 列出资料（可按 folder / tag / query 过滤，recursive=true 含子文件夹）；read 读一项资料的内容（PDF/Word/PPT 抽取文字，图片直接给你看）；" +
                "mkdir 建文件夹（folder 写路径如「数学分析/期中复习」，自动逐级创建）；rename 改资料或文件夹的名字；move 把资料移到文件夹；tag 加/删标签；" +
                "delete 删除资料或文件夹（会先弹窗问用户）；save 把你写的内容（Markdown+LaTeX：总结、整理的笔记、网上找到的资料摘录）存成一项资料；" +
                "import_url 下载网上的文件（PDF、图片）或网页正文存进资料库；open 在屏幕上打开资料库给用户看（某个文件夹或某项资料）；restore_builtins 恢复被删掉的内置资料。" +
                "资料 id 来自 list/tree 的结果；内置讲义 id 形如 note:analysis/01-…，题集 id 形如 cmc-math-15-prelim-A，这些也能用 library 工具的 get/insert。找网上的资料先用 web_search / web_fetch，再 save 或 import_url。"
            override val parameters = obj(
                "action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("tree", "list", "read", "mkdir", "rename", "move", "tag", "delete", "save", "import_url", "open", "restore_builtins"))),
                "id" to t("string", "一项资料（或 rename/delete 时一个文件夹）的 id"),
                "ids" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "多项资料的 id（move/tag/delete）"),
                "folder" to t("string", "文件夹：id 或路径（如「讲义/数学分析」；空字符串 = 根目录）"),
                "recursive" to t("boolean", "list：包含子文件夹，默认 true"),
                "tag" to t("string", "list：只看带这个标签的"),
                "query" to t("string", "list：标题/标签关键词"),
                "title" to t("string", "rename/save/import_url 的标题"),
                "add" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "tag：要加的标签"),
                "remove" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "tag：要删的标签"),
                "tags" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "save/import_url：标签"),
                "content" to t("string", "save：Markdown 正文"),
                "url" to t("string", "import_url 的链接；save 时可写来源链接"),
                required = listOf("action"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val L = lib(); L.ensure()
                fun line(i: UserLibrary.Item) = "${i.id} | ${i.title} | ${mapOf("note" to "讲义", "set" to "题集", "text" to "文本", "file" to "文件")[i.type] ?: i.type}" +
                    (if (i.type == "file") "(${i.mime})" else "") + " | 📁${L.folderName(i.folder)}" + (if (i.tags.isNotEmpty()) " | #" + i.tags.joinToString(" #") else "") +
                    (if (i.source.isNotBlank() && i.source != "内置") " | 来源 ${i.source}" else "")
                return when (args.optString("action")) {
                    "tree" -> {
                        val sb = StringBuilder("文件夹（id · 名称 · 资料数）：\n")
                        fun walk(parent: String, depth: Int) {
                            synchronized(L) { L.folders.values.filter { it.parent == parent }.sortedBy { it.name } }.forEach { f ->
                                sb.append("  ".repeat(depth)).append("- ${f.id} · ${f.name} · ${L.list(f.id, true, null, null).size}\n"); walk(f.id, depth + 1)
                            }
                        }
                        walk("", 0)
                        sb.append("根目录直接放着 ${L.list("", false, null, null).size} 项。共 ${L.items.size} 项。\n标签：")
                        sb.append(L.allTags().joinToString("、") { "#${it.first}(${it.second})" }.ifEmpty { "（还没有）" })
                        ToolResult.text(sb.toString())
                    }
                    "list" -> {
                        val folder = if (args.has("folder")) L.resolveFolder(args.optString("folder")) ?: return ToolResult.error("没有这个文件夹：${args.optString("folder")}") else null
                        val items = L.list(folder, args.optBoolean("recursive", true), args.optString("tag").ifBlank { null }, args.optString("query").ifBlank { null })
                        ToolResult.text(if (items.isEmpty()) "没有符合条件的资料" else "共 ${items.size} 项（id | 标题 | 类型 | 文件夹 | 标签 | 来源）：\n" + items.take(200).joinToString("\n") { line(it) } + if (items.size > 200) "\n…只列前 200 项，缩小范围再看" else "")
                    }
                    "read" -> {
                        val id = args.optString("id")
                        if (L.isImage(id)) {
                            val f = L.fileOf(id) ?: return ToolResult.error("文件不见了")
                            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(f.path, o)
                            return ToolResult(listOf(com.inkagent.llm.Block.Text("资料 $id（图片）："), com.inkagent.llm.Block.Image(f.path, o.outWidth, o.outHeight)))
                        }
                        val t = L.text(id) ?: return ToolResult.error(if (synchronized(L) { L.items[id] } == null && !id.startsWith("note:")) "没有 id=$id 的资料" else "这项资料没有可读的文字（可能是扫描版 PDF 或不支持的格式）。可以 open 让用户看。")
                        ToolResult.text(t)
                    }
                    "mkdir" -> {
                        val path = args.optString("folder").ifBlank { args.optString("title") }
                        if (path.isBlank()) return ToolResult.error("folder 写要建的路径")
                        val id = L.resolveFolder(path, create = true) ?: return ToolResult.error("建不了")
                        ToolResult.text("文件夹「${L.folderName(id)}」id=$id")
                    }
                    "rename" -> {
                        val id = args.optString("id"); val title = args.optString("title")
                        if (title.isBlank()) return ToolResult.error("title 不能为空")
                        when {
                            synchronized(L) { L.items[id] } != null -> { L.rename(id, title); ToolResult.text("已改名为「$title」") }
                            synchronized(L) { L.folders[id] } != null -> { L.renameFolder(id, title); ToolResult.text("文件夹已改名为「$title」") }
                            else -> ToolResult.error("没有 id=$id")
                        }
                    }
                    "move" -> {
                        val ids = idsOf(args); if (ids.isEmpty()) return ToolResult.error("ids 不能为空")
                        val folder = L.resolveFolder(args.optString("folder"), create = true) ?: ""
                        val folderIds = ids.filter { synchronized(L) { L.folders[it] } != null }
                        folderIds.forEach { L.moveFolder(it, folder) }
                        val n = L.move(ids - folderIds.toSet(), folder)
                        ToolResult.text("移动了 ${n + folderIds.size} 项到「${L.folderName(folder)}」")
                    }
                    "tag" -> {
                        val ids = idsOf(args); if (ids.isEmpty()) return ToolResult.error("ids 不能为空")
                        val n = L.tag(ids, strs(args.optJSONArray("add")), strs(args.optJSONArray("remove")))
                        ToolResult.text("更新了 $n 项的标签")
                    }
                    "delete" -> {
                        val ids = idsOf(args); if (ids.isEmpty()) return ToolResult.error("ids 不能为空")
                        val folders = ids.mapNotNull { synchronized(L) { L.folders[it] } }
                        val items = ids.mapNotNull { synchronized(L) { L.items[it] } }
                        if (folders.isEmpty() && items.isEmpty()) return ToolResult.error("没有这些 id")
                        val lines = folders.map { "文件夹" to "${L.folderName(it.id)}（里面的资料移到上一级，不删）" } + items.take(12).map { "资料" to it.title } +
                            (if (items.size > 12) listOf("…" to "还有 ${items.size - 12} 项") else emptyList())
                        if (!confirm("删除资料库里的这些内容？", lines)) return ToolResult.text("用户没有同意删除，什么都没删。")
                        folders.forEach { L.deleteFolder(it.id) }
                        val n = L.delete(items.map { it.id })
                        ToolResult.text("删除了 $n 项资料、${folders.size} 个文件夹")
                    }
                    "save" -> {
                        val content = args.optString("content"); if (content.isBlank()) return ToolResult.error("content 不能为空")
                        val folder = L.resolveFolder(args.optString("folder").ifBlank { "收件箱" }, create = true) ?: ""
                        val url = args.optString("url")
                        val it = L.addText(args.optString("title").ifBlank { content.lineSequence().first().trimStart('#', ' ').take(40) },
                            content, folder, strs(args.optJSONArray("tags")), if (url.isNotBlank()) "网络" else "AI 整理", url)
                        ToolResult.text("已存为资料 ${it.id}「${it.title}」，在「${L.folderName(folder)}」")
                    }
                    "import_url" -> importUrl(L, args)
                    "open" -> {
                        val id = args.optString("id"); val folder = args.optString("folder")
                        val i = Intent(activity, com.inkagent.library.LibraryActivity::class.java)
                        if (id.isNotBlank()) i.putExtra("item", id) else if (folder.isNotBlank()) L.resolveFolder(folder)?.let { i.putExtra("folder", it) }
                        activity.runOnUiThread { activity.startActivity(i) }
                        ToolResult.text("已在屏幕上打开资料库")
                    }
                    "restore_builtins" -> ToolResult.text("恢复了 ${L.restoreBuiltins()} 项内置资料")
                    else -> ToolResult.error("未知 action")
                }
            }
        })

        reg.register(object : Tool {
            override val name = "app_notes"
            override val description = "管理用户的笔记文件（.xnote）。action：list 列出笔记和文件夹；open 打开一个笔记（uri 或 name）；create 新建笔记（name，可选 folder）并打开；" +
                "rename 改名；trash 移到回收站（会先问用户）；home 回到首页。打开/新建后可以继续用 canvas_* 工具在上面写。"
            override val parameters = obj(
                "action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("list", "open", "create", "rename", "trash", "home"))),
                "uri" to t("string", "笔记的 uri（list 的结果里有）"),
                "name" to t("string", "open：按文件名找；create/rename：新名字（不用写 .xnote）"),
                "folder" to t("string", "create：放进哪个文件夹（路径，如「高代/作业」，不存在就建）"),
                required = listOf("action"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val ed = editor() ?: return ToolResult.error("编辑器还没准备好")
                val root = ed.browseRoot ?: return ToolResult.error("还没有笔记文件夹")
                fun all(): List<Pair<String, BrowseEntry>> {
                    val out = ArrayList<Pair<String, BrowseEntry>>()
                    fun walk(doc: String, prefix: String, depth: Int) {
                        if (depth > 4 || out.size > 400) return
                        runCatching { ed.browseChildren(root, doc) }.getOrDefault(emptyList()).forEach { e ->
                            if (e.name.startsWith(".")) return@forEach
                            out.add(prefix to e)
                            if (e.isDir) walk(android.provider.DocumentsContract.getDocumentId(android.net.Uri.parse(e.documentUri)), "$prefix${e.name}/", depth + 1)
                        }
                    }
                    walk(ed.browseRootDocId(root), "", 0)
                    return out
                }
                fun find(): BrowseEntry? {
                    val uri = args.optString("uri"); val name = args.optString("name").removeSuffix(".xnote")
                    val list = all().map { it.second }.filter { !it.isDir }
                    return list.firstOrNull { uri.isNotBlank() && it.documentUri == uri }
                        ?: list.firstOrNull { name.isNotBlank() && it.name.removeSuffix(".xnote") == name }
                        ?: list.firstOrNull { name.isNotBlank() && it.name.contains(name) }
                }
                fun openEntry(e: BrowseEntry) = runBlocking { withContext(Dispatchers.Main) { ed.openAsync(e.documentUri, e.name) } }
                return when (args.optString("action")) {
                    "list" -> {
                        val es = all()
                        ToolResult.text(if (es.isEmpty()) "还没有笔记" else es.joinToString("\n") { (p, e) ->
                            if (e.isDir) "📁 $p${e.name}/" else "📄 $p${e.name}　uri=${e.documentUri}"
                        } + "\n当前打开：" + (if (ed.noteOpen) "有笔记打开（view_page/list_pages 可以看）" else "没有，在首页"))
                    }
                    "open" -> {
                        val e = find() ?: return ToolResult.error("没找到这个笔记，先 list 看看")
                        openEntry(e); ToolResult.text("已打开「${e.name}」")
                    }
                    "create" -> {
                        val name = args.optString("name").ifBlank { "新笔记" }
                        var parent = ed.browseRootDocId(root)
                        args.optString("folder").split('/').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                            val kids = ed.browseChildren(root, parent)
                            val hit = kids.firstOrNull { it.isDir && it.name == part } ?: run {
                                ed.createFolder(root, parent, part)
                                ed.browseChildren(root, parent).firstOrNull { it.isDir && it.name == part }
                            } ?: return ToolResult.error("建不了文件夹 $part")
                            parent = android.provider.DocumentsContract.getDocumentId(android.net.Uri.parse(hit.documentUri))
                        }
                        val uri = ed.createBlankNoteFile(root, parent, name) ?: return ToolResult.error("新建失败（可能重名了）")
                        runBlocking { withContext(Dispatchers.Main) { ed.openAsync(uri, "$name.xnote") } }
                        ToolResult.text("已新建并打开「$name」 uri=$uri")
                    }
                    "rename" -> {
                        val e = find() ?: return ToolResult.error("没找到这个笔记")
                        val nn = args.optString("name").ifBlank { return ToolResult.error("name 写新名字") }
                        // name doubles as the lookup key; for rename the uri must identify the note
                        if (args.optString("uri").isBlank()) return ToolResult.error("rename 要给 uri（list 里有），name 写新名字")
                        val ok = ed.renameDocument(e.documentUri, if (nn.endsWith(".xnote")) nn else "$nn.xnote")
                        if (ok) ToolResult.text("已改名为「$nn」") else ToolResult.error("改名失败")
                    }
                    "trash" -> {
                        val e = find() ?: return ToolResult.error("没找到这个笔记")
                        if (!confirm("把这个笔记移到回收站？", listOf("笔记" to e.name, "可以恢复" to "在「全部笔记 → 回收站」里"))) return ToolResult.text("用户没有同意，笔记还在。")
                        val (moved, failed) = ed.trashEntries(root, listOf(e))
                        if (moved.isNotEmpty()) ToolResult.text("已移到回收站") else ToolResult.error("没能移到回收站（${failed.size}）")
                    }
                    "home" -> { activity.runOnUiThread { ed.goHomeAll() }; ToolResult.text("已回到首页") }
                    else -> ToolResult.error("未知 action")
                }
            }
        })
    }

    private fun importUrl(L: UserLibrary, args: JSONObject): ToolResult {
        val url = args.optString("url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ToolResult.error("只支持 http(s) 链接")
        val folder = L.resolveFolder(args.optString("folder").ifBlank { "收件箱" }, create = true) ?: ""
        val tags = strs(args.optJSONArray("tags"))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000; conn.readTimeout = 60_000; conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) InkAgent/0.6")
        if (conn.responseCode !in 200..299) return ToolResult.error("HTTP ${conn.responseCode}")
        val type = (conn.contentType ?: "").substringBefore(';').trim().lowercase()
        val len = conn.contentLengthLong
        if (len > 60L * 1024 * 1024) return ToolResult.error("文件太大（${len / 1024 / 1024} MB）")
        return if (type.contains("html")) {
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val title = args.optString("title").ifBlank { Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.trim()?.take(60) ?: url }
            val text = html.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr)>"), "\n").replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n").trim()
            val it = L.addText(title, "# $title\n\n来源：$url\n\n$text", folder, tags, "网络", url)
            ToolResult.text("网页正文已存为资料 ${it.id}「${it.title}」（${text.length} 字），在「${L.folderName(folder)}」。最好再 read 一下，删掉无关内容后用 save 存一份整理版。")
        } else {
            val name = args.optString("title").ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "下载的文件" } }
            val it = L.addStream(if (name.contains('.')) name else name + extOf(type), type, conn.inputStream, folder, tags, "网络", url)
            ToolResult.text("已下载为资料 ${it.id}「${it.title}」（$type），在「${L.folderName(folder)}」")
        }
    }

    private fun extOf(type: String) = when {
        type == "application/pdf" -> ".pdf"; type == "image/png" -> ".png"; type == "image/jpeg" -> ".jpg"
        type.startsWith("text/") -> ".txt"; else -> ""
    }
}
