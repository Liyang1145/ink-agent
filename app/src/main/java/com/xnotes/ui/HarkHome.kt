package com.xnotes.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkagent.Suggestions
import com.xnotes.ui.icons.XnotesIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Requests from InkAgent's home to the xnotes backstage underneath it. */
object HomeRequests {
    var newNote by mutableStateOf(false)
}

// Hark-like palette: warm paper, ink-black accents, soft hairlines.
private val HBg = Color(0xFFF6F4EF)
private val HCard = Color(0xFFFFFFFF)
private val HLine = Color(0xFFE7E3DA)
private val HInk = Color(0xFF1C1B19)
private val HDim = Color(0xFF8B877E)
private val HSoft = Color(0xFFEFECE5)
private val Serif = FontFamily.Serif

private val KIND_COLORS = listOf(Color(0xFFD9775A), Color(0xFF5B8DEF), Color(0xFF4CAF7A), Color(0xFFB07CD8), Color(0xFFE0A73C))
private fun kindColor(k: String) = KIND_COLORS[(k.hashCode() and 0x7fffffff) % KIND_COLORS.size]

/**
 * InkAgent's own front door, drawn over the xnotes backstage: a greeting, one box to ask the
 * agent, a few entry points, and suggestion cards the agent writes from recent study while idle.
 * The original xnotes explorer is 「我的笔记」.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarkHome(
    editor: Editor,
    onOpenFile: (String) -> Unit,
    onNotes: () -> Unit,
) {
    val ctx = LocalContext.current
    var recents by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<com.inkagent.agent.SessionStore.Info>>(emptyList()) }
    var libCount by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf(Suggestions.load(ctx)) }
    var generating by remember { mutableStateOf(false) }
    var genTick by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    val ctrl = com.inkagent.AgentBridge.controller

    LaunchedEffect(editor.browseRoot, editor.noteOpen) {
        withContext(Dispatchers.IO) {
            recents = runCatching { editor.browseRoot?.let { editor.recentEntries(it) }.orEmpty().take(5) }.getOrDefault(emptyList())
            sessions = runCatching { com.inkagent.AgentBridge.controller?.recentSessions(5).orEmpty() }.getOrDefault(emptyList())
            libCount = runCatching { com.inkagent.LibraryBridge.userLib(ctx).let { it.ensure(); synchronized(it) { it.items.size } } }.getOrDefault(0)
        }
    }
    // While idle on home, let the agent refresh its suggestions (stale + something new happened).
    LaunchedEffect(genTick) {
        val force = genTick > 0
        kotlinx.coroutines.delay(if (force) 0 else 1200)
        withContext(Dispatchers.IO) {
            generating = true
            val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
            Suggestions.refresh(ctx, force) { done.complete(it) }
            val ok = done.await()
            generating = false
            if (ok) cards = Suggestions.load(ctx)
            else if (force && Suggestions.error().isNotEmpty()) withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, "生成建议失败：" + Suggestions.error().take(60), android.widget.Toast.LENGTH_SHORT).show()
            } else if (force && ctrl?.settings?.callConfig() == null) withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, "先在 AI 设置里配置模型", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun ask(t: String) { val s = t.trim(); if (s.isEmpty()) return; input = ""; ctrl?.askFromHome(s) }
    fun openLibrary(extra: Intent.() -> Unit = {}) = ctx.startActivity(Intent(ctx, com.inkagent.library.LibraryActivity::class.java).apply(extra))

    Box(Modifier.fillMaxSize().background(HBg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(HInk), contentAlignment = Alignment.Center) {
                    Text("i", color = Color.White, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text("InkAgent", color = HInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TopLink(XnotesIcons.folder, "我的笔记", onNotes)
                Spacer(Modifier.width(6.dp))
                TopLink(XnotesIcons.database, "资料库") { openLibrary() }
            }

            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth()) {
                Spacer(Modifier.height(72.dp))
                Text(greet(), color = HInk, fontFamily = Serif, fontSize = 44.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text("今天想学点什么？", color = HDim, fontSize = 17.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(30.dp))

                // ask box
                Row(
                    Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                        .clip(RoundedCornerShape(28.dp)).background(HCard).border(1.dp, HLine, RoundedCornerShape(28.dp))
                        .padding(start = 24.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (input.isEmpty()) Text("问点什么，或者让我出几道题…", color = HDim, fontSize = 17.sp)
                        BasicTextField(
                            input, { input = it }, Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = HInk, fontSize = 17.sp), cursorBrush = SolidColor(HInk), maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { ask(input) }),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (input.isBlank()) HSoft else HInk).clickable { ask(input) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(XnotesIcons.arrowUp, "发送", tint = if (input.isBlank()) HDim else Color.White, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                FlowRow(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HPill(XnotesIcons.edit, "新建笔记") { HomeRequests.newNote = true; onNotes() }
                    HPill(XnotesIcons.folder, "我的笔记", onClick = onNotes)
                    HPill(XnotesIcons.database, if (libCount > 0) "资料库 · $libCount" else "资料库") { openLibrary() }
                    HPill(XnotesIcons.importDoc, "导入资料") { openLibrary { putExtra("import", true) } }
                    HPill(XnotesIcons.idea, "新对话") { ctrl?.newSessionFromHome() }
                }

                // suggestions
                Spacer(Modifier.height(48.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("为你准备", color = HInk, fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    if (generating) Text("根据最近的学习整理中…", color = HDim, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("换一批", color = if (generating) HDim else HInk, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(HSoft)
                            .clickable(enabled = !generating) { genTick++ }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
                Spacer(Modifier.height(14.dp))
                if (cards.isEmpty()) Text("这一批都看过了。点「换一批」让我再想想。", color = HDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 18.dp))
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards.forEach { c ->
                        SuggestCard(c, onGo = { ask(c.prompt) }, onDismiss = { Suggestions.dismiss(ctx, c.id); cards = cards.filter { it.id != c.id } })
                    }
                }

                // recents
                Spacer(Modifier.height(40.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
                    HPanel("继续写", Modifier.weight(1f).widthIn(min = 300.dp)) {
                        if (recents.isEmpty()) HEmpty("还没有打开过的笔记")
                        recents.forEach { r ->
                            HItem(XnotesIcons.page, r.entry.name.removeSuffix(".xnote").removeSuffix(".xcanvas"), ago(r.opened)) { onOpenFile(r.entry.documentUri) }
                        }
                    }
                    HPanel("最近对话", Modifier.weight(1f).widthIn(min = 300.dp)) {
                        if (sessions.isEmpty()) HEmpty("还没有对话")
                        sessions.forEach { s -> HItem(XnotesIcons.idea, s.title, ago(s.lastTime)) { ctrl?.openSessionFromHome(s.id) } }
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun TopLink(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HInk, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = HInk, fontSize = 14.sp)
    }
}

@Composable
private fun HPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(HCard).border(1.dp, HLine, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HInk, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = HInk, fontSize = 14.sp)
    }
}

@Composable
private fun SuggestCard(c: Suggestions.Card, onGo: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.width(300.dp).height(236.dp).clip(RoundedCornerShape(22.dp)).background(HCard)
            .border(1.dp, HLine, RoundedCornerShape(22.dp)).clickable(onClick = onGo).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(c.kind)))
            Spacer(Modifier.width(8.dp))
            Text(c.kind, color = HDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(XnotesIcons.close, "不需要", tint = HDim, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.title, color = HInk, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(c.body, color = Color(0xFF5E5A53), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(HInk).clickable(onClick = onGo).padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(c.action, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HPanel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(HCard).border(1.dp, HLine, RoundedCornerShape(22.dp)).padding(vertical = 14.dp, horizontal = 10.dp)) {
        Text(title, color = HInk, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        content()
    }
}

@Composable
private fun HEmpty(t: String) = Text(t, color = HDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))

@Composable
private fun HItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = HInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(sub, color = HDim, fontSize = 12.sp)
    }
}

private fun greet(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..10 -> "早上好"; in 11..13 -> "中午好"; in 14..17 -> "下午好"; in 18..22 -> "晚上好"; else -> "夜深了" }
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
