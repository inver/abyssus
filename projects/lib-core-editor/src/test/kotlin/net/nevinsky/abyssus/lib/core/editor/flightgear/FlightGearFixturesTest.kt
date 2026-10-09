/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class FlightGearFixturesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun theArchiveHoldsEveryFixtureFile() {
        val zip = fixtureArchive(temp.root, license = true)
        ZipFile(zip).use { archive ->
            val names = archive.entries().toList().map { it.name }.toSet()
            assertEquals(fixtureFiles(license = true).keys, names)
            for ((path, bytes) in fixtureFiles(license = true)) {
                assertTrue(path, archive.getInputStream(archive.getEntry(path)).readBytes().contentEquals(bytes))
            }
        }
    }

    @Test
    fun theSkinIsAnRleSgiImage() {
        val skin = fixtureFiles().getValue("fixture/Models/skin.rgb")
        assertEquals(474, ((skin[0].toInt() and 0xFF) shl 8) or (skin[1].toInt() and 0xFF))
        assertEquals(1, skin[2].toInt())
    }
}
