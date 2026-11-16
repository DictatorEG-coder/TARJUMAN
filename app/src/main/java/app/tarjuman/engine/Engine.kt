package app.tarjuman.engine

import app.tarjuman.core.Prefs
import app.tarjuman.core.TextUtil
import app.tarjuman.core.await
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TResult(val text: String, val engine: String, val detected: String?)

/** Thrown when offline models are required but not downloaded. */
class ModelMissing(val langs: List<String>) : Exception("Missing models: " + langs.joinToString())

interface LanguageDetectionProvider {
    suspend fun detect(text: String): String?
}

interface TranslationProvider {
    val id: String
    suspend fun translate(text: String, source: String?, target: String): TResult
}

class MlKitLanguageId : LanguageDetectionProvider {
    override suspend fun detect(text: String): String? {
        val c = LanguageIdentification.getClient()
        try {
            val r = c.identifyLanguage(text).await()
            return if (r == "und") null else r
        } finally {
            c.close()
        }
    }
}

object ModelStore {
    private val mgr get() = RemoteModelManager.getInstance()

    suspend fun downloaded(): Set<String> =
        mgr.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()

    suspend fun isDownloaded(code: String): Boolean =
        mgr.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()

    suspend fun download(code: String, wifiOnly: Boolean) {
        val cond = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        mgr.download(TranslateRemoteModel.Builder(code).build(), cond).await()
    }

    suspend fun delete(code: String) {
        mgr.deleteDownloadedModel(TranslateRemoteModel.Builder(code).build()).await()
    }
}

/** On-device translation (Google ML Kit). Works offline once the language models are downloaded. */
class MlKitOfflineProvider : TranslationProvider {
    override val id = "mlkit-offline"

    override suspend fun translate(text: String, source: String?, target: String): TResult {
        val s = source?.let { TranslateLanguage.fromLanguageTag(it) }
            ?: throw IllegalStateException("Unknown or unsupported source language / لغة المصدر غير معروفة")
        val t = TranslateLanguage.fromLanguageTag(target)
            ?: throw IllegalStateException("Unsupported target language / لغة الهدف غير مدعومة")
        if (s == t) return TResult(text, id, s)

        val need = linkedSetOf(s, t)
        if (s != "en" && t != "en") need.add("en") // ML Kit pivots through English
        if (!Prefs.autoDownload) {
            val missing = need.filter { !ModelStore.isDownloaded(it) }
            if (missing.isNotEmpty()) throw ModelMissing(missing)
        }
        val tr = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build()
        )
        try {
            if (Prefs.autoDownload) tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return TResult(tr.translate(text).await(), id, s)
        } finally {
            tr.close()
        }
    }
}

/** Any LibreTranslate-compatible server (self-hosted recommended). URL is set by the user. */
class LibreTranslateProvider : TranslationProvider {
    override val id = "libretranslate"

    override suspend fun translate(text: String, source: String?, target: String): TResult =
        withContext(Dispatchers.IO) {
            val base = Prefs.libreUrl.trim().trimEnd('/')
            if (base.isEmpty()) throw IllegalStateException("LibreTranslate URL not set / لم يُحدَّد عنوان الخادم")
            val body = JSONObject().put("q", text).put("source", source ?: "auto")
                .put("target", target).put("format", "text")
            if (Prefs.libreKey.isNotBlank()) body.put("api_key", Prefs.libreKey)
            val conn = URL("$base/translate").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IllegalStateException("HTTP $code: ${resp.take(200)}")
                val j = JSONObject(resp)
                TResult(j.getString("translatedText"), id, j.optJSONObject("detectedLanguage")?.optString("language"))
            } finally {
                conn.disconnect()
            }
        }
}

/** Chooses providers by the user's engine mode and falls back when one fails. */
object Engine {
    private val detector: LanguageDetectionProvider = MlKitLanguageId()
    private val offline = MlKitOfflineProvider()
    private val online = LibreTranslateProvider()

    suspend fun detect(text: String): String? = try {
        detector.detect(text.take(400))?.substringBefore('-')
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun translate(text: String, source: String?, target: String, modeOverride: String? = null): TResult {
        val src = source?.takeIf { it != "auto" } ?: detect(text) ?: TextUtil.guessLang(text)
        val chain: List<TranslationProvider> = when (modeOverride ?: Prefs.engineMode) {
            "offline_only" -> listOf(offline)
            "online_only" -> listOf(online)
            "online_first" -> listOf(online, offline)
            else -> listOf(offline, online)
        }.filter { it !== online || Prefs.libreUrl.isNotBlank() }

        val parts = ArrayList<String>()
        var engine = ""
        for (piece in TextUtil.chunk(text)) {
            if (piece.isBlank()) { parts.add(piece); continue }
            val r = translateChunk(chain, piece, src, target)
            parts.add(r.text)
            engine = r.engine
        }
        return TResult(parts.joinToString("\n"), engine.ifEmpty { "none" }, src)
    }

    private suspend fun translateChunk(
        chain: List<TranslationProvider>, text: String, src: String?, target: String
    ): TResult {
        var first: Exception? = null
        for (p in chain) {
            try {
                return p.translate(text, src, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (first == null) first = e
            }
        }
        throw first ?: IllegalStateException("No translation engine available / لا يوجد محرك ترجمة متاح")
    }
}
