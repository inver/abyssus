/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathSides
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class PathArrangementComponentTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()
    private val loader = EcsLoader(json, registry)

    private fun load(body: String): Pair<PathArrangementComponent, EcsLoadingWarns> {
        val ecs = NativeJsonTree().parse("""{"entities":{"11":{"components":{"PathArrangementComponent":$body}}}}""")
        val engine = SceneEngine()
        val warns = loader.loadToEngine(ecs, engine)
        return engine.entities.single().getComponent(PathArrangementComponent::class.java) to warns
    }

    private val example = """{"points":[{"x":0,"y":1.2,"z":0},{"x":40,"y":0.8,"z":0}],"closed":false,""" +
        """"asset":{"type":"MODEL","assetName":"tree"},"spacing":10,"offset":0,"sides":"CENTER","alignToPath":true,""" +
        """"yawOffset":0,"slots":[12,13,14,15,16]}"""

    @Test
    fun registeredUnderShortAndQualifiedNames() {
        assertSame(PathArrangementComponent::class.java, registry.get("PathArrangementComponent"))
        assertSame(PathArrangementComponent::class.java, registry.get(PathArrangementComponent::class.qualifiedName!!))
        assertTrue(PathArrangementComponent::class.java in registry.types)
    }

    @Test
    fun theDesignExampleLoadsWithoutWarnings() {
        val (component, warns) = load(example)
        assertTrue(warns.warnings.toString(), warns.warnings.isEmpty())
        assertTrue(warns.carried.isEmpty())
        assertEquals(2, component.points.size)
        assertEquals(1.2f, component.points[0].y, 0f)
        assertEquals(40f, component.points[1].x, 0f)
        assertEquals(MetaType.MODEL, component.asset.type)
        assertEquals("tree", component.asset.name)
        assertEquals(listOf(12, 13, 14, 15, 16), component.slots)
    }

    @Test
    fun defaultsAreOmittedOnWrite() {
        val (component, _) = load(example)
        assertEquals(
            """{"points":[{"x":0,"y":1.2,"z":0},{"x":40,"y":0.8,"z":0}],"asset":{"type":"MODEL","assetName":"tree"},""" +
                """"slots":[12,13,14,15,16]}""",
            json.componentTree(component).toString(),
        )
    }

    @Test
    fun nonDefaultSettingsWriteBackByteIdentical() {
        val text = """{"points":[{"x":0,"y":1.2,"z":0},{"x":40,"y":0.8,"z":0}],"closed":true,""" +
            """"asset":{"type":"MODEL","assetName":"tree"},"spacing":5.5,"offset":3,"sides":"BOTH","alignToPath":false,""" +
            """"yawOffset":90,"slots":[12,-1,14]}"""
        val (component, warns) = load(text)
        assertTrue(warns.warnings.isEmpty())
        assertEquals(PathSides.BOTH, component.sides)
        assertEquals(text, json.componentTree(component).toString())
    }

    @Test
    fun anUnknownSidesNameTakesTheDefault() {
        val (component, _) = load("""{"sides":"UP"}""")
        assertEquals(PathSides.CENTER, component.sides)
    }
}
