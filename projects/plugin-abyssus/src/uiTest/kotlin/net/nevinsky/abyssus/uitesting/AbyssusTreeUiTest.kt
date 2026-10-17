package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

class AbyssusTreeUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"

    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun scene() = SceneFacts.read(project.resolve(scenePath))

    private fun openHierarchy(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expand("ecs")
        return tree
    }

    private fun waitForDocument(description: String, check: (String) -> Boolean) {
        val path = project.resolve(scenePath).toString()
        try {
            waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), description) { ide.documentText(path)?.let(check) == true }
        } catch (e: Exception) {
            val text = ide.documentText(path)
            throw AssertionError("$description; IDE document: " + (text?.let { Regex("\"fogEnabled\"[^,]*").find(it)?.value } ?: "not loaded") + "; disk: " + Regex("\"fogEnabled\"[^,]*").find(Files.readString(project.resolve(scenePath)))?.value, e)
        }
    }

    @Test
    fun expandsProjectScenesAssetsAndEcs() {
        val facts = scene()
        val tree = openHierarchy()
        tree.expand("Assets")
        val rows = tree.rows()
        assertTrue("project row in $rows", rows.first().startsWith("Untitled.abss"))
        assertTrue("scene row ${facts.treeLabel} in $rows", facts.treeLabel in rows)
        facts.entityName(0)?.let { name -> assertTrue("entity $name in $rows", rows.any { it.startsWith(name) }) }
        project.dir.resolve("assets").toFile().list()!!.forEach { asset ->
            assertTrue("asset $asset in $rows", rows.any { it.startsWith(asset) })
        }
        tree.clickRow("Untitled.abss") // a fixed selection for the capture
        shot("tree-expanded", frame.projectToolWindow)
    }

    @Test
    fun fogEyeTogglesOnlyFogEnabledAndUndoRestoresTheBytes() {
        val original = Files.readString(project.resolve(scenePath))
        val facts = SceneFacts(original)
        assertTrue("fixture fog starts enabled", facts.fogEnabled)
        val tree = openHierarchy()

        tree.clickRowEye("fog")
        waitForDocument("fogEnabled false in the IDE document") { it.contains("\"fogEnabled\": false") }
        shot("tree-fog-off", frame.projectToolWindow)
        frame.saveAll()
        val expected = original.replaceFirst("\"fogEnabled\": true", "\"fogEnabled\": false")
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "disk shows only fogEnabled changed") {
            Files.readString(project.resolve(scenePath)) == expected
        }

        // Undo is tied to the document: bring the scene file into a text editor, then use the IDE shortcut.
        ide.openTextEditor(project.resolve(scenePath).toString())
        try {
            frame.undo()
            waitForDocument("fogEnabled true again") { it.contains("\"fogEnabled\": true") }
            frame.saveAll()
            waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "disk restored by Undo") {
                Files.readString(project.resolve(scenePath)) == original
            }
        } finally {
            ide.closeFile(project.resolve(scenePath).toString())
        }
        assertTrue(project.differences().isEmpty())
    }

    @Test
    fun switchingViewsDoesNotDuplicateNodes() {
        val first = openHierarchy().rows()
        frame.selectView("Project")
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        val rows = tree.rows()
        assertEquals("one project row in $rows", 1, rows.count { it.startsWith("Untitled.abss") })
        assertEquals("one Scenes row in $rows", 1, rows.count { it.startsWith("Scenes") })
        assertEquals("one scene row in $rows", 1, rows.count { it == scene().treeLabel })
        assertTrue("same rows as before: $first vs $rows", rows.size <= first.size)
    }
}
