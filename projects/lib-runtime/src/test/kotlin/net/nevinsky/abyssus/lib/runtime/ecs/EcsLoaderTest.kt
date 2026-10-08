/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.TypeComponent
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.CAMERA_NEAR
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_RANGE
import net.nevinsky.abyssus.lib.runtime.ecs.render.ASSET_RENDERABLE_KIND
import net.nevinsky.abyssus.lib.runtime.ecs.render.AssetReference
import net.nevinsky.abyssus.lib.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.lib.runtime.ecs.render.FolderAssetResolver
import net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.lib.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.lib.runtime.schema.GameComponents
import net.nevinsky.abyssus.lib.runtime.schema.PlaneComponent
import net.nevinsky.abyssus.lib.runtime.schema.PlaneRegistry
import net.nevinsky.abyssus.lib.runtime.testConfigurator
import net.nevinsky.abyssus.lib.runtime.testJson
import net.nevinsky.abyssus.lib.runtime.testProject
import net.nevinsky.abyssus.lib.core.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Stable scene/asset identities, independent of the interactive Untitled project.
internal val UNTITLED = testProject("Tree")

internal fun untitledAssets() = FolderAssetResolver(File(UNTITLED, "assets").list().orEmpty().toList())

internal fun mainSceneEcs() = testJson(File(UNTITLED, "scenes/Main Scene.scene").readText())["ecs"]

/** A component class that is on the classpath but registered nowhere: a scene must not be able to load it. */
class UnregisteredComponent : Component {
    var value = 0

    init {
        created++
    }
}

private var created = 0

private const val COMPONENT_PACKAGE = "net.nevinsky.abyssus.lib.runtime.ecs.component"

class EcsLoaderTest {
    private val messages = mutableListOf<String>()

    private fun load(
        ecs: String,
        resolver: AssetResolver = AssetResolver { type, name -> AssetReference(name, type) },
        game: GameComponents = GameComponents(),
    ) = testConfigurator(resolver, warningsTo(messages), game).load(testJson(ecs))

    private fun <C : Component> net.nevinsky.abyssus.lib.runtime.ecs.LoadedScene.get(id: Int, type: Class<C>): C? =
        engine.ids[id]?.getComponent(type)

    // --- class names ---

    @Test
    fun aComponentKeyedByItsFullyQualifiedClassNameIsBoundByJackson() {
        val scene = load(
            """{"entities":{"0":{"components":{
                "$COMPONENT_PACKAGE.NameComponent":{"name":"A"},
                "$COMPONENT_PACKAGE.TypeComponent":{"type":"LIGHT_SPOT"},
                "$COMPONENT_PACKAGE.ParentComponent":{"parentEntityId":1},
                "$COMPONENT_PACKAGE.Point2PointPositionComponent":{"entity1Id":1,"entity2Id":1}}},
              "1":{"components":{}}}}""",
        )
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(TypeComponent.Type.LIGHT_SPOT, scene.get(0, TypeComponent::class.java)!!.type)
        assertEquals(1, scene.get(0, ParentComponent::class.java)!!.parentEntityId)
        assertEquals(1, scene.get(0, Point2PointPositionComponent::class.java)!!.entity2Id)
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun theShortClassNameOfTheSceneFilesStillNamesTheSameComponent() {
        val scene = load(
            """{"entities":{"0":{"components":{"NameComponent":{"name":"A"}}},
                "1":{"components":{"$COMPONENT_PACKAGE.NameComponent":{"name":"B"}}}}}""",
        )
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals("B", scene.get(1, NameComponent::class.java)!!.name)
    }

    @Test
    fun aClassThatIsNotARegisteredComponentIsNeverLoaded() {
        val before = created
        val scene = load(
            """{"entities":{"0":{"components":{
                "${UnregisteredComponent::class.java.name}":{"value":7},
                "java.lang.String":{},
                "no.such.Component":{"x":1},
                "$COMPONENT_PACKAGE.NameComponent":{"name":"A"}}}}}""",
        )
        assertEquals("a class a scene names is not instantiated unless it is registered", before, created)
        assertNull(scene.get(0, UnregisteredComponent::class.java))
        assertEquals(
            listOf(UnregisteredComponent::class.java.name, "java.lang.String", "no.such.Component"),
            scene.document.carried[0L]!!.keys.toList(),
        )
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(3, messages.count { "is not modeled and is kept unchanged" in it })
    }

    @Test
    fun aValueJacksonCannotBindKeepsTheComponentRawAndTheRestLoads() {
        val scene = load(
            """{"entities":{"0":{"components":{"TypeComponent":{"type":"WIDGET"},"NameComponent":{"name":"A"}}}}}""",
        )
        assertNull(scene.get(0, TypeComponent::class.java))
        assertEquals("""{"type":"WIDGET"}""", scene.document.carried[0L]!!["TypeComponent"].toString())
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(1, messages.count { it.startsWith("entity 0: component TypeComponent could not be read") })
    }

    @Test
    fun unmodeledComponentsAreLoggedOnceToTheCallersLog() {
        val scene = testConfigurator(untitledAssets(), warningsTo(messages)).load(mainSceneEcs())
        assertEquals(9, scene.engine.entities.size())
        assertEquals(1, messages.count { "PickableComponent" in it && "kept unchanged" in it })
        assertEquals(scene.document.warnings, messages)
    }

    // --- game components ---

    private val game = GameComponents(PlaneRegistry())

    private fun plane(values: String, key: String = "PlaneComponent") =
        load("""{"entities":{"0":{"components":{"$key":$values}}}}""", game = game).get(0, PlaneComponent::class.java)!!

    @Test
    fun aRegisteredGameComponentIsBoundByItsShortOrFullyQualifiedName() {
        for (key in listOf("PlaneComponent", PlaneComponent::class.java.name)) {
            val plane = plane("""{"lineLength":22,"kind":"STUNT","name":"Red","hasTipWeight":false,"pilot":-1}""", key)
            assertEquals(22f, plane.lineLength, 0f)
            assertEquals(PlaneComponent.Kind.STUNT, plane.kind)
            assertEquals("Red", plane.name)
            assertEquals(false, plane.hasTipWeight)
            assertEquals(60, plane.fuelSeconds)
        }
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun aGameValueJacksonCannotBindKeepsTheWholeComponentRawWithOneWarning() {
        for (bad in listOf("""{"lineLength":"long"}""", """{"kind":"WARP"}""", """{"fuelSeconds":"many"}""")) {
            messages.clear()
            val scene = load("""{"entities":{"0":{"components":{"PlaneComponent":$bad}}}}""", game = game)
            assertNull(bad, scene.get(0, PlaneComponent::class.java))
            assertEquals(bad, scene.document.carried[0L]!!["PlaneComponent"].toString())
            assertEquals(messages.toString(), 1, messages.size)
            assertTrue(messages.single(), messages.single().startsWith("entity 0: component PlaneComponent could not be read"))
        }
    }

    @Test
    fun aGameVectorMissingAnAxisKeepsTheDefaultOfThatAxis() {
        val plane = plane("""{"leadout":{"x":1}}""")
        assertEquals(Vector3(1f, 0f, -0.3f), plane.leadout)
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun onlyTheFieldsOfAGameComponentBind() {
        val plane = plane("""{"speed":99,"lineLength":22}""")
        assertEquals(0f, plane.speed, 0f)
        assertEquals(22f, plane.lineLength, 0f)
    }

    @Test
    fun anUnregisteredGameComponentIsCarriedRaw() {
        val scene = load("""{"entities":{"0":{"components":{"PlaneComponent":{"lineLength":22}}}}}""")
        assertNull(scene.get(0, PlaneComponent::class.java))
        assertEquals("""{"lineLength":22}""", scene.document.carried[0L]!!["PlaneComponent"].toString())
    }

    // --- built-in components ---

    @Test
    fun archetypesAreNeitherReadNorCarried() {
        val scene = load(
            """{"entities":{"0":{"archetype":1,"components":{"NameComponent":{"name":"A"}}}},"archetypes":{"1":["NameComponent"]},"metadata":{"v":1}}""",
        )
        assertEquals("A", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(emptyMap<Long, Any>(), scene.document.carried)
        assertEquals(listOf("metadata"), scene.document.extras.keys.toList())
        assertEquals(emptyList<String>(), messages)
    }

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
        assertEquals(setOf("PickableComponent"), scene.document.carried[0L]!!.keys)
        assertEquals(setOf("PickableComponent"), scene.document.carried[1L]!!.keys)
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
        assertEquals(-1, position.lookAtId)
        assertNull(position.lookAtRef)
    }

    @Test
    fun aPositionMergesWhatTheFileNamesIntoItsDefaults() {
        val position = load(
            """{"entities":{"0":{"components":{"PositionComponent":{
                "localPosition":{"x":1,"z":3},"localRotation":{"y":0.5},"localScale":{"x":2}}}}}}""",
        ).get(0, PositionComponent::class.java)!!
        assertEquals(Vector3(1f, 0f, 3f), position.localPosition)
        assertEquals(0.5f, position.localRotation.y, 0f)
        assertEquals("a rotation the file does not name stays the identity", 1f, position.localRotation.w, 0f)
        assertEquals("a scale axis the file does not name stays 1", Vector3(2f, 1f, 1f), position.localScale)
    }

    @Test
    fun aLookAtReferenceIsAnIntegerOrText() {
        fun lookAt(node: String) = load(
            """{"entities":{"0":{"components":{"PositionComponent":{"lookAtId":$node}}},"3":{"components":{}}}}""",
        ).get(0, PositionComponent::class.java)!!
        lookAt("3").let { assertEquals(3, it.lookAtId); assertEquals("3", it.lookAtRef); assertEquals("3", it.lookAtSource.toString()) }
        lookAt("\"3\"").let { assertEquals(3, it.lookAtId); assertEquals("3", it.lookAtRef) }
        lookAt("-1").let { assertEquals(-1, it.lookAtId); assertNull(it.lookAtRef) }
        lookAt("null").let { assertEquals(-1, it.lookAtId); assertNull(it.lookAtRef) }
        lookAt("\"h\"").let { assertEquals(-1, it.lookAtId); assertEquals("h", it.lookAtRef) }
    }

    @Test
    fun aCameraKeepsItsDefaultsWhereTheFileIsSilent() {
        val camera = load(
            """{"entities":{"0":{"components":{"CameraComponent":{"camera":{"position":{"x":1,"y":2,"z":3},"far":500}}}}}}""",
        ).get(0, CameraComponent::class.java)!!.camera
        assertEquals(Vector3(1f, 2f, 3f), camera.position)
        assertEquals(500f, camera.far, 0f)
        assertEquals(CAMERA_NEAR, camera.near, 0f)
        assertEquals(CAMERA_FOV, camera.fieldOfView, 0f)
        val empty = load("""{"entities":{"0":{"components":{"CameraComponent":{}}}}}""").get(0, CameraComponent::class.java)!!
        assertEquals(CAMERA_FAR, empty.camera.far, 0f)
    }

    @Test
    fun lightBothShapes() {
        val scene = load(
            """{"entities":{
              "0":{"components":{"LightComponent":{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":2}}}},
              "1":{"components":{"LightComponent":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":2}}}}}""",
        )
        val nested = scene.get(0, LightComponent::class.java)!!
        val direct = scene.get(1, LightComponent::class.java)!!
        assertEquals(nested.light, direct.light)
        assertEquals(2f, nested.light.intensity, 0f)
        assertTrue(nested.nested)
        assertTrue(!direct.nested)
        assertEquals(LIGHT_RANGE, nested.light.range, 0f)
    }

    @Test
    fun modelAssetReference() {
        val scene = load(
            """{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"kind":"$ASSET_RENDERABLE_KIND",
                  "shaderKey":"pbr","asset":{"type":"MODEL","assetName":"m1"}}}}}}}""",
        )
        val delegate = scene.get(0, RenderComponent::class.java)!!.renderable as RenderableObjectDelegate
        assertEquals(MetaType.MODEL, delegate.asset.type)
        assertEquals("m1", delegate.asset.assetName)
        assertEquals("pbr", delegate.shaderKey)
    }

    @Test
    fun modelAssetWithoutFolderLoadsWithoutRenderable() {
        val scene = load(
            """{"entities":{
              "0":{"components":{"RenderComponent":{"renderable":{"kind":"$ASSET_RENDERABLE_KIND",
                  "shaderKey":"s","asset":{"type":"MODEL","assetName":"missing"}}}}},
              "1":{"components":{"NameComponent":{"name":"B"}}}}}""",
            resolver = FolderAssetResolver(emptyList()),
        )
        assertNull(scene.get(0, RenderComponent::class.java)!!.renderable)
        assertNotNull(scene.get(0, RenderComponent::class.java)!!.raw)
        assertEquals("B", scene.get(1, NameComponent::class.java)!!.name)
        assertEquals(1, scene.document.warnings.count { it.contains("missing") })
    }

    @Test
    fun aRenderAssetThatNamesNoTypeLoadsWithoutRenderable() {
        val scene = load(
            """{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"kind":"$ASSET_RENDERABLE_KIND",
                  "asset":{"type":"WIDGET","assetName":"m"}}}}}}}""",
        )
        assertNull(scene.get(0, RenderComponent::class.java)!!.renderable)
        assertEquals(1, scene.document.warnings.count { it.contains("names no MODEL or TERRAIN asset") })
    }

    @Test
    fun mainSceneLoads() {
        val file = File(UNTITLED, "scenes/Main Scene.scene")
        val before = file.readBytes()
        val scene = testConfigurator(untitledAssets()).load(mainSceneEcs())
        assertEquals(9, scene.engine.entities.size())
        assertEquals("Model 0", scene.get(0, NameComponent::class.java)!!.name)
        assertEquals(TypeComponent.Type.OBJECT, scene.get(0, TypeComponent::class.java)!!.type)
        val delegate = scene.get(0, RenderComponent::class.java)!!.renderable as RenderableObjectDelegate
        assertEquals(MetaType.MODEL, delegate.asset.type)
        assertEquals("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", delegate.asset.assetName)
        assertEquals((0L..8L).toList(), scene.engine.getFromWorld(IdComponent::class.java) { id, _ -> id }.sorted())
        assertEquals(
            listOf(
                "Model 0", "Terrain", "Model 2", "'Direction' handle", "Camera 4", "Model 6", "Directional Light 7", "Spot Light 8",
            ),
            scene.engine.getFromWorld(NameComponent::class.java) { _, n -> n.name },
        )
        assertTrue(file.readBytes().contentEquals(before))
        assertEquals(3, scene.get(4, PositionComponent::class.java)!!.lookAtId)
    }
}
