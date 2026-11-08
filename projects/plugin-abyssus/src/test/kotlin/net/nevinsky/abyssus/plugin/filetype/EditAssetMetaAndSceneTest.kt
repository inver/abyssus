/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.scene.SceneEdit
import net.nevinsky.abyssus.lib.core.format.EntityType

class EditAssetMetaAndSceneTest : BasePlatformTestCase() {
    private val metaText = """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"m.glb"}}"""
    private val sceneText = """{"formatVersion":1,"name":"S"}"""

    private fun files() = myFixture.addFileToProject("p/assets/m/meta.json", metaText).virtualFile to
        myFixture.addFileToProject("p/scenes/S.scene", sceneText).virtualFile

    private fun text(f: com.intellij.openapi.vfs.VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    fun testOneCommandChangesBothAndOneUndoRestoresBoth() {
        val (meta, scene) = files()
        myFixture.openFileInEditor(meta)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val ok = editAssetMetaAndScene(
            project, meta, scene, "Pivot",
            { TextEditOutcome.Edited("""{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"n.glb"}}""") },
            SceneEdit { it.add(EntityType.OBJECT); true },
        )
        assertTrue(ok)
        assertTrue(text(meta).contains("n.glb"))
        assertTrue(text(scene).contains("OBJECT"))
        UndoManager.getInstance(project).undo(editor)
        assertEquals(metaText, text(meta))
        assertEquals(sceneText, text(scene))
    }

    fun testNothingIsWrittenWhenEitherTransformRefuses() {
        val (meta, scene) = files()
        val refused = TextEditOutcome.Refused(IllegalStateException("no"))
        assertFalse(editAssetMetaAndScene(project, meta, scene, "Pivot", { refused }, SceneEdit { it.add(EntityType.OBJECT); true }))
        assertEquals(metaText, text(meta))
        assertEquals(sceneText, text(scene))

        // a scene that cannot be read refuses, so the meta is not written either
        val broken = myFixture.addFileToProject("p/scenes/Broken.scene", "{ not json").virtualFile
        val badScene = editAssetMetaAndScene(
            project, meta, broken, "Pivot",
            { TextEditOutcome.Edited(metaText.replace("m.glb", "z.glb")) },
            SceneEdit { it.add(EntityType.OBJECT); true },
        )
        assertFalse(badScene)
        assertEquals(metaText, text(meta))
        assertEquals("{ not json", text(broken))
    }

    fun testASceneThatDoesNotChangeIsLeftAlone() {
        val (meta, scene) = files()
        assertTrue(
            editAssetMetaAndScene(project, meta, scene, "Pivot", { TextEditOutcome.Edited(metaText.replace("m.glb", "q.glb")) }, SceneEdit { false })
        )
        assertEquals(sceneText, text(scene))
        assertTrue(text(meta).contains("q.glb"))
    }
}
