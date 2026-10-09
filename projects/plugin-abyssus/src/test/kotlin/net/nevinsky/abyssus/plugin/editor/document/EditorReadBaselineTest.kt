package net.nevinsky.abyssus.lib.gdx.editor.document

import net.nevinsky.abyssus.lib.gdx.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.gdx.editor.scene.sceneContentOf
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import net.nevinsky.abyssus.lib.gdx.editor.parseScene
import net.nevinsky.abyssus.plugin.projectView.ComponentTarget
import net.nevinsky.abyssus.plugin.projectView.ecsRows
import net.nevinsky.abyssus.plugin.projectView.entityRows
import net.nevinsky.abyssus.plugin.properties.PanelState
import net.nevinsky.abyssus.plugin.properties.readEntityState
import net.nevinsky.abyssus.lib.gdx.editor.scene.CameraParams
import net.nevinsky.abyssus.plugin.testPanelServices
import java.io.File

/** Frozen consumer outputs before introducing SceneDocument, using a copy of the committed fixture text. */
class EditorReadBaselineTest : BasePlatformTestCase() {
    private val text get() = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    fun testContentAndRayCaptureInputs() {
        val scene = parseScene(text)
        val content = sceneContentOf(scene)
        assertEquals(listOf("0", "2", "6", "9"), content.models.map { it.entityId })
        assertEquals(listOf("1"), content.terrains.map { it.entityId })
        assertEquals(listOf("7", "8", "10"), content.lights.map { it.entityId })
        assertEquals(listOf("4"), content.cameras.map { it.entityId })
        assertEquals(setOf("3"), content.handleIds)
        assertEquals("skybox_physical", content.skybox)
        assertEquals(Vec3(-3.035308f, .9123962f, -3.2570944f), content.models.first().transform.position)
        val params = renderParamsOf(scene, CameraParams.DEFAULT)
        assertEquals(content, params.content)
        assertEquals(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER).readObject(text).get("ecs"), params.ecs)
        assertEquals(CameraParams.DEFAULT, params.camera)
        assertNotNull(params.rayTracing.settings)
    }

    fun testTreeRowsAndPanelState() {
        val root = SceneJson().parse(text)
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
