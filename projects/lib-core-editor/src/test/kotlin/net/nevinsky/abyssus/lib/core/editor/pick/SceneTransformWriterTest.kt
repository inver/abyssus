/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * Transform write-back on the scene DTO. Cases that pinned the spelling of numbers and the order of keys in the saved
 * text (`1.230000` staying as written, a rotation appended after the position) move to Phase 6 with the serializer.
 */
class SceneTransformWriterTest {
    private val text = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val writer = SceneTransformWriter()

    private fun main(): SceneDto = parseScene(text)

    private fun local(scene: SceneDto, id: Long) = scene.component(id, PositionComponent::class.java)!!.localPosition

    /** Everything the scene holds, as text, to compare "nothing changed". */
    private fun snapshot(scene: SceneDto): Map<Long, List<String>> = scene.ecs.mapValues { (_, e) ->
        e.components.map {
            when (it) {
                is HeaderComponent -> "H ${it.id} ${it.type} ${it.entityEnabled} ${it.flags}"
                is CameraComponent -> "Camera ${it.camera.position} ${it.camera.direction}"
                else -> it.javaClass.simpleName + try { json.componentValues(it) } catch (e: Exception) { it.toString() }
            }
        }
    }

    @Test
    fun droppingAModelChangesOnlyY() {
        val scene = sceneOf(entityOf(0, EntityType.OBJECT, PositionComponent(1.23f, 5f, -2.34f)))
        assertTrue(writer.apply(scene, 0L, TransformEdit(position = Vector3(1.23f, 1f, -2.34f))))
        assertEquals(Vector3(1.23f, 1f, -2.34f), local(scene, 0))
    }

    @Test
    fun droppingACameraChangesBothYsAndNothingElse() {
        val scene = main()
        val before = snapshot(main())
        val camera = scene.component(4, CameraComponent::class.java)!!.camera
        assertEquals(local(scene, 4), camera.position)
        val p = Vector3(local(scene, 4)).also { it.y -= 5f }

        assertTrue(writer.apply(scene, 4L, TransformEdit(position = p)))

        assertEquals(p.y, local(scene, 4).y, 0f)
        assertEquals(p.y, camera.position.y, 0f)
        // restoring the two changed values reproduces every other value
        p.y += 5f
        writer.apply(scene, 4L, TransformEdit(position = p))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun movingAnEntityChangesOnlyItsLocalPositionX() {
        val scene = main()
        val old = Vector3(local(scene, 0))
        val before = snapshot(main())

        assertTrue(writer.apply(scene, 0L, TransformEdit(position = Vector3(-3.035308f + 2f, old.y, old.z))))

        assertEquals(-1.035308f, local(scene, 0).x, 1e-5f)
        assertEquals(old.y, local(scene, 0).y, 0f)
        assertEquals(old.z, local(scene, 0).z, 0f)
        local(scene, 0).x = old.x
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun movingACameraChangesBothPositions() {
        val scene = main()
        val p = local(scene, 4)
        val moved = Vector3(p.x, p.y + 1f, p.z)
        assertTrue(writer.apply(scene, 4L, TransformEdit(position = moved)))
        assertEquals(moved.y, local(scene, 4).y, 1e-5f)
        assertEquals(moved.y, scene.component(4, CameraComponent::class.java)!!.camera.position.y, 1e-5f)
        assertEquals(12.318308f + 1f, moved.y, 1e-5f)
    }

    @Test
    fun rotatingACameraWritesItsViewPoint() {
        val scene = main()
        val dir = Vector3(1f, 0f, 0f)
        assertTrue(writer.apply(scene, 4L, TransformEdit(rotation = Quaternion(0f, 0.7071f, 0f, 0.7071f), direction = dir)))
        assertEquals(1f, scene.component(4, CameraComponent::class.java)!!.camera.direction.x, 0f)
        assertEquals(0.7071f, scene.component(4, PositionComponent::class.java)!!.localRotation.y, 0f)
    }

    @Test
    fun rotatingAnEntityWithoutALocalRotationSetsIt() {
        val scene = main()
        assertEquals(Quaternion(), scene.component(0, PositionComponent::class.java)!!.localRotation)
        assertTrue(writer.apply(scene, 0L, TransformEdit(rotation = Quaternion(0f, 0.7071f, 0f, 0.7071f))))
        val q = scene.component(0, PositionComponent::class.java)!!.localRotation
        assertEquals(0.7071f, q.y, 0f)
        assertEquals(0.7071f, q.w, 0f)
    }

    @Test
    fun anEntityWithoutAPositionComponentGetsOne() {
        val scene = sceneOf(entityOf(1, EntityType.OBJECT, NameComponent("a")))
        assertTrue(writer.apply(scene, 1L, TransformEdit(position = Vector3(1f, 0f, 0f))))
        assertEquals(1f, local(scene, 1).x, 0f)
    }

    @Test
    fun anUnknownEntityReturnsFalse() {
        val scene = main()
        val before = snapshot(scene)
        assertFalse(writer.apply(scene, 99L, TransformEdit(position = Vector3(1f, 2f, 3f))))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun anEditEqualToTheCurrentValuesReturnsFalse() {
        val scene = main()
        val before = snapshot(scene)
        assertFalse(writer.apply(scene, 0L, TransformEdit(position = Vector3(local(scene, 0)))))
        assertFalse(writer.apply(scene, 0L, TransformEdit(rotation = Quaternion())))
        assertEquals(before, snapshot(scene))
    }

    private val lights get() = parseScene(File("src/test/testData/project/Lights/scenes/Abyssus Lights.scene").readText())

    @Test
    fun aHandleMoveWritesTheHandlePosition() {
        val scene = lights
        val lightBefore = snapshot(scene).getValue(1L)
        assertTrue(writer.apply(scene, 1L, TransformEdit(target = TargetMove(0L, Vector3(0f, 10f, -10f)))))

        assertEquals(Vector3(0f, 10f, -10f), local(scene, 0))
        // the light itself is unchanged, and so is every other entity
        assertEquals(lightBefore, snapshot(scene).getValue(1L))
        assertEquals(0, scene.component(1, PositionComponent::class.java)!!.lookAtId)
        assertEquals(Quaternion(), scene.component(1, PositionComponent::class.java)!!.localRotation)
        assertNotNull(scene.component(1, LightComponent::class.java))
    }

    @Test
    fun aHandleMoveToTheSamePlaceChangesNothing() {
        val scene = lights
        val same = Vector3(0f, 10f, -10f)
        assertTrue(writer.apply(scene, 1L, TransformEdit(target = TargetMove(0L, same))))
        val after = snapshot(scene)
        assertFalse(writer.apply(scene, 1L, TransformEdit(target = TargetMove(0L, same))))
        assertEquals(after, snapshot(scene))
    }

    @Test
    fun aHandleMoveToAMissingEntityLeavesTheSceneAsItWas() {
        val scene = lights
        val before = snapshot(scene)
        val edit = TransformEdit(rotation = Quaternion(0f, 1f, 0f, 0f), target = TargetMove(99L, Vector3(0f, 10f, -10f)))
        assertFalse(writer.apply(scene, 1L, edit))
        assertEquals(before, snapshot(scene))
    }

    /** Main Scene with a line of trees: arrangement 11 and copies 12 to 16. */
    private fun arranged(): SceneDto {
        val scene = main()
        val points = listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f))
        ArrangementEdits(ResourceEditorMessages()).create(scene, BaseMeta(MetaType.MODEL, "tree"), points, false, LineHeights())
        return scene
    }

    private fun slots(scene: SceneDto) = scene.component(11, PathArrangementComponent::class.java)!!.slots.toList()

    @Test
    fun movingAnArrangedCopyDetachesIt() {
        val scene = arranged()
        assertTrue(writer.apply(scene, 14L, TransformEdit(position = Vector3(20f, 0f, 4f))))
        assertNull(scene.component(14, ParentComponent::class.java))
        assertEquals(4f, local(scene, 14).z, 0f)
        assertEquals(listOf(12, 13, -1, 15, 16), slots(scene))
    }

    @Test
    fun turningAnArrangedCopyDetachesIt() {
        val scene = arranged()
        assertTrue(writer.apply(scene, 13L, TransformEdit(rotation = Quaternion(0f, 1f, 0f, 0f))))
        assertNull(scene.component(13, ParentComponent::class.java))
        assertEquals(listOf(12, -1, 14, 15, 16), slots(scene))
    }

    @Test
    fun anEditThatChangesNothingKeepsTheCopyLinked() {
        val scene = arranged()
        assertFalse(writer.apply(scene, 14L, TransformEdit(position = Vector3(local(scene, 14)))))
        assertEquals(11, scene.component(14, ParentComponent::class.java)!!.parentEntityId)
        assertEquals(listOf(12, 13, 14, 15, 16), slots(scene))
    }
}
