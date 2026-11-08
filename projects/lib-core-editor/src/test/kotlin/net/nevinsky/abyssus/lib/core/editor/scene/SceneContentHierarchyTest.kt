/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.hierarchy.modelNodeTransforms
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.DragResult
import net.nevinsky.abyssus.lib.core.editor.pick.ScenePreview
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.hierarchy.HierarchyProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `SceneContent` at world poses through parents (OpenSpec `scene-entity-hierarchy`). */
class SceneContentHierarchyTest {
    private fun untitled(): SceneDto =
        parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())

    private fun assertVec(expected: Vector3, actual: Vector3?) =
        assertTrue("expected $expected, got $actual", actual != null && expected.epsilonEquals(actual, 1e-4f))

    private fun dummy(id: Long, parent: Int, x: Float, y: Float, z: Float, node: String? = null): EntityDto =
        entityOf(id, EntityType.DUMMY, NameComponent("Dummy"), PositionComponent(x, y, z), ParentComponent(parent,
                node))

    private fun SceneDto.with(entity: EntityDto) = also { ecs[entity.header().id] = entity }

    @Test
    fun aDummyUnderModelNineIsAboveIt() {
        val content = sceneContentOf(untitled().with(dummy(20, 9, 0f, 1f, 0f)))
        val placed = content.dummy(20)!!
        assertVec(Vector3(0f, 22.632797f, 0f), placed.transform.position)
        assertEquals(9L, placed.parentId)
        assertEquals(9L, content.hierarchy.parents[20L])
        assertEquals(listOf(20L), content.hierarchy.childrenOf(9))
        // a dummy is no model, light or camera
        assertNull(content.model(20))
    }

    @Test
    fun theHandleAimedLightsOfAbyssusLightsShineDown() {
        val text = File("src/test/testData/project/Lights/scenes/Abyssus Lights.scene").readText()
        val c = sceneContentOf(parseScene(text))
        assertVec(Vector3(0f, 0f, 0f), c.entityPositions[0L])
        assertVec(Vector3(0f, 0f, 0f), c.entityPositions[3L])
        assertVec(Vector3(0f, -1f, 0f), c.light(1)!!.direction)
        assertVec(Vector3(0f, -1f, 0f), c.light(4)!!.direction)
    }

    @Test
    fun aRotatedParentTurnsItsChildsOffset() {
        val scene = sceneOf(
            entityOf(1, EntityType.OBJECT, PositionComponent().also { it.localRotation.set(Vector3.Y, 90f) }),
            dummy(2, 1, 1f, 0f, 0f),
        )
        assertVec(Vector3(0f, 0f, -1f), sceneContentOf(scene).dummy(2)!!.transform.position)
    }

    @Test
    fun aModelNodeFramesItsChildOnceTheModelIsRead() {
        val plane = entityOf(
            7, EntityType.OBJECT,
            PositionComponent(10f, 0f, 0f),
            RenderComponent("defaultShader", MetaType.MODEL, "model_trainer"),
        )
        val scene = sceneOf(plane, dummy(8, 7, 0f, 0f, 0f, "LeftWing"))
        // before the model is read the dummy sits on the plane
        assertVec(Vector3(10f, 0f, 0f), sceneContentOf(scene).dummy(8)!!.transform.position)
        val nodes = modelNodeTransforms(scene) { asset ->
            if (asset == "model_trainer") {
                mapOf("LeftWing" to Matrix4().setToTranslation(0f, 0f, -0.6f))
            } else {
                null
            }
        }
        val content = sceneContentOf(scene, nodes)
        assertVec(Vector3(10f, 0f, -0.6f), content.dummy(8)!!.transform.position)
        assertEquals("LeftWing", content.hierarchy.nodes[8L])
        // an unknown node: on the plane itself, with one warning naming the child and the node
        val unknown = sceneContentOf(sceneOf(plane, dummy(8, 7, 0f, 0f, 0f, "NoSuchNode")), nodes)
        assertVec(Vector3(10f, 0f, 0f), unknown.dummy(8)!!.transform.position)
        val warning = unknown.hierarchy.warnings.single()
        assertEquals(HierarchyProblem.UNKNOWN_NODE, warning.problem)
        val message = warning.message()
        assertTrue(message, message.contains("entity 8") && message.contains("NoSuchNode"))
    }

    @Test
    fun aDisabledParentHidesItsDummyWithoutChangingIt() {
        val scene = untitled().with(dummy(20, 9, 0f, 1f, 0f))
        scene.entity(9)!!.component(HeaderComponent::class.java)!!.entityEnabled = false
        val content = sceneContentOf(scene)
        assertNull(content.dummy(20))
        assertTrue(scene.entity(20)!!.component(HeaderComponent::class.java)!!.entityEnabled)
        assertTrue(20L in content.hierarchy.disabledByAncestor)
    }

    @Test
    fun aMissingParentIsARootWithAWarning() {
        val content = sceneContentOf(sceneOf(dummy(5, 99, 3f, 0f, 0f)))
        assertVec(Vector3(3f, 0f, 0f), content.dummy(5)!!.transform.position)
        assertEquals(HierarchyProblem.MISSING_PARENT, content.hierarchy.warnings.single().problem)
    }

    @Test
    fun aDraggedParentCarriesItsChildInThePreview() {
        val content = sceneContentOf(untitled().with(dummy(20, 9, 0f, 1f, 0f)))
        val model = content.model(9)!!.transform
        val moved = PlacementTransform(Vector3(model.position).add(2f, 0f, 0f), Quaternion(model.rotation), model.scale)
        val previewed = ScenePreview().apply(content, 9, DragResult(moved, null))
        assertVec(Vector3(2f, 22.632797f, 0f), previewed.dummy(20)!!.transform.position)
    }

    @Test
    fun movingACameraCarriesItsHandleAndKeepsItsAim() {
        val content = sceneContentOf(untitled())
        val camera = content.camera(4)!!
        val handleBefore = Vector3(content.entityPositions.getValue(3L))
        val up = Vector3(camera.position).add(0f, 1f, 0f)
        val moved = PlacementTransform(up, Quaternion(camera.rotation), Vector3(1f, 1f, 1f))
        val previewed = ScenePreview().apply(content, 4, DragResult(moved, null))
        assertVec(handleBefore.add(0f, 1f, 0f), previewed.entityPositions[3L])
    }
}
