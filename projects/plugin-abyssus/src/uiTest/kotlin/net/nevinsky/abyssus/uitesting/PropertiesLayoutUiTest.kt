package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The geometry and content of the redesigned Properties panel in the real IDE (spec `object-properties-panel`). */
class PropertiesLayoutUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private fun scene() = SceneFacts.read(project.resolve("scenes/Main Scene.scene"))

    private fun tree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expand("ecs")
        frame.normalizeLayout(ide)
        return tree
    }

    private fun await(description: String, check: () -> Boolean) = waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), description) { check() }

    @Test
    fun entityHeaderAndGeometryFollowTheBoard() {
        val entityName = scene().entityName(0)!!
        tree().clickRow(entityName)
        await("entity header") { frame.properties().subtitle().startsWith("Entity 0 ·") }
        val panel = frame.properties()
        assertEquals(entityName, panel.headerTitle())
        assertEquals("ecs", panel.breadcrumb().last())
        assertTrue(panel.breadcrumb().first().endsWith(".abss"))
        assertEquals(150, panel.preferredWidth("label-NameComponent-name"))
        assertTrue("field height", panel.height("field-NameComponent-name") >= 24) // the look and feel's own height
        assertEquals(34, panel.sectionHeaderHeight("0.NameComponent"))
        assertTrue("sections ${panel.sectionTitles()}", panel.sectionTitles().containsAll(listOf("Name", "Position", "Render")))
        assertFalse("Name has no Enabled box", panel.hasEnabledBox("0.NameComponent"))
        assertTrue(panel.hasEnabledBox("0.RenderComponent"))
        shot("props-entity-design", panel.component)
    }

    @Test
    fun dimDefaultsAreDrawnInTheSecondaryColor() {
        tree().clickRow(scene().entityName(0)!!)
        await("entity header") { frame.properties().subtitle().startsWith("Entity 0 ·") }
        val panel = frame.properties()
        // localScale is not written in the fixture, so its boxes are the defaults and are dim; the position is written
        assertEquals(0xFF868A91.toInt(), panel.foreground("field-PositionComponent-localScale.x"))
        assertTrue(panel.foreground("field-PositionComponent-localPosition.x") != 0xFF868A91.toInt())
    }

    @Test
    fun collapsingASectionHidesItsFieldsAndKeepsItAfterARefresh() {
        tree().clickRow(scene().entityName(0)!!)
        await("entity header") { frame.properties().subtitle().startsWith("Entity 0 ·") }
        val panel = frame.properties()
        assertFalse(panel.isCollapsed("0.PositionComponent"))
        panel.clickChevron("0.PositionComponent")
        await("collapsed") { frame.properties().isCollapsed("0.PositionComponent") }
    }

    @Test
    fun folderEcsProjectAndEmptyStatesShowTheirHeaders() {
        val tree = tree()
        tree.clickRow("Assets")
        await("folder header") { frame.properties().subtitle().startsWith("Folder ·") }
        assertEquals(listOf("read-only"), frame.properties().badges())
        assertEquals(listOf("Folder"), frame.properties().sectionTitles())
        shot("props-folder", frame.properties().component)

        tree.clickRow("ecs")
        await("ecs header") { frame.properties().subtitle().startsWith("ecs ·") }
        assertEquals(listOf("Entities"), frame.properties().sectionTitles())
        shot("props-ecs", frame.properties().component)

        tree.clickRow(project.dir.fileName.toString() + ".abss")
        await("project header") { frame.properties().subtitle().startsWith("Project ·") }
        assertEquals(emptyList<String>(), frame.properties().breadcrumb())
        assertEquals(listOf("Project", "Main camera"), frame.properties().sectionTitles())
        shot("props-project", frame.properties().component)
    }

    @Test
    fun anUnusedOrReadOnlyAssetHasItsBadges() {
        val tree = tree()
        tree.expand("Assets")
        tree.expandAssetGroups()
        val hdr = project.dir.resolve("assets").toFile().listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }
            .first { java.nio.file.Files.readString(it.toPath().resolve("meta.json")).contains("SKYBOX_HDR") }.name
        tree.clickRow(hdr)
        await("hdr header") { frame.properties().subtitle() == "SKYBOX_HDR asset" }
        assertTrue(frame.properties().badges().contains("read-only"))
        assertEquals(listOf("meta.json", "additional"), frame.properties().sectionTitles())
    }
}
