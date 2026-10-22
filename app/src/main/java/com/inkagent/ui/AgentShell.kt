package com.inkagent.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.inkagent.AgentController
import com.xnotes.ui.Editor

private fun detach(v: View): View { (v.parent as? ViewGroup)?.removeView(v); return v }

/**
 * Lays the notes app and the agent panel out together. Landscape: the panel is a right-hand pane whose
 * width drags; portrait: a bottom sheet whose height drags. Collapsed: the panel becomes a floating ball
 * in the bottom-right corner and the notes take the whole screen.
 *
 * The notes [content] always sits at the same place in the composition (only its padding changes), so the
 * editor's canvas views and caches survive collapsing, resizing and rotating.
 */
@Composable
fun AgentShell(ctrl: AgentController, content: @Composable () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    var sheetFrac by remember { mutableFloatStateOf(0.45f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth; val maxH = maxHeight
        // Off-screen-ish formula/SVG rasterizer: kept attached (it must be laid out to draw) and covered by the notes.
        AndroidView(factory = { detach(ctrl.raster.view) }, modifier = Modifier.size(900.dp))
        val open = !ctrl.collapsed
        val paneW = ctrl.widthDp.dp.coerceAtMost(maxW * 0.7f).coerceAtLeast(300.dp)
        val sheetH = maxH * sheetFrac
        val handle = 10.dp
        Box(
            Modifier.fillMaxSize().background(Color.White).padding(
                end = if (open && landscape) paneW + handle else 0.dp,
                bottom = if (open && !landscape) sheetH + handle else 0.dp,
            ),
        ) { content() }

        // drag handle
        if (open) {
            if (landscape) Box(
                Modifier.align(Alignment.TopEnd).offset(x = -paneW).width(handle).fillMaxHeight()
                    .background(Color(0xFFF1F2F4))
                    .semantics { contentDescription = "拖动调整 Agent 宽度" }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(onDragEnd = { ctrl.commitWidth() }) { _, d ->
                            val nw = ctrl.widthDp - d / density.density
                            ctrl.widthDp = nw.toInt().coerceIn(300, (maxW.value * 0.7f).toInt())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFC4C8CF))) }
            else Box(
                Modifier.align(Alignment.BottomStart).offset(y = -sheetH).fillMaxWidth().height(handle)
                    .background(Color(0xFFF1F2F4))
                    .semantics { contentDescription = "拖动调整 Agent 高度" }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, d ->
                            sheetFrac = (sheetFrac - d / density.density / maxH.value).coerceIn(0.25f, 0.85f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFC4C8CF))) }
        }

        // the panel WebView: always composed (keeps its page and state); zero-sized while collapsed
        val panelMod = when {
            !open -> Modifier.align(Alignment.BottomEnd).size(1.dp)
            landscape -> Modifier.align(Alignment.TopEnd).width(paneW).fillMaxHeight()
            else -> Modifier.align(Alignment.BottomStart).fillMaxWidth().height(sheetH)
        }
        Box(panelMod.background(Color.White).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            AndroidView(factory = { detach(ctrl.panel.view) }, modifier = Modifier.fillMaxSize())
        }

        if (!open) Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 36.dp).size(60.dp)
                .shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFF4D6BFE))
                .border(2.dp, Color.White, CircleShape)
                .semantics { contentDescription = "展开 Agent" }
                .clickable { ctrl.updateCollapsed(false) },
            contentAlignment = Alignment.Center,
        ) { Text("AI", color = Color.White, fontSize = 20.sp) }
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
