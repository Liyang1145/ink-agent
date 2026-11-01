package com.inkagent.library

import com.inkagent.agent.*
import com.inkagent.llm.CancelSignal
import org.json.JSONArray
import org.json.JSONObject

/** Agent tools over the built-in library, plus exam-sheet layout on the canvas. */
class LibraryTools(private val lib: Library, private val canvas: () -> CanvasTools?) {

    /** Blank answer space under a problem (page units), from its score / difficulty. */
    fun spaceFor(p: Library.Problem, pageH: Double = 1753.0): Double {
        val s = p.score
        val f = when {
            p.obj.optString("type").contains("填空") || p.obj.optString("type").contains("选择") -> 0.06
            s >= 15 || p.difficulty >= 4 -> 0.30
            s >= 10 || p.difficulty == 3 -> 0.22
            else -> 0.16
        }
        return pageH * f
    }

    fun problemMd(p: Library.Problem, withHeader: Boolean = true): String {
        val head = if (withHeader) "**${p.no.let { if (it.all { c -> c.isDigit() }) "$it." else "$it、" }}**" + (if (p.score > 0) "（${p.score} 分）" else "") + " " else ""
        return head + p.statement.trim()
    }

    /** Insert a whole set (paper / exercise chapter) or a single problem as an exam sheet. */
    fun insert(id: String, newPage: Boolean = true, page: Int? = null, space: Double? = null): String {
        val ct = canvas() ?: throw IllegalStateException("当前没有打开的笔记")
        lib.ensure()
        val blocks = ArrayList<CanvasTools.Block2>()
        val set = lib.sets[id]
        if (set != null) {
            val sub = buildList {
                set.meta.optInt("durationMin").takeIf { it > 0 }?.let { add("考试时间 $it 分钟") }
                set.meta.optInt("totalScore").takeIf { it > 0 }?.let { add("满分 $it 分") }
            }.joinToString("　")
            blocks.add(CanvasTools.Block2("## ${set.title}", 1753.0 * 0.005, center = true))
            if (sub.isNotEmpty()) blocks.add(CanvasTools.Block2(sub, 1753.0 * 0.02, center = true))
            set.problems.forEach { blocks.add(CanvasTools.Block2(problemMd(it), space ?: spaceFor(it))) }
        } else {
            val p = lib.problem(id) ?: throw IllegalArgumentException("资料库里没有 id=$id（用 library 的 search 查）")
            blocks.add(CanvasTools.Block2("*${p.setTitle} 第 ${p.no} 题*\n\n" + problemMd(p, false), space ?: spaceFor(p)))
        }
        return ct.layoutBlocks(blocks, newPage && page == null, page?.let { it - 1 })
    }

    fun register(reg: ToolRegistry) {
        reg.register(object : Tool {
            override val name = "library"
            override val description = "内置资料库：全国大学生数学竞赛(CMC)历届真题（数学类、非数学类，初赛/决赛，含官方参考解答）、数学分析和高等代数的分章经典习题、初学者讲义。" +
                "action=search 按关键词找题或讲义（query 用空格分隔多个词，都要命中；可选 kind=cmc|exercise|note、track=数学类|非数学类）；" +
                "action=get 取一道题（id 形如 cmc-math-15-prelim-A#三）、一整套（试卷/习题章节 id）或一篇讲义（note:...）的完整内容，include_solution=true 时附答案和解答（用户自己做题时不要先给答案）；" +
                "action=catalog 列出有哪些试卷、习题章节和讲义；" +
                "action=insert 把一道题或一整套题排版成试卷放到笔记页面（默认新开一页，每题下面留作答空白）。"
            override val parameters = obj(
                "action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("search", "get", "catalog", "insert"))),
                "query" to t("string", "search 的关键词，如「一致连续」「特征值 相似」「第十五届 数学类」"),
                "kind" to t("string", "可选：cmc / exercise / note"),
                "track" to t("string", "可选：数学类 / 非数学类"),
                "id" to t("string", "get / insert 的 id"),
                "include_solution" to t("boolean", "get 时是否附答案和解答，默认 false"),
                "page" to t("integer", "insert：放到哪一页下方（省略则新开一页）"),
                "answer_space" to t("number", "insert：每题下方留白高度（页面单位，页高约 1753），省略按分值自动"),
                required = listOf("action"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                lib.ensure()
                return when (args.optString("action")) {
                    "catalog" -> {
                        val c = lib.catalog()
                        val sb = StringBuilder()
                        val papers = c.getJSONArray("papers")
                        sb.append("CMC 真题 ${papers.length()} 套：\n")
                        for (i in 0 until papers.length()) papers.getJSONObject(i).let { sb.append("- ${it.optString("id")} ${it.optString("title")}（${it.optInt("count")} 题）\n") }
                        val ex = c.getJSONArray("exercises"); sb.append("分章习题 ${ex.length()} 章：\n")
                        for (i in 0 until ex.length()) ex.getJSONObject(i).let { sb.append("- ${it.optString("id")} ${it.optString("title")}（${it.optInt("count")} 题）\n") }
                        val ns = c.getJSONArray("notes"); sb.append("讲义 ${ns.length()} 篇：\n")
                        for (i in 0 until ns.length()) ns.getJSONObject(i).let { sb.append("- ${it.optString("id")} ${it.optString("title")}\n") }
                        ToolResult.text(if (lib.sets.isEmpty() && lib.notes.isEmpty()) "资料库是空的（这个版本还没有内置资料）。" else sb.toString())
                    }
                    "search" -> {
                        val q = args.optString("query")
                        val hits = lib.search(q, args.optString("track").ifBlank { null }, args.optString("kind").ifBlank { null }, 25)
                        ToolResult.text(if (hits.isEmpty()) "没有找到匹配「$q」的内容，换个关键词或用 catalog 看目录。" else hits.joinToString("\n") { "${it.first}　${it.second}" })
                    }
                    "get" -> {
                        val id = args.optString("id"); val sol = args.optBoolean("include_solution", false)
                        fun pm(p: Library.Problem) = buildString {
                            append("【${p.id}】${problemMd(p)}\n")
                            if (sol) { if (p.answer.isNotBlank()) append("答案：${p.answer}\n"); if (p.solution.isNotBlank()) append("解答：${p.solution}\n") }
                        }
                        when {
                            id.startsWith("note:") -> lib.noteText(id)?.let { ToolResult.text(it) } ?: ToolResult.error("没有讲义 $id")
                            lib.sets[id] != null -> ToolResult.text("${lib.sets[id]!!.title}\n\n" + lib.sets[id]!!.problems.joinToString("\n") { pm(it) })
                            else -> lib.problem(id)?.let { ToolResult.text("${it.setTitle}\n" + pm(it)) } ?: ToolResult.error("没有 id=$id")
                        }
                    }
                    "insert" -> ToolResult.text(insert(args.optString("id"), true,
                        if (args.has("page")) args.optInt("page") else null,
                        if (args.has("answer_space")) args.optDouble("answer_space") else null))
                    else -> ToolResult.error("未知 action")
                }
            }
        })

        reg.register(object : Tool {
            override val name = "canvas_write_exam"
            override val description = "把题目排版成试卷写到笔记页面上：每题全宽排版（Markdown + \$LaTeX\$，中文正文与公式混排、自动换行），每题下方留作答空白，放不下自动续到新页。" +
                "用于录入题目、出练习卷、把照片里的题目整理成卷子。只写题目，不写答案。"
            override val parameters = obj(
                "title" to t("string", "试卷标题，可省略"),
                "problems" to JSONObject().put("type", "array").put("description", "题目列表，按顺序")
                    .put("items", JSONObject().put("type", "object").put("properties", JSONObject()
                        .put("text", t("string", "题干，Markdown，数学用 \$...\$ 或 \$\$...\$\$；自己带题号，如「1. 求极限 \$\\\\lim_{x\\\\to0}\\\\frac{\\\\sin x}{x}\$」"))
                        .put("space", t("number", "题下留白高度（页面单位，页高约 1753）；省略则按 12% 页高")))
                        .put("required", JSONArray(listOf("text")))),
                "new_page" to t("boolean", "是否新开一页开始，默认 true"),
                "page" to t("integer", "不新开页时，接在哪一页已有内容下方"),
                required = listOf("problems"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val ct = canvas() ?: return ToolResult.error("当前没有打开的笔记")
                val arr = args.optJSONArray("problems") ?: return ToolResult.error("problems 不能为空")
                val blocks = ArrayList<CanvasTools.Block2>()
                args.optString("title").takeIf { it.isNotBlank() }?.let { blocks.add(CanvasTools.Block2("## $it", 1753.0 * 0.02, center = true)) }
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: JSONObject().put("text", arr.optString(i))
                    blocks.add(CanvasTools.Block2(o.optString("text"), if (o.has("space")) o.optDouble("space") else 1753.0 * 0.12))
                }
                val np = args.optBoolean("new_page", !args.has("page"))
                return ToolResult.text(ct.layoutBlocks(blocks, np, if (args.has("page")) args.optInt("page") - 1 else null))
            }
        })
    }
}
