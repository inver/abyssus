/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.project

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProjectTest {
    @Test
    fun untitledListsItsProjectFileAndScene() {
        val project = ProjectDto(dir = testProject("Untitled").toPath())
        assertEquals("Untitled.abss", project.file()?.fileName.toString())
        assertEquals(listOf("Main Scene.scene"), project.sceneFiles().map { it.fileName.toString() })
    }

    @Test
    fun aFolderWithoutAbssHasNoProjectFile() {
        assertNull(ProjectDto(dir = testProject("Animated").toPath()).file())
    }

    @Test
    fun aProjectNotMadeFromAFolderHasNoFiles() {
        val project = ProjectDto(name = "Memory")
        assertNull(project.file())
        assertTrue(project.sceneFiles().isEmpty())
    }

    @Test
    fun aMissingFolderHasNoScenes() {
        assertTrue(ProjectDto(dir = Files.createTempDirectory("core-project").also { it.toFile().deleteRecursively() }).sceneFiles().isEmpty())
    }

    @Test
    fun sceneFilesAreSortedAndCaseSensitive() {
        val temp = Files.createTempDirectory("core-project")
        try {
            Files.createDirectory(temp.resolve("scenes"))
            for (name in listOf("Z.scene", "A.scene", "Wrong.SCENE", "bad.scene.bak")) Files.writeString(temp.resolve("scenes/$name"), "{}")
            Files.createDirectory(temp.resolve("scenes/directory.scene"))
            assertEquals(listOf("A.scene", "Z.scene"), ProjectDto(dir = temp).sceneFiles().map { it.fileName.toString() })
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun theProjectFileIsTheFirstAbssByName() {
        val temp = Files.createTempDirectory("core-project")
        try {
            for (name in listOf("B.abss", "A.abss", "notes.txt", "C.ABSS")) Files.writeString(temp.resolve(name), "{}")
            assertEquals("A.abss", ProjectDto(dir = temp).file()?.fileName.toString())
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun aLoadedProjectKnowsItsFolderAndTheFolderIsNotPartOfTheFile() {
        val dir = testProject("Untitled")
        val loaded = ProjectLoader(JsonProcessor(), FileLoader(dir)).load("Untitled.abss")
        assertEquals("Untitled", loaded.name)
        assertEquals(dir.toPath(), loaded.dir)
        assertEquals(listOf("Main Scene.scene"), loaded.sceneFiles().map { it.fileName.toString() })
        assertTrue("the folder is never written to the file", !JsonProcessor().toString(loaded).contains("\"dir\""))
    }
}
