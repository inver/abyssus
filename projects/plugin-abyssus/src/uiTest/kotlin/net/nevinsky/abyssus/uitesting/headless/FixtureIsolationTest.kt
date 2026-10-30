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

    @Test
    fun restoreRemovesFoldersAScenarioCreated() {
        val project = copy()
        Files.createDirectories(project.resolve("assets/New Model/nested"))
        Files.write(project.resolve("assets/New Model/nested/meta.json"), "{}".toByteArray())
        Files.createDirectories(project.resolve("assets/Empty Leftover"))

        project.restore()

        assertFalse(Files.exists(project.resolve("assets/New Model")))
        assertFalse("an empty folder would collide with the next creation of that name", Files.exists(project.resolve("assets/Empty Leftover")))
        assertTrue("the project's own folders stay", Files.isDirectory(project.resolve("scenes")))
        assertTrue(project.differences().isEmpty())
    }

    @Test
    fun restoreKeepsIdeStateFoldersEvenWhenEmpty() {
        val project = copy()
        Files.createDirectories(project.resolve(".idea/empty"))
        project.restore()
        assertTrue(Files.isDirectory(project.resolve(".idea/empty")))
    }

    @Test
    fun switchingProfilesRebuildsTheCopyAndLeavesBothSourcesAlone() {
        val project = copy()
        val physics = source().parent.resolve("Physics")
        val untitledBefore = project.sourceHashes()
        Files.createDirectories(project.resolve("assets/Leftover"))

        project.switchTo(physics)

        assertEquals(physics, project.profile)
        assertTrue("the Physics project file is there", Files.exists(project.resolve("Physics.abss")))
        assertFalse("the previous profile's files are gone", Files.exists(project.resolve("Untitled.abss")))
        assertFalse(Files.exists(project.resolve("assets/Leftover")))
        assertTrue(project.differences().isEmpty())
        Files.write(project.resolve("Physics.abss"), "edited".toByteArray())
        assertEquals(setOf("Physics.abss"), project.differences())

        project.switchTo(source())

        assertEquals(untitledBefore, project.hashes())
        assertTrue(project.untouchedProfiles().isEmpty())
    }

    @Test
    fun repeatedRestorationIsStable() {
        val project = copy()
        repeat(3) {
            Files.write(project.resolve("scenes/Main Scene.scene"), "edited $it".toByteArray())
            Files.createDirectories(project.resolve("assets/Made $it"))
            project.restore()
            assertTrue(project.differences().isEmpty())
            assertFalse(Files.exists(project.resolve("assets/Made $it")))
        }
        assertEquals(project.sourceHashes(), project.hashes())
    }

    @Test
    fun aChangedCommittedProfileIsReported() {
        val folder = tmp.newFolder("profileSource").toPath()
        Files.write(folder.resolve("a.txt"), "a".toByteArray())
        val project = FixtureProject(folder, tmp.newFolder("copy").toPath()).also { it.create() }
        assertTrue(project.untouchedProfiles().isEmpty())
        Files.write(folder.resolve("a.txt"), "tampered".toByteArray())
        assertEquals(listOf(folder), project.untouchedProfiles())
    }
}
