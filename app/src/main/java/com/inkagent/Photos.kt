package com.inkagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Camera capture helpers: a FileProvider target for ACTION_IMAGE_CAPTURE and an EXIF-aware decode. */
object Photos {
    /** Set by MainActivity: open the camera and insert the photo into this editor's current page. */
    @JvmStatic var takeForNote: ((com.xnotes.ui.Editor) -> Unit)? = null

    fun newTarget(ctx: Context): Pair<File, Uri> {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    }

    /** Decode [f] upright (EXIF orientation applied), long side ≤ [maxSide]. */
    fun decodeUpright(f: File, maxSide: Int): Bitmap? {
        if (!f.exists() || f.length() == 0L) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val rot = runCatching {
            when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val k = minOf(1.0, maxSide.toDouble() / maxOf(b.width, b.height))
        if (rot == 0f && k >= 1.0) return b
        val m = Matrix().apply { if (k < 1.0) postScale(k.toFloat(), k.toFloat()); if (rot != 0f) postRotate(rot) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    fun jpeg(b: Bitmap, q: Int = 92): ByteArray = ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }
    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
}
