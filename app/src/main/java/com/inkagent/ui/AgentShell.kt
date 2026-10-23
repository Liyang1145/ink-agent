package com.inkagent.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.inkagent.AgentController
import com.xnotes.ui.Editor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun detach(v: View): View { (v.parent as? ViewGroup)?.removeView(v); return v }

/** coerceIn that never throws: an empty range collapses to its lower end. */
private fun Float.clampTo(lo: Float, hi: Float): Float = if (hi < lo) lo else coerceIn(lo, hi)

private class Geo(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun lerp(o: Geo, t: Float) = Geo(x + (o.x - x) * t, y + (o.y - y) * t, w + (o.w - w) * t, h + (o.h - h) * t)
}

/** Screen bounds the window and the split divider are clamped to (px). */
private class Bounds(
    val d: Float, val W: Float, val H: Float, val top: Float, val bottom: Float,
    val minW: Float, val minH: Float, val landscape: Boolean, val fMin: Float, val fMax: Float,
)

/** Mode-transition bookkeeping that must not itself trigger recomposition. */
private class Anim { var mode: String? = null; var from: Geo? = null; var last: Geo? = null; var pending = false; var appear = false }

private const val BAR_DP = 26f
private const val BALL_DP = 58f
private const val DIV_DP = 12f

/** Default floating-window geometry (px), when nothing was saved yet. */
private fun defaultWin(b: Bounds): Geo {
    val w = minOf(440f * b.d, b.W * 0.9f).clampTo(b.minW, b.W)
    val h = minOf((b.bottom - b.top) * 0.78f, 760f * b.d).clampTo(b.minH, b.bottom - b.top)
    return Geo(b.W - w - 24f * b.d, b.top + 64f * b.d, w, h)
}

/** The window as laid out now (px): saved geometry clamped into the visible screen (also above the IME). */
private fun currentWin(ctrl: AgentController, b: Bounds): Geo {
    val def = defaultWin(b)
    val w = (if (ctrl.winW > 0) ctrl.winW * b.d else def.w).clampTo(b.minW, b.W)
    val h = (if (ctrl.winH > 0) ctrl.winH * b.d else def.h).clampTo(minOf(b.minH, b.bottom - b.top), b.bottom - b.top)
    val x = (if (ctrl.winX >= 0) ctrl.winX * b.d else def.x).clampTo(0f, b.W - w)
    val y = (if (ctrl.winY >= 0) ctrl.winY * b.d else def.y).clampTo(b.top, b.bottom - h)
    return Geo(x, y, w, h)
}

private fun storeWin(ctrl: AgentController, g: Geo, d: Float) {
    ctrl.winX = g.x / d; ctrl.winY = g.y / d; ctrl.winW = g.w / d; ctrl.winH = g.h / d
}

/**
 * Lays the notes app and the agent panel out together, in one of three modes (ctrl.mode):
 *  - ball:   the agent is a draggable floating ball that snaps to the nearest side edge; tap → window.
 *  - window: a floating window over the notes, moved by its grab bar, resized by its edges/corners;
 *            position and size are remembered.
 *  - split:  「划分领土」 — docked right (landscape) or bottom (portrait) with a draggable divider whose
 *            ratio is remembered; the notes area shrinks instead of being covered.
 *
 * The notes [content] and the panel WebView each stay at one fixed place in the composition (only their
 * modifiers change), so the editor's canvas and the WebView's page survive every mode switch and rotation.
 */
@Composable
fun AgentShell(ctrl: AgentController, content: @Composable () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat()
        val H = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.systemBars.getTop(density).toFloat()
        val sysBottom = WindowInsets.systemBars.getBottom(density).toFloat()
        val imeBottom = WindowInsets.ime.getBottom(density).toFloat()
        val mode = ctrl.mode
        val split = mode == "split"
        val window = mode == "window"
        val ball = mode == "ball"

        // ---- geometry ----
        val total = if (landscape) W else H
        val minPane = (if (landscape) 300f else 240f) * d
        val minNotes = (if (landscape) 320f else 260f) * d
        val fMin = (minPane / total).clampTo(0.15f, 0.5f)
        val fMax = (1f - minNotes / total).clampTo(fMin, 0.8f)
        val b = Bounds(
            d, W, H, topInset, H - maxOf(sysBottom, imeBottom),
            minOf(300f * d, W - 16f * d), minOf(340f * d, H - topInset - 16f * d), landscape, fMin, fMax,
        )
        val bs = rememberUpdatedState(b)
        val frac = (if (landscape) ctrl.splitLand else ctrl.splitPort).clampTo(fMin, fMax)
        val panePx = total * frac
        val divPx = DIV_DP * d
        val barPx = BAR_DP * d
        val win = currentWin(ctrl, b)
        val hiddenWin = Geo(W + 200f * d, 0f, win.w, win.h - barPx)

        val target = when {
            split && landscape -> Geo(W - panePx, 0f, panePx, H)
            split -> Geo(0f, H - panePx, W, panePx)
            window -> Geo(win.x, win.y + barPx, win.w, win.h - barPx)
            else -> hiddenWin
        }

        // ---- mode transitions: split <-> window glide, ball -> window/split pops in ----
        val anim = remember { Anim() }
        val prog = remember { Animatable(1f) }
        if (anim.mode == null) anim.mode = mode
        if (anim.mode != mode) {
            val old = anim.mode
            anim.from = if (old == "ball" || mode == "ball") null else anim.last
            anim.appear = old == "ball"
            anim.mode = mode
            anim.pending = mode != "ball"
        }
        LaunchedEffect(mode) {
            if (anim.pending) {
                try {
                    prog.snapTo(0f); anim.pending = false
                    prog.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                } finally {
                    anim.pending = false
                    if (prog.value < 1f) runCatching { prog.snapTo(1f) }
                }
            }
        }
        val t = if (anim.pending) 0f else prog.value
        val from = anim.from
        val host = if (from != null && t < 1f) from.lerp(target, t) else target
        anim.last = host
        val appear = if (anim.appear && t < 1f) t else 1f
        ctrl.debugAnim = "t=$t pending=${anim.pending} prog=${prog.value} appear=$appear from=${from != null}"

        // Off-screen-ish formula/SVG rasterizer: kept attached (it must be laid out to draw) and covered by the notes.
        AndroidView(factory = { detach(ctrl.raster.view) }, modifier = Modifier.size(900.dp))

        // ---- the notes: shrink in split mode, full screen otherwise ----
        val notesEnd = if (split && landscape) with(density) { (panePx + divPx).toDp() } else 0.dp
        val notesBottom = if (split && !landscape) with(density) { (panePx + divPx).toDp() } else 0.dp
        Box(
            Modifier.fillMaxSize().background(Color.White)
                .padding(end = notesEnd, bottom = notesBottom)
                .onSizeChanged { ctrl.notesSizePx = intArrayOf(it.width, it.height) },
        ) { content() }

        // ---- split divider ----
        if (split) {
            val dm = if (landscape)
                Modifier.offset { IntOffset((W - panePx - divPx).roundToInt(), 0) }
                    .size(with(density) { divPx.toDp() }, with(density) { H.toDp() })
            else
                Modifier.offset { IntOffset(0, (H - panePx - divPx).roundToInt()) }
                    .size(with(density) { W.toDp() }, with(density) { divPx.toDp() })
            Box(
                dm.background(Color(0xFFF1F2F4))
                    .semantics { contentDescription = "拖动调整分屏比例" }
                    .pointerInput(landscape) {
                        detectDragGestures(onDragEnd = { ctrl.commitSplit() }, onDragCancel = { ctrl.commitSplit() }) { ch, amt ->
                            ch.consume()
                            val bb = bs.value
                            if (bb.landscape) ctrl.splitLand = (ctrl.splitLand.clampTo(bb.fMin, bb.fMax) - amt.x / bb.W).clampTo(bb.fMin, bb.fMax)
                            else ctrl.splitPort = (ctrl.splitPort.clampTo(bb.fMin, bb.fMax) - amt.y / bb.H).clampTo(bb.fMin, bb.fMax)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val pill = if (landscape) Modifier.size(4.dp, 40.dp) else Modifier.size(40.dp, 4.dp)
                Box(pill.clip(RoundedCornerShape(2.dp)).background(Color(0xFFB9BEC7)))
            }
        }

        // ---- floating window chrome behind the panel: card + grab bar ----
        val showChrome = window
        val cardGeo = Geo(host.x, host.y - barPx, host.w, host.h + barPx)
        val origin = TransformOrigin(if (ctrl.ballRight) 1f else 0f, 0.85f)
        if (showChrome) {
            Box(
                Modifier.offset { IntOffset(cardGeo.x.roundToInt(), cardGeo.y.roundToInt()) }
                    .size(with(density) { cardGeo.w.toDp() }, with(density) { cardGeo.h.toDp() })
                    .graphicsLayer { scaleX = 0.94f + 0.06f * appear; scaleY = 0.94f + 0.06f * appear; transformOrigin = origin }
                    .shadow(14.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(0.5.dp, Color(0xFFDADDE2), RoundedCornerShape(14.dp)),
            ) {
                Box(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().height(BAR_DP.dp)
                        .background(Color(0xFFF6F7F9))
                        .semantics { contentDescription = "拖动浮窗" }
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { ctrl.commitWindow() }, onDragCancel = { ctrl.commitWindow() }) { ch, amt ->
                                ch.consume()
                                val bb = bs.value
                                val g = currentWin(ctrl, bb)
                                storeWin(ctrl, Geo((g.x + amt.x).clampTo(0f, bb.W - g.w), (g.y + amt.y).clampTo(bb.top, bb.bottom - g.h), g.w, g.h), bb.d)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFC4C8CF))) }
            }
        }

        // ---- the panel WebView: always composed (keeps its page and state); parked off-screen as a ball ----
        val hostShape = if (window) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RectangleShape
        Box(
            Modifier.offset { IntOffset(host.x.roundToInt(), host.y.roundToInt()) }
                .size(with(density) { host.w.coerceAtLeast(1f).toDp() }, with(density) { host.h.coerceAtLeast(1f).toDp() })
                .graphicsLayer {
                    if (window) { scaleX = 0.94f + 0.06f * appear; scaleY = 0.94f + 0.06f * appear; transformOrigin = origin }
                }
                .clip(hostShape)
                .background(Color.White)
                .then(if (split) Modifier.windowInsetsPadding(WindowInsets.systemBars).imePadding() else Modifier)
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    ctrl.panelRectPx = intArrayOf(p.x.roundToInt(), p.y.roundToInt(), c.size.width, c.size.height)
                },
        ) {
            AndroidView(factory = { detach(ctrl.panel.view) }, modifier = Modifier.fillMaxSize())
        }

        // ---- resize handles (on top of the panel edges) ----
        if (window) {
            val e = 22f * d   // handle thickness (straddles the edge)
            val c = 34f * d   // corner square
            fun resize(left: Boolean, right: Boolean, bottom: Boolean, dx: Float, dy: Float) {
                val bb = bs.value
                val g = currentWin(ctrl, bb)
                var x = g.x; var w = g.w; var h = g.h
                if (right) w = (w + dx).clampTo(bb.minW, bb.W - x)
                if (left) { val nx = (x + dx).clampTo(0f, x + w - bb.minW); w += x - nx; x = nx }
                if (bottom) h = (h + dy).clampTo(bb.minH, bb.bottom - g.y)
                storeWin(ctrl, Geo(x, g.y, w, h), bb.d)
            }
            @Composable
            fun Handle(x: Float, y: Float, w: Float, h: Float, label: String, l: Boolean, r: Boolean, bt: Boolean, grip: Int = 0) {
                Box(
                    Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                        .semantics { contentDescription = label }
                        .pointerInput(label) {
                            detectDragGestures(onDragEnd = { ctrl.commitWindow() }, onDragCancel = { ctrl.commitWindow() }) { ch, amt ->
                                ch.consume(); resize(l, r, bt, amt.x, amt.y)
                            }
                        },
                ) {
                    if (grip != 0) Canvas(Modifier.fillMaxSize()) {
                        val s = size.width
                        val col = Color(0xFF9AA0AA)
                        val sw = 1.6f * d
                        val inset = s * 0.5f   // the corner of the card sits at the handle centre
                        for (k in 1..3) {
                            val o = k * 4.5f * d
                            if (grip > 0) drawLine(col, Offset(inset - o, inset - 2f), Offset(inset - 2f, inset - o), sw, StrokeCap.Round)
                            else drawLine(col, Offset(inset + o, inset - 2f), Offset(inset + 2f, inset - o), sw, StrokeCap.Round)
                        }
                    }
                }
            }
            val L = cardGeo.x; val T = cardGeo.y; val R = cardGeo.x + cardGeo.w; val B = cardGeo.y + cardGeo.h
            Handle(R - e / 2, T + barPx, e, (B - T - barPx - c / 2).coerceAtLeast(1f), "调整浮窗宽度", false, true, false)
            Handle(L - e / 2, T + barPx, e, (B - T - barPx - c / 2).coerceAtLeast(1f), "调整浮窗左边", true, false, false)
            Handle(L + c / 2, B - e / 2, (R - L - c).coerceAtLeast(1f), e, "调整浮窗高度", false, false, true)
            Handle(R - c / 2, B - c / 2, c, c, "调整浮窗大小", false, true, true, grip = 1)
            Handle(L - c / 2, B - c / 2, c, c, "调整浮窗左下角", true, false, true, grip = -1)
        }

        // ---- the floating ball ----
        if (ball) {
            val ballPx = BALL_DP * d
            val margin = 12f * d
            fun restX(right: Boolean, bb: Bounds) = if (right) bb.W - ballPx - margin else margin
            fun clampY(y: Float, bb: Bounds) = y.clampTo(bb.top + margin, bb.bottom - ballPx - margin)
            val restY = clampY(if (ctrl.ballY >= 0) ctrl.ballY * d else H - ballPx - 140f * d, b)
            val bx = remember { Animatable(restX(ctrl.ballRight, b)) }
            var dragging by remember { mutableStateOf(false) }
            var dragX by remember { mutableFloatStateOf(0f) }
            var dragY by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(W, H) { if (!dragging) bx.snapTo(restX(ctrl.ballRight, bs.value)) }
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
            val x = if (dragging) dragX else bx.value
            val y = if (dragging) dragY else restY
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(BALL_DP.dp)
                    .graphicsLayer { scaleX = 0.4f + 0.6f * pop.value; scaleY = 0.4f + 0.6f * pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                    .shadow(10.dp, CircleShape).clip(CircleShape).background(Color(0xFF4D6BFE))
                    .border(2.dp, Color.White, CircleShape)
                    .semantics { contentDescription = "展开 Agent" }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragX = bx.value; dragY = clampY(if (ctrl.ballY >= 0) ctrl.ballY * d else bs.value.H - ballPx - 140f * d, bs.value); dragging = true },
                            onDragEnd = {
                                val bb = bs.value
                                val right = dragX + ballPx / 2 > bb.W / 2
                                ctrl.ballRight = right; ctrl.ballY = dragY / bb.d; ctrl.commitBall()
                                val sx = dragX
                                scope.launch {
                                    bx.snapTo(sx); dragging = false
                                    bx.animateTo(restX(right, bb), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                                }
                            },
                            onDragCancel = { dragging = false },
                        ) { ch, amt ->
                            ch.consume()
                            val bb = bs.value
                            dragX = (dragX + amt.x).clampTo(0f, bb.W - ballPx)
                            dragY = clampY(dragY + amt.y, bb)
                        }
                    }
                    .clickable { ctrl.changeMode("window") },
                contentAlignment = Alignment.Center,
            ) { Text("AI", color = Color.White, fontSize = 20.sp) }
        }
    }
}

/** First launch lands in a note, not on the file explorer: reopen the restored note, or open/create "我的笔记". */
suspend fun openStartupNote(ed: Editor) {
    if (ed.noteOpen) return
    if (ed.agentShowRestored()) return
    if (ed.browseRoot == null) ed.useInternalStorage()
    val root = ed.browseRoot ?: return
    val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val rootDoc = ed.browseRootDocId(root)
            ed.browseChildren(root, rootDoc).firstOrNull { !it.isDir && it.name == "我的笔记.xnote" }?.documentUri
                ?: ed.createBlankNoteFile(root, rootDoc, "我的笔记")
        }.getOrNull()
    } ?: return
    ed.openAsync(uri, "我的笔记.xnote")
}
