package net.nevinsky.abyssus.sceneview.skybox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrSkyFilesTest {
    @Test
    fun usesANamedFile() {
        assertEquals(HdrChoice("b.hdr"), HdrSkyFiles.choose(listOf("a.hdr", "b.hdr", "meta.json"), listOf("whatever", "b.hdr")))
    }

    @Test
    fun aNamedFileThatIsMissingIsIgnored() {
        assertEquals(HdrChoice("sky.hdr"), HdrSkyFiles.choose(listOf("sky.hdr", "meta.json"), listOf("gone.hdr")))
    }

    @Test
    fun findsTheOnlyHdr() {
        assertEquals(HdrChoice("Sky.HDR"), HdrSkyFiles.choose(listOf("meta.json", "Sky.HDR", "notes.txt"), emptyList()))
    }

    @Test
    fun picksTheFirstOfSeveralAndWarns() {
        val choice = HdrSkyFiles.choose(listOf("b.hdr", "a.hdr", "meta.json"), emptyList())!!
        assertEquals("a.hdr", choice.file)
        assertTrue(choice.warning!!.contains("'a.hdr'"))
    }

    @Test
    fun noHdrIsNone() {
        assertNull(HdrSkyFiles.choose(listOf("meta.json", "sky.png"), listOf("sky.png")))
    }
}
