/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.WorldTransformComponent
import net.nevinsky.abyssus.lib.core.ecs.component.isActiveInHierarchy
import net.nevinsky.abyssus.lib.core.ecs.component.worldPosition
import net.nevinsky.abyssus.lib.core.ecs.component.worldTransform
import net.nevinsky.abyssus.lib.core.ecs.system.HierarchySystem
import net.nevinsky.abyssus.lib.core.ecs.system.LookAtSystem
import net.nevinsky.abyssus.lib.core.hierarchy.NodeTransform
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import com.badlogic.gdx.math.Matrix4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchySystemTest {
    private val warnings = ArrayList<String>()

    private fun engine(ecs: String): SceneEngine {
        val engine = SceneEngine()
        engine.addSystem(HierarchySystem(warnings::add))
        engine.addSystem(LookAtSystem(engine.ids, 0))
        loadEcs(ecs, engine)
        return engine
    }

    private fun SceneEngine.at(id: Int) = ids[id]!!.worldPosition(Vector3())!!

    private fun assertVec(expected: Vector3, actual: Vector3) =
        assertTrue("expected $expected, got $actual", expected.epsilonEquals(actual, 1e-4f))

    private val parentAndChild =
        """{
          "1": {"components": {"PositionComponent": {"localPosition": {"x": 0, "y": 5, "z": 0}},
                "RenderComponent": {"type": "MODEL", "assetName": "a"}}},
          "2": {"components": {"PositionComponent": {"localPosition": {"x": 1, "y": 0, "z": 0}},
                "ParentComponent": {"parentEntityId": 1}, "RenderComponent": {"type": "MODEL", "assetName": "b"}}}
        }"""

    @Test
    fun aChildsRenderableIsPlacedThroughItsParent() {
        val engine = engine(parentAndChild)
        engine.update(1f / 60)
        assertVec(Vector3(1f, 5f, 0f), engine.at(2))
        assertTrue(engine.ids[2]!!.getComponent(WorldTransformComponent::class.java).child)
    }

    @Test
    fun aChildFollowsInTheSameUpdate() {
        val engine = engine(parentAndChild)
        engine.update(1f / 60)
        engine.ids[1]!!.getComponent(PositionComponent::class.java).translate(1f, 0f, 0f)
        engine.update(1f / 60)
        assertVec(Vector3(2f, 5f, 0f), engine.at(2))
        assertVec(Vector3(1f, 0f, 0f), engine.ids[2]!!.getComponent(PositionComponent::class.java).localPosition)
    }

    @Test
    fun lookAtAimsAtTheChildsWorldPositionAndLeavesTheStoredRotation() {
        // a light at (0, 10, 0) aimed at its own child handle at local (0, -10, 0): it faces straight down
        val engine = engine(
            """{
              "1": {"type": "LIGHT_DIRECTIONAL", "components": {"PositionComponent": {"lookAtId": 0,
                    "localPosition": {"x": 0, "y": 10, "z": 0}}}},
              "0": {"type": "HANDLE", "components": {"PositionComponent": {"localPosition": {"x": 0, "y": -10, "z": 0}},
                    "ParentComponent": {"parentEntityId": 1}}}
            }"""
        )
        repeat(3) { engine.update(1f / 60) }
        assertVec(Vector3(0f, 0f, 0f), engine.at(0))
        val forward = Vector3(0f, 0f, -1f).rot(Matrix4(engine.ids[1]!!.worldTransform()).setTranslation(0f, 0f, 0f))
        assertVec(Vector3(0f, -1f, 0f), forward.nor())
        assertEquals(Quaternion(), engine.ids[1]!!.getComponent(PositionComponent::class.java).localRotation)
    }

    @Test
    fun aNodeBindingIsComposedOnceTheModelAnswers() {
        val engine = engine(
            """{
              "1": {"components": {"PositionComponent": {"localPosition": {"x": 0, "y": 5, "z": 0}}}},
              "2": {"type": "DUMMY", "components": {"PositionComponent": {},
                    "ParentComponent": {"parentEntityId": 1, "node": "LeftWing"}}}
            }"""
        )
        engine.update(1f / 60)
        assertVec(Vector3(0f, 5f, 0f), engine.at(2))
        engine.getSystem(HierarchySystem::class.java).nodes = { _, node ->
            if (node == "LeftWing") {
                NodeTransform.Found(Matrix4().setToTranslation(-2f, 0f, 0f))
            } else {
                NodeTransform.Missing
            }
        }
        engine.update(1f / 60)
        assertVec(Vector3(-2f, 5f, 0f), engine.at(2))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun aCycleIsWarnedOnceOverManyUpdates() {
        val engine = engine(
            """{
              "1": {"components": {"PositionComponent": {}, "ParentComponent": {"parentEntityId": 2}}},
              "2": {"components": {"PositionComponent": {}, "ParentComponent": {"parentEntityId": 1}}}
            }"""
        )
        repeat(100) { engine.update(1f / 60) }
        assertEquals(warnings.toString(), 2, warnings.size)
        assertTrue(warnings[0].startsWith("entity 1:"))
        assertTrue(warnings[1].startsWith("entity 2:"))
    }

    @Test
    fun aDisabledParentDisablesItsChildWithoutWritingIt() {
        val engine = engine(parentAndChild)
        engine.ids[1]!!.getComponent(HeaderComponent::class.java).entityEnabled = false
        engine.update(1f / 60)
        assertFalse(engine.ids[2]!!.isActiveInHierarchy())
        assertTrue(engine.ids[2]!!.getComponent(HeaderComponent::class.java).entityEnabled)
        engine.ids[1]!!.getComponent(HeaderComponent::class.java).entityEnabled = true
        engine.update(1f / 60)
        assertTrue(engine.ids[2]!!.isActiveInHierarchy())
    }
}
