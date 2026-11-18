package com.aminfathi.nava.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

class RejectedFile(val key: String): IllegalArgumentException(key)

object PageImages {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_PAGES = 3
    data class Prepared(val pageCount: Int, val pages: List<ByteArray>)
    fun prepare(context: Context, bytes: ByteArray, mime: String): Prepared {
        val type = mime.lowercase()
        return when {
            type == "application/pdf" -> pdf(context, bytes)
            type.startsWith("image/") -> Prepared(1, listOf(jpeg(decode(bytes))))
            else -> throw RejectedFile("file_unsupported")
        }
    }
    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw RejectedFile("file_unsupported")
        var sample = 1
        while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        return bitmap ?: throw RejectedFile("file_unsupported")
    }
    private fun jpeg(source: Bitmap): ByteArray {
        val longest = max(source.width, source.height).coerceAtLeast(1)
        val scale = min(1f, 1600f / longest)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        val bytes = ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)) throw RejectedFile("file_unsupported")
            out.toByteArray()
        }
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        return bytes
    }
    private fun pdf(context: Context, bytes: ByteArray): Prepared {
        val temp = File.createTempFile("nava-page", ".pdf", context.cacheDir)
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) throw RejectedFile("file_unsupported")
                    val pages = (0 until min(renderer.pageCount, MAX_PAGES)).map { index ->
                        renderer.openPage(index).use { page ->
                            val width = 1200
                            val height = (page.height * (width.toFloat() / page.width.coerceAtLeast(1))).toInt().coerceIn(1, 2000)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            jpeg(bitmap)
                        }
                    }
                    return Prepared(renderer.pageCount, pages)
                }
            }
        } finally { temp.delete() }
    }
}
