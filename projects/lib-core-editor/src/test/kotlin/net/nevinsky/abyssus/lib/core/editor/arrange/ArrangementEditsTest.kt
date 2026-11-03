/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.GroundHeights
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.terrainData
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class ArrangementEditsTest {
    private val edits = ArrangementEdits(ResourceEditorMessages())
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val tree = BaseMeta(MetaType.MODEL, "tree")
    private val project = testProject("Untitled")
    private val terrain = terrainData(project, "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
    private val heights =
        GroundHeights(listOf(TerrainTarget(1L, terrain, Matrix4().setToTranslation(-38.25267f, 0f, -32.7754f))))

    private fun mainScene() = parseScene(File(project, "scenes/Main Scene.scene").readText())

    private fun arrangement(scene: SceneDto, id: Long = 11) = scene.component(id, PathArrangementComponent::class.java)!!
    private fun slots(scene: SceneDto, id: Long = 11) = arrangement(scene, id).slots.toList()
    private fun name(scene: SceneDto, id: Long) = scene.component(id, NameComponent::class.java)!!.name
    private fun render(scene: SceneDto, id: Long) = scene.component(id, RenderComponent::class.java)!!
    private fun position(scene: SceneDto, id: Long) = scene.component(id, PositionComponent::class.java)!!.localPosition
    private fun y(scene: SceneDto, id: Long) = position(scene, id).y
    private fun x(scene: SceneDto, id: Long) = position(scene, id).x

    /** Everything the scene holds, as text, to compare "nothing changed". */
    private fun snapshot(scene: SceneDto): Map<Long, List<String>> = scene.ecs.mapValues { (_, e) ->
        e.components.map {
            when (it) {
                is HeaderComponent -> "H ${it.id} ${it.type} ${it.entityEnabled} ${it.flags}"
                else -> it.javaClass.simpleName + try { json.componentValues(it) } catch (e: Exception) { it.toString() }
            }
        }
    }

    private fun point(x: Float, z: Float) = Vector3(x, heights.heightAt(x, z, 0f), z)

    /** A fresh line of trees from (0, 0) to (40, 0) written to Main Scene: arrangement 11, copies 12 to 16. */
    private fun drawn(): SceneDto {
        val scene = mainScene()
        val result = edits.create(scene, tree, listOf(point(0f, 0f), point(40f, 0f)), false, heights)
        assertEquals(ArrangementResult.Changed(11L), result)
        return scene
    }

    /** Applies [change] to the arrangement's component, then regenerates the way a settings edit does. */
    private fun edit(
        scene: SceneDto,
        id: Long = 11,
        refillAware: Boolean = true,
        change: (PathArrangementComponent) -> Unit,
    ): ArrangementResult {
        val component = arrangement(scene, id)
        val before = arrangementAlong(component.points, component)
        change(component)
        return edits.regenerate(scene, id, if (refillAware) before else null, heights)
    }

    private fun setPoints(component: PathArrangementComponent, vararg points: Vector3) {
        component.points = points.map { Vector3(it) }.toMutableList()
    }

    @Test
    fun aLineOfTreesInMainScene() {
        val before = snapshot(mainScene())
        val scene = drawn()
        for (id in 0L..10L) assertEquals(before.getValue(id), snapshot(scene).getValue(id))
        assertEquals("Path 11", name(scene, 11))
        assertEquals(EntityType.GROUP, scene.entity(11)!!.header().type)
        val c = arrangement(scene)
        assertEquals(2, c.points.size)
        assertFalse(c.closed)
        assertEquals("tree", c.asset.name)
        assertEquals(10f, c.spacing, 0f)
        assertEquals(listOf(12, 13, 14, 15, 16), c.slots)
        assertEquals(17, scene.ecs.size)
    }

    @Test
    fun aCopyHoldsANameATypeAPositionAModelAndItsParent() {
        val scene = drawn()
        assertEquals("Path 11 #0", name(scene, 12))
        assertEquals(EntityType.OBJECT, scene.entity(12)!!.header().type)
        assertEquals("tree", render(scene, 12).assetName)
        assertEquals(MetaType.MODEL, render(scene, 12).type)
        assertEquals("defaultShader", render(scene, 12).shaderKey)
        assertEquals(11, scene.component(12, ParentComponent::class.java)!!.parentEntityId)
        assertNotNull(scene.component(12, PositionComponent::class.java))
    }

    @Test
    fun copiesStandOnTheTerrain() {
        val scene = drawn()
        assertEquals(heights.terrainHeight(20f, 0f)!!, y(scene, 14), 1e-4f)
        assertEquals(20f, x(scene, 14), 1e-4f)
        assertEquals(terrain.heightAt(20f + 38.25267f, 32.7754f)!!, y(scene, 14), 1e-3f)
    }

    @Test
    fun aTooLongLineIsRefusedAndWritesNothing() {
        val scene = mainScene()
        val before = snapshot(scene)
        val result = edits.create(scene, tree, listOf(Vector3(0f, 0f, 0f), Vector3(20000f, 0f, 0f)), false, heights)
        assertTrue(result is ArrangementResult.Rejected)
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun draggingTheEndPointToSixtyAddsTwoCopies() {
        val scene = drawn()
        val result = edit(scene) { setPoints(it, point(0f, 0f), point(60f, 0f)) }
        assertEquals(ArrangementResult.Changed(), result)
        assertEquals(listOf(12, 13, 14, 15, 16, 17, 18), slots(scene))
        assertEquals(50f, x(scene, 17), 1e-4f)
        assertEquals(60f, x(scene, 18), 1e-4f)
        assertEquals("Path 11 #5", name(scene, 17))
    }

    @Test
    fun shorteningTheLineRemovesTheLastCopies() {
        val scene = drawn()
        edit(scene) { setPoints(it, point(0f, 0f), point(20f, 0f)) }
        assertEquals(listOf(12, 13, 14), slots(scene))
        assertNull(scene.entity(15))
        assertNull(scene.entity(16))
        assertNotNull(scene.entity(14))
    }

    @Test
    fun changingTheModelChangesEveryCopy() {
        val scene = drawn()
        edit(scene) { it.asset = BaseMeta(MetaType.MODEL, "model_900f6f61-6384-434a-be81-56ce303fbb56") }
        for (id in 12L..16L) {
            assertEquals("model_900f6f61-6384-434a-be81-56ce303fbb56", render(scene, id).assetName)
            assertEquals("Path 11 #${id - 12}", name(scene, id))
        }
        assertEquals(listOf(12, 13, 14, 15, 16), slots(scene))
    }

    @Test
    fun aCopyKeepsAGameComponent() {
        class Plane : Component {
            var speed = 3
        }
        val scene = drawn()
        scene.entity(12)!!.add(Plane())
        edit(scene) { setPoints(it, point(0f, 0f), point(60f, 0f)) }
        assertEquals(3, scene.component(12, Plane::class.java)!!.speed)
    }

    @Test
    fun aDeletedCopyStaysEmpty() {
        val scene = drawn()
        scene.ecs.remove(15L)
        edit(scene) { setPoints(it, point(0f, 0f), point(60f, 0f)) }
        assertEquals(listOf(12, 13, 14, -1, 16, 17, 18), slots(scene))
        assertNull(scene.entity(15))
    }

    @Test
    fun aSpacingChangeRefillsEmptySlotsAndLeavesADetachedCopyAlone() {
        val scene = drawn()
        assertTrue(edits.detach(scene, 14L))
        val detached = snapshot(scene).getValue(14L)
        edit(scene) { it.spacing = 5f }
        assertEquals(9, slots(scene).size)
        assertTrue(slots(scene).none { it == -1 })
        assertEquals(detached, snapshot(scene).getValue(14L))
        assertFalse(14 in slots(scene))
    }

    @Test
    fun movingTheLastPointKeepsAnEmptyFirstSlotEmpty() {
        val scene = drawn()
        scene.ecs.remove(12L)
        edit(scene) { setPoints(it, point(0f, 0f), point(60f, 0f)) }
        assertEquals(-1, slots(scene)[0])
        assertEquals(7, slots(scene).size)
    }

    @Test
    fun movingTheFirstPointRefillsEmptySlots() {
        val scene = drawn()
        scene.ecs.remove(12L)
        edit(scene) { setPoints(it, point(2f, 0f), point(40f, 0f)) }
        assertTrue(slots(scene).none { it == -1 })
    }

    @Test
    fun regeneratingWithoutAChangeWritesNothing() {
        val scene = drawn()
        val before = snapshot(scene)
        val result = edits.regenerate(scene, 11L, null, heights)
        assertEquals(ArrangementResult.Unchanged, result)
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun regenerateReplacesOnlyTheHeightsWhenTheGroundChanged() {
        val scene = drawn()
        val flat = GroundHeight { _, _, _ -> 123f }
        assertEquals(ArrangementResult.Changed(), edits.regenerate(scene, 11L, null, flat))
        for (id in 12L..16L) assertEquals(123f, y(scene, id), 0f)
        assertEquals(20f, x(scene, 14), 1e-4f)
        assertEquals(listOf(12, 13, 14, 15, 16), slots(scene))
    }

    @Test
    fun emptySlotsAreWrittenAsMinusOne() {
        val scene = drawn()
        scene.entity(13)!!.components.removeAll { it is ParentComponent }
        edits.regenerate(scene, 11L, null, heights)
        assertEquals(listOf(12, -1, 14, 15, 16), slots(scene))
    }

    @Test
    fun anUnusableArrangementIsRefusedAndLeftAlone() {
        val scene = drawn()
        arrangement(scene).spacing = 0f
        val before = snapshot(scene)
        assertTrue(edits.regenerate(scene, 11L, null, heights) is ArrangementResult.Rejected)
        assertEquals(before, snapshot(scene))
        arrangement(scene).also { it.spacing = 1f; setPoints(it, point(0f, 0f), point(5000f, 0f)) }
        val before2 = snapshot(scene)
        assertTrue(edits.regenerate(scene, 11L, null, heights) is ArrangementResult.Rejected)
        assertEquals(before2, snapshot(scene))
    }

    @Test
    fun detachRemovesTheParentAndEmptiesTheSlot() {
        val scene = drawn()
        assertTrue(edits.detach(scene, 14L))
        assertNull(scene.component(14, ParentComponent::class.java))
        assertEquals(listOf(12, 13, -1, 15, 16), slots(scene))
        assertFalse(edits.detach(scene, 14L))
        assertFalse(edits.detach(scene, 0L))
        assertFalse(edits.detach(scene, 99L))
    }

    @Test
    fun setPointsMovesTheEndAndRegeneratesInTheSameEdit() {
        val scene = drawn()
        val result = edits.setPoints(scene, 11L, listOf(point(0f, 0f), point(60f, 0f)), heights)
        assertEquals(ArrangementResult.Changed(), result)
        assertEquals(listOf(12, 13, 14, 15, 16, 17, 18), slots(scene))
        assertEquals(60f, arrangement(scene).points[1].x, 0f)
    }

    @Test
    fun setPointsInsertsAndRemovesAPoint() {
        val scene = drawn()
        edits.setPoints(scene, 11L, listOf(point(0f, 0f), point(20f, 20f), point(40f, 0f)), heights)
        assertEquals(3, arrangement(scene).points.size)
        assertTrue(slots(scene).size > 5)
        edits.setPoints(scene, 11L, listOf(point(0f, 0f), point(40f, 0f)), heights)
        assertEquals(2, arrangement(scene).points.size)
        assertEquals(5, slots(scene).size)
    }

    @Test
    fun setPointsRefusesOnePointAndTooManyCopies() {
        val scene = drawn()
        val before = snapshot(scene)
        assertTrue(edits.setPoints(scene, 11L, listOf(point(0f, 0f)), heights) is ArrangementResult.Rejected)
        assertTrue(
            edits.setPoints(scene, 11L, listOf(point(0f, 0f), point(50000f, 0f)), heights) is ArrangementResult.Rejected,
        )
        assertTrue(
            edits.setPoints(scene, 99L, listOf(point(0f, 0f), point(5f, 0f)), heights) is ArrangementResult.Rejected,
        )
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun setPointsKeepsThePointsThatDidNotMove() {
        val scene = drawn()
        val first = arrangement(scene).points[0]
        edits.setPoints(scene, 11L, listOf(Vector3(first.x, first.y, first.z), point(60f, 0f)), heights)
        assertEquals(first.y, arrangement(scene).points[0].y, 0f)
    }
}
