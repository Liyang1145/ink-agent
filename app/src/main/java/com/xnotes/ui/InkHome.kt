package com.xnotes.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * InkAgent's own home: continue writing, start a note, the study library and recent AI
 * conversations in one place. The xnotes file explorer lives one tap away under 「全部笔记」.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InkHomePane(
    editor: Editor,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onOpenFile: (String) -> Unit,
    onNewNote: () -> Unit,
    onAllNotes: () -> Unit,
) {
    val palette = LocalPalette.current
    val ctx = LocalContext.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    var recents by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var libTop by remember { mutableStateOf<List<Triple<String, String, Int>>>(emptyList()) }
    var libNew by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var libCount by remember { mutableStateOf(0) }
    var sessions by remember { mutableStateOf<List<com.inkagent.agent.SessionStore.Info>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(editor.browseRoot, editor.noteOpen, tick) {
        withContext(Dispatchers.IO) {
            recents = runCatching { editor.browseRoot?.let { editor.recentEntries(it) }.orEmpty().take(10) }.getOrDefault(emptyList())
            runCatching {
                val lib = com.inkagent.LibraryBridge.userLib(ctx)
                lib.ensure()
                synchronized(lib) {
                    libCount = lib.items.size
                    libTop = lib.folders.values.filter { it.parent.isEmpty() }.map { f -> Triple(f.id, f.name, lib.list(f.id, true, null, null).size) }
                    libNew = lib.items.values.filter { it.added > 0 }.sortedByDescending { it.added }.take(5).map { it.id to it.title }
                }
            }
            sessions = runCatching { com.inkagent.AgentBridge.controller?.recentSessions(6).orEmpty() }.getOrDefault(emptyList())
        }
    }

    fun openLibrary(extra: Intent.() -> Unit = {}) {
        ctx.startActivity(Intent(ctx, com.inkagent.library.LibraryActivity::class.java).apply(extra))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!sidebarOpen) IconButton(onClick = onShowSidebar) { Icon(XnotesIcons.menu, "菜单", tint = text, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("InkAgent", color = text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(greeting() + "  手写、资料、AI，一起学数学", color = dim, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(XnotesIcons.edit, "新建笔记", "空白页开始写", true, onNewNote)
            ActionCard(XnotesIcons.database, "资料库", if (libCount > 0) "$libCount 份讲义、习题与资料" else "讲义、真题、我的资料") { openLibrary() }
            ActionCard(XnotesIcons.importDoc, "导入资料", "本机文件、微信 QQ 分享") { openLibrary { putExtra("import", true) } }
            ActionCard(XnotesIcons.idea, "问 AI", "新开一个对话") { com.inkagent.AgentBridge.controller?.newSessionFromHome() }
            ActionCard(XnotesIcons.folder, "全部笔记", "文件夹与文件管理", false, onAllNotes)
        }

        Section("继续写", if (recents.isNotEmpty()) "全部笔记" else null, onAllNotes)
        if (recents.isEmpty()) Hint("还没有打开过的笔记。点「新建笔记」开始写。")
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            recents.forEach { r ->
                Tile(r.entry.name.removeSuffix(".xnote").removeSuffix(".xcanvas"), listOfNotNull(r.where, ago(r.opened)).joinToString(" · "), XnotesIcons.page) { onOpenFile(r.entry.documentUri) }
            }
        }

        Section("资料库", "打开", { openLibrary() })
        if (libTop.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            libTop.forEach { (id, name, n) -> Chip("$name  $n") { openLibrary { putExtra("folder", id) } } }
        }
        if (libNew.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("最近加入", color = dim, fontSize = 13.sp)
            libNew.forEach { (id, title) -> Line(XnotesIcons.file, title) { openLibrary { putExtra("item", id) } } }
        }

        Section("最近的 AI 对话", null, {})
        if (sessions.isEmpty()) Hint("还没有对话。圈出题目点「加入 Agent」，或者点「问 AI」。")
        else sessions.forEach { s -> Line(XnotesIcons.idea, s.title, ago(s.lastTime)) { com.inkagent.AgentBridge.controller?.openSessionFromHome(s.id) } }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, sub: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = if (primary) p.selectionBackground.toComposeColor() else p.surface.toComposeColor()
    val fg = if (primary) p.selectionForeground.toComposeColor() else p.text.toComposeColor()
    Column(
        Modifier.width(176.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, p.border.toComposeColor().copy(alpha = if (primary) 0f else 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        Icon(icon, null, tint = if (primary) fg else p.accent.toComposeColor(), modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(sub, color = if (primary) fg.copy(alpha = 0.75f) else p.textDim.toComposeColor(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Section(title: String, action: String?, onAction: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.text.toComposeColor(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = p.accent.toComposeColor(), fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun Hint(t: String) = Text(t, color = LocalPalette.current.textDim.toComposeColor(), fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun Tile(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(168.dp).height(132.dp).clip(RoundedCornerShape(16.dp)).background(p.paper.toComposeColor())
            .border(1.dp, p.paperBorder.toComposeColor(), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, null, tint = p.textDim.toComposeColor(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = p.text.toComposeColor(), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = p.textDim.toComposeColor(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Chip(t: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(p.surface.toComposeColor()).border(1.dp, p.border.toComposeColor().copy(alpha = 0.6f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(t, color = p.text.toComposeColor(), fontSize = 14.sp)
    }
}

@Composable
private fun Line(icon: ImageVector, title: String, sub: String? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.textDim.toComposeColor(), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = p.text.toComposeColor(), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (sub != null) Text(sub, color = p.textDim.toComposeColor(), fontSize = 12.sp)
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..10 -> "早上好"; in 11..13 -> "中午好"; in 14..17 -> "下午好"; else -> "晚上好" }
}

private fun ago(t: Long): String {
    if (t <= 0) return ""
    val d = (System.currentTimeMillis() - t) / 1000
    return when {
        d < 60 -> "刚刚"
        d < 3600 -> "${d / 60} 分钟前"
        d < 86400 -> "${d / 3600} 小时前"
        d < 86400 * 7 -> "${d / 86400} 天前"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(t))
    }
}
