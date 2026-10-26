/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
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
        GroundHeights(listOf(TerrainTarget("1", terrain, Matrix4().setToTranslation(-38.25267f, 0f, -32.7754f))))

    private fun mainScene() = SceneJson().parse(File(project, "scenes/Main Scene.scene").readText())

    private fun entity(root: JsonNode, id: Int) = root["ecs"].let { (it["entities"] ?: it)[id.toString()] }
    private fun components(root: JsonNode, id: Int) = entity(root, id)?.get("components") as ObjectNode?
    private fun arrangement(root: JsonNode, id: Int = 11) =
        components(root, id)!![PathArrangementComponent::class.simpleName] as ObjectNode

    private fun slots(root: JsonNode, id: Int = 11) = arrangement(root, id)["slots"].map { it.asInt() }
    private fun component(root: JsonNode, id: Int = 11) =
        json.bindComponent(arrangement(root, id), PathArrangementComponent::class.java)

    private fun y(root: JsonNode, id: Int) =
        components(root, id)!!["PositionComponent"]["localPosition"].path("y").floatValue()

    private fun x(root: JsonNode, id: Int) =
        components(root, id)!!["PositionComponent"]["localPosition"].path("x").floatValue()

    private fun z(root: JsonNode, id: Int) =
        components(root, id)!!["PositionComponent"]["localPosition"].path("z").floatValue()

    private fun point(x: Float, z: Float) = Vector3(x, heights.heightAt(x, z, 0f), z)

    /** A fresh line of trees from (0, 0) to (40, 0) written to Main Scene: arrangement 11, copies 12 to 16. */
    private fun drawn(): ObjectNode {
        val root = mainScene() as ObjectNode
        val result = edits.create(root, tree, listOf(point(0f, 0f), point(40f, 0f)), false, heights)
        assertEquals(ArrangementResult.Changed("11"), result)
        return root
    }

    /** Applies [change] to the arrangement's component text, then regenerates the way a settings edit does. */
    private fun edit(
        root: JsonNode,
        id: Int = 11,
        refillAware: Boolean = true,
        change: (PathArrangementComponent, ObjectNode) -> Unit
    ): ArrangementResult {
        val before = component(root, id)
        val node = arrangement(root, id)
        change(component(root, id), node)
        return edits.regenerate(root, id.toString(), if (refillAware) before else null, heights)
    }

    private fun setPoints(node: ObjectNode, vararg points: Vector3) {
        val array = node.putArray("points")
        for (p in points) array.addObject().put("x", p.x).put("y", p.y).put("z", p.z)
    }

    @Test
    fun aLineOfTreesInMainScene() {
        val before = mainScene()
        val root = drawn()
        for (id in 0..10) assertEquals(entity(before, id).toString(), entity(root, id).toString())
        val path = components(root, 11)!!
        assertEquals("Path 11", path["NameComponent"]["name"].asText())
        assertEquals("GROUP", path["TypeComponent"]["type"].asText())
        val c = component(root)
        assertEquals(2, c.points.size)
        assertFalse(c.closed)
        assertEquals("tree", c.asset.name)
        assertEquals(10f, c.spacing, 0f)
        assertEquals(listOf(12, 13, 14, 15, 16), c.slots)
        assertEquals(17, (root["ecs"].let { it["entities"] ?: it }).size())
    }

    @Test
    fun aCopyHoldsANameATypeAPositionAModelAndItsParent() {
        val root = drawn()
        val copy = components(root, 12)!!
        assertEquals("Path 11 #0", copy["NameComponent"]["name"].asText())
        assertEquals("OBJECT", copy["TypeComponent"]["type"].asText())
        assertEquals("tree", copy["RenderComponent"]["assetName"].asText())
        assertEquals("MODEL", copy["RenderComponent"]["type"].asText())
        assertEquals("defaultShader", copy["RenderComponent"]["shaderKey"].asText())
        assertEquals(11, copy["ParentComponent"]["parentEntityId"].asInt())
        assertNotNull(copy["PositionComponent"])
    }

    @Test
    fun copiesStandOnTheTerrain() {
        val root = drawn()
        assertEquals(heights.terrainHeight(20f, 0f)!!, y(root, 14), 1e-4f)
        assertEquals(20f, x(root, 14), 1e-4f)
        assertEquals(terrain.heightAt(20f + 38.25267f, 32.7754f)!!, y(root, 14), 1e-3f)
    }

    @Test
    fun aNonNativeSceneIsRefused() {
        val root = SceneJson().parse("""{"ecs":{}}""")
        val result = edits.create(root, tree, listOf(point(0f, 0f), point(40f, 0f)), false, heights)
        assertTrue(result is ArrangementResult.Rejected)
        assertEquals("""{"ecs":{}}""", root.toString())
    }

    @Test
    fun aTooLongLineIsRefusedAndWritesNothing() {
        val root = mainScene()
        val before = root.toString()
        val result = edits.create(root, tree, listOf(Vector3(0f, 0f, 0f), Vector3(20000f, 0f, 0f)), false, heights)
        assertTrue(result is ArrangementResult.Rejected)
        assertEquals(before, root.toString())
    }

    @Test
    fun draggingTheEndPointToSixtyAddsTwoCopies() {
        val root = drawn()
        val result = edit(root) { _, node -> setPoints(node, point(0f, 0f), point(60f, 0f)) }
        assertEquals(ArrangementResult.Changed(), result)
        assertEquals(listOf(12, 13, 14, 15, 16, 17, 18), slots(root))
        assertEquals(50f, x(root, 17), 1e-4f)
        assertEquals(60f, x(root, 18), 1e-4f)
        assertEquals("Path 11 #5", components(root, 17)!!["NameComponent"]["name"].asText())
    }

    @Test
    fun shorteningTheLineRemovesTheLastCopies() {
        val root = drawn()
        edit(root) { _, node -> setPoints(node, point(0f, 0f), point(20f, 0f)) }
        assertEquals(listOf(12, 13, 14), slots(root))
        assertNull(entity(root, 15))
        assertNull(entity(root, 16))
        assertNotNull(entity(root, 14))
    }

    @Test
    fun changingTheModelChangesEveryCopy() {
        val root = drawn()
        edit(root) { _, node ->
            (node["asset"] as ObjectNode).put(
                "assetName",
                "model_900f6f61-6384-434a-be81-56ce303fbb56"
            )
        }
        for (id in 12..16) {
            assertEquals(
                "model_900f6f61-6384-434a-be81-56ce303fbb56",
                components(root, id)!!["RenderComponent"]["assetName"].asText()
            )
            assertEquals("Path 11 #${id - 12}", components(root, id)!!["NameComponent"]["name"].asText())
        }
        assertEquals(listOf(12, 13, 14, 15, 16), slots(root))
    }

    @Test
    fun aCopyKeepsAGameComponent() {
        val root = drawn()
        components(root, 12)!!.putObject("Plane").put("speed", 3)
        edit(root) { _, node -> setPoints(node, point(0f, 0f), point(60f, 0f)) }
        assertEquals(3, components(root, 12)!!["Plane"]["speed"].asInt())
    }

    @Test
    fun aDeletedCopyStaysEmpty() {
        val root = drawn()
        (root["ecs"].let { it["entities"] ?: it } as ObjectNode).remove("15")
        edit(root) { _, node -> setPoints(node, point(0f, 0f), point(60f, 0f)) }
        assertEquals(listOf(12, 13, 14, -1, 16, 17, 18), slots(root))
        assertNull(entity(root, 15))
    }

    @Test
    fun aSpacingChangeRefillsEmptySlotsAndLeavesADetachedCopyAlone() {
        val root = drawn()
        assertTrue(edits.detach(root, "14"))
        val detached = entity(root, 14).toString()
        edit(root) { _, node -> node.put("spacing", 5) }
        assertEquals(9, slots(root).size)
        assertTrue(slots(root).none { it == -1 })
        assertEquals(detached, entity(root, 14).toString())
        assertFalse(14 in slots(root))
    }

    @Test
    fun movingTheLastPointKeepsAnEmptyFirstSlotEmpty() {
        val root = drawn()
        (root["ecs"].let { it["entities"] ?: it } as ObjectNode).remove("12")
        edit(root) { _, node -> setPoints(node, point(0f, 0f), point(60f, 0f)) }
        assertEquals(-1, slots(root)[0])
        assertEquals(7, slots(root).size)
    }

    @Test
    fun movingTheFirstPointRefillsEmptySlots() {
        val root = drawn()
        (root["ecs"].let { it["entities"] ?: it } as ObjectNode).remove("12")
        edit(root) { _, node -> setPoints(node, point(2f, 0f), point(40f, 0f)) }
        assertTrue(slots(root).none { it == -1 })
    }

    @Test
    fun regeneratingWithoutAChangeWritesNothing() {
        val root = drawn()
        val text = root.toString()
        val result = edits.regenerate(root, "11", null, heights)
        assertEquals(ArrangementResult.Unchanged, result)
        assertEquals(text, root.toString())
    }

    @Test
    fun regenerateReplacesOnlyTheHeightsWhenTheGroundChanged() {
        val root = drawn()
        val flat = GroundHeight { _, _, _ -> 123f }
        assertEquals(ArrangementResult.Changed(), edits.regenerate(root, "11", null, flat))
        for (id in 12..16) assertEquals(123f, y(root, id), 0f)
        assertEquals(20f, x(root, 14), 1e-4f)
        assertEquals(listOf(12, 13, 14, 15, 16), slots(root))
    }

    @Test
    fun emptySlotsAreWrittenAsMinusOne() {
        val root = drawn()
        components(root, 13)!!.remove("ParentComponent")
        edits.regenerate(root, "11", null, heights)
        assertEquals(listOf(12, -1, 14, 15, 16), slots(root))
    }

    @Test
    fun anUnusableArrangementIsRefusedAndLeftAlone() {
        val root = drawn()
        arrangement(root).put("spacing", 0)
        val text = root.toString()
        assertTrue(edits.regenerate(root, "11", null, heights) is ArrangementResult.Rejected)
        assertEquals(text, root.toString())
        arrangement(root).put("spacing", 1).also { setPoints(it, point(0f, 0f), point(5000f, 0f)) }
        val text2 = root.toString()
        assertTrue(edits.regenerate(root, "11", null, heights) is ArrangementResult.Rejected)
        assertEquals(text2, root.toString())
    }

    @Test
    fun detachRemovesTheParentAndEmptiesTheSlot() {
        val root = drawn()
        assertTrue(edits.detach(root, "14"))
        assertNull(components(root, 14)!!["ParentComponent"])
        assertEquals(listOf(12, 13, -1, 15, 16), slots(root))
        assertFalse(edits.detach(root, "14"))
        assertFalse(edits.detach(root, "0"))
        assertFalse(edits.detach(root, "99"))
    }

    @Test
    fun setPointsMovesTheEndAndRegeneratesInTheSameEdit() {
        val root = drawn()
        val result = edits.setPoints(root, "11", listOf(point(0f, 0f), point(60f, 0f)), heights)
        assertEquals(ArrangementResult.Changed(), result)
        assertEquals(listOf(12, 13, 14, 15, 16, 17, 18), slots(root))
        assertEquals(60f, component(root).points[1].x, 0f)
    }

    @Test
    fun setPointsInsertsAndRemovesAPoint() {
        val root = drawn()
        edits.setPoints(root, "11", listOf(point(0f, 0f), point(20f, 20f), point(40f, 0f)), heights)
        assertEquals(3, component(root).points.size)
        assertTrue(slots(root).size > 5)
        edits.setPoints(root, "11", listOf(point(0f, 0f), point(40f, 0f)), heights)
        assertEquals(2, component(root).points.size)
        assertEquals(5, slots(root).size)
    }

    @Test
    fun setPointsRefusesOnePointAndTooManyCopies() {
        val root = drawn()
        val text = root.toString()
        assertTrue(edits.setPoints(root, "11", listOf(point(0f, 0f)), heights) is ArrangementResult.Rejected)
        assertTrue(
            edits.setPoints(
                root,
                "11",
                listOf(point(0f, 0f), point(50000f, 0f)),
                heights
            ) is ArrangementResult.Rejected
        )
        assertTrue(
            edits.setPoints(
                root,
                "99",
                listOf(point(0f, 0f), point(5f, 0f)),
                heights
            ) is ArrangementResult.Rejected
        )
        assertEquals(text, root.toString())
    }

    @Test
    fun setPointsKeepsTheTextOfPointsThatDidNotMove() {
        val root = drawn()
        val first = component(root).points[0]
        edits.setPoints(root, "11", listOf(Vector3(first.x, first.y, first.z), point(60f, 0f)), heights)
        assertEquals(first.y, component(root).points[0].y, 0f)
    }
}
