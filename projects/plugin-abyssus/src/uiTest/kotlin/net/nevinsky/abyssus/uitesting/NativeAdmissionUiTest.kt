package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Native admission and preservation (DOC-01, DOC-02): prepared unsupported scenes are never rewritten or migrated by
 * opening, viewing or saving them, and an authoring edit beside unknown data and number text keeps every unrelated byte.
 * The prepared inputs are written into the disposable project before the IDE reads them.
 */
class NativeAdmissionUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun closeEditors() {
        runCatching { ide.closeFile(scene.toString()) }
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun prepared(): Map<String, String> {
        val valid = Files.readString(scene)
        fun edit(change: (ObjectNode) -> Unit): String = SceneJson().parseObject(valid).also(change).let { SceneJson().pretty(it) }
        return linkedMapOf(
            "malformed JSON" to "{ \"format\": \"abyssus\", ",
            "missing marker" to edit { it.remove("format") },
            "foreign marker" to edit { it.put("format", "other") },
            "future version" to edit { it.put("formatVersion", 2) },
            "nonintegral version" to edit { it.put("formatVersion", 1.5) },
            "legacy component identifiers" to edit { (it.get("ecs") as ObjectNode).putArray("componentIdentifiers") },
        )
    }

    @Test
    fun unsupportedScenesAreNeverRewrittenByOpeningViewingOrSaving() {
        for ((label, text) in prepared()) {
            Files.writeString(scene, text)
            ide.refresh(scene.toString())
            val tree = frame.selectAbyssusView()
            runCatching { tree.rows() }
            ide.openSceneView(scene.toString())
            // the view reports why it cannot show the scene instead of an editing surface
            LifecycleHarness.awaitState("$label: the Scene View reports the unreadable scene", true) {
                frame.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='JBLabel' and contains(@visible_text, 'Cannot read scene')]")).isNotEmpty()
            }
            Thread.sleep(1500)
            frame.saveAll()
            Thread.sleep(500)
            assertEquals("$label: the prepared input is byte-identical after opening, viewing and saving", text, Files.readString(scene))
            // the text editor must not format an unsupported document either
            ide.openTextEditor(scene.toString())
            Thread.sleep(1500)
            frame.saveAll()
            ide.closeFile(scene.toString())
            assertEquals("$label: nor after the text editor", text, Files.readString(scene))
        }
    }

    @Test
    fun anAuthoringEditKeepsUnknownDataNumberTextAndKeyOrder() {
        val valid = Files.readString(scene)
        val root = SceneJson().parseObject(valid)
        root.putObject("x-extension").put("keep", "me").putArray("list").add(1).add(2)
        // number text: give entity 0's position x the spelling 1.000
        val prepared = SceneJson().pretty(root).replaceFirst(Regex("\"x\": -3\\.035308"), "\"x\": -3.0353080")
        assertTrue("the spelling was prepared", prepared.contains("-3.0353080"))
        Files.writeString(scene, prepared)
        ide.refresh(scene.toString())
        ide.refresh(project.dir.toString())
        ide.refreshProjectView()
        // an earlier scenario in this IDE read unsupported project files: show the view afresh
        runCatching { frame.reopenAbyssusView() }
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the project tree shows its project row", true) { tree.rows().any { it.startsWith("Untitled.abss") } }
        tree.expand("Untitled.abss")
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        tree.rightClickRow(SceneFacts.read(scene).treeLabel)
        frame.clickMenuPath("Add", "Add Light")
        frame.choosePopupItem("Directional")
        LifecycleHarness.awaitState("the light was added", true) { ide.documentText(scene.toString())?.contains("LIGHT_DIRECTIONAL") == true && ide.documentText(scene.toString()) != prepared }
        frame.saveAll()
        val after = Files.readString(scene)
        assertTrue("only the new entity differs (key order and number text kept)", LifecycleHarness.sameExcept(prepared, after, "/ecs/11"))
        assertTrue("the unknown extension survived", after.contains("\"x-extension\"") && after.contains("\"keep\": \"me\""))
        assertTrue("the number spelling survived", after.contains("-3.0353080"))
    }

    private fun variants(valid: String): Map<String, String> {
        fun edit(change: (ObjectNode) -> Unit): String = SceneJson().parseObject(valid).also(change).let { SceneJson().pretty(it) }
        return linkedMapOf(
            "malformed JSON" to "{ \"format\": \"abyssus\", ",
            "missing marker" to edit { it.remove("format") },
            "future version" to edit { it.put("formatVersion", 2) },
            "nonintegral version" to edit { it.put("formatVersion", 1.5) },
        )
    }

    @Test
    fun unsupportedProjectAndAssetMetadataAreNeverRewritten() {
        val abss = project.resolve("Untitled.abss")
        val meta = Files.list(project.resolve("assets")).use { dirs -> dirs.map { it.resolve("meta.json") }.filter { Files.exists(it) }.sorted().findFirst().orElseThrow() }
        for (target in listOf(abss, meta)) {
            val valid = Files.readString(target)
            for ((label, text) in variants(valid)) {
                Files.writeString(target, text)
                ide.refresh(target.toString())
                ide.refreshProjectView()
                val tree = frame.selectAbyssusView()
                runCatching { tree.rows() }
                runCatching { tree.clickRow("Untitled.abss") }
                Thread.sleep(1200)
                frame.saveAll()
                Thread.sleep(500)
                assertEquals("${target.fileName} $label: the prepared input is byte-identical after the tree read it and a save", text, Files.readString(target))
                Files.writeString(target, valid)
                ide.refresh(target.toString())
            }
        }
    }
}
