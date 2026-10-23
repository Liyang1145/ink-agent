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

    /**
     * Per-model price table, ¥ per million tokens, editable in settings. Each entry: hit/miss/out as
     * [空闲, 高峰] pairs, plus ctx = context window in tokens. Defaults are DeepSeek's official prices
     * (api-docs.deepseek.com/zh-cn/quick_start/pricing, fetched 2026-10-22).
     */
    var pricingJson: String
        get() = prefs.getString("pricing", null) ?: DEFAULT_PRICING
        set(v) = prefs.edit().putString("pricing", v).apply()

    fun pricing(): org.json.JSONObject = runCatching { org.json.JSONObject(pricingJson) }.getOrElse { org.json.JSONObject(DEFAULT_PRICING) }

    /** ¥ for [u] on [model] at the current Beijing time, or null if the model is not in the table. */
    fun cost(model: String, u: com.inkagent.llm.Usage, now: java.util.Calendar = beijingNow()): Double? {
        val e = pricing().optJSONObject(model) ?: return null
        val i = if (isPeak(now)) 1 else 0
        fun price(k: String) = e.optJSONArray(k)?.optDouble(i, 0.0) ?: e.optDouble(k, 0.0)
        return (u.cacheHit * price("hit") + u.cacheMiss * price("miss") + u.output * price("out")) / 1_000_000.0
    }

    fun contextWindow(model: String): Int = pricing().optJSONObject(model)?.optInt("ctx", 0)?.takeIf { it > 0 } ?: 128_000

    fun providerStatus(id: String): String = prefs.getString("status_$id", "unknown") ?: "unknown"
    fun setProviderStatus(id: String, st: String) = prefs.edit().putString("status_$id", st).apply()

    /**
     * Agent window placement. mode: ball (minimised to the floating ball) | window (floating window over the
     * notes) | split (「划分领土」: docked right in landscape, bottom in portrait). Geometry is in dp; -1 = unset.
     */
    var agentMode: String
        get() = prefs.getString("agentMode", null) ?: if (prefs.getBoolean("panelCollapsed", false)) "ball" else "window"
        set(v) = prefs.edit().putString("agentMode", v).apply()
    var winX: Float get() = prefs.getFloat("winX", -1f); set(v) = prefs.edit().putFloat("winX", v).apply()
    var winY: Float get() = prefs.getFloat("winY", -1f); set(v) = prefs.edit().putFloat("winY", v).apply()
    var winW: Float get() = prefs.getFloat("winW", -1f); set(v) = prefs.edit().putFloat("winW", v).apply()
    var winH: Float get() = prefs.getFloat("winH", -1f); set(v) = prefs.edit().putFloat("winH", v).apply()
    /** Floating ball: which edge it is snapped to and its vertical position (dp from top). */
    var ballRight: Boolean get() = prefs.getBoolean("ballRight", true); set(v) = prefs.edit().putBoolean("ballRight", v).apply()
    var ballY: Float get() = prefs.getFloat("ballY", -1f); set(v) = prefs.edit().putFloat("ballY", v).apply()
    /** Split mode: the agent's share of the screen (width in landscape, height in portrait). */
    var splitLand: Float get() = prefs.getFloat("splitLand", 0.36f); set(v) = prefs.edit().putFloat("splitLand", v).apply()
    var splitPort: Float get() = prefs.getFloat("splitPort", 0.42f); set(v) = prefs.edit().putFloat("splitPort", v).apply()

    /** 「在其他应用上显示悬浮球」: cross-app overlay ball + window (OverlayService). */
    var overlayEnabled: Boolean get() = prefs.getBoolean("overlayEnabled", false); set(v) = prefs.edit().putBoolean("overlayEnabled", v).apply()
    /** Cross-app overlay geometry in screen px (-1 = unset). */
    var ovBallRight: Boolean get() = prefs.getBoolean("ovBallRight", true); set(v) = prefs.edit().putBoolean("ovBallRight", v).apply()
    var ovBallY: Int get() = prefs.getInt("ovBallY", -1); set(v) = prefs.edit().putInt("ovBallY", v).apply()
    var ovWinX: Int get() = prefs.getInt("ovWinX", -1); set(v) = prefs.edit().putInt("ovWinX", v).apply()
    var ovWinY: Int get() = prefs.getInt("ovWinY", -1); set(v) = prefs.edit().putInt("ovWinY", v).apply()
    var ovWinW: Int get() = prefs.getInt("ovWinW", -1); set(v) = prefs.edit().putInt("ovWinW", v).apply()
    var ovWinH: Int get() = prefs.getInt("ovWinH", -1); set(v) = prefs.edit().putInt("ovWinH", v).apply()

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
        const val DEFAULT_PRICING = """{
  "deepseek-flash": {"hit": [0.02, 0.04], "miss": [1, 2], "out": [4, 8], "ctx": 1000000},
  "deepseek-v4-pro": {"hit": [0.15, 0.30], "miss": [4.5, 9.0], "out": [13.5, 27.0], "ctx": 1000000}
}"""

        fun beijingNow(): java.util.Calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))

        /** DeepSeek peak hours: Mon–Fri 9:00–12:00 and 14:00–18:00 Beijing time (public holidays not modelled). */
        fun isPeak(c: java.util.Calendar): Boolean {
            val d = c.get(java.util.Calendar.DAY_OF_WEEK)
            if (d == java.util.Calendar.SATURDAY || d == java.util.Calendar.SUNDAY) return false
            val h = c.get(java.util.Calendar.HOUR_OF_DAY)
            return h in 9..11 || h in 14..17
        }

        val EFFORT_LABEL = mapOf("off" to "不思考", "low" to "低", "medium" to "中", "high" to "高", "max" to "最高", "default" to "默认")
        /** dsh llm-deepseek defaults: Anthropic Messages at api.deepseek.com/anthropic. */
        /** dsh adapter default model catalog, by provider id (恢复默认模型). */
        fun defaultModels(id: String): List<String> = defaults().firstOrNull { it.id == id }?.models ?: emptyList()

        fun defaults() = listOf(
            Provider("deepseek", "DeepSeek", Protocol.ANTHROPIC_MESSAGES, "https://api.deepseek.com/anthropic", "",
                listOf("deepseek-flash", "deepseek-v4-pro")),
        )
    }
}
