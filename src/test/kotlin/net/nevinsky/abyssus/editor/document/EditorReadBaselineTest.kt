package net.nevinsky.abyssus.editor.document

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.parseScene
import net.nevinsky.abyssus.projectView.ComponentTarget
import net.nevinsky.abyssus.projectView.ecsRows
import net.nevinsky.abyssus.projectView.entityRows
import net.nevinsky.abyssus.properties.PanelState
import net.nevinsky.abyssus.properties.readEntityState
import net.nevinsky.abyssus.sceneview.CameraParams
import net.nevinsky.abyssus.sceneview.SceneContent
import net.nevinsky.abyssus.sceneview.SceneRenderParams
import net.nevinsky.abyssus.testPanelServices
import java.io.File

/** Frozen consumer outputs before introducing SceneDocument, using a copy of the committed fixture text. */
class EditorReadBaselineTest : BasePlatformTestCase() {
    private val text get() = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    fun testContentAndRayCaptureInputs() {
        val scene = parseScene(text)
        val content = SceneContent.of(scene)
        assertEquals(listOf("0", "2", "6", "9"), content.models.map { it.entityId })
        assertEquals(listOf("1"), content.terrains.map { it.entityId })
        assertEquals(listOf("7", "8", "10"), content.lights.map { it.entityId })
        assertEquals(listOf("4"), content.cameras.map { it.entityId })
        assertEquals(setOf("3"), content.handleIds)
        assertEquals("skybox_physical", content.skybox)
        assertEquals(Vec3(-3.035308f, .9123962f, -3.2570944f), content.models.first().transform.position)
        val params = SceneRenderParams.from(scene, CameraParams.DEFAULT)
        assertEquals(content, params.content)
        assertEquals(net.nevinsky.abyssus.core.io.JsonProcessor().readObject(text).get("ecs"), params.ecs)
        assertEquals(CameraParams.DEFAULT, params.camera)
        assertNotNull(params.rayTracing.settings)
    }

    fun testTreeRowsAndPanelState() {
        val root = SceneJson.parse(text)
        assertEquals((0..10).map(Int::toString), ecsRows(root.get("ecs")).map { it.name })
        assertEquals(listOf("NameComponent", "PickableComponent", "PositionComponent", "RenderComponent", "TypeComponent"),
            entityRows(root.get("ecs").get("0")).map { it.name })
        val file = myFixture.addFileToProject("baseline/Main.scene", text).virtualFile
        val state = readEntityState(ComponentTarget(file, "0", null), testPanelServices(project)) as PanelState.EntityDetails
        assertEquals("Model 0", state.name)
        assertEquals(listOf("NameComponent", "PickableComponent", "PositionComponent", "RenderComponent", "TypeComponent"), state.sections.map { it.kind })
        assertEquals(listOf("ParentComponent", "CameraComponent", "LightComponent", "Point2PointPositionComponent"), state.addable)
        assertNotNull(state.sections.single { it.kind == "PickableComponent" }.raw)
    }
}
