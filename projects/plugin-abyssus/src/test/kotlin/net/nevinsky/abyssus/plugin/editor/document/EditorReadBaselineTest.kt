package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.plugin.projectView.ComponentTarget
import net.nevinsky.abyssus.plugin.projectView.ecsRows
import net.nevinsky.abyssus.plugin.projectView.entityRows
import net.nevinsky.abyssus.plugin.properties.PanelState
import net.nevinsky.abyssus.plugin.properties.readEntityState
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.plugin.testPanelServices
import java.io.File

/** Frozen consumer outputs before introducing SceneDocument, using a copy of the committed fixture text. */
class EditorReadBaselineTest : BasePlatformTestCase() {
    private val text get() = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    fun testContentAndRayCaptureInputs() {
        val scene = parseScene(text)
        val content = sceneContentOf(scene)
        assertEquals(listOf(0L, 2L, 6L, 9L), content.models.map { it.entityId })
        assertEquals(listOf(1L), content.terrains.map { it.entityId })
        assertEquals(listOf(7L, 8L, 10L), content.lights.map { it.entityId })
        assertEquals(listOf(4L), content.cameras.map { it.entityId })
        assertEquals(setOf(3L), content.handleIds)
        assertEquals("skybox_physical", content.skybox)
        assertEquals(Vector3(-3.035308f, .9123962f, -3.2570944f), content.models.first().transform.position)
        val params = renderParamsOf(scene, CameraParams.DEFAULT)
        assertEquals(content, params.content)
        assertEquals(scene.ecs, params.ecs)
        assertEquals(CameraParams.DEFAULT, params.camera)
    }

    fun testTreeRowsAndPanelState() {
        val root = parseScene(text)
        assertEquals((0..10).map(Int::toString), ecsRows(root).map { it.name })
        assertEquals(listOf("NameComponent", "PositionComponent", "RenderComponent", "PickableComponent"),
            entityRows(net.nevinsky.abyssus.plugin.projectView.EntityNode(root, 0)).map { it.name })
        val file = myFixture.addFileToProject("baseline/Main.scene", text).virtualFile
        val state = readEntityState(ComponentTarget(file, 0L, null), testPanelServices(project)) as PanelState.EntityDetails
        assertEquals("Model 0", state.name)
        assertEquals(listOf("NameComponent", "PositionComponent", "RenderComponent", "PickableComponent"), state.sections.map { it.kind })
        assertEquals(listOf("ParentComponent", "CameraComponent", "LightComponent"), state.addable)
        assertNotNull(state.sections.single { it.kind == "PickableComponent" }.raw)
    }
}
