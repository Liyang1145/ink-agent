package com.inkagent.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The built-in study library shipped in assets/library/:
 *  - cmc/index.json + cmc/<paper>.json — 全国大学生数学竞赛 past papers (数学类 / 非数学类)
 *  - exercises/<analysis|algebra>/NN-*.json — 分章经典习题 (same problem format)
 *  - notes/<analysis|algebra>/NN-*.md — 初学者讲义 (markdown + LaTeX, front matter)
 * Ids: a paper "cmc-math-15-prelim-A"; a problem "<paper or exercise set id>#<no>"; a note "note:<subject>/<file>".
 */
class Library(private val ctx: Context) {
    class Problem(val id: String, val setId: String, val setTitle: String, val no: String, val obj: JSONObject) {
        val statement get() = obj.optString("statement")
        val answer get() = obj.optString("answer")
        val solution get() = obj.optString("solution")
        val score get() = obj.optInt("score", 0)
        val difficulty get() = obj.optInt("difficulty", 0)
        fun tags(): List<String> = listOf("subjects", "topics").flatMap { k -> obj.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
    }
    class ProblemSet(val id: String, val kind: String, val title: String, val meta: JSONObject, val problems: List<Problem>)
    class Note(val id: String, val subject: String, val title: String, val chapter: Int, val path: String)

    private var loaded = false
    val sets = LinkedHashMap<String, ProblemSet>()
    val notes = ArrayList<Note>()

    private fun read(path: String) = ctx.assets.open(path).bufferedReader().use { it.readText() }
    private fun list(dir: String): List<String> = runCatching { ctx.assets.list(dir)?.toList()?.sorted() ?: emptyList() }.getOrElse { emptyList() }

    @Synchronized fun ensure() {
        if (loaded) return
        loaded = true
        for (f in list("library/cmc")) {
            if (!f.endsWith(".json") || f == "index.json") continue
            runCatching { addSet("cmc", JSONObject(read("library/cmc/$f")), f.removeSuffix(".json")) }
                .onFailure { com.inkagent.AppLog.w("Library", "bad paper $f", it) }
        }
        for (subj in listOf("analysis", "algebra")) {
            for (f in list("library/exercises/$subj")) if (f.endsWith(".json")) runCatching {
                addSet("exercise", JSONObject(read("library/exercises/$subj/$f")), "ex-$subj-" + f.removeSuffix(".json"), subj)
            }.onFailure { com.inkagent.AppLog.w("Library", "bad exercises $f", it) }
            for (f in list("library/notes/$subj")) if (f.endsWith(".md")) {
                val text = runCatching { read("library/notes/$subj/$f") }.getOrNull() ?: continue
                val fm = frontMatter(text)
                notes.add(Note("note:$subj/${f.removeSuffix(".md")}", subj, fm["title"] ?: f.removeSuffix(".md"),
                    fm["chapter"]?.filter { it.isDigit() }?.toIntOrNull() ?: f.take(2).toIntOrNull() ?: 0, "library/notes/$subj/$f"))
            }
        }
    }

    private fun addSet(kind: String, o: JSONObject, fallbackId: String, subject: String? = null) {
        val id = o.optString("id").ifBlank { fallbackId }
        val title = o.optString("title").ifBlank { fallbackId }
        val arr = o.optJSONArray("problems") ?: JSONArray()
        val ps = (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val no = p.optString("no").ifBlank { (i + 1).toString() }
            Problem("$id#$no", id, title, no, p)
        }
        if (subject != null) o.put("subjectKey", subject)
        sets[id] = ProblemSet(id, kind, title, o, ps)
    }

    private fun frontMatter(t: String): Map<String, String> {
        if (!t.startsWith("---")) return emptyMap()
        val end = t.indexOf("\n---", 3); if (end < 0) return emptyMap()
        return t.substring(3, end).lines().mapNotNull { l -> val i = l.indexOf(':'); if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim().trim('"', '\'') else null }.toMap()
    }

    fun noteText(id: String): String? {
        ensure()
        val n = notes.firstOrNull { it.id == id } ?: return null
        val t = read(n.path)
        return if (t.startsWith("---")) t.substring((t.indexOf("\n---", 3) + 4).coerceAtMost(t.length)).trimStart() else t
    }

    fun problem(id: String): Problem? { ensure(); val sid = id.substringBefore('#'); return sets[sid]?.problems?.firstOrNull { it.id == id } }

    /** Catalog for the library screen. */
    fun catalog(): JSONObject {
        ensure()
        val papers = JSONArray(); val ex = JSONArray(); val ns = JSONArray()
        sets.values.forEach { s ->
            val o = JSONObject().put("id", s.id).put("title", s.title).put("count", s.problems.size)
            listOf("year", "track", "round", "paper", "subjectKey", "chapter").forEach { k -> if (s.meta.has(k)) o.put(k, s.meta.get(k)) }
            if (s.kind == "cmc") papers.put(o) else ex.put(o)
        }
        notes.sortedWith(compareBy({ it.subject }, { it.chapter })).forEach { ns.put(JSONObject().put("id", it.id).put("subject", it.subject).put("title", it.title).put("chapter", it.chapter)) }
        return JSONObject().put("papers", papers).put("exercises", ex).put("notes", ns)
    }

    fun setJson(id: String): String? { ensure(); return sets[id]?.meta?.toString() }

    /** Keyword search over problems and notes; every whitespace-separated term must match. */
    fun search(query: String, track: String?, kind: String?, limit: Int): List<Pair<String, String>> {
        ensure()
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<Pair<String, String>>()
        if (kind == null || kind == "cmc" || kind == "exercise") for (s in sets.values) {
            if (kind != null && s.kind != kind) continue
            if (track != null && s.kind == "cmc" && !s.meta.optString("track").contains(track)) continue
            for (p in s.problems) {
                val hay = (s.title + " " + p.tags().joinToString(" ") + " " + p.statement).lowercase()
                if (terms.all { it in hay }) out.add(p.id to "${s.title} 第${p.no}题 [${p.tags().joinToString("、")}] 难度${p.difficulty}：${p.statement.replace('\n', ' ').take(90)}")
                if (out.size >= limit) return out
            }
        }
        if (kind == null || kind == "note") for (n in notes) {
            val hay = (n.title + " " + (noteText(n.id) ?: "")).lowercase()
            if (terms.all { it in hay }) out.add(n.id to "讲义《${n.title}》")
            if (out.size >= limit) return out
        }
        return out
    }
}
