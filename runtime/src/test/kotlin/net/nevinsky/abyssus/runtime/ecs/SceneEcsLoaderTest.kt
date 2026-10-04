/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import net.nevinsky.abyssus.runtime.ecs.render.FolderAssetResolver
import net.nevinsky.abyssus.runtime.ecs.render.ASSET_RENDERABLE_KIND
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.runtime.ecs.component.IdComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.ParentComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.RawComponentsComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.render.AssetType
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.runtime.testJson
import net.nevinsky.abyssus.runtime.testProject
import net.nevinsky.abyssus.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal val UNTITLED = testProject("Untitled")

internal fun untitledAssets() = FolderAssetResolver(File(UNTITLED, "assets").list().orEmpty().toList())

internal fun mainSceneEcs() = testJson(File(UNTITLED, "scenes/Main Scene.scene").readText())["ecs"]

class SceneEcsLoaderTest {
    @Test fun rawLegacyPayloadIsRejectedBeforeEntitiesAreAdded() {
        val engine = net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine()
        org.junit.Assert.assertThrows(net.nevinsky.abyssus.assets.format.UnsupportedDocumentFormat::class.java) {
            net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsLoader().load(testJson("""{"entities":{"0":{"components":{"NameComponent":{"name":"A"}}}},"componentIdentifiers":{}}"""), engine)
        }
        assertEquals(0, engine.entities.size())
    }

    @Test fun unmodeledComponentsAreLoggedOnceToTheCallersLog() {
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val scene = EcsConfigurator(untitledAssets(), log).load(mainSceneEcs())
        assertEquals(9, scene.engine.entities.size())
        for (name in listOf("PickableComponent", "DependenciesComponent")) {
            assertEquals(1, messages.count { name in it && "kept unchanged" in it })
        }
        assertEquals(scene.document.warnings, messages)
    }

    private fun load(ecs: String, resolver: AssetResolver = AssetResolver { type, name -> net.nevinsky.abyssus.runtime.ecs.render.AssetReference(name, type) }) =
        EcsConfigurator(resolver).load(testJson(ecs))

    private fun <C : com.badlogic.ashley.core.Component> LoadedScene.get(id: Int, type: Class<C>): C? = engine.ids[id]?.getComponent(type)

    @Test
    fun carriesUnknownComponentsAndEditorRenderables() {
        val scene = load(
            """{"entities":{
                "0":{"archetype":1,"components":{"NameComponent":{"name":"A"},"PickableComponent":{"pickerIdAttribute":{"r":1}},
                    "RenderComponent":{"renderable":{"kind":"debug-marker"}}}},
                "1":{"components":{"PickableComponent":{}}}},
              "metadata":{"version":1}}""",
        )
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        val raw = scene.get(0, RawComponentsComponent::class.java)!!
        assertEquals(listOf("NameComponent", "PickableComponent", "RenderComponent"), raw.order)
        assertEquals(setOf("PickableComponent"), raw.components.keys)
        assertNull(scene.get(0, RenderComponent::class.java)!!.renderable)
        assertNotNull(scene.get(0, RenderComponent::class.java)!!.raw)
        assertEquals(2, scene.engine.entities.size())
        assertEquals("""{"version":1}""", scene.document.metadata.toString())
        assertEquals(1, scene.document.warnings.count { it.contains("PickableComponent") })
    }

    @Test
    fun missingReferencesBecomeNoEntity() {
        val scene = load(
            """{"entities":{"0":{"components":{"PositionComponent":{"lookAtId":9},"ParentComponent":{"parentEntityId":1}}},
                "1":{"components":{}}}}""",
        )
        assertEquals(-1, scene.get(0, PositionComponent::class.java)!!.lookAtId)
        assertEquals(1, scene.get(0, ParentComponent::class.java)!!.parentEntityId)
        assertEquals(1, scene.document.warnings.count { it.contains("entity 9") })
    }

    @Test
    fun emptyPositionIsDefault() {
        val position = load("""{"entities":{"0":{"components":{"PositionComponent":{}}}}}""")
            .get(0, PositionComponent::class.java)!!
        assertEquals(Vector3(), position.localPosition)
        assertEquals(1f, position.localRotation.w, 0f)
        assertEquals(0f, position.localRotation.x, 0f)
        assertEquals(Vector3(1f, 1f, 1f), position.localScale)
    }

    @Test
    fun modelAssetWithoutFolderLoadsWithoutRenderable() {
        val scene = load(
            """{"entities":{
              "0":{"components":{"RenderComponent":{"renderable":{"kind":"${ASSET_RENDERABLE_KIND}",
                  "shaderKey":"s","asset":{"type":"MODEL","assetName":"missing"}}}}},
              "1":{"components":{"NameComponent":{"name":"B"}}}}}""",
            resolver = FolderAssetResolver(emptyList()),
        )
        assertNull(scene.get(0, RenderComponent::class.java)!!.renderable)
        assertEquals("B", scene.get(1, NameComponent::class.java)!!.name)
        assertEquals(1, scene.document.warnings.count { it.contains("missing") })
    }

    @Test
    fun modelAssetReference() {
        val scene = load(
            """{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"kind":"${ASSET_RENDERABLE_KIND}",
                  "shaderKey":"pbr","asset":{"type":"MODEL","assetName":"m1"}}}}}}}""",
        )
        val delegate = scene.get(0, RenderComponent::class.java)!!.renderable as RenderableObjectDelegate
        assertEquals(AssetType.MODEL, delegate.asset.type)
        assertEquals("m1", delegate.asset.assetName)
        assertEquals("pbr", delegate.shaderKey)
    }

    @Test
    fun lightBothShapes() {
        val scene = load(
            """{"entities":{
              "0":{"components":{"LightComponent":{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":2}}}},
              "1":{"components":{"LightComponent":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":2}}}}}""",
        )
        assertEquals(scene.get(0, LightComponent::class.java)!!.light, scene.get(1, LightComponent::class.java)!!.light)
        assertEquals(2f, scene.get(0, LightComponent::class.java)!!.light.intensity, 0f)
    }

    @Test
    fun mainSceneLoads() {
        val file = File(UNTITLED, "scenes/Main Scene.scene")
        val before = file.readBytes()
        val scene = EcsConfigurator(untitledAssets()).load(mainSceneEcs())
        assertEquals(9, scene.engine.entities.size())
        assertEquals("Model 0", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(TypeComponent.Type.OBJECT, scene.get(0, TypeComponent::class.java)!!.type)
        val delegate = scene.get(0, RenderComponent::class.java)!!.renderable as RenderableObjectDelegate
        assertEquals(AssetType.MODEL, delegate.asset.type)
        assertEquals("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", delegate.asset.assetName)
        assertEquals(
            (0..8).toList(),
            scene.engine.getFromWorld(IdComponent::class.java) { id, _ -> id }.sorted(),
        )
        assertEquals(listOf("Model 0", "Terrain", "Model 2", "'Direction' handle", "Camera 4", "Model 6", "Directional Light 7", "Spot Light 8"),
            scene.engine.getFromWorld(NameComponent::class.java) { _, n -> n.name })
        assertTrue(file.readBytes().contentEquals(before))
        assertEquals(3, scene.get(4, PositionComponent::class.java)!!.lookAtId)
    }
}
