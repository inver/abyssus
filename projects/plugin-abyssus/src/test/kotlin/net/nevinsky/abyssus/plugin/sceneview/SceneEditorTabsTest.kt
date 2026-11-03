/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.SceneFileEditorProvider

/**
 * A scene is two editors of one tab on one document: the IDE's `Text` and `Scene View` tabs. The light test editor manager
 * only creates text editors, so the tab switching itself is checked by `SceneViewUiTest`; this pins what the provider owes it.
 */
class SceneEditorTabsTest : BasePlatformTestCase() {
    private val minified = """{"format":"abyssus","formatVersion":1,"name":"tabs","ecs":{}}"""
    private val provider = SceneFileEditorProvider()

    fun testTheSceneViewEditorReadsTheSameDocumentAsTheTextEditor() {
        val file = myFixture.addFileToProject("tabs/Main.scene", minified).virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val editor = provider.createEditor(project, file) as SceneFileEditor
        try {
            assertSame(document, editor.documentReferences.single().document)
        } finally {
            editor.dispose()
        }
    }

    fun testCreatingAndClosingTheSceneViewWritesNothing() {
        val file = myFixture.addFileToProject("tabs/Other.scene", minified).virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = document.text
        val editor = provider.createEditor(project, file) as SceneFileEditor
        editor.dispose()
        assertEquals(before, document.text)
    }

    fun testTheSceneViewIsOfferedAfterTheTextEditorUnderItsOwnTypeId() {
        assertEquals("abyssus-scene-view", provider.editorTypeId)
        assertEquals(com.intellij.openapi.fileEditor.FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR, provider.policy)
    }
}
