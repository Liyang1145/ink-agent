package com.xnotes.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

// Hark-like palette: warm paper, ink-black accents, soft hairlines. A dark twin follows the xnotes appearance
// (system / dark / light / oled) so home, explorer and editor always agree.
private class HomeColors(
    val bg: Color, val card: Color, val line: Color, val ink: Color, val dim: Color, val soft: Color,
    val body: Color, val primary: Color, val onPrimary: Color, val shadow: Color,
)
private val LightHC = HomeColors(
    bg = Color(0xFFF6F4EF), card = Color(0xFFFFFFFF), line = Color(0xFFE7E3DA), ink = Color(0xFF1C1B19), dim = Color(0xFF8B877E),
    soft = Color(0xFFEFECE5), body = Color(0xFF5E5A53), primary = Color(0xFF1C1B19), onPrimary = Color.White, shadow = Color(0x1F000000),
)
private val DarkHC = HomeColors(
    bg = Color(0xFF171716), card = Color(0xFF222120), line = Color(0xFF34322F), ink = Color(0xFFEDEAE4), dim = Color(0xFF9A968D),
    soft = Color(0xFF2B2A28), body = Color(0xFFBDB8AE), primary = Color(0xFFEDEAE4), onPrimary = Color(0xFF171716), shadow = Color(0x00000000),
)
private val LocalHC = staticCompositionLocalOf { LightHC }
private val Serif = FontFamily.Serif

private val KIND_COLORS = listOf(Color(0xFFD9775A), Color(0xFF5B8DEF), Color(0xFF4CAF7A), Color(0xFFB07CD8), Color(0xFFE0A73C))
private fun kindColor(k: String) = KIND_COLORS[(k.hashCode() and 0x7fffffff) % KIND_COLORS.size]

/**
 * InkAgent's own front door, drawn over the xnotes backstage: a greeting, one box to ask the
 * agent, a few entry points, and suggestion cards the agent writes from recent study while idle.
 * The original xnotes explorer is 「我的笔记」.
 * Layout adapts to the space it gets (landscape with the agent pane beside it, full landscape, portrait):
 * the top gap scales with height, suggestion cards share the row when they fit and scroll when they don't,
 * and the two recents panels stack on narrow widths.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarkHome(
    editor: Editor,
    onOpenFile: (String) -> Unit,
    onNotes: () -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { com.inkagent.AppLog.i("Home", "hark home shown") } // e2e reads this (Compose text is invisible to uiautomator)
    var recents by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<com.inkagent.agent.SessionStore.Info>>(emptyList()) }
    var libCount by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf(Suggestions.load(ctx)) }
    var generating by remember { mutableStateOf(false) }
    var genTick by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    val ctrl = com.inkagent.AgentBridge.controller
    val hc = if (com.xnotes.ui.theme.LocalPalette.current.isDark) DarkHC else LightHC

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
                android.widget.Toast.makeText(ctx, "这次没想出来：" + Suggestions.error().take(60), android.widget.Toast.LENGTH_SHORT).show()
            } else if (force && ctrl?.settings?.callConfig() == null) withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, "先在 AI 设置里配置好模型，我才能帮你准备", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    // a quick, quiet entrance: fade up 10 dp
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    val rise = with(LocalDensity.current) { 10.dp.toPx() }

    fun ask(t: String) { val s = t.trim(); if (s.isEmpty()) return; input = ""; ctrl?.askFromHome(s) }
    fun openLibrary(extra: Intent.() -> Unit = {}) = ctx.startActivity(Intent(ctx, com.inkagent.library.LibraryActivity::class.java).apply(extra))

    CompositionLocalProvider(LocalHC provides hc) {
    BoxWithConstraints(Modifier.fillMaxSize().background(hc.bg)) {
        val narrow = maxWidth < 600.dp
        val side = if (narrow) 20.dp else 32.dp
        val content = (minOf(maxWidth, 920.dp) - side * 2)
        val topGap = (maxHeight * 0.075f).coerceIn(16.dp, 84.dp)
        val shortH = maxHeight < 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = side)
                .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * rise },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // top bar: brand left, today's date right
            Row(Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(hc.primary), contentAlignment = Alignment.Center) {
                    Text("i", color = hc.onPrimary, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text("InkAgent", color = hc.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(today(), color = hc.dim, fontSize = 14.sp)
            }

            Column(Modifier.widthIn(max = 856.dp).fillMaxWidth()) {
                Spacer(Modifier.height(topGap))
                Text(greet(), color = hc.ink, fontFamily = Serif, fontSize = if (narrow) 34.sp else 42.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(6.dp))
                Text("今天想弄懂点什么？", color = hc.dim, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(26.dp))

                // ask box
                Row(
                    Modifier.fillMaxWidth().shadow(if (hc === LightHC) 8.dp else 0.dp, RoundedCornerShape(28.dp), ambientColor = hc.shadow, spotColor = hc.shadow)
                        .clip(RoundedCornerShape(28.dp)).background(hc.card).border(1.dp, hc.line, RoundedCornerShape(28.dp))
                        .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (input.isEmpty()) Text("问一道题、讲一个概念，或者让我出几道练习…", color = hc.dim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(
                            input, { input = it }, Modifier.fillMaxWidth().semantics { contentDescription = "向 AI 提问" },
                            textStyle = TextStyle(color = hc.ink, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(hc.ink), maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { ask(input) }),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (input.isBlank()) hc.soft else hc.primary).clickable(enabled = input.isNotBlank()) { ask(input) }
                            .semantics { contentDescription = "发送" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(XnotesIcons.arrowUp, null, tint = if (input.isBlank()) hc.dim else hc.onPrimary, modifier = Modifier.size(21.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HPill(XnotesIcons.edit, "新建笔记", primary = true) { HomeRequests.newNote = true; onNotes() }
                    HPill(XnotesIcons.folder, "我的笔记", onClick = onNotes)
                    HPill(XnotesIcons.database, if (libCount > 0) "资料库 · $libCount" else "资料库") { openLibrary() }
                    HPill(XnotesIcons.importDoc, "导入资料") { openLibrary { putExtra("import", true) } }
                    HPill(XnotesIcons.idea, "新对话") { ctrl?.newSessionFromHome() }
                }

                // suggestions
                Spacer(Modifier.height(if (shortH) 36.dp else 48.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("为你准备", color = hc.ink, fontFamily = Serif, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    if (generating) {
                        CircularProgressIndicator(Modifier.size(13.dp), color = hc.dim, strokeWidth = 1.6.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("正在根据最近的学习整理…", color = hc.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(hc.soft).clickable(enabled = !generating) { genTick++ }
                            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(XnotesIcons.restore, null, tint = if (generating) hc.dim else hc.ink, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("换一批", color = if (generating) hc.dim else hc.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val gap = 14.dp
                val fitAll = cards.isNotEmpty() && content >= (236.dp * cards.size + gap * (cards.size - 1))
                if (cards.isEmpty()) HEmpty("这一批都处理完了。点「换一批」，我再根据最近的学习想几张。", Modifier.padding(vertical = 14.dp))
                else if (fitAll) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    cards.forEach { c -> key(c.id) {
                        SuggestCard(c, Modifier.weight(1f), onGo = { ask(c.prompt) }, onDismiss = { Suggestions.dismiss(ctx, c.id); cards = cards.filter { it.id != c.id } })
                    } }
                } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    cards.forEach { c -> key(c.id) {
                        SuggestCard(c, Modifier.width(minOf(280.dp, content * 0.86f)), onGo = { ask(c.prompt) }, onDismiss = { Suggestions.dismiss(ctx, c.id); cards = cards.filter { it.id != c.id } })
                    } }
                }

                // recents: side by side when there is room, stacked otherwise
                Spacer(Modifier.height(32.dp))
                val notes: @Composable (Modifier) -> Unit = { m ->
                    HPanel("继续写", m) {
                        if (recents.isEmpty()) HEmpty("还没有笔记。点上面的「新建笔记」开始写。")
                        recents.forEach { r ->
                            HItem(XnotesIcons.page, r.entry.name.removeSuffix(".xnote").removeSuffix(".xcanvas"), ago(r.opened)) { onOpenFile(r.entry.documentUri) }
                        }
                    }
                }
                val chats: @Composable (Modifier) -> Unit = { m ->
                    HPanel("最近对话", m) {
                        if (sessions.isEmpty()) HEmpty("还没有对话。在上面问一句试试。")
                        sessions.forEach { s -> HItem(XnotesIcons.idea, s.title, ago(s.lastTime)) { ctrl?.openSessionFromHome(s.id) } }
                    }
                }
                if (content >= 600.dp) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    notes(Modifier.weight(1f).fillMaxHeight()); chats(Modifier.weight(1f).fillMaxHeight())
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { notes(Modifier.fillMaxWidth()); chats(Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    }
}

@Composable
private fun HPill(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    val hc = LocalHC.current
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (primary) hc.primary else hc.card)
            .border(1.dp, if (primary) hc.primary else hc.line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) hc.onPrimary else hc.ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (primary) hc.onPrimary else hc.ink, fontSize = 14.sp, fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun SuggestCard(c: Suggestions.Card, modifier: Modifier, onGo: () -> Unit, onDismiss: () -> Unit) {
    val hc = LocalHC.current
    // each new card fades in once
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    Column(
        modifier.height(228.dp).graphicsLayer { alpha = a.value }.clip(RoundedCornerShape(22.dp)).background(hc.card)
            .border(1.dp, hc.line, RoundedCornerShape(22.dp)).clickable(onClick = onGo).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(c.kind)))
            Spacer(Modifier.width(8.dp))
            Text(c.kind, color = hc.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss).semantics { contentDescription = "不需要这张" }, contentAlignment = Alignment.Center) {
                Icon(XnotesIcons.close, null, tint = hc.dim, modifier = Modifier.size(15.dp))
            }
        }
        // a two-line title leaves room for two lines of body, so the text never runs under the button
        var titleLines by remember(c.id) { mutableStateOf(1) }
        Column(Modifier.padding(end = 12.dp)) {
            Text(c.title, color = hc.ink, fontFamily = Serif, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp,
                onTextLayout = { titleLines = it.lineCount })
            Spacer(Modifier.height(6.dp))
            Text(c.body, color = hc.body, fontSize = 14.sp, lineHeight = 21.sp, maxLines = if (titleLines >= 2) 2 else 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(hc.primary).clickable(onClick = onGo).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(c.action, color = hc.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HPanel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hc = LocalHC.current
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(hc.card).border(1.dp, hc.line, RoundedCornerShape(22.dp)).padding(vertical = 12.dp, horizontal = 8.dp)) {
        Text(title, color = hc.ink, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        content()
    }
}

@Composable
private fun HEmpty(t: String, modifier: Modifier = Modifier) =
    Text(t, color = LocalHC.current.dim, fontSize = 14.sp, lineHeight = 21.sp, modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp))

@Composable
private fun HItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val hc = LocalHC.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = hc.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = hc.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(sub, color = hc.dim, fontSize = 12.sp)
    }
}

private fun greet(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..10 -> "早上好"; in 11..13 -> "中午好"; in 14..17 -> "下午好"; in 18..22 -> "晚上好"; else -> "夜深了" }
}

private fun today(): String = java.text.SimpleDateFormat("M月d日 EEEE", java.util.Locale.CHINA).format(java.util.Date())

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
