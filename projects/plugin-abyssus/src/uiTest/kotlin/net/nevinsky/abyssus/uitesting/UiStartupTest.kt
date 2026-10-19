package net.nevinsky.abyssus.uitesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class UiStartupTest : UiTestBase() {

    @Test
    fun robotConnectsAndLoadsThePreparedProject() {
        val opened = ide.openProjectPaths().map { Paths.get(it).toRealPath() }
        assertEquals(listOf(project.dir.toRealPath()), opened)
    }

    @Test
    fun abyssusIsInstalledAndNoSceneEditorOpened() {
        assertTrue("Abyssus Properties tool window is registered", "Abyssus Properties" in ide.toolWindowIds())
        val editors = ide.openEditorFiles()
        assertTrue("no scene editor opens on start: $editors", editors.none { it.endsWith(".scene") })
    }

    @Test
    fun propertiesWindowOpensAndCloses() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        frame.openProperties()
        frame.closeProperties()
    }

    @Test
    fun abyssusTreeShowsEntitiesWithoutDesktopCoordinates() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand("Ololo (0)")
        tree.expand("ecs")
        val rows = tree.rows()
        assertTrue("entity 0 row in $rows", rows.any { it.contains("Model 0") })
        assertTrue("Camera 4 row in $rows", rows.any { it.contains("Camera 4") })
        tree.expand("Camera 4")
        val expanded = tree.rows()
        val entity = expanded.indexOfFirst { it.startsWith("Camera 4") }
        assertEquals("Camera component row under Camera 4 in $expanded", "Camera", expanded.getOrNull(entity + 1))
    }

    @Test
    fun theHeaderOfASelectedEntityIsReadable() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand("Ololo (0)")
        tree.expand("ecs")
        tree.clickRow(tree.rows().first { it.contains("Model 0") }.substringBefore("  "))
        com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(15), java.time.Duration.ofMillis(300), "entity header") {
            frame.properties().subtitle().startsWith("Entity (0) ·")
        }
        assertEquals("Ololo", frame.properties().breadcrumb().getOrNull(1)?.substringBefore(" ("))
    }

    @Test
    fun treePageObjectReadsDepthAndIconOfTheFirstRows() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        val tree = frame.selectAbyssusView()
        tree.expand("Untitled.abss")
        val rows = tree.rowInfo()
        assertEquals(listOf(0, 1, 1, 1), rows.take(4).map { it.depth })
        assertEquals(listOf("Scenes", "UI", "Assets"), rows.drop(1).take(3).map { it.label })
        assertTrue("icon paths in $rows", rows.take(4).all { it.icon.contains("tree/") })
    }

    @Test
    fun resetRestoresTheDocumentAndTreeAfterAnExternalChange() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        val scene = project.resolve("scenes/Main Scene.scene")
        val original = Files.readString(scene)
        val path = scene.toString()
        try {
            Files.writeString(scene, original.replace("\"name\": \"Ololo\"", "\"name\": \"Changed\""))
            ide.refresh(project.dir.toString())
            // the IDE picks the outside change up through the VFS; the tree only follows plugin edits, so it is not asserted here
            com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(15), java.time.Duration.ofMillis(300), "IDE document sees the outside change") {
                ide.documentText(path)?.contains("\"name\": \"Changed\"") == true
            }
        } finally {
            project.restore()
            ide.refresh(project.dir.toString())
        }
        com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(15), java.time.Duration.ofMillis(300), "IDE document restored") {
            ide.documentText(path) == original
        }
        assertTrue(project.differences().isEmpty())
        frame.reopenAbyssusView()
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        waitForRow(frame, "Ololo (0)")
    }

    private fun waitForRow(frame: IdeFrame, text: String) =
        com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(15), java.time.Duration.ofMillis(300), "tree row '$text'") {
            frame.tree().rows().any { it.contains(text) }
        }
}
