package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.project.ProjectLoader
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiIssueCode
import net.nevinsky.abyssus.lib.core.project.UiProjectRegistry
import net.nevinsky.abyssus.lib.core.project.UiRegistryEntry
import net.nevinsky.abyssus.lib.core.project.UiRegistryProblem
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UiProjectRegistryTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val dir: File = Files.createTempDirectory("ui-registry").toFile()

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun asset(name: String, meta: String) {
        File(dir, "assets/$name").mkdirs()
        File(dir, "assets/$name/meta.json").writeText(meta)
    }

    private fun uiMeta(version: Int = 1) =
        """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":$version,"screens":[{"id":"main","root":{"id":"r","type":"panel"}}]}}}"""

    private fun registry() = UiProjectRegistry(json, testFileLoader(dir))

    private fun project(vararg names: String) = ProjectDto(uiAssetNames = names.toList(), dir = dir.toPath())

    @Test fun absentListIsEmptyAndTheProjectFileIsNotRewritten() {
        val file = File(dir, "P.abss")
        val text = """{"format":"abyssus","formatVersion":1,"name":"P","scenes":[]}"""
        file.writeText(text)
        val loaded = ProjectLoader(json, testFileLoader(dir)).load("P.abss")
        assertTrue(loaded.uiAssetNames.isEmpty())
        assertTrue(registry().entries(loaded).isEmpty())
        assertEquals(text, file.readText())
    }

    @Test fun listedNamesDecodeInOrder() {
        File(dir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P","uiAssetNames":["menus","hud"]}""")
        asset("menus", uiMeta()); asset("hud", uiMeta())
        val loaded = ProjectLoader(json, testFileLoader(dir)).load("P.abss")
        assertEquals(listOf("menus", "hud"), loaded.uiAssetNames)
        val entries = registry().entries(loaded)
        assertEquals(listOf("menus", "hud"), entries.map { it.name })
        assertTrue(entries.all { it is UiRegistryEntry.Registered && it.admission is UiAdmission.Admitted })
        assertTrue(registry().lookup(loaded, "hud") is UiAdmission.Admitted)
        assertNull(registry().lookup(loaded, "other"))
    }

    @Test fun duplicateMissingAndWrongTypeTargetsAreProblems() {
        asset("menus", uiMeta())
        asset("tree", """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}""")
        asset("broken", "{not json")
        val entries = registry().entries(project("menus", "menus", "gone", "tree", "broken", "../escape"))
        assertTrue(entries[0] is UiRegistryEntry.Registered)
        fun problem(i: Int) = (entries[i] as UiRegistryEntry.Problem).problem
        assertEquals(UiRegistryProblem.DUPLICATE, problem(1))
        assertEquals(UiRegistryProblem.MISSING, problem(2))
        assertEquals(UiRegistryProblem.WRONG_TYPE, problem(3))
        assertEquals(UiRegistryProblem.UNREADABLE, problem(4))
        assertEquals(UiRegistryProblem.MISSING, problem(5))
        assertNull(registry().lookup(project("tree"), "tree"))
    }

    @Test fun nativeAdmissionRefusesAForeignOrFutureAsset() {
        asset("future", uiMeta(version = 2))
        asset("foreign", """{"format":"other","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1}}}""")
        val entries = registry().entries(project("future", "foreign")).map { (it as UiRegistryEntry.Registered).admission as UiAdmission.Refused }
        assertEquals(UiIssueCode.UI_VERSION, entries[0].issues.single().code)
        assertEquals(UiIssueCode.FORMAT_MARKER, entries[1].issues.single().code)
    }

    @Test fun theProjectFileItselfStillNeedsNativeAdmission() {
        File(dir, "P.abss").writeText("""{"name":"P","uiAssetNames":["menus"]}""")
        assertThrows(UnsupportedDocumentFormat::class.java) { ProjectLoader(json, testFileLoader(dir)).load("P.abss") }
    }

    @Test fun untitledFixtureHasNoRegistry() {
        val project = ProjectDto(dir = testProject("Untitled").toPath())
        assertTrue(project.uiAssetNames.isEmpty())
    }
}
