package app.tarjuman.core

object TextUtil {
    /** Splits text on line boundaries into pieces of at most [max] characters. */
    fun chunk(text: String, max: Int = 1200): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
        }
        for (line in text.split("\n")) {
            if (line.length > max) {
                flush()
                line.chunked(max).forEach { out.add(it) }
                continue
            }
            if (sb.isNotEmpty() && sb.length + 1 + line.length > max) flush()
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
        }
        flush()
        return out
    }

    fun hasHangul(s: String) = s.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' }
    fun hasKana(s: String) = s.any { it in '\u3040'..'\u30FF' }
    fun hasHan(s: String) = s.any { it in '\u4E00'..'\u9FFF' }

    fun isLatinHeavy(s: String): Boolean {
        val v = s.filter { !it.isWhitespace() }
        if (v.isEmpty()) return false
        return v.count { it.code < 0x250 } * 100 / v.length >= 70
    }

    /** Script-based language guess used when language-id gives no answer. */
    fun guessLang(s: String): String? = when {
        hasHangul(s) -> "ko"
        hasKana(s) -> "ja"
        hasHan(s) -> "zh"
        else -> null
    }
}
