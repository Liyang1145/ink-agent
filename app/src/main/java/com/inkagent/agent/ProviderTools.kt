package com.inkagent.agent

import com.inkagent.Settings
import com.inkagent.llm.CancelSignal
import com.inkagent.llm.Llm
import com.inkagent.llm.Protocol
import com.inkagent.llm.Provider
import org.json.JSONArray
import org.json.JSONObject

/** "sk-…abcd" style mask; never the key itself. */
fun maskKey(k: String): String {
    val s = k.trim()
    if (s.isEmpty()) return "（未设置）"
    if (s.length <= 8) return "••••"
    return s.take(3) + "…" + s.takeLast(4)
}

/**
 * Lets the agent add or edit model providers from chat: the user pastes a key and a base URL, the agent
 * calls manage_provider. Every change goes through [confirm] (a card in the panel the user must accept)
 * before it takes effect, and nothing here ever returns a stored key — only masked values.
 */
class ProviderTools(
    private val settings: Settings,
    /** Blocks until the user answers the confirm card; true = accepted. */
    private val confirm: (title: String, lines: List<Pair<String, String>>) -> Boolean,
    private val onChanged: () -> Unit,
) {
    private fun describe(p: Provider): JSONObject = JSONObject()
        .put("id", p.id).put("name", p.name).put("protocol", protocolId(p.protocol)).put("base_url", p.baseUrl)
        .put("api_key", maskKey(p.apiKey)).put("models", JSONArray(p.models))
        .put("status", settings.providerStatus(p.id)).put("is_default", settings.providerId == p.id)

    private fun protocolId(p: Protocol) = when (p) { Protocol.ANTHROPIC_MESSAGES -> "anthropic-messages"; Protocol.OPENAI_CHAT -> "openai-chat" }
    private fun protocolOf(s: String?): Protocol? = when (s?.lowercase()) {
        null, "" -> null
        "anthropic", "anthropic-messages", "anthropic_messages" -> Protocol.ANTHROPIC_MESSAGES
        "openai", "openai-chat", "openai_chat", "openai-completions", "chat-completions" -> Protocol.OPENAI_CHAT
        else -> null
    }

    /** GET /models; records the status dot. Returns the ids or throws. */
    private fun probe(p: Provider): List<String> = try {
        Llm.listModels(p).also { settings.setProviderStatus(p.id, "ok") }
    } catch (e: Exception) { settings.setProviderStatus(p.id, "error"); throw e }

    fun register(reg: ToolRegistry) {
        reg.register(object : Tool {
            override val name = "manage_provider"
            override val description = """管理模型服务商（OpenAI 兼容或 Anthropic Messages 协议）。
action=list：列出服务商（密钥只返回打码值）。
action=upsert：新建或修改服务商（传 id 表示修改；api_key 留空表示保留已保存的密钥）；保存后自动请求 /models 并测试连通性。
action=fetch_models：重新获取某服务商的可用模型并写入其模型目录。
action=test：测试连通性。
action=delete：删除服务商。
action=set_default：设置默认服务商与模型。
所有会改动配置的操作都会先在面板里弹出确认卡片，用户确认后才生效。你永远读不到已保存的密钥。"""
            override val parameters = obj(
                "action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("list", "upsert", "fetch_models", "test", "delete", "set_default"))),
                "id" to t("string", "服务商 id（修改/删除/测试时必填）"),
                "name" to t("string", "显示名称"),
                "base_url" to t("string", "API 地址，例如 https://api.deepseek.com 或 https://api.openai.com/v1"),
                "protocol" to t("string", "openai-chat 或 anthropic-messages，默认 openai-chat"),
                "api_key" to t("string", "API 密钥；修改时留空表示保留已保存的"),
                "models" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "模型 id 列表；留空则自动获取"),
                "model" to t("string", "set_default 时的默认模型"),
                required = listOf("action"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val list = settings.providers
                fun find(): Provider? = list.firstOrNull { it.id == args.optString("id") }
                return when (args.getString("action")) {
                    "list" -> ToolResult.text(JSONArray().also { a -> list.forEach { a.put(describe(it)) } }.toString(2))
                    "test" -> {
                        val p = find() ?: return ToolResult.error("没有这个服务商")
                        try { val m = probe(p); onChanged(); ToolResult.text("连通正常：${p.name} 返回 ${m.size} 个模型") }
                        catch (e: Exception) { onChanged(); ToolResult.error("连通失败：${e.message}") }
                    }
                    "fetch_models" -> {
                        val p = find() ?: return ToolResult.error("没有这个服务商")
                        val models = try { probe(p) } catch (e: Exception) { onChanged(); return ToolResult.error("获取模型失败：${e.message}") }
                        if (!confirm("更新「${p.name}」的模型目录", listOf("模型数" to "${models.size} 个", "模型" to models.take(12).joinToString("、") + if (models.size > 12) " 等" else "")))
                            return ToolResult.error("用户取消了这次修改")
                        settings.providers = settings.providers.map { if (it.id == p.id) it.copy(models = models) else it }
                        onChanged()
                        ToolResult.text("已更新模型目录：${models.joinToString(", ")}")
                    }
                    "delete" -> {
                        val p = find() ?: return ToolResult.error("没有这个服务商")
                        if (!confirm("删除服务商「${p.name}」", listOf("API 地址" to p.baseUrl, "API 密钥" to maskKey(p.apiKey))))
                            return ToolResult.error("用户取消了删除")
                        settings.providers = settings.providers.filter { it.id != p.id }
                        onChanged()
                        ToolResult.text("已删除 ${p.name}")
                    }
                    "set_default" -> {
                        val p = find() ?: return ToolResult.error("没有这个服务商")
                        val model = args.optString("model").ifBlank { p.models.firstOrNull() ?: "" }
                        if (model.isBlank()) return ToolResult.error("没有可用的模型")
                        if (!confirm("设为默认模型", listOf("服务商" to p.name, "模型" to model)))
                            return ToolResult.error("用户取消了修改")
                        settings.providerId = p.id; settings.model = model
                        onChanged()
                        ToolResult.text("默认模型已设为 ${p.name} / $model")
                    }
                    "upsert" -> {
                        val old = find()
                        val baseUrl = args.optString("base_url").trim().ifEmpty { old?.baseUrl ?: "" }
                        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return ToolResult.error("需要合法的 http(s) API 地址")
                        val proto = protocolOf(args.optString("protocol")) ?: old?.protocol ?: Protocol.OPENAI_CHAT
                        val key = args.optString("api_key").trim().ifEmpty { old?.apiKey ?: "" }
                        val name = args.optString("name").trim().ifEmpty { old?.name ?: baseUrl.substringAfter("://").substringBefore('/') }
                        val givenModels = args.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() } }
                        val id = old?.id ?: ("p" + System.currentTimeMillis().toString(36))
                        var p = Provider(id, name, proto, baseUrl, key, givenModels ?: old?.models ?: emptyList())
                        // Probe first so the card can show what the user is about to get.
                        var probeNote = "未测试"
                        try {
                            val m = Llm.listModels(p)
                            probeNote = "连通正常（${m.size} 个模型）"
                            if (givenModels.isNullOrEmpty()) p = p.copy(models = m)
                        } catch (e: Exception) { probeNote = "连通失败：${e.message?.take(80)}" }
                        val lines = listOf(
                            "显示名称" to p.name, "API 地址" to p.baseUrl,
                            "API 协议" to if (p.protocol == Protocol.ANTHROPIC_MESSAGES) "Anthropic Messages" else "OpenAI Chat Completions",
                            "API 密钥" to maskKey(p.apiKey) + if (old != null && args.optString("api_key").isBlank()) "（沿用已保存）" else "",
                            "模型目录" to p.models.take(10).joinToString("、").ifEmpty { "（空）" } + if (p.models.size > 10) " 等 ${p.models.size} 个" else "",
                            "连通性" to probeNote,
                        )
                        if (!confirm(if (old == null) "添加服务商「${p.name}」" else "修改服务商「${p.name}」", lines))
                            return ToolResult.error("用户取消了这次修改，配置没有变化")
                        val cur = settings.providers.toMutableList()
                        val i = cur.indexOfFirst { it.id == id }
                        if (i >= 0) cur[i] = p else cur.add(p)
                        settings.providers = cur
                        settings.setProviderStatus(id, if (probeNote.startsWith("连通正常")) "ok" else "error")
                        onChanged()
                        ToolResult.text("已保存：" + describe(p).toString())
                    }
                    else -> ToolResult.error("未知 action")
                }
            }
        })
    }
}
