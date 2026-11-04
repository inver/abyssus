/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ColliderShape
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.loadPhysicsScene
import org.junit.Assert.*
import org.junit.Test

/** Design decision D11 of `improve-control-line-performance-stability`: asset shapes are built once for a game run. */
class ShapeCacheTest {
    private val natives = JoltNatives()
    private val assets = PhysicsAssets(testProject("Physics"))

    /**
     * The fixture's scene with a convex hull collider on each collider entity whose model has readable points (the
     * fixture's own colliders are boxes and a height field). The component is changed in memory; no document is.
     */
    private fun withHulls(): SceneContext = loadPhysicsScene().also { scene ->
        for (entity in scene.engine.entities) {
            val collider = entity.getComponent(ColliderComponent::class.java) ?: continue
            val model = assetName(entity, MetaType.MODEL) ?: continue
            if (assets.modelPoints(model) != null) collider.shape = ColliderShape.CONVEX_HULL
        }
    }

    @Test fun aSecondWorldBuildsNoHull() {
        ShapeCache().use { cache ->
            val messages = mutableListOf<String>()
            PhysicsWorld(withHulls().engine, assets, warningsTo(messages), natives, cache).close()
            assertTrue("a hull and the height field are built: $messages", cache.hullsBuilt >= 1 && cache.built > cache.hullsBuilt)
            val hulls = cache.hullsBuilt
            val built = cache.built

            PhysicsWorld(withHulls().engine, assets, warningsTo(messages), natives, cache).close()
            assertEquals("the second world builds no hull", hulls, cache.hullsBuilt)
            assertEquals("nor any other asset shape", built, cache.built)
            assertTrue(messages.toString(), messages.isEmpty())
        }
    }

    @Test fun worldsSharingAShapeSimulateAsWorldsThatDoNot() {
        fun fall(cache: ShapeCache?): List<Vector3> {
            val scene = withHulls()
            val world = PhysicsWorld(scene.engine, assets, warningsTo(mutableListOf()), natives, cache)
            try {
                repeat(120) { world.advance(1f / 60f) }
                return scene.engine.ids.ids.sorted().mapNotNull { id ->
                    scene.engine.ids[id]?.getComponent(PositionComponent::class.java)?.localPosition?.cpy()
                }
            } finally {
                world.close()
            }
        }
        val alone = fall(null)
        ShapeCache().use { cache ->
            fall(cache)
            // the second world uses the shapes the first one made and closed with: the bodies keep their own references
            assertEquals(alone, fall(cache))
        }
    }

    @Test fun aClosedCacheRefusesToBuild() {
        val cache = ShapeCache()
        cache.close()
        cache.close()
        assertThrows(IllegalStateException::class.java) {
            PhysicsWorld(withHulls().engine, assets, warningsTo(mutableListOf()), natives, cache)
        }
    }
}
