package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Editable asset metadata (AST-01, AST-04, and the asset half of FLD-01..05) on the fixture terrain: `size` and `uv`.
 * Edits are made in the asset Properties; its own Undo and Redo buttons revert them, and the file is compared byte for
 * byte outside the edited member.
 */
class AssetPropertyLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)

    private val terrain: String get() = project.dir.resolve("assets").toFile().listFiles()!!
        .filter { it.isDirectory }.sortedBy { it.name }
        .first { Regex("\"type\"\\s*:\\s*\"TERRAIN\"").containsMatchIn(Files.readString(it.toPath().resolve("meta.json"))) }.name
    private val meta: Path get() = project.dir.resolve("assets").resolve(terrain).resolve("meta.json")

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openTerrain(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expandAssets()
        frame.normalizeLayout(ide)
        tree.clickRow(terrain)
        LifecycleHarness.awaitState("the terrain's fields are shown", true) { frame.properties().exists("asset-field-size") }
        return tree
    }

    private fun additional(): JsonNode = ObjectMapper().readTree(Files.readString(meta))["additional"]

    private fun asText(name: String) = frame.properties().fieldText(name)

    @Test
    fun sizeAndUvCommitOnEnterAndOnFocusLossWithAssetUndoAndRedo() {
        val original = Files.readString(meta)
        openTerrain()
        val p = frame.properties()

        p.enter("asset-field-size", "800")
        LifecycleHarness.awaitState("Enter saves the size", 800) { additional()["size"].asInt() }
        val sized = Files.readString(meta)
        val diff = LifecycleHarness.lineDiff(original, sized)
        assertEquals("only the size line changed: $diff", 1, diff.added.size)

        // focus loss commits without Enter, and the displayed value agrees with the file
        p.type("asset-field-uv", "30")
        p.focus("asset-field-size")
        LifecycleHarness.awaitState("losing focus saves the uv", 30.0) { additional()["uv"].asDouble() }
        LifecycleHarness.awaitState("the field shows the file's value", additional()["uv"].asText().removeSuffix(".0")) { asText("asset-field-uv").removeSuffix(".0") }
        val edited = Files.readString(meta)

        // the panel's own Undo and Redo revert one edit at a time
        p.click("asset-undo")
        frame.saveAll()
        LifecycleHarness.awaitState("Undo restores the uv edit only", sized) { Files.readString(meta) }
        p.click("asset-undo")
        frame.saveAll()
        LifecycleHarness.awaitState("a second Undo restores the original bytes", original) { Files.readString(meta) }
        p.click("asset-redo")
        frame.saveAll()
        LifecycleHarness.awaitState("Redo reapplies the size", sized) { Files.readString(meta) }
        p.click("asset-redo")
        frame.saveAll()
        LifecycleHarness.awaitState("Redo reapplies the uv", edited) { Files.readString(meta) }
        assertEquals("only the terrain meta changed", setOf("assets/${terrain}/meta.json"), project.differences().map { it.replace('\\', '/') }.toSet())
    }

    @Test
    fun invalidSizeAndUvAreRefusedWithAReasonAndWriteNothing() {
        val original = Files.readString(meta)
        openTerrain()
        val p = frame.properties()
        for ((field, bad) in listOf("size" to "0", "size" to "-5", "size" to "abc", "size" to "1.5", "uv" to "0", "uv" to "-2", "uv" to "abc")) {
            val name = "asset-field-$field"
            val shown = asText(name)
            p.enter(name, bad)
            LifecycleHarness.awaitState("'$bad' for $field is refused with a reason", true) {
                p.labelTexts("asset-error-$field").firstOrNull().orEmpty().isNotEmpty()
            }
            assertEquals("'$bad' for $field wrote nothing", original, Files.readString(meta))
            assertEquals("the stored value comes back", shown, asText(name))
        }
        // a valid replacement after a refusal saves and clears the message
        p.enter("asset-field-size", "900")
        LifecycleHarness.awaitState("the valid size saves", 900) { additional()["size"].asInt() }
        LifecycleHarness.awaitState("the message is cleared", "") { p.labelTexts("asset-error-size").firstOrNull().orEmpty() }
    }

    @Test
    fun resubmittingTheStoredValuesWritesNothing() {
        val original = Files.readString(meta)
        openTerrain()
        val p = frame.properties()
        p.enter("asset-field-size", asText("asset-field-size"))
        p.enter("asset-field-uv", asText("asset-field-uv"))
        Thread.sleep(1000)
        assertEquals("a no-op submission changes nothing", original, Files.readString(meta))
        assertEquals("and the undo button stays unavailable", false, p.isEnabled("asset-undo"))
    }
}
