package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.FixtureProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths

class FixtureIsolationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun source() = Paths.get(System.getProperty("abyssus.ui.fixtureSource"))

    private fun copy(): FixtureProject = FixtureProject(source(), tmp.newFolder("Untitled").toPath()).also { it.create() }

    @Test
    fun copyMatchesTheSourceWithoutIdeState() {
        val project = copy()
        assertEquals(project.sourceHashes(), project.hashes())
        assertFalse(Files.exists(project.resolve(".idea")))
        assertTrue(Files.exists(project.resolve("scenes/Main Scene.scene")))
    }

    @Test
    fun editingTheCopyLeavesTheSourceAlone() {
        val project = copy()
        val before = project.sourceHashes()
        Files.write(project.resolve("scenes/Main Scene.scene"), "edited".toByteArray())
        Files.write(project.resolve("extra.txt"), "x".toByteArray())
        assertEquals(setOf("scenes/Main Scene.scene", "extra.txt"), project.differences())
        assertEquals(before, project.sourceHashes())
    }

    @Test
    fun restoreBringsBackTheOriginalBytes() {
        val project = copy()
        val scene = project.resolve("scenes/Main Scene.scene")
        val original = Files.readAllBytes(scene)
        Files.write(scene, "edited".toByteArray())
        Files.delete(project.resolve("Untitled.abss"))
        Files.write(project.resolve("extra.txt"), "x".toByteArray())

        project.restore()

        assertArrayEquals(original, Files.readAllBytes(scene))
        assertTrue(project.differences().isEmpty())
        assertFalse(Files.exists(project.resolve("extra.txt")))
    }

    @Test
    fun restoreKeepsIdeStateTheIdeWrote() {
        val project = copy()
        Files.createDirectories(project.resolve(".idea"))
        Files.write(project.resolve(".idea/workspace.xml"), "<ide/>".toByteArray())
        project.restore()
        assertTrue(Files.exists(project.resolve(".idea/workspace.xml")))
    }
}
