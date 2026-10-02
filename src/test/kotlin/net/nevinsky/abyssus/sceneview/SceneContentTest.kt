/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.dto.SceneReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneContentTest {
    private fun content(json: String) = SceneContent.of(SceneReader.parse(json))

    private fun entity(components: String) = """{"ecs":{"entities":{"7":{"archetype":1,"components":$components}}}}"""

    @Test
    fun mainSceneHasThreeModelsAndOneTerrain() {
        val text = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
        val c = content(text)
        assertEquals(
            listOf("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "model_fc33e1f1-015b-4524-9b10-aa417acd273c", "model_900f6f61-6384-434a-be81-56ce303fbb56"),
            c.models.map { it.assetName },
        )
        assertEquals(listOf("0", "2", "6"), c.models.map { it.entityId })
        assertEquals(listOf("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"), c.terrains.map { it.assetName })
        assertTrue(c.lights.isEmpty())
        assertEquals("skybox_default", c.skybox)
    }

    @Test
    fun transformFieldsDefaultToIdentity() {
        val c = content(entity("""{"PositionComponent":{"localRotation":{"w":0.5,"x":-0.5},"localPosition":{"y":2}},
            "RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}"""))
        val t = c.models.single().transform
        assertEquals(Vec3(0f, 2f, 0f), t.position)
        assertEquals(Quat(-0.5f, 0f, 0f, 0.5f), t.rotation)
        assertEquals(Vec3(1f, 1f, 1f), t.scale)
    }

    @Test
    fun missingPositionComponentIsIdentity() {
        val c = content(entity("""{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}"""))
        assertEquals(PlacementTransform.IDENTITY, c.models.single().transform)
    }

    @Test
    fun unknownAndMalformedEntitiesAreIgnored() {
        val json = """{"ecs":{"entities":{
            "1":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"SPRITE","assetName":"s"}}}}},
            "2":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL"}}}}},
            "3":{"components":{"RenderComponent":"nope"}},
            "4":"nope",
            "5":{"components":{"PositionComponent":{"localPosition":{"x":"a"}},"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"ok"}}}}}}}}"""
        val c = content(json)
        assertEquals(listOf("ok"), c.models.map { it.assetName })
        assertEquals(0f, c.models.single().transform.position.x, 0f)
    }

    @Test
    fun skyboxNeedsEnabledAndName() {
        assertEquals("sky", content("""{"skyboxEnabled":true,"skyboxName":"sky"}""").skybox)
        assertNull(content("""{"skyboxEnabled":false,"skyboxName":"sky"}""").skybox)
        assertNull(content("""{"skyboxEnabled":true,"skyboxName":null}""").skybox)
        assertNull(content("""{"skyboxName":"sky"}""").skybox)
    }

    @Test
    fun lightEntitiesAreRead() {
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},
            "LightComponent":{"light":{"color":{"r":1.0,"g":0.5,"b":0.0,"a":1.0},"intensity":0.8}},
            "PositionComponent":{"localPosition":{"x":1,"y":10,"z":2}}}"""))
        val l = c.lights.single()
        assertEquals(LightKind.DIRECTIONAL, l.kind)
        assertEquals(Rgba(1f, 0.5f, 0f, 1f), l.color)
        assertEquals(0.8f, l.intensity, 0f)
        assertEquals(Vec3(1f, 10f, 2f), l.position)
        assertEquals(Vec3(0f, 0f, -1f), l.direction)
    }

    @Test
    fun lightColorAndIntensityMayBeFlat_andPointKindIsRecognised() {
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{"color":{"r":0.2},"intensity":2}}"""))
        val l = c.lights.single()
        assertEquals(LightKind.POINT, l.kind)
        assertEquals(0.2f, l.color.r, 0f)
        assertEquals(2f, l.intensity, 0f)
    }

    @Test
    fun directionFollowsRotation() {
        // 90 degrees about +Y turns -Z into -X
        val s = Math.sqrt(0.5).toFloat()
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
            "PositionComponent":{"localRotation":{"y":$s,"w":$s}}}"""))
        val d = c.lights.single().direction
        assertEquals(-1f, d.x, 1e-5f)
        assertEquals(0f, d.y, 1e-5f)
        assertEquals(0f, d.z, 1e-5f)
    }

    @Test
    fun noEcsIsEmpty() {
        assertEquals(SceneContent.EMPTY, content("{}"))
    }
}
