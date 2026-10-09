/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import org.junit.Assert.assertEquals
import org.junit.Test

class ColladaAssetTest {
    private fun read(xml: String) = ColladaAsset().read(xml.byteInputStream())

    @Test
    fun readsTheUnitAndUpAxisOfTheAsset() {
        val stated = ColladaAsset().read(resourceCopy("/model/crate.dae"))
        assertEquals(0.01f, stated.unitMetres!!, 1e-7f)
        assertEquals(UpAxis.Z, stated.upAxis)
    }

    @Test
    fun aFileWithoutAssetStatesNothing() {
        assertEquals(StatedFrame.NONE, read("""<COLLADA version="1.4.1"><library_geometries/></COLLADA>"""))
    }

    @Test
    fun xUpIsReportedAsStated() {
        val stated = read(
            """<COLLADA xmlns="http://www.collada.org/2005/11/COLLADASchema"><asset>
              |<contributor><author>x</author></contributor><up_axis>X_UP</up_axis></asset></COLLADA>""".trimMargin()
        )
        assertEquals(StatedFrame(null, UpAxis.X), stated)
    }

    @Test
    fun anInvalidUnitIsIgnored() {
        assertEquals(StatedFrame(null, UpAxis.Y), read("""<COLLADA><asset><unit meter="-2"/><up_axis>Y_UP</up_axis></asset></COLLADA>"""))
    }
}
