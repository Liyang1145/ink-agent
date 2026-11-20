package com.inkagent.agent

import android.util.Base64
import com.inkagent.llm.Block
import com.inkagent.llm.CancelSignal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Reports from animation cards (agent.html → AgentPanel → here), keyed like agent.html's animKey(code). */
object AnimBridge {
    private val reports = ConcurrentHashMap<String, String>()
    private val lock = Object()
    fun key(code: String): String {
        var h = 0
        for (c in code) h = (h shl 5) - h + c.code
        return "a" + (h.toLong() and 0xffffffffL).toString(36) + "_" + code.length
    }
    fun report(key: String, json: String) { reports[key] = json; synchronized(lock) { lock.notifyAll() } }
    fun await(key: String, ms: Long, signal: CancelSignal): String? {
        val until = System.currentTimeMillis() + ms
        while (true) {
            reports.remove(key)?.let { return it }
            val left = until - System.currentTimeMillis()
            if (left <= 0 || signal.cancelled) return null
            synchronized(lock) { lock.wait(minOf(left, 200)) }
        }
    }
}

class AnimTool(private val dir: File) : Tool {
    override val name = "show_animation"
    override val description = "在对话里放一个可交互的 three.js 动画/3D 场景来讲解（用户可拖动旋转、缩放、暂停、重播、放大）。适合：空间几何与立体图形、曲面与切平面、参数曲线与运动、向量与线性变换、旋转体/截面、极限与逼近过程、级数部分和、黎曼和、傅里叶叠加、动态演示「变化过程」。静态的平面函数图像用 canvas_plot 写到页面上即可，不必做动画。\n" +
        "code 是一段 JavaScript 函数体，会以 (A, THREE) 调用；THREE 是 three.js r147（含 THREE.OrbitControls）。已经建好 A.scene、A.camera（透视，位于 (6,5,8) 看原点，y 轴朝上）、A.renderer、A.controls、灯光和白色背景，你只需要往场景里加东西并用 A.onFrame 驱动动画。辅助函数：\n" +
        "A.onFrame((t, dt) => {...}) 每帧调用，t 为秒（重播时归零）；A.axes(size, {names:['x','y','z'], dims:3, negative:true}) 坐标轴带标签；A.grid(size, divisions)；" +
        "A.point([x,y,z], {color, r})；A.arrow(from, to, {color}) 返回值有 .update(from, to)；A.line(points, {color}) 返回值有 .setPoints(points)；A.curve(t => [x,y,z], t0, t1, {color, tube: 半径, samples})；" +
        "A.surface((x, y) => z, [x0,x1], [y0,y1], {color, opacity, samples}) 画 z=f(x,y)（注意 three.js y 轴朝上，函数值放在 y 方向、x-y 定义域铺在 x-z 平面），也可以返回 [X,Y,Z] 画参数曲面；" +
        "A.label('文字或 \$\\\\LaTeX\$', [x,y,z], {color, size}) 返回 {set(text), move(pos), hide(bool)}；A.caption('当前步骤说明，可含 \$公式\$') 底部字幕；A.slider('\$a\$', min, max, value, step, v => {...}) 滑块；A.button('下一步', fn)；" +
        "A.mode2d(xmin, xmax, ymin, ymax) 切换为平面正交视角（平面动画用，x 向右 y 向上）；A.ease(s)、A.lerp(a, b, s)。\n" +
        "要点：分阶段的讲解用 t 控制（如 t%12 划分几段，并用 A.caption 同步说明）；颜色区分对象；标签用 LaTeX；场景尺度控制在 ±5 左右。调用后你会收到报错信息和一帧截图，有错就修好再调用一次。"
    override val parameters = obj(
        "title" to t("string", "动画标题（几个字）"),
        "code" to t("string", "JavaScript 函数体，参数 (A, THREE)"),
        "height" to t("number", "高度（像素，默认 340，200–700）"),
        required = listOf("title", "code"),
    )

    override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
        val code = args.optString("code")
        if (code.isBlank()) return ToolResult.error("code 不能为空")
        val r = AnimBridge.await(AnimBridge.key(code), 9000, signal)
            ?: return ToolResult.text("动画已放在对话里（没有收到画面自检结果，可能面板被收起了）。")
        val j = runCatching { JSONObject(r) }.getOrNull() ?: return ToolResult.text("动画已放在对话里。")
        val errs = j.optJSONArray("errors") ?: JSONArray()
        val blocks = ArrayList<Block>()
        val snap = j.optString("snapshot")
        val img = if (snap.startsWith("data:image/png;base64,")) runCatching {
            savePngBytes(dir, Base64.decode(snap.substringAfter(','), Base64.DEFAULT), "anim")
        }.getOrNull() else null
        return if (errs.length() > 0) {
            val list = List(errs.length()) { errs.optString(it) }.joinToString("\n")
            blocks.add(Block.Text("动画运行出错（用户能看到这张卡片，请修好后用完整代码再调用一次）：\n$list\n下面是出错时的画面："))
            img?.let { blocks.add(it) }
            ToolResult(blocks, isError = true)
        } else {
            blocks.add(Block.Text("动画已放在对话里，运行正常。下面是第 1.6 秒左右的一帧（不含文字标签），确认画面合理即可，不要为了微调反复重做；接着在回复里配合动画讲解。"))
            img?.let { blocks.add(it) }
            ToolResult(blocks)
        }
    }
}
