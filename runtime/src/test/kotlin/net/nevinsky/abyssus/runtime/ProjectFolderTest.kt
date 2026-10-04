/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.runtime.project.ProjectFolder
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ProjectFolderTest {
    @Test fun untitledListsItsScene() {
        val project = ProjectFolder(testProject("Untitled").toPath())
        assertEquals("Untitled.abss", project.abss()?.fileName.toString())
        assertEquals(listOf("Main Scene.scene"), project.sceneFiles().map { it.fileName.toString() })
    }
    @Test fun folderWithoutAbssHasNoProject() {
        assertNull(ProjectFolder(testProject("Animated").toPath()).abss())
    }
    @Test fun sceneFilesAreSortedAndCaseSensitive() {
        val temp = Files.createTempDirectory("runtime-project")
        try {
            Files.createDirectory(temp.resolve("scenes"))
            for (name in listOf("Z.scene", "A.scene", "Wrong.SCENE", "bad.scene.bak")) Files.writeString(temp.resolve("scenes/$name"), "{}")
            Files.createDirectory(temp.resolve("scenes/directory.scene"))
            assertEquals(listOf("A.scene", "Z.scene"), ProjectFolder(temp).sceneFiles().map { it.fileName.toString() })
        } finally { temp.toFile().deleteRecursively() }
    }
}
