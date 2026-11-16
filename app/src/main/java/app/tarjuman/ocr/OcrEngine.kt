package app.tarjuman.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import app.tarjuman.core.Prefs
import app.tarjuman.core.TextUtil
import app.tarjuman.core.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrBlock(val text: String, val box: Rect)

class OcrResult(val blocks: List<OcrBlock>) {
    val fullText: String get() = blocks.joinToString("\n") { it.text }
}

/**
 * On-device OCR (ML Kit). Supports Latin, Chinese, Japanese, Korean scripts.
 * Arabic-script OCR is NOT supported by ML Kit and is not included.
 */
object OcrEngine {
    private suspend fun run(bmp: Bitmap, o: TextRecognizerOptionsInterface): List<OcrBlock> {
        val rec = TextRecognition.getClient(o)
        try {
            val t = rec.process(InputImage.fromBitmap(bmp, 0)).await()
            return t.textBlocks.mapNotNull { b ->
                val box = b.boundingBox ?: return@mapNotNull null
                OcrBlock(b.text, box)
            }
        } finally {
            rec.close()
        }
    }

    private fun joined(l: List<OcrBlock>) = l.joinToString("\n") { it.text }

    suspend fun recognize(bmp: Bitmap, script: String = Prefs.ocrScript): OcrResult {
        val blocks = when (script) {
            "latin" -> run(bmp, TextRecognizerOptions.DEFAULT_OPTIONS)
            "zh" -> run(bmp, ChineseTextRecognizerOptions.Builder().build())
            "ja" -> run(bmp, JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> run(bmp, KoreanTextRecognizerOptions.Builder().build())
            else -> auto(bmp)
        }
        return OcrResult(blocks.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.box.top / 20 }, { it.box.left })))
    }

    private suspend fun auto(bmp: Bitmap): List<OcrBlock> {
        val latin = run(bmp, TextRecognizerOptions.DEFAULT_OPTIONS)
        if (TextUtil.isLatinHeavy(joined(latin))) return latin
        val ko = run(bmp, KoreanTextRecognizerOptions.Builder().build())
        if (joined(ko).count { it in '\uAC00'..'\uD7AF' } >= 2) return ko
        val ja = run(bmp, JapaneseTextRecognizerOptions.Builder().build())
        if (joined(ja).count { it in '\u3040'..'\u30FF' } >= 2) return ja
        val zh = run(bmp, ChineseTextRecognizerOptions.Builder().build())
        if (TextUtil.hasHan(joined(zh))) return zh
        return if (latin.isNotEmpty()) latin else ja
    }
}
