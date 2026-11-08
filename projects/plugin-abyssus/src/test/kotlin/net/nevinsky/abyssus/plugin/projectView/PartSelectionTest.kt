/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPart
import net.nevinsky.abyssus.lib.core.editor.modelmesh.NO_PIVOT
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTarget
import net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState

/** OpenSpec `model-parts`, "Selecting a part": the tree row and the view's selection agree. */
class PartSelectionTest : BasePlatformTestCase() {
    fun testAPartRowStandsForItsEntityAndNode() {
        val file = myFixture.addFileToProject("a.scene", "{}").virtualFile
        val scene = parseScene("""{"formatVersion":1,"ecs":{"6":{"components":{}}}}""")
        val part = PartNode(scene, 6, "model", null, ModelPart("flap_L01_Cube.012", emptyList(), null, NO_PIVOT))
        val node = DtoEntryNode(project, "/p", DtoRow("flap_L01_Cube.012", part), file, emptyList())
        assertEquals(PartTarget(file, 6, "flap_L01_Cube.012", "model", null), partTargetOf(node))
        // a part row is not an entity row: the entity listener does not see it
        assertNull(componentTargetOf(node))
        assertNull(partTargetOf(DtoEntryNode(project, "/p", DtoRow("6", EntityNode(scene, 6)), file, emptyList())))
    }

    fun testSelectingAPartSelectsItsEntityAndAPlainSelectionClearsIt() {
        val state = SceneViewState()
        state.selectPart(6, "flap_L01_Cube.012")
        assertEquals(SceneTarget.Entity(6), state.selection)
        assertEquals("flap_L01_Cube.012", state.selectedPart)
        state.editPivot = true

        state.selectedId = 0
        assertNull(state.selectedPart)
        assertFalse(state.editPivot)
        assertEquals(0L, state.selectedId)

        state.selectPart(6, "x")
        state.selectedId = 6 // the same entity, plainly
        assertNull(state.selectedPart)
    }
}
