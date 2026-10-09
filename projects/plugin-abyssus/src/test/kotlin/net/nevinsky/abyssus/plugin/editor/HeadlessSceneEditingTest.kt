/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEdit
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import net.nevinsky.abyssus.lib.core.editor.meta.FieldValue
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTransformWriter
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.projectView.ComponentTarget
import net.nevinsky.abyssus.plugin.properties.AssetEditResult
import net.nevinsky.abyssus.plugin.properties.AssetMetaEdits
import net.nevinsky.abyssus.plugin.properties.PanelState
import net.nevinsky.abyssus.plugin.properties.readEntityState
import net.nevinsky.abyssus.plugin.testCore
import net.nevinsky.abyssus.plugin.testPanelServices
import java.io.File

/**
 * Spec `headless-scene-editing`: for the same input text, the editing library without the IDE gives exactly what the
 * plugin writes, and the same refusal with the same reason.
 */
class HeadlessSceneEditingTest : BasePlatformTestCase() {
    private val sceneText = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
    private val terrainMeta =
        File("src/test/testData/project/Untitled/assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/meta.json").readText()
    private val headless get() = HeadlessEditing(EditorBundle)

    private fun textOf(file: VirtualFile) = FileDocumentManager.getInstance().getDocument(file)!!.text

    fun testMovingAnEntityWritesWhatTheGizmoDragWrites() {
        val file = myFixture.addFileToProject("p/scenes/Main Scene.scene", sceneText).virtualFile
        val position = SceneJson().parse(sceneText)["ecs"]["0"]["components"]["PositionComponent"]["localPosition"]
        val edit = TransformEdit(position = Vec3(1f, position["y"].floatValue(), position["z"].floatValue()))
        // what SceneFileEditor.applyTransform runs for a drag of Model 0
        assertTrue(editSceneJson(project, file, "Move Entity") { root -> SceneTransformWriter().apply(root, "0", edit) })
        assertEquals(HeadlessEdit.Edited(textOf(file)), headless.transform(sceneText, "0", edit))
    }

    fun testALegacySceneIsRefusedWithTheReasonThePluginShows() {
        val legacy = sceneText.replaceFirst("\"ecs\": {", "\"ecs\": {\n    \"componentIdentifiers\": {},")
        val file = myFixture.addFileToProject("p/scenes/Legacy.scene", legacy).virtualFile
        val refusal = headless.validate(legacy, DocumentKind.SCENE)!!
        val shown = readEntityState(ComponentTarget(file, "0", null), testPanelServices(project)) as PanelState.Empty
        assertEquals(AbyssusBundle.message("propertiesSceneUnreadable", refusal.message), shown.message)
        assertFalse(editSceneJson(project, file, "Move Entity") { root ->
            SceneTransformWriter().apply(root, "0", TransformEdit(position = Vec3(1f, 0f, 0f)))
        })
        assertEquals(legacy, textOf(file))
        assertEquals(HeadlessEdit.Refused(refusal), headless.transform(legacy, "0", TransformEdit(position = Vec3(1f, 0f, 0f))))
    }

    fun testAnAssetPropertyEditWritesWhatThePanelWrites() {
        val meta = myFixture.addFileToProject("p/assets/terrain/meta.json", terrainMeta).virtualFile
        assertEquals(
            AssetEditResult.Changed,
            AssetMetaEdits.update(project, meta.parent, "size", FieldValue.Int(1600), FieldValue.Int(2048), testCore.assetEditor),
        )
        assertEquals(
            HeadlessEdit.Edited(textOf(meta)),
            headless.setAssetProperty(terrainMeta, "size", FieldValue.Int(1600), FieldValue.Int(2048)),
        )
        val written = SceneJson().parse(textOf(meta))
        val original = SceneJson().parse(terrainMeta)
        for (key in listOf("version", "uuid", "type", "lastModified")) assertEquals(key, original[key], written[key])
        assertEquals(2048, written["additional"]["size"].intValue())
    }
}
