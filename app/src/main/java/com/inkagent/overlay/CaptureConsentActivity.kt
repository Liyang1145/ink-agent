package com.inkagent.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Invisible trampoline for the MediaProjection consent dialog (only an Activity can ask for it).
 * Hands the result to [OverlayService], which caches the grant for its lifetime.
 */
class CaptureConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        try {
            // Android 14+: ask for the whole default display only, so the system dialog skips its
            // 「整个屏幕 / 单个应用」 choice (single-app capture would break the full-screen selector).
            val intent = if (android.os.Build.VERSION.SDK_INT >= 34)
                mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent()
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ)
        } catch (e: Exception) {
            OverlayService.onConsentResult(RESULT_CANCELED, null)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            OverlayService.onConsentResult(resultCode, data)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object { private const val REQ = 4711 }
}
