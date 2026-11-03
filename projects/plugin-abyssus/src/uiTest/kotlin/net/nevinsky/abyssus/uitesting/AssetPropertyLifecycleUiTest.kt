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

    // ---- sky assets: cube faces (AST-02), procedural defaults (AST-03), unresolved and stale values (AST-05), read-only (AST-06)

    private fun assetMeta(asset: String): Path = project.dir.resolve("assets/$asset/meta.json")

    private fun openAsset(asset: String, awaitField: String? = null): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expandAssets()
        frame.normalizeLayout(ide)
        tree.clickRow(asset)
        LifecycleHarness.awaitState("$asset is shown", asset) { frame.properties().headerTitle() }
        awaitField?.let { LifecycleHarness.awaitState("the field $it is shown", true) { frame.properties().exists(it) } }
        return tree
    }

    private fun faces(asset: String = "skybox_default"): JsonNode = ObjectMapper().readTree(Files.readString(assetMeta(asset)))["additional"]

    /** What choosing None on [face] did: `cleared` when the member is gone, `refused` when a reason is shown and the file keeps [kept]; anything else fails with what was seen. */
    private fun noneOutcome(asset: String, face: String, kept: String): String {
        var outcome = ""
        LifecycleHarness.awaitState("None is cleared or refused with a reason", "ok") {
            // a cleared face is written as `null` or left out
            val value = faces(asset)[face]?.takeUnless { it.isNull }?.asText()
            val reason = runCatching { frame.properties().labelText("asset-error-$face") }.getOrDefault("")
            val seen = when {
                value.isNullOrEmpty() -> "cleared"
                reason.isNotBlank() && value == kept -> "refused"
                else -> "value='$value' reason='$reason' shown='${runCatching { frame.properties().choiceText("asset-field-$face") }.getOrNull()}'"
            }
            if (seen == "cleared" || seen == "refused") outcome = seen
            if (outcome.isNotEmpty()) "ok" else seen
        }
        return outcome
    }

    @Test
    fun cubeFaceChoicesFollowTheChosenFileAndNoneClearsOnlyThatFace() {
        val sky = "skybox_default"
        val original = Files.readString(assetMeta(sky))
        Files.copy(project.dir.resolve("assets/$sky/skybox_default.png"), project.dir.resolve("assets/$sky/alt.png"))
        ide.refresh(project.dir.resolve("assets/$sky").toString())
        openAsset(sky, "asset-field-top")
        val p = { frame.properties() }
        assertTrue("the choices are None and the folder's images: ${p().choiceItems("asset-field-top")}", p().choiceItems("asset-field-top").size == 3)

        p().chooseWithKeyboard("asset-field-top", "alt.png")
        LifecycleHarness.awaitState("the top face names alt.png", "alt.png") { faces(sky)["top"]?.asText() }
        for (face in listOf("bottom", "left", "right", "front", "back")) assertEquals("$face is as it was", "skybox_default.png", faces(sky)[face].asText())
        LifecycleHarness.awaitState("the face previews follow", 6) { p().previewCount() }
        val chosen = Files.readString(assetMeta(sky))

        p().chooseWithKeyboard("asset-field-top", "")
        val outcome = noneOutcome(sky, "top", "alt.png")
        for (face in listOf("bottom", "left", "right", "front", "back")) assertEquals("$face is still as it was", "skybox_default.png", faces(sky)[face].asText())

        if (outcome == "cleared") {
            p().clickFirst("asset-undo")
            frame.saveAll()
            LifecycleHarness.awaitState("Undo restores alt.png", chosen) { Files.readString(assetMeta(sky)) }
        } else {
            assertEquals("a refused None wrote nothing", chosen, Files.readString(assetMeta(sky)))
        }
        p().clickFirst("asset-undo")
        frame.saveAll()
        LifecycleHarness.awaitState("Undo restores the original bytes", original) { Files.readString(assetMeta(sky)) }
    }

    @Test
    fun anUnresolvedFaceStaysVisibleAndLookingAtItWritesNothing() {
        val sky = "skybox_default"
        val ghost = Files.readString(assetMeta(sky)).replace("\"left\": \"skybox_default.png\"", "\"left\": \"ghost.png\"")
        Files.writeString(assetMeta(sky), ghost)
        ide.refresh(assetMeta(sky).toString())
        openAsset(sky, "asset-field-left")
        LifecycleHarness.awaitState("the missing file is named with its state", true) { frame.properties().texts().any { it == "ghost.png (not found)" } }
        assertTrue("the choice keeps the stored identity", frame.properties().choiceText("asset-field-left").contains("value=ghost.png"))
        // choosing another face does not silently replace the unresolved one
        Files.copy(project.dir.resolve("assets/$sky/skybox_default.png"), project.dir.resolve("assets/$sky/alt.png"))
        ide.refresh(project.dir.resolve("assets/$sky").toString())
        Thread.sleep(1000)
        frame.properties().chooseWithKeyboard("asset-field-top", "alt.png")
        LifecycleHarness.awaitState("the top face is changed", "alt.png") { faces(sky)["top"]?.asText() }
        assertEquals("the unresolved face is untouched", "ghost.png", faces(sky)["left"].asText())
    }

    @Test
    fun omittedProceduralValuesShowTheirEffectiveDefaultAndRadiiNeedAnAtmosphereAbovethePlanet() {
        val sky = "skybox_physical"
        val original = Files.readString(assetMeta(sky))
        Files.writeString(assetMeta(sky), original.replace("    \"mieG\": 0.76,\n", ""))
        ide.refresh(assetMeta(sky).toString())
        val omitted = Files.readString(assetMeta(sky))
        // the IDE must hold the rewritten text before the panel reads it
        LifecycleHarness.awaitState("the IDE holds the file without mieG", omitted) { ide.documentText(assetMeta(sky).toString()) }
        val tree = openAsset(sky, "asset-field-mieG")
        tree.clickRow("skybox_default")
        tree.clickRow(sky)
        LifecycleHarness.awaitState("the field is shown again", true) { frame.properties().exists("asset-field-mieG") }
        val p = { frame.properties() }
        assertTrue("the file has no mieG", faces(sky)["mieG"] == null)
        assertEquals("the effective value is shown before editing", "0.76", p().fieldText("asset-field-mieG"))
        assertEquals("looking wrote nothing", omitted, Files.readString(assetMeta(sky)))

        p().enter("asset-field-mieG", "0.5")
        frame.saveAll() // the file was rewritten from outside: its buffer is written out with the next save
        LifecycleHarness.awaitState("0.5 is saved", "0.5") {
            faces(sky)["mieG"]?.asText() ?: "not saved; error='${runCatching { p().labelText("asset-error-mieG") }.getOrNull()}' shown='${runCatching { p().fieldText("asset-field-mieG") }.getOrNull()}'"
        }
        p().enter("asset-field-mieG", "0.76")
        frame.saveAll()
        // back at the default: the value is the default, whether the member is written or omitted
        LifecycleHarness.awaitState("the default is the value again", 0.76) { faces(sky)["mieG"]?.asDouble() ?: 0.76 }

        // the radii must keep the atmosphere above the planet, whichever one is edited
        val saved = Files.readString(assetMeta(sky))
        p().enter("asset-field-atmosphereRadius", "1000")
        LifecycleHarness.awaitState("the atmosphere radius is explained", true) { p().labelText("asset-error-atmosphereRadius").isNotBlank() }
        p().enter("asset-field-planetRadius", "7000000")
        LifecycleHarness.awaitState("the planet radius is explained", true) { p().labelText("asset-error-planetRadius").isNotBlank() }
        assertEquals("no refused radius was written", saved, Files.readString(assetMeta(sky)))
    }

    @Test
    fun aStaleCommitReportsAConflictAndKeepsTheNewerValue() {
        val sky = "skybox_physical"
        openAsset(sky, "asset-field-heightMie")
        val p = { frame.properties() }
        p().type("asset-field-heightMie", "7")
        val newer = Files.readString(assetMeta(sky)).replace("\"heightMie\": 1200.0", "\"heightMie\": 1300.0")
        Files.writeString(assetMeta(sky), newer)
        ide.refresh(assetMeta(sky).toString())
        Thread.sleep(1500) // the panel re-reads the file before the older editor commits
        p().focus("asset-field-sunIntensity")
        LifecycleHarness.awaitState("the conflict is reported", true) { runCatching { p().labelText("asset-error-heightMie").contains("changed elsewhere") }.getOrDefault(false) }
        assertEquals("the newer value stays in the file", newer, Files.readString(assetMeta(sky)))
        assertEquals("and is the one shown", "1300.0", p().fieldText("asset-field-heightMie"))
    }

    @Test
    fun aModelAssetShowsItsValuesReadOnlyAndSelectingItWritesNothing() {
        val before = project.hashes()
        val tree = openAsset("tree")
        assertTrue("no asset field is editable", frame.properties().fieldNames("asset-field-").isEmpty())
        LifecycleHarness.awaitState("the asset says it is read only", true) { frame.properties().texts().any { it.contains("read-only", ignoreCase = true) || it.contains("no editors", ignoreCase = true) } }
        tree.clickRow("skybox_physical")
        tree.clickRow("tree")
        assertEquals("selection rewrote nothing", before, project.hashes())
    }
}
