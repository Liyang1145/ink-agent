package com.inkagent

import android.content.Context
import com.inkagent.llm.CallConfig
import com.inkagent.llm.Protocol
import com.inkagent.llm.Provider
import org.json.JSONArray

/**
 * Model settings in the dsh shape: a list of providers (each with its own models), plus the
 * default selection (provider, model, reasoning effort, optional max tokens). No temperature.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var providers: List<Provider>
        get() {
            val raw = prefs.getString("providers", null) ?: return defaults()
            return try { val a = JSONArray(raw); (0 until a.length()).map { Provider.fromJson(a.getJSONObject(it)) } }
            catch (_: Exception) { defaults() }
        }
        set(v) { prefs.edit().putString("providers", JSONArray().also { a -> v.forEach { a.put(it.toJson()) } }.toString()).apply() }

    var providerId: String
        get() = prefs.getString("providerId", "deepseek") ?: "deepseek"
        set(v) = prefs.edit().putString("providerId", v).apply()
    var model: String
        get() = prefs.getString("model", "deepseek-flash") ?: ""
        set(v) = prefs.edit().putString("model", v).apply()
    var effort: String
        get() = prefs.getString("effort", "high") ?: "high"
        set(v) = prefs.edit().putString("effort", v).apply()
    var maxTokens: Int?
        get() = prefs.getInt("maxTokens", 0).takeIf { it > 0 }
        set(v) = prefs.edit().putInt("maxTokens", v ?: 0).apply()
    var systemPrompt: String
        get() = prefs.getString("systemPrompt", "") ?: ""
        set(v) = prefs.edit().putString("systemPrompt", v).apply()
    var tavilyKey: String
        get() = prefs.getString("tavilyKey", "") ?: ""
        set(v) = prefs.edit().putString("tavilyKey", v).apply()
    var fingerDraw: Boolean
        get() = prefs.getBoolean("finger", false)
        set(v) = prefs.edit().putBoolean("finger", v).apply()
    /** dsh 工作步骤展示: compact | standard | detailed | verbose */
    var transcriptMode: String
        get() = prefs.getString("transcriptMode", "compact") ?: "compact"
        set(v) = prefs.edit().putString("transcriptMode", v).apply()
    /** dsh 工作步骤收起时机: completion | next-input */
    var collapseTiming: String
        get() = prefs.getString("collapseTiming", "completion") ?: "completion"
        set(v) = prefs.edit().putString("collapseTiming", v).apply()
    var sessionId: String?
        get() = prefs.getString("sessionId", null)
        set(v) = prefs.edit().putString("sessionId", v).apply()

    fun provider(): Provider? = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()

    fun callConfig(): CallConfig? {
        val p = provider() ?: return null
        if (model.isBlank()) return null
        val e = effort.takeIf { it in p.efforts() } ?: p.efforts().first()
        return CallConfig(p, model, e, maxTokens)
    }

    fun label(): String {
        val p = provider() ?: return "未配置模型"
        val e = effort.takeIf { it in p.efforts() } ?: p.efforts().first()
        return "${model.ifBlank { "未选模型" }} · ${EFFORT_LABEL[e] ?: e}"
    }

    companion object {
        val EFFORT_LABEL = mapOf("off" to "不思考", "low" to "低", "medium" to "中", "high" to "高", "max" to "最高", "default" to "默认")
        /** dsh llm-deepseek defaults: Anthropic Messages at api.deepseek.com/anthropic. */
        fun defaults() = listOf(
            Provider("deepseek", "DeepSeek", Protocol.ANTHROPIC_MESSAGES, "https://api.deepseek.com/anthropic", "",
                listOf("deepseek-flash", "deepseek-v4-pro")),
        )
    }
}
