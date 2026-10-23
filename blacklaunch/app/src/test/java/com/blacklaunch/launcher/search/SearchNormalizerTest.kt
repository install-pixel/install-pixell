package com.blacklaunch.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizerTest {
    private fun n(text: String) = SearchNormalizer.normalize(text)

    @Test fun ignoresCase() = assertEquals("youtube", n("YouTube"))

    @Test fun collapsesAndTrimsSpaces() = assertEquals("google maps", n("  Google   Maps "))

    @Test fun removesApostrophes() {
        assertEquals("пятниця", n("П'ятниця"))
        assertEquals("пятниця", n("Пʼятниця"))
        assertEquals("пятниця", n("П’ятниця"))
    }

    @Test fun unifiesGheAndYo() {
        assertEquals("гугл", n("Ґуґл"))
        assertEquals("елка", n("Ёлка"))
    }

    @Test fun keepsUkrainianLetters() = assertEquals("київ їжа йод", n("Київ Їжа Йод"))

    @Test fun stripsLatinDiacritics() = assertEquals("cafe", n("Café"))

    @Test fun punctuationBecomesSpace() = assertEquals("at t mobile", n("AT&T-Mobile"))

    @Test fun emptyAndBlank() {
        assertEquals("", n(""))
        assertEquals("", n("   "))
        assertEquals(emptyList<String>(), SearchNormalizer.words(""))
    }
}
