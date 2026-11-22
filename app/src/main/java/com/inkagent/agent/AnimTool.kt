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
    override val description =
        "在对话里放一个可交互的 three.js 动画来讲解（用户可暂停、重播、拖动进度、放大；3D 场景可旋转缩放）。适合：变化过程（逼近与极限、级数部分和、黎曼和、傅里叶叠加、运动）、参数对图形的影响（滑块/切换取值）、平面几何作图与区域、空间图形与曲面、向量与线性变换。静态的函数图像直接用 canvas_plot 写到页面上，不必做动画。\n" +
        "code 是一段 JavaScript 函数体，以 (A, THREE) 调用（THREE 是 three.js r147，含 OrbitControls）；白色背景、灯光、A.scene、A.camera、A.controls、A.renderer 都已建好。\n" +
        "\n" +
        "【平面讲解（首选，大多数题目用它）】开头调用 A.mode2d(xmin, xmax, ymin, ymax, {grid: 1, axes: true})：x 向右、y 向上，这个范围总会完整显示，卡片高度按它的长宽比自动确定；grid 是网格间距（false 不画），axes 画带刻度数字的坐标轴（ticks: 刻度间距；names: ['x','y']）。平面里的点写 [x, y] 即可。可用单指拖动平移、双指缩放。\n" +
        "【3D】不调用 mode2d 时是透视相机，位于 (6,5,8) 看原点，y 轴朝上；场景尺度控制在 ±5 左右。\n" +
        "\n" +
        "画图（颜色可写 'blue' 'red' 'green' 'orange' 'purple' 'teal' 'pink' 'gray' 'ink' 或 '#rrggbb'；线宽按屏幕像素算，任何缩放下都清晰）：\n" +
        "A.line(points, {color, width, dashed, closed}) 返回值 .setPoints(points)（每帧调用也没问题）；\n" +
        "A.curve(t => [x, y, z], t0, t1, {color, width, samples, tube: 半径})；\n" +
        "A.point(p, {color, size: 平面里的像素半径, r: 3D 球半径}) 返回值 .move(p)；A.arrow(from, to, {color, width}) 返回值 .update(from, to)；\n" +
        "A.polygon(points, {color, opacity, stroke, dashed}) 半透明填充的多边形（区域、扇形、交集），返回值 .setPoints(points)；\n" +
        "A.circle(center, r, {color, fill: true})；A.arc(center, r, a0, a1, {color}) 标角（弧度，从 +x 逆时针），返回值 .set(center, r, a0, a1)；\n" +
        "A.axes(size)、A.grid(size, divisions)（3D 用）；A.surface((x, y) => z, [x0, x1], [y0, y1], {color, opacity, samples})（3D：函数值放在 y 方向，定义域铺在 x-z 平面；也可返回 [X, Y, Z] 画参数曲面）；\n" +
        "A.label('文字或 \$\\LaTeX\$', pos, {color, size, align: 'center'|'left'|'right'|'above'|'below', dx, dy, bg: true}) 返回值 .set(text)、.move(pos)、.hide(bool)。\n" +
        "\n" +
        "讲解与交互：\n" +
        "A.caption('一句话说明当前在看什么，可含 \$公式\$') 显示在画面下方，不遮挡图形，30 字以内；\n" +
        "A.info('读数，可含 \$公式\$，\\n 换行') 右上角读数框，返回值 .set(text)，放面积、长度等关键数值；\n" +
        "A.slider('\$a\$', min, max, value, step, v => {...}) 连续参数；A.choice('\$b\$', [6, 8], 6, v => {...}) 在几个取值之间切换（分段按钮）；A.button('下一步', fn)；\n" +
        "A.duration(秒数) 声明时间轴：卡片下方出现进度条，用户可拖动回看；onFrame 收到的 t 在 [0, 秒数) 里循环（{loop: false} 则播完停住）；\n" +
        "A.onFrame((t, dt) => {...}) 每帧调用；A.seg(t, a, b) 给出 t 在 [a, b] 这一段里 0→1 的进度；A.ease(s)、A.lerp(a, b, s)；A.colors 是调色板。\n" +
        "\n" +
        "写法要点：\n" +
        "- 对象在开头建好，onFrame 里只做 setPoints / move / set，不要每帧新建对象；画面尽量完全由 t 和当前参数算出（拖动进度条、重播才正确）。\n" +
        "- 分阶段讲解：A.duration(总时长)，用 A.seg 划分几段，A.caption 同步说明这一段在看什么。\n" +
        "- 颜色要有含义（同一对象始终同色），在标签或字幕里点明；标签不要压在线上（用 align / dx / dy 挪开）。\n" +
        "调用后你会收到报错信息和第 1.6 秒左右的一帧截图（不含文字标签）；有错就修好后用完整代码再调用一次，没错就不要为了微调反复重做。"
    override val parameters = obj(
        "title" to t("string", "动画标题（几个字）"),
        "code" to t("string", "JavaScript 函数体，参数 (A, THREE)"),
        "height" to t("number", "卡片高度（像素，200–700）；平面动画可省略，按 mode2d 的范围自动定高"),
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
