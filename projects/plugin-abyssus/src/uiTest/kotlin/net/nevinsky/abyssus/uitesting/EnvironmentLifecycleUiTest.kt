package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Scene environment and sky assets (ENV and the sky half of AST): the Enabled boxes of the scene's ambient light, fog and
 * skybox write only their flag, a procedural sky's atmosphere fields commit and refuse like any asset field, and an HDR sky
 * is shown read only with the reason its preview failed. Every stage is compared with the saved files.
 */
class EnvironmentLifecycleUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openTree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expandAssets()
        return tree
    }

    private fun meta(asset: String): Path = project.dir.resolve("assets/$asset/meta.json")

    private fun additional(asset: String): JsonNode = mapper.readTree(Files.readString(meta(asset)))["additional"]

    private fun redoThroughSceneDocument(restored: String) {
        val path = scene.toString()
        ide.openTextEditor(path)
        try {
            com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
            frame.redo()
            LifecycleHarness.awaitState("Redo restores the document", restored) { ide.documentText(path) }
            frame.saveAll()
            LifecycleHarness.awaitState("Redo is saved", restored) { Files.readString(scene) }
        } finally {
            ide.closeFile(path)
        }
    }

    /** Clicks the Enabled box of the scene setting [row] (section [key]) and returns the flag member it must write. */
    private fun enabledCycle(row: String, key: String, flag: String) {
        val original = Files.readString(scene)
        val tree = openTree()
        tree.clickRow(row)
        LifecycleHarness.awaitState("the $row section shows its Enabled box", true) { frame.properties().hasEnabledBox(key) }
        assertTrue("$flag starts on", frame.properties().enabledChecked(key))

        frame.properties().clickEnabled(key)
        LifecycleHarness.awaitState("$flag is false in the file", true) { Files.readString(scene).contains("\"$flag\": false") || ide.documentText(scene.toString())?.contains("\"$flag\": false") == true }
        frame.saveAll()
        LifecycleHarness.awaitState("$flag false is saved", true) { Regex("\"$flag\": false").containsMatchIn(Files.readString(scene)) }
        val off = Files.readString(scene)
        val diff = LifecycleHarness.lineDiff(original, off)
        assertEquals("only the $flag line changed: $diff", 1, diff.added.size)
        assertFalse("the box follows the file", frame.properties().enabledChecked(key))

        undoThroughSceneDocument(scene, original)
        LifecycleHarness.awaitState("Undo checks the box", true) { frame.properties().enabledChecked(key) }
        redoThroughSceneDocument(off)
        LifecycleHarness.awaitState("Redo unchecks it", false) { frame.properties().enabledChecked(key) }

        frame.properties().clickEnabled(key)
        LifecycleHarness.awaitState("the box is checked again", true) { frame.properties().enabledChecked(key) }
        frame.saveAll()
        LifecycleHarness.awaitState("the return restores the bytes", original) { Files.readString(scene) }
    }

    @Test
    fun ambientEnabledBoxWritesOnlyItsFlagAndReturnsToTheOriginalBytes() = enabledCycle("ambientLight", "scene.ambientLight", "ambientLightEnabled")

    @Test
    fun fogEnabledBoxWritesOnlyItsFlagAndReturnsToTheOriginalBytes() = enabledCycle("fog", "scene.fog", "fogEnabled")

    @Test
    fun skyboxEnabledBoxWritesOnlyItsFlagAndReturnsToTheOriginalBytes() = enabledCycle("skybox", "scene.skyboxName", "skyboxEnabled")

    @Test
    fun proceduralSkyFieldsCommitRefuseInvalidValuesAndFollowAssetUndo() {
        val sky = "skybox_physical"
        val original = Files.readString(meta(sky))
        val tree = openTree()
        tree.clickRow(sky)
        LifecycleHarness.awaitState("the atmosphere fields are shown", true) { frame.properties().exists("asset-field-mieG") }
        val p = { frame.properties() }

        for ((field, bad) in listOf("mieG" to "abc", "sunIntensity" to "abc", "planetRadius" to "abc", "heightMie" to "abc")) {
            p().enter("asset-field-$field", bad)
            LifecycleHarness.awaitState("'$bad' for $field is explained", true) { runCatching { p().labelText("asset-error-$field").isNotBlank() }.getOrDefault(false) }
            assertEquals("a refused $field writes nothing", original, Files.readString(meta(sky)))
        }

        p().enter("asset-field-sunIntensity", "30")
        LifecycleHarness.awaitState("sunIntensity is saved", 30.0) { additional(sky)["sunIntensity"]?.asDouble() }
        // the document is printed again in the file's indented style, so a hand-written inline array may be laid out
        // differently; the values, their number text and the key order are what an edit keeps
        val edited = Files.readString(meta(sky))
        val before = mapper.readTree(original)["additional"] as com.fasterxml.jackson.databind.node.ObjectNode
        val after = mapper.readTree(edited)["additional"] as com.fasterxml.jackson.databind.node.ObjectNode
        assertEquals("the keys keep their order", before.fieldNames().asSequence().toList(), after.fieldNames().asSequence().toList())
        for (key in before.fieldNames().asSequence().filter { it != "sunIntensity" }) assertEquals("$key is as it was", before[key], after[key])
        assertTrue("the spelling of the untouched numbers is kept", listOf("5.8e-6", "13.5e-6", "33.1e-6").all { edited.contains(it) })

        p().enter("asset-field-mieG", "0.5")
        LifecycleHarness.awaitState("mieG is saved", 0.5) { additional(sky)["mieG"]?.asDouble() }

        p().clickFirst("asset-undo")
        frame.saveAll()
        LifecycleHarness.awaitState("Undo restores the sunIntensity edit only", edited) { Files.readString(meta(sky)) }
        p().clickFirst("asset-undo")
        frame.saveAll()
        LifecycleHarness.awaitState("a second Undo restores the original bytes", original) { Files.readString(meta(sky)) }
        p().clickFirst("asset-redo")
        frame.saveAll()
        LifecycleHarness.awaitState("Redo reapplies the sunIntensity", edited) { Files.readString(meta(sky)) }
        assertEquals("only this asset changed", setOf("assets/$sky/meta.json"), project.differences().map { it.replace('\\', '/') }.toSet())
    }

    @Test
    fun anHdrSkyIsReadOnlyAndSaysWhyItsPreviewIsMissing() {
        val sky = "skybox_hdr"
        val before = Files.readAllBytes(meta(sky))
        val tree = openTree()
        tree.clickRow(sky)
        LifecycleHarness.awaitState("the HDR asset is shown", sky) { frame.properties().headerTitle() }
        val texts = { frame.properties().texts() }
        LifecycleHarness.awaitState("the asset says it has no editors", true) { texts().any { it.contains("no editors here") } }
        assertTrue("it is marked read only: ${texts()}", texts().any { it.contains("read-only", ignoreCase = true) })
        assertTrue("no asset field is editable", frame.properties().fieldNames("asset-field-").isEmpty())
        // the preview is an image where the EXR loader works, and says why where it cannot (the arm64 `--fast` container has no LWJGL natives)
        LifecycleHarness.awaitState("the preview is shown or says why not", true) { texts().any { it == "PREVIEW" } }
        texts().filter { it.startsWith("Cannot read") }.forEach { assertTrue("the reason names the file: $it", it.contains("sky.exr")) }
        assertTrue("looking at it wrote nothing", before.contentEquals(Files.readAllBytes(meta(sky))))
        assertTrue(project.differences().isEmpty())
    }
}
