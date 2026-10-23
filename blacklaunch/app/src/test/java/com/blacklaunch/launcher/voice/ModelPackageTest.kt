package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackageTest {
    private val ourPackage = listOf("blacklaunch-model.json", "model/am/final.mdl", "model/conf/model.conf", "model/graph/HCLr.fst")

    @Test fun markerDefinesLanguage() =
        assertEquals(VoiceLanguages.POLISH, ModelPackage.detectLanguage("""{"format":1,"language":"pl-PL"}""", ourPackage))

    @Test fun markerWinsOverFolderName() =
        assertEquals(
            VoiceLanguages.ENGLISH,
            ModelPackage.detectLanguage("""{"language": "en-US"}""", listOf("vosk-model-small-uk-v3/am/final.mdl")),
        )

    @Test fun originalVoskZipWithoutMarker() {
        val paths = listOf("vosk-model-small-uk-v3-small/am/final.mdl", "vosk-model-small-uk-v3-small/conf/model.conf")
        assertEquals(VoiceLanguages.UKRAINIAN, ModelPackage.detectLanguage(null, paths))
        assertEquals("vosk-model-small-en-us-0.15/", ModelPackage.modelRoot(listOf("vosk-model-small-en-us-0.15/am/final.mdl")))
    }

    @Test fun englishUsFolder() =
        assertEquals(VoiceLanguages.ENGLISH, ModelPackage.detectLanguage(null, listOf("vosk-model-small-en-us-0.15/am/final.mdl")))

    @Test fun unknownLanguageIsNull() =
        assertNull(ModelPackage.detectLanguage(null, listOf("model/am/final.mdl")))

    @Test fun notAModel() {
        assertNull(ModelPackage.modelRoot(listOf("photo.jpg", "readme.txt")))
        assertNull(ModelPackage.detectLanguage("""{"language":"uk-UA"}""".takeIf { false }, listOf("photo.jpg")))
    }

    @Test fun modelRootOfOurPackage() = assertEquals("model/", ModelPackage.modelRoot(ourPackage))

    @Test fun modelAtArchiveRoot() = assertEquals("", ModelPackage.modelRoot(listOf("am/final.mdl", "conf/model.conf")))

    @Test fun rejectsPathTraversal() {
        assertFalse(ModelPackage.isSafe("../evil.so"))
        assertFalse(ModelPackage.isSafe("model/../../evil"))
        assertFalse(ModelPackage.isSafe("/abs/path"))
        assertTrue(ModelPackage.isSafe("model/am/final.mdl"))
    }
}
