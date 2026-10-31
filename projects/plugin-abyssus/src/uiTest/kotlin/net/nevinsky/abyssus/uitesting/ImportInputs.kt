package net.nevinsky.abyssus.uitesting

import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The local inputs of the import and Game UI scenarios (see `src/uiTest/resources/import-inputs/README.md`): a small OBJ
 * model with its material and texture, a file that is not a model, a FlightGear aircraft archive built from the editor's
 * test fixtures, and an image and a font for the Game UI import. [stage] copies them into a folder outside the project, so a
 * scenario can pick them in a file chooser and the project never holds them until an import copies what it needs.
 */
class ImportInputs(val folder: Path) {
    val model: Path get() = folder.resolve("model/crate.obj")
    val brokenModel: Path get() = folder.resolve("model/broken.obj")
    val image: Path get() = folder.resolve("gameui/button.png")
    /** The bitmap font's `.fnt`; its page `ui-font.png` sits beside it. */
    val font: Path get() = folder.resolve("gameui/ui-font.fnt")

    /** A `.zip` file that is not an archive, written into [folder] on first use. */
    val brokenArchive: Path get() = folder.resolve("broken.zip").also { if (!Files.exists(it)) Files.writeString(it, "this is not a zip archive\n") }

    /** The FlightGear archive, built into [folder] on first use. */
    val flightGearArchive: Path
        get() = folder.resolve("fixture.zip").also { if (!Files.exists(it)) fixtureArchive(folder.toFile(), license = true) }

    companion object {
        private const val RESOURCE_ROOT = "/import-inputs/"

        /** What `crate.obj` stages as `model_crate` (centimetres, Y up): the folder's files in order. */
        val STAGED_MODEL_FILES = listOf("meta.json", "model.glb", "textures/wood.png", "source.json")

        /** The `additional` member of the staged model's `meta.json`. */
        const val STAGED_MODEL_ADDITIONAL = """{"file":"model.glb","format":"GLTF","binary":true,"materials":[]}"""

        /** The recorded inputs: path below the folder to SHA-256. */
        fun expectedHashes(): Map<String, String> =
            ImportInputs::class.java.getResourceAsStream("${RESOURCE_ROOT}import-inputs.sha256")!!.bufferedReader().readLines()
                .filter { it.isNotBlank() }.associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }

        /** Copies every recorded input into [folder] and returns the handle. */
        fun stage(folder: Path): ImportInputs {
            for (relative in expectedHashes().keys) {
                val target = folder.resolve(relative)
                Files.createDirectories(target.parent)
                ImportInputs::class.java.getResourceAsStream("$RESOURCE_ROOT$relative")!!.use { Files.copy(it, target) }
            }
            return ImportInputs(folder)
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
