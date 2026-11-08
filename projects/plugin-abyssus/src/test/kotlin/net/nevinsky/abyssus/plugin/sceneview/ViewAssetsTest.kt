package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPart
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AssetLoading
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ViewAssetsTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun first(part: ModelPart): ModelPart = part.children.firstOrNull { it.bounds != null } ?: part

    @Test
    fun changedPartsInUnsavedMetaTextGiveNewResultsAfterTheInvalidationSignal() {
        GdxNativesLoader.load()
        val dir = Files.createTempDirectory("view-assets").toFile().also(dirs::add)
        val name = "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"
        assertTrue(File("src/test/testData/project/Untitled/assets/$name").copyRecursively(File(dir, "assets/$name")))
        val assets =
            ViewAssets(AssetLoading(JsonProcessor(NOPLogger.NOP_LOGGER), NOPLogger.NOP_LOGGER, Executor(Runnable::run), ShaderStorage()))
        val project = assets.project(dir)

        val before = checkNotNull(project.modelParts.parts(name))
        val node = generateSequence(before.roots.firstOrNull()) { it.children.firstOrNull() }.first { it.bounds != null }.name
        assertFalse(checkNotNull(before.find(node)).pivot.configured)

        val metaFile = File(dir, "assets/$name/meta.json").absoluteFile
        val text = metaFile.readText().replace(
            Regex("\"materials\"\\s*:\\s*\\[\\s*]"),
            "\"materials\": [], \"parts\": {\"$node\": {\"pivot\": {\"position\": {\"x\": 1, \"y\": 0, \"z\": 0}}}}",
        )
        assets.replaceUnsaved(mapOf(metaFile to text))
        // Without the signal the cached answer stays.
        assertFalse(checkNotNull(project.modelParts.parts(name)).find(node)!!.pivot.configured)
        assets.invalidate(setOf(name))
        assertTrue(checkNotNull(project.modelParts.parts(name)).find(node)!!.pivot.configured)
        assets.dispose()
    }
}
