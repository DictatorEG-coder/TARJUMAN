package app.tarjuman.core

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.applicationContext.getSharedPreferences("tarjuman", Context.MODE_PRIVATE)
    }

    private fun s(k: String, d: String): String = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun i(k: String, d: Int): Int = sp.getInt(k, d)
    private fun putI(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    var targetLang: String
        get() = s("target", "ar")
        set(v) = putS("target", v)
    var sourceLang: String
        get() = s("source", "auto")
        set(v) = putS("source", v)
    /** auto | latin | zh | ja | ko */
    var ocrScript: String
        get() = s("ocr", "auto")
        set(v) = putS("ocr", v)
    /** offline_first | online_first | offline_only | online_only */
    var engineMode: String
        get() = s("engine", "offline_first")
        set(v) = putS("engine", v)
    var libreUrl: String
        get() = s("libre_url", "")
        set(v) = putS("libre_url", v)
    var libreKey: String
        get() = s("libre_key", "")
        set(v) = putS("libre_key", v)
    var autoDownload: Boolean
        get() = b("auto_dl", false)
        set(v) = putB("auto_dl", v)
    /** overlay | panel */
    var displayMode: String
        get() = s("display", "overlay")
        set(v) = putS("display", v)
    var bubbleSizeDp: Int
        get() = i("bubble_size", 56)
        set(v) = putI("bubble_size", v)
    var bubbleAlpha: Int
        get() = i("bubble_alpha", 90)
        set(v) = putI("bubble_alpha", v)
    var bubbleX: Int
        get() = i("bubble_x", 0)
        set(v) = putI("bubble_x", v)
    var bubbleY: Int
        get() = i("bubble_y", 300)
        set(v) = putI("bubble_y", v)
    var historyText: Boolean
        get() = b("hist_text", true)
        set(v) = putB("hist_text", v)
    var historyScreen: Boolean
        get() = b("hist_screen", false)
        set(v) = putB("hist_screen", v)
}
