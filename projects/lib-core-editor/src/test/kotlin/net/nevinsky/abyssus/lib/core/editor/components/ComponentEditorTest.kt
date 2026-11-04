/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/**
 * Edits on the scene's components. Cases about the spelling of numbers and the omission of defaults in the text
 * (`1.000`, a written-out default) are deferred to Phase 6, when scenes can be written again.
 */
class ComponentEditorTest {
    private val editor = ComponentEditor(ResourceEditorMessages())
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    private fun scene(vararg entities: String): SceneDto =
        parseScene("""{"format":"abyssus","formatVersion":1,"ecs":{${entities.joinToString(",")}}}""")

    /** Entity [id]; a `"TypeComponent":{"type":...}` in [components] is the entity's `type`. */
    private fun entity(id: Int, components: String): String {
        val typed = Regex(""""TypeComponent"\s*:\s*\{\s*"type"\s*:\s*"(\w+)"\s*\},?""")
        val type = typed.find(components)?.groupValues?.get(1)
        val header =
            if (type != null) {
                """"type":"$type","""
            } else {
                ""
            }
        return """"$id":{$header"components":{${typed.replace(components, "").trimEnd(',')}}}"""
    }

    /** Everything the scene holds, as text, to compare "nothing changed". */
    private fun snapshot(scene: SceneDto): Map<Long, List<String>> =
        scene.ecs.mapValues { (_, e) ->
            e.components.map {
                when (it) {
                    is HeaderComponent ->
                        "H ${it.id} ${it.type} ${it.entityEnabled} " +
                            "${it.disabledComponents.map { c -> c.simpleName }} ${it.flags}"
                    is RawComponentsWrapper ->
                        "R " + it.entries.joinToString { r -> "${r.name}=${r.json}/${r.disabled}" }
                    else -> it.javaClass.simpleName + json.componentValues(it)
                }
            }
        }

    private fun <C : Component> SceneDto.get(id: Long, type: Class<C>): C = component(id, type)!!

    private fun assertRejected(result: EditResult) =
        assertTrue("expected Rejected, was $result", result is EditResult.Rejected)

    private val untitled
        get() =
            parseScene(
                File(net.nevinsky.abyssus.lib.core.assets.testProject("Untitled"), "scenes/Main Scene.scene").readText()
            )

    @Test
    fun spotLightFieldsCarryTheirLabelsAndTheRangeTooltip() {
        val fields = editor.read(untitled, 8L, "LightComponent")!!.associateBy { it.field }
        assertEquals("Range", fields.getValue("range").label)
        assertEquals("Distance a point or spot light reaches.", fields.getValue("range").tooltip)
        assertEquals("Cone angle (degrees)", fields.getValue("coneAngle").label)
        assertEquals("Edge softness (%)", fields.getValue("edgeSoftness").label)
        assertEquals("a field without a message key keeps its name", "intensity", fields.getValue("intensity").label)
        assertEquals(null, fields.getValue("intensity").tooltip)
    }

    @Test
    fun everyKindHasFields() {
        val names = editor.kinds.associate { it.name to it.fields.map { f -> f.name } }
        assertEquals(7, names.size)
        assertEquals(listOf("name"), names["NameComponent"])
        assertEquals(listOf("parentEntityId"), names["ParentComponent"])
        assertEquals(11, names["PositionComponent"]!!.size)
        assertTrue("camera.fieldOfView" in names["CameraComponent"]!!)
        assertEquals(
            listOf("color.r", "color.g", "color.b", "color.a", "intensity", "range", "coneAngle", "edgeSoftness"),
            names["LightComponent"],
        )
        assertEquals(listOf("assetType", "assetName", "meshId", "shaderKey"), names["RenderComponent"])
        assertEquals(
            listOf("asset.assetName", "spacing", "offset", "sides", "alignToPath", "yawOffset"),
            names["PathArrangementComponent"],
        )
    }

    @Test
    fun lightRangeChangesOnlyRange() {
        val scene =
            scene(entity(0, """"LightComponent":{"light":{"color":{"r":1.00,"g":1,"b":1,"a":1},"intensity":1.0}}"""))
        val before = snapshot(scene)
        assertEquals(EditResult.Unchanged, editor.update(scene, 0L, "LightComponent", "range", "100"))
        assertEquals(before, snapshot(scene))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "range", "30"))
        assertEquals(30f, scene.get(0, LightComponent::class.java).light.range, 0f)
        assertEquals(1f, scene.get(0, LightComponent::class.java).light.intensity, 0f)
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "range", "100"))
        assertEquals(100f, scene.get(0, LightComponent::class.java).light.range, 0f)
        val after = snapshot(scene)
        for (invalid in listOf("0", "-1", "abc", "NaN", "Infinity")) {
            val result = editor.update(scene, 0L, "LightComponent", "range", invalid)
            assertRejected(result)
            assertTrue((result as EditResult.Rejected).reason.contains("range"))
            assertEquals(after, snapshot(scene))
        }
    }

    @Test
    fun spotlightEditsValidateBoundaries() {
        val light = """{"light":{"intensity":1.000},"outer":7.00}"""
        val scene = scene(entity(0, """"TypeComponent":{"type":"LIGHT_SPOT"},"LightComponent":$light"""))
        val before = snapshot(scene)
        for ((field, default) in listOf("coneAngle" to "45", "edgeSoftness" to "20")) {
            assertEquals(EditResult.Unchanged, editor.update(scene, 0L, "LightComponent", field, default))
        }
        for ((field, invalid) in
            listOf(
                "coneAngle" to listOf("0", "180", "-1", "181", "NaN", "Infinity", "abc"),
                "edgeSoftness" to listOf("-1", "101", "NaN", "Infinity", "abc"),
            )) {
            for (value in invalid) {
                val result = editor.update(scene, 0L, "LightComponent", field, value)
                assertRejected(result)
                assertTrue((result as EditResult.Rejected).reason.contains(field))
                assertEquals(before, snapshot(scene))
            }
        }
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "coneAngle", "60"))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "edgeSoftness", "25"))
        val saved = scene.get(0, LightComponent::class.java).light
        assertEquals(60f, saved.coneAngle, 0f)
        assertEquals(0.25f, saved.edgeSoftness, 0f)
        assertEquals(1f, saved.intensity, 0f)
        for (softness in listOf("0", "100")) {
            assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "edgeSoftness", softness))
        }
    }

    @Test
    fun aLightWithoutItsWrapperBindsAsADefaultLightAndIsEditable() {
        val scene = scene(entity(0, """"LightComponent":{}"""))
        assertEquals(100f, scene.get(0, LightComponent::class.java).light.range, 0f)
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "LightComponent", "range", "40"))
        assertEquals(40f, scene.get(0, LightComponent::class.java).light.range, 0f)
    }

    @Test
    fun addLightUsesDefaults() {
        val scene = scene(entity(0, """"NameComponent":{"name":"a"}"""))
        assertEquals(EditResult.Changed, editor.add(scene, 0L, "LightComponent"))
        val kinds = scene.entity(0)!!.components.filterNot { it is HeaderComponent }.map { it.javaClass }
        assertEquals(listOf(NameComponent::class.java, LightComponent::class.java), kinds)
        val light = scene.get(0, LightComponent::class.java).light
        assertEquals(1f, light.intensity, 0f)
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf(light.color.r, light.color.g, light.color.b, light.color.a))
        assertEquals("a", scene.get(0, NameComponent::class.java).name)
    }

    @Test
    fun addRenderNeedsAnAsset() {
        val scene = scene(entity(0, """"NameComponent":{}"""))
        assertRejected(editor.add(scene, 0L, "RenderComponent"))
        assertNull(scene.component(0, RenderComponent::class.java))
        assertRejected(editor.add(scene, 0L, "RenderComponent", mapOf("assetName" to "tree"), assets = setOf("rock")))
        assertEquals(
            EditResult.Changed,
            editor.add(scene, 0L, "RenderComponent", mapOf("assetName" to "tree"), setOf("tree")),
        )
        val render = scene.get(0, RenderComponent::class.java)
        assertEquals(MetaType.MODEL, render.type)
        assertEquals("tree", render.assetName)
    }

    @Test
    fun addTwiceIsRejected() {
        val scene = scene(entity(0, """"PositionComponent":{}"""))
        val before = snapshot(scene)
        assertRejected(editor.add(scene, 0L, "PositionComponent"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun missingKindsListsOnlyModeledOnes() {
        val scene = scene(entity(0, """"PositionComponent":{},"PickableComponent":{}"""))
        val missing = editor.missingKinds(scene, 0L).map { it.name }
        assertFalse("PositionComponent" in missing)
        assertFalse("PickableComponent" in missing)
        assertTrue("LightComponent" in missing)
        assertEquals(5, missing.size)
    }

    @Test
    fun updateCameraFieldTouchesOnlyThatValue() {
        val camera =
            """"CameraComponent":{"camera":{"position":{"x":1.50,"y":2},"far":200,"near":0.5,"fieldOfView":67}}"""
        val scene = scene(entity(0, camera))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "CameraComponent", "camera.fieldOfView", "60"))
        val cam = scene.get(0, CameraComponent::class.java).camera
        assertEquals(60f, cam.fieldOfView, 0f)
        assertEquals(1.5f, cam.position.x, 0f)
        assertEquals(2f, cam.position.y, 0f)
        assertEquals(200f, cam.far, 0f)
        assertEquals(0.5f, cam.near, 0f)
    }

    @Test
    fun updateRejectsNonNumbers() {
        val scene = scene(entity(0, """"LightComponent":{"light":{"intensity":1}}"""))
        val before = snapshot(scene)
        val result = editor.update(scene, 0L, "LightComponent", "intensity", "abc")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason.contains("intensity"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun updateToTheDefaultValueSetsIt() {
        val scene = scene(entity(0, """"PositionComponent":{"localScale":{"x":1,"y":2,"z":1}}"""))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "PositionComponent", "localScale.y", "1"))
        assertEquals(Vector3(1f, 1f, 1f), scene.get(0, PositionComponent::class.java).localScale)
    }

    @Test
    fun updateToSameValueIsUnchanged() {
        val scene = scene(entity(0, """"CameraComponent":{"camera":{"far":200,"near":0.5,"fieldOfView":60}}"""))
        val before = snapshot(scene)
        assertEquals(EditResult.Unchanged, editor.update(scene, 0L, "CameraComponent", "camera.fieldOfView", "60"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun updateName() {
        val scene = scene(entity(0, """"NameComponent":{"name":"a"}"""))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "NameComponent", "name", "b"))
        assertRejected(editor.update(scene, 0L, "TypeComponent", "type", "GROUP"))
        assertEquals("b", scene.get(0, NameComponent::class.java).name)
    }

    @Test
    fun lookAtMustNameAnEntityOrNone() {
        val scene = scene(entity(0, """"PositionComponent":{"lookAtId":1}"""), entity(1, """"PositionComponent":{}"""))
        val before = snapshot(scene)
        assertRejected(editor.update(scene, 0L, "PositionComponent", "lookAtId", "99"))
        assertRejected(editor.update(scene, 0L, "PositionComponent", "lookAtId", "x"))
        assertEquals(before, snapshot(scene))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "PositionComponent", "lookAtId", "-1"))
        assertEquals(-1, scene.get(0, PositionComponent::class.java).lookAtId)
    }

    @Test
    fun parentCyclesAreRejected() {
        val scene =
            scene(
                entity(1, """"ParentComponent":{"parentEntityId":2}"""),
                entity(2, """"ParentComponent":{}"""),
                entity(3, """"ParentComponent":{"parentEntityId":2}"""),
            )
        assertRejected(editor.update(scene, 2L, "ParentComponent", "parentEntityId", "1"))
        assertRejected(editor.update(scene, 2L, "ParentComponent", "parentEntityId", "2"))
        assertEquals(EditResult.Changed, editor.update(scene, 1L, "ParentComponent", "parentEntityId", "3"))
        assertRejected(editor.update(scene, 2L, "ParentComponent", "parentEntityId", "1"))
    }

    @Test
    fun removeKeepsTheOtherComponentsInOrder() {
        val scene = scene(entity(0, """"NameComponent":{},"LightComponent":{"light":{}},"PickableComponent":{}"""))
        assertEquals(EditResult.Changed, editor.remove(scene, 0L, "LightComponent"))
        val kinds = scene.entity(0)!!.components.filterNot { it is HeaderComponent }.map { it.javaClass.simpleName }
        assertEquals(
            "the unmodeled Pickable stays in the raw wrapper",
            listOf("NameComponent", "RawComponentsWrapper"),
            kinds,
        )
        assertRejected(editor.remove(scene, 0L, "LightComponent"))
    }

    @Test
    fun removeRefusedWhileReferenced() {
        val scene =
            scene(
                entity(3, """"PositionComponent":{}"""),
                entity(5, """"PositionComponent":{"lookAtId":3}"""),
            )
        val before = snapshot(scene)
        val result = editor.remove(scene, 3L, "PositionComponent")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason.contains("5"))
        assertEquals(before, snapshot(scene))
        assertEquals(EditResult.Changed, editor.remove(scene, 5L, "PositionComponent"))
        assertEquals(EditResult.Changed, editor.remove(scene, 3L, "PositionComponent"))
    }

    @Test
    fun unmodeledComponentsAreNotEditable() {
        val scene = scene(entity(0, """"PickableComponent":{"x":1}"""))
        assertRejected(editor.remove(scene, 0L, "PickableComponent"))
        assertRejected(editor.update(scene, 0L, "PickableComponent", "x", "2"))
        assertEquals(null, editor.read(scene, 0L, "PickableComponent"))
    }

    @Test
    fun readListsFieldValues() {
        val scene =
            scene(entity(0, """"LightComponent":{"light":{"color":{"r":0.5,"g":1,"b":1,"a":1},"intensity":2}}"""))
        val values = editor.read(scene, 0L, "LightComponent")!!.associate { it.field to it.value }
        assertEquals("0.5", values["color.r"])
        assertEquals("2", values["intensity"])
    }

    @Test
    fun mainSceneEditsKeepEverythingElse() {
        val scene = untitled
        val before = snapshot(untitled)
        assertEquals(EditResult.Changed, editor.add(scene, 0L, "LightComponent"))
        assertEquals(EditResult.Changed, editor.update(scene, 4L, "CameraComponent", "camera.fieldOfView", "50"))
        assertEquals(EditResult.Changed, editor.remove(scene, 0L, "LightComponent"))
        // the camera field changed; put it back and everything else equals the original
        assertEquals(EditResult.Changed, editor.update(scene, 4L, "CameraComponent", "camera.fieldOfView", "67"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun anUnrelatedPositionEditKeepsTheLookAtReference() {
        val scene =
            scene(
                entity(0, """"PositionComponent":{"lookAtId":3,"localPosition":{"x":1}}"""),
                entity(3, """"NameComponent":{}"""),
            )
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "PositionComponent", "localPosition.x", "5"))
        assertEquals(3, scene.get(0, PositionComponent::class.java).lookAtId)
        assertEquals(5f, scene.get(0, PositionComponent::class.java).localPosition.x, 0f)
    }

    @Test
    fun aPositionWithAnUnreadableLookAtStaysRawAndIsNotEditable() {
        val scene = scene(entity(0, """"PositionComponent":{"lookAtId":"h","localPosition":{"x":1.00}}"""))
        val before = snapshot(scene)
        assertNull(scene.component(0, PositionComponent::class.java))
        assertRejected(editor.update(scene, 0L, "PositionComponent", "localPosition.x", "5"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun customComponentsStayReadOnlyAndSurviveBuiltInEdits() {
        val scene = parseScene(File("src/test/testData/project/Custom/scenes/Field.scene").readText())
        val plane = { snapshot(scene).getValue(0L).single { it.startsWith("R ") } }
        val planeBefore = plane()
        val before = snapshot(scene)
        assertEquals(null, editor.kindOf("PlaneComponent"))
        assertRejected(editor.add(scene, 1L, "PlaneComponent"))
        assertRejected(editor.update(scene, 0L, "PlaneComponent", "lineLength", "25"))
        assertRejected(editor.remove(scene, 0L, "PlaneComponent"))
        assertEquals(before, snapshot(scene))
        assertEquals(EditResult.Changed, editor.update(scene, 0L, "NameComponent", "name", "Renamed"))
        assertEquals(planeBefore, plane())
    }

    /** Main Scene with a line of trees: arrangement 11 and copies 12 to 16. */
    private fun arranged(): SceneDto {
        val scene = untitled
        val points = listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f))
        ArrangementEdits(ResourceEditorMessages())
            .create(
                scene,
                BaseMeta(MetaType.MODEL, " tree "),
                points,
                false,
                LineHeights(),
            )
        return scene
    }

    private fun slotsOf(scene: SceneDto) = scene.get(11, PathArrangementComponent::class.java).slots.toList()

    @Test
    fun editAnArrangedCopysPosition() {
        val scene = arranged()
        assertEquals(EditResult.Changed, editor.update(scene, 14L, "PositionComponent", "localPosition.z", "3"))
        assertNull(scene.component(14, ParentComponent::class.java))
        assertEquals(listOf(12, 13, -1, 15, 16), slotsOf(scene))
    }

    @Test
    fun aRenameDoesNotDetach() {
        val scene = arranged()
        assertEquals(EditResult.Changed, editor.update(scene, 13L, "NameComponent", "name", "Lamp"))
        assertEquals(11, scene.get(13, ParentComponent::class.java).parentEntityId)
        assertEquals(listOf(12, 13, 14, 15, 16), slotsOf(scene))
    }

    @Test
    fun changeAnArrangementsSpacing() {
        val scene = arranged()
        assertEquals(EditResult.Changed, editor.update(scene, 11L, "PathArrangementComponent", "spacing", "20"))
        assertEquals(listOf(12, 13, 14), slotsOf(scene))
        assertEquals(20f, scene.get(11, PathArrangementComponent::class.java).spacing, 0f)
        assertNull(scene.entity(15))
        assertEquals(EditResult.Unchanged, editor.update(scene, 11L, "PathArrangementComponent", "spacing", "20"))
    }

    @Test
    fun aSpacingOfZeroIsRefused() {
        val scene = arranged()
        val before = snapshot(scene)
        assertRejected(editor.update(scene, 11L, "PathArrangementComponent", "spacing", "0"))
        assertRejected(editor.update(scene, 11L, "PathArrangementComponent", "offset", "-1"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun settingsThatMakeTooManyCopiesAreRefused() {
        val scene = arranged()
        scene.get(11, PathArrangementComponent::class.java).points[1].x = 2000f
        val before = snapshot(scene)
        assertRejected(editor.update(scene, 11L, "PathArrangementComponent", "spacing", "1"))
        assertEquals(before, snapshot(scene))
    }

    @Test
    fun anArrangementIsNotOfferedAsANewComponent() {
        val scene = arranged()
        assertFalse(editor.missingKinds(scene, 0L).any { it.name == "PathArrangementComponent" })
    }
}
