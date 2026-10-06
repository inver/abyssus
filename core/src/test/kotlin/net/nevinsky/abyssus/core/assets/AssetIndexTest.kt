package net.nevinsky.abyssus.core.assets

import net.nevinsky.abyssus.core.io.FileLoader

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class AssetIndexTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private val dir: File = Files.createTempDirectory("index").toFile().also(dirs::add)
    private val files = net.nevinsky.abyssus.core.io.FileLoader(dir)
    private val index = AssetIndex(files, testMetaLoader(dir, fileLoader = files))

    private fun asset(folder: String, uuid: String?, type: String = "TEXTURE") {
        File(dir, "assets/$folder").mkdirs()
        val id = uuid?.let { "\"uuid\":\"$it\"," } ?: ""
        File(dir, "assets/$folder/meta.json").writeText("""{"format":"abyssus","formatVersion":1,$id"type":"$type","additional":{"file":"a.png"}}""")
    }

    @Test
    fun findsTheFolderOfAUuid() {
        asset("tex", "00000000-0000-0000-0000-000000000001")
        asset("other", "00000000-0000-0000-0000-000000000002", "MODEL")
        assertEquals("tex", index.folder("00000000-0000-0000-0000-000000000001"))
        assertEquals("other", index.folder("00000000-0000-0000-0000-000000000002"))
        assertEquals(setOf("tex", "other"), index.folders().values.toSet())
    }

    @Test
    fun unknownMalformedAndMissingReferencesHaveNoFolder() {
        asset("tex", "00000000-0000-0000-0000-000000000001")
        for (ref in listOf(null, "", "not-a-uuid", "00000000-0000-0000-0000-0000000000ff")) assertNull(ref, index.folder(ref))
    }

    @Test
    fun assetsWithoutAUuidAreLeftOut() {
        asset("anonymous", null)
        assertEquals(emptyMap<UUID, String>(), index.folders())
    }

    @Test
    fun theFirstFolderByNameWinsAShareduuid() {
        asset("b", "00000000-0000-0000-0000-000000000001")
        asset("a", "00000000-0000-0000-0000-000000000001")
        assertEquals("a", index.folder("00000000-0000-0000-0000-000000000001"))
    }

    @Test
    fun theIndexSeesAddedReplacedAndRemovedAssetsWithoutARefresh() {
        val id = "00000000-0000-0000-0000-000000000001"
        assertNull(index.folder(id))
        asset("tex", id)
        assertEquals("tex", index.folder(id))
        asset("tex", "00000000-0000-0000-0000-000000000002") // another uuid, longer text: the cached read is dropped
        assertNull(index.folder(id))
        assertEquals("tex", index.folder("00000000-0000-0000-0000-000000000002"))
        File(dir, "assets/tex").deleteRecursively()
        assertNull(index.folder("00000000-0000-0000-0000-000000000002"))
    }

    @Test
    fun brokenMetasAreSkipped() {
        File(dir, "assets/bad").mkdirs()
        File(dir, "assets/bad/meta.json").writeText("{ not json")
        asset("tex", "00000000-0000-0000-0000-000000000001")
        assertEquals(mapOf(UUID.fromString("00000000-0000-0000-0000-000000000001") to "tex"), index.folders())
    }
}
