package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.ImportInputs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.zip.ZipFile

/** The import inputs are what their recorded hashes say, stage into any folder unchanged and build the same archive every time. */
class ImportInputsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun everyRecordedInputIsStagedWithItsRecordedHash() {
        val recorded = ImportInputs.expectedHashes()
        assertTrue("the model, the broken file, the image and the font are recorded: ${recorded.keys}", recorded.keys.containsAll(
            listOf("model/crate.obj", "model/crate.mtl", "model/wood.png", "model/broken.obj", "gameui/button.png", "gameui/ui-font.fnt", "gameui/ui-font.png")))
        val inputs = ImportInputs.stage(tmp.newFolder("inputs").toPath())
        for ((relative, hash) in recorded) {
            assertEquals("$relative changed", hash, ImportInputs.sha256(Files.readAllBytes(inputs.folder.resolve(relative))))
        }
    }

    @Test
    fun theBrokenModelIsNotAModelAndTheFontIsATextBitmapFont() {
        val inputs = ImportInputs.stage(tmp.newFolder("inputs").toPath())
        assertTrue("no vertex line in the broken file", Files.readAllLines(inputs.brokenModel).none { it.startsWith("v ") || it.startsWith("f ") })
        val fnt = Files.readString(inputs.font)
        assertTrue("a text bitmap font starts with an info line", fnt.startsWith("info "))
        assertTrue("and names its page, which sits beside it", fnt.contains("file=\"ui-font.png\"") && Files.exists(inputs.font.resolveSibling("ui-font.png")))
        assertEquals("the image is a PNG", "89504e47", Files.readAllBytes(inputs.image).copyOf(4).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun theFlightGearArchiveIsTheSameEveryTimeAndHoldsTheLicense() {
        val first = ImportInputs.stage(tmp.newFolder("a").toPath()).flightGearArchive
        val second = ImportInputs.stage(tmp.newFolder("b").toPath()).flightGearArchive
        assertArrayEquals("the archive is deterministic", Files.readAllBytes(first), Files.readAllBytes(second))
        ZipFile(first.toFile()).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("the aircraft set file: $names", names.contains("fixture/fixture-set.xml"))
            assertTrue("the AC3D model: $names", names.contains("fixture/Models/fixture.ac"))
            assertTrue("a license file: $names", names.any { it.contains("license", ignoreCase = true) || it.contains("COPYING", ignoreCase = true) })
        }
    }

    @Test
    fun theStagedModelFactsAreRecorded() {
        assertEquals(listOf("meta.json", "model.glb", "textures/wood.png", "source.json"), ImportInputs.STAGED_MODEL_FILES)
        assertEquals("""{"file":"model.glb","format":"GLTF","binary":true,"materials":[]}""", ImportInputs.STAGED_MODEL_ADDITIONAL)
    }
}
