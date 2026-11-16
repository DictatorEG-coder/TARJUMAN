package app.tarjuman.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import app.tarjuman.ocr.OcrEngine

/** Image -> OCR -> translation. Used by the image and document screens. */
object Pipeline {
    class Out(val original: String, val translated: String, val src: String?, val engine: String)

    suspend fun bitmap(bmp: Bitmap, tgt: String): Out {
        val text = OcrEngine.recognize(bmp).fullText
        if (text.isBlank()) return Out("", "", null, "")
        val r = Engine.translate(text, null, tgt)
        return Out(text, r.text, r.detected, r.engine)
    }

    /** Decodes with down-sampling to limit memory use. Call from a background dispatcher. */
    fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > maxSide) {
                    val f = maxSide.toFloat() / m
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
            }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > maxSide) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o2)!! }
    }
}
