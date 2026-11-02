package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.Element
import com.blacklaunch.qrcodes.label.LabelDesign
import com.blacklaunch.qrcodes.label.LabelSize
import com.blacklaunch.qrcodes.label.Placement
import com.blacklaunch.qrcodes.label.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenesTest {
    private val tw = { text: String, letters: Float -> text.length * letters * 0.5f }
    private val code = Element.Qr(1, 0f, 0f, "https://x.pl", "Сайт", 30f)

    @Test
    fun codesLandInsideTheirSlotsOnEveryLabel() {
        for (size in LabelSize.PRESETS.filter { minOf(it.widthMm, it.heightMm) >= 100f }) for (scene in Scenes.all) {
            val d = Scenes.apply(scene, LabelDesign(size = size), listOf(code))
            assertEquals("${scene.id} $size", scene.slots.size, d.elements.size)
            val bg = d.background!!
            // Картинка ціла в межах наклейки.
            assertTrue("${scene.id} $size", bg.width <= d.size.widthMm + 0.01f && bg.height <= d.size.heightMm + 0.01f)
            d.elements.forEach { assertTrue("${scene.id} $size", Placement.fits(d, it, tw)) }
        }
    }
}
