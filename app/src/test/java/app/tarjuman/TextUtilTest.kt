package app.tarjuman

import app.tarjuman.core.TextUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilTest {
    @Test fun chunkKeepsShortTextWhole() {
        assertEquals(listOf("a\nb"), TextUtil.chunk("a\nb", 10))
    }

    @Test fun chunkSplitsOnLineBoundaries() {
        assertEquals(listOf("aaaa", "bbbb"), TextUtil.chunk("aaaa\nbbbb", 6))
    }

    @Test fun chunkHardSplitsVeryLongLine() {
        val parts = TextUtil.chunk("x".repeat(25), 10)
        assertEquals(listOf(10, 10, 5), parts.map { it.length })
    }

    @Test fun chunkOfEmptyTextIsEmpty() {
        assertTrue(TextUtil.chunk("", 10).isEmpty())
    }

    @Test fun guessLanguageByScript() {
        assertEquals("ko", TextUtil.guessLang("안녕하세요"))
        assertEquals("ja", TextUtil.guessLang("こんにちは世界"))
        assertEquals("zh", TextUtil.guessLang("你好世界"))
        assertNull(TextUtil.guessLang("hello world"))
    }

    @Test fun chineseLocaleTagsNormalizeToMlKitCode() {
        assertEquals("zh", TextUtil.normalizeTranslationLanguage("zh-CN"))
        assertEquals("zh", TextUtil.normalizeTranslationLanguage("zh-Hans"))
        assertEquals("zh", TextUtil.normalizeTranslationLanguage("zh_SG"))
        assertEquals("zh", TextUtil.normalizeTranslationLanguage("zh-Latn"))
        assertEquals("ar", TextUtil.normalizeTranslationLanguage("ar-SA"))
        assertNull(TextUtil.normalizeTranslationLanguage(null))
    }

    @Test fun hanExtensionAIsRecognized() {
        assertTrue(TextUtil.hasHan("\u3400"))
        assertTrue(TextUtil.hasHan("汉字"))
    }

    @Test fun latinHeavyDetection() {
        assertTrue(TextUtil.isLatinHeavy("Hello, how are you?"))
        assertFalse(TextUtil.isLatinHeavy("こんにちは"))
        assertFalse(TextUtil.isLatinHeavy("   "))
    }
}
