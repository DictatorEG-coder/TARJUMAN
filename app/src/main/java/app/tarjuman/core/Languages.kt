package app.tarjuman.core

import java.util.Locale

data class Lang(val code: String, val ar: String, val en: String)

object Languages {
    val all = listOf(
        Lang("ar", "العربية", "Arabic"), Lang("en", "الإنجليزية", "English"),
        Lang("fr", "الفرنسية", "French"), Lang("de", "الألمانية", "German"),
        Lang("es", "الإسبانية", "Spanish"), Lang("it", "الإيطالية", "Italian"),
        Lang("pt", "البرتغالية", "Portuguese"), Lang("ru", "الروسية", "Russian"),
        Lang("tr", "التركية", "Turkish"), Lang("zh", "الصينية", "Chinese"),
        Lang("ja", "اليابانية", "Japanese"), Lang("ko", "الكورية", "Korean"),
        Lang("hi", "الهندية", "Hindi"), Lang("fa", "الفارسية", "Persian"),
        Lang("ur", "الأردية", "Urdu"), Lang("id", "الإندونيسية", "Indonesian"),
        Lang("vi", "الفيتنامية", "Vietnamese"), Lang("th", "التايلاندية", "Thai"),
        Lang("uk", "الأوكرانية", "Ukrainian"), Lang("nl", "الهولندية", "Dutch"),
        Lang("pl", "البولندية", "Polish"), Lang("he", "العبرية", "Hebrew"),
        Lang("bn", "البنغالية", "Bengali")
    )

    /** Languages the bubble's quick switch cycles through. */
    val quick = listOf("ar", "en", "fr", "de", "es", "tr", "ru")

    /** Core pack for the main use case: foreign -> Arabic. */
    val corePack = listOf("ar", "en", "ja", "zh", "ko")

    fun label(code: String): String {
        val l = all.firstOrNull { it.code == code } ?: return code
        return if (Locale.getDefault().language == "ar") l.ar else l.en
    }
}
