/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.scene.Color
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.runtime.loadComponent
import net.nevinsky.abyssus.runtime.writeComponent
import net.nevinsky.abyssus.runtime.schema.ComponentRegistrationException
import net.nevinsky.abyssus.runtime.schema.ComponentRegistry
import net.nevinsky.abyssus.runtime.schema.GameComponents
import net.nevinsky.abyssus.runtime.schema.SceneComponent
import net.nevinsky.abyssus.runtime.testJson
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ComponentJsonTest {
    @Test
    fun nativeAssetKindsResolveAndRoundTripWithoutClassDispatch() {
        val source =
            json("""{"renderable":{"kind":"asset","asset":{"type":"MODEL","assetName":"tree"},"shaderKey":"pbr","extra":7}}""")
        val value = loadComponent(RenderComponent::class.java, source)
        assertTrue(value.renderable is RenderableObjectDelegate)
        assertEquals(source, writeComponent(value))
        val unknown = json("""{"renderable":{"kind":"debug-marker","payload":{"class":"opaque"}}}""")
        assertEquals(unknown, writeComponent(loadComponent(RenderComponent::class.java, unknown)))
    }

    private fun json(text: String) = testJson(text)

    private fun light(text: String) = loadComponent(LightComponent::class.java, json(text))

    private fun position(text: String) = loadComponent(PositionComponent::class.java, json(text))

    @SceneComponent("NameComponent")
    class FakeName : com.badlogic.ashley.core.Component

    @Test
    fun aGameCannotTakeABuiltInName() {
        val registry = ComponentRegistry { listOf(FakeName::class.java) }
        val error =
            assertThrows(ComponentRegistrationException::class.java) { GameComponents(registry) }
        assertTrue(error.message, error.message!!.contains("NameComponent is a built-in component"))
//        val loading = assertThrows(ComponentRegistrationException::class.java) {
//            SceneLoading(JsonProcessor(), NOPLogger.NOP_LOGGER, registry = registry)
//        }
//        assertEquals(error.message, loading.message)
    }

    @Test
    fun cameraRoundTrip() {
        val node = json(
            """{"camera":{"viewPointPosition":{"x":0.5,"y":-0.25,"z":-0.75},"position":{"x":1,"y":2.5,"z":3},
               "far":200,"near":0.5,"fieldOfView":60}}""",
        )
        val camera = loadComponent(CameraComponent::class.java, node)
        assertEquals(Vector3(0.5f, -0.25f, -0.75f), camera.camera.direction)
        assertEquals(Vector3(1f, 2.5f, 3f), camera.camera.position)
        assertEquals(200f, camera.camera.far, 0f)
        assertEquals(0.5f, camera.camera.near, 0f)
        assertEquals(60f, camera.camera.fieldOfView, 0f)
        val written = writeComponent(camera)
        assertEquals(node, written)
        val again = loadComponent(CameraComponent::class.java, written)
        assertEquals(camera.camera.direction, again.camera.direction)
        assertEquals(camera.camera.position, again.camera.position)
        assertEquals(60f, again.camera.fieldOfView, 0f)
        assertFalse(written.toString().contains("combined"))
    }

    @Test
    fun lightInBothShapes() {
        val nested = light("""{"light":{"color":{"r":1,"g":0.5,"b":0.25,"a":1},"intensity":0.8}}""")
        val direct = light("""{"color":{"r":1,"g":0.5,"b":0.25,"a":1},"intensity":0.8}""")
        for (light in listOf(nested, direct)) {
            assertEquals(Color(1f, 0.5f, 0.25f, 1f), light.light.color)
            assertEquals(0.8f, light.light.intensity, 0f)
        }
        assertTrue(nested.nested)
        assertFalse(direct.nested)
        assertTrue(writeComponent(nested).has("light"))
        val written = writeComponent(direct)
        assertFalse(written.has("light"))
        assertEquals(0.8f, written["intensity"].floatValue(), 0f)
        assertEquals(direct.light, light(written.toString()).light)
    }

    @Test
    fun rangeRoundTripsInBothShapesAndDefaultIsOmitted() {
        for (text in listOf(
            """{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"range":30}}""",
            """{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"range":30}""",
        )) {
            val node = json(text)
            val light = light(text)
            assertEquals(30f, light.light.range, 0f)
            assertEquals(text, writeComponent(light).toString())
            light.light.range = 100f
            val written = writeComponent(light)
            val values = written.get("light") ?: written
            assertFalse(values.has("range"))
            assertEquals(100f, light(written.toString()).light.range, 0f)
            assertEquals(written.toString(), writeComponent(light(written.toString())).toString())
        }
    }

    @Test
    fun positionDefaultsAndRoundTrip() {
        val empty = position("{}")
        assertEquals(Vector3(), empty.localPosition)
        assertEquals(1f, empty.localRotation.w, 0f)
        assertEquals(Vector3(1f, 1f, 1f), empty.localScale)
        assertEquals("{}", writeComponent(empty).toString())

        val node =
            json("""{"localRotation":{"w":0.5,"x":0.5,"y":0.5,"z":0.5},"lookAtId":3,"localPosition":{"x":-3.5,"z":2}}""")
        val position = loadComponent(PositionComponent::class.java, node)
        assertEquals(Vector3(-3.5f, 0f, 2f), position.localPosition)
        assertEquals(3, position.lookAtId)
        assertEquals(node, writeComponent(position))
    }

    @Test
    fun beamDefaultsAndSavedValuesRoundTripWithoutLosingUnknownData() {
        val defaults = light("{}")
        assertEquals(45f, defaults.light.coneAngle, 0f)
        assertEquals(0.2f, defaults.light.edgeSoftness, 0f)
        val defaultValues = writeComponent(defaults)["light"]
        assertFalse(defaultValues.has("coneAngle"))
        assertFalse(defaultValues.has("edgeSoftness"))
        for (nested in listOf(true, false)) {
            val values =
                """{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"coneAngle":60,"edgeSoftness":0.2500,"future":1.23400}"""
            val text = if (nested) """{"outerUnknown":7.000,"light":$values}""" else values
            val light = light(text)
            assertEquals(nested, light.nested)
            assertEquals(60f, light.light.coneAngle, 0f)
            assertEquals(0.25f, light.light.edgeSoftness, 0f)
            assertEquals(text, writeComponent(light).toString())
            light.light.coneAngle = 45f
            light.light.edgeSoftness = 0.2f
            val written = writeComponent(light)
            val saved = written["light"] ?: written
            assertFalse(saved.has("coneAngle"))
            assertFalse(saved.has("edgeSoftness"))
            assertEquals("1.23400", saved["future"].toString())
            if (nested) assertEquals("7.000", written["outerUnknown"].toString())
        }
    }

    @Test
    fun lookAtIdIsReadAsAnIntegerOrTextAndWrittenBackAsItWas() {
        data class Case(val node: String, val id: Int, val ref: String?)
        for ((node, id, ref) in listOf(
            Case("3", 3, "3"),
            Case("\"3\"", 3, "3"),
            Case("\"-1\"", -1, null),
            Case("\"h\"", -1, "h"),
            Case("-1", -1, null),
        )) {
            val source = json("""{"lookAtId":$node,"localPosition":{"x":2}}""")
            val position = loadComponent(PositionComponent::class.java, source)
            assertEquals(node, id, position.lookAtId)
            assertEquals(node, ref, position.lookAtRef)
            assertEquals(node, source.get("lookAtId"), writeComponent(position).get("lookAtId"))
        }
    }

    @Test
    fun aChangedLookAtIdIsWrittenAsAnInteger() {
        val position = position("""{"lookAtId":"h"}""")
        position.lookAtId = 7
        position.lookAtRef = "7"
        assertEquals(json("7"), writeComponent(position).get("lookAtId"))
    }
}
