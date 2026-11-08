/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.system.HierarchySystem
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DummiesTest {
    private val warnings = ArrayList<String>()
    private val dummies = Dummies(warnings::add)

    private fun engine(ecs: String) = SceneEngine().also {
        it.addSystem(HierarchySystem({ }))
        loadEcs(ecs, it)
        it.update(1f / 60)
    }

    private fun dummy(id: Int, parent: Int, name: String, x: Float, y: Float, z: Float) =
        """"$id": {"type": "DUMMY", "components": {"NameComponent": {"name": "$name"},
            "PositionComponent": {"localPosition": {"x": $x, "y": $y, "z": $z}},
            "ParentComponent": {"parentEntityId": $parent}}}"""

    private val plane =
        """"7": {"type": "OBJECT", "components": {"NameComponent": {"name": "Trainer"},
            "PositionComponent": {"localPosition": {"x": 10, "y": 1, "z": 0},
            "localRotation": {"x": 0, "y": 0.70710677, "z": 0, "w": 0.70710677}}}}"""

    @Test
    fun aLeadoutIsFoundWithItsPoseInThePlanesFrame() {
        val upEntry = dummy(20, 7, "leadoutUp", 0.48f, 0f, 0.02f)
        val downEntry = dummy(21, 7, "leadoutDown", 0.48f, 0f, -0.04f)
        val engine = engine("{$plane, $upEntry, $downEntry}")
        val up = dummies.find(engine, 7, "leadoutUp")!!
        assertEquals(20L, up.getComponent(HeaderComponent::class.java).id)
        val local = dummies.inParent(up, engine.ids[7]!!, Matrix4()).getTranslation(Vector3())
        assertTrue("$local", Vector3(0.48f, 0f, 0.02f).epsilonEquals(local, 1e-5f))
        // world: the plane's quarter turn about Y takes +X to -Z
        val world = dummies.world(up, Matrix4()).getTranslation(Vector3())
        assertTrue("$world", Vector3(10.02f, 1f, -0.48f).epsilonEquals(world, 1e-4f))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun theWorldPoseFollowsTheParent() {
        val engine = engine("{$plane, ${dummy(20, 7, "leadoutUp", 0f, 1f, 0f)}}")
        engine.ids[7]!!.getComponent(PositionComponent::class.java).apply {
            localPosition.set(0f, 0f, 0f)
            localRotation.set(Quaternion())
        }
        engine.update(1f / 60)
        val world = dummies.world(dummies.find(engine, 7, "leadoutUp")!!, Matrix4()).getTranslation(Vector3())
        assertTrue("$world", Vector3(0f, 1f, 0f).epsilonEquals(world, 1e-5f))
    }

    @Test
    fun aSharedNameFindsTheLowestIdAndWarnsOnce() {
        val first = dummy(31, 7, "leadoutUp", 1f, 0f, 0f)
        val second = dummy(30, 7, "leadoutUp", 2f, 0f, 0f)
        val engine = engine("{$plane, $first, $second}")
        repeat(3) { assertEquals(30L, dummies.find(engine, 7,
                "leadoutUp")!!.getComponent(HeaderComponent::class.java).id) }
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("leadoutUp") && warnings.single().contains("entity 7"))
    }

    @Test
    fun onlyDummiesOfThatParentAreFound() {
        val engine = engine(
            """{$plane, ${dummy(20, 99, "leadoutUp", 0f, 0f, 0f)},
            "21": {"type": "OBJECT", "components": {"NameComponent": {"name": "leadoutDown"},
              "ParentComponent": {"parentEntityId": 7}}}}"""
        )
        assertNull(dummies.find(engine, 7, "leadoutUp"))
        assertNull(dummies.find(engine, 7, "leadoutDown"))
    }
}
