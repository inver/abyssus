/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.*
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import org.junit.Assert.*
import org.junit.Test

class FoliageMetaEditsTest {
    private val text = """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","extension":60.0,"additional":{"terrain":"meadow","layers":[{"id":1,"note":"north slope","density":0.05,"models":[{"asset":"tree","weight":1.0,"custom":42}],"drawDistance":60.0}]}}"""
    private val before = FoliageMeta(terrain = "meadow", layers = listOf(
        FoliageLayerMeta(id = 1, models = listOf(FoliageModelMeta("tree")), drawDistance = 60f)))

    @Test fun densityKeepsUnknownMembersOrderAndNumberText() {
        val after = before.copy(layers = listOf(before.layers.single().copy(density = 0.2f)))
        val result = FoliageMetaEdits().edit(text, before, after) as TextEditOutcome.Edited
        assertEquals(text.replace("\"density\":0.05", "\"density\":0.2"), result.text)
    }

    @Test fun anIdLessLayerEntryStaysThroughAnEdit() {
        val withJunk = text.replace("\"layers\":[", "\"layers\":[\"junk\",")
        val after = before.copy(layers = listOf(before.layers.single().copy(density = 0.2f)))
        val result = FoliageMetaEdits().edit(withJunk, before, after) as TextEditOutcome.Edited
        val layers = SceneJson().parse(result.text)["additional"]["layers"]
        assertEquals("junk", layers[0].asText())
        assertEquals(0.2, layers[1]["density"].asDouble(), 0.0)
    }

    @Test fun addingAppendsAndRemovingDeletesOnlyTheLayer() {
        val added = before.copy(layers = before.layers + FoliageLayerMeta(id = 2, models = listOf(FoliageModelMeta("rock"))))
        val result = FoliageMetaEdits().edit(text, before, added) as TextEditOutcome.Edited
        val layers = SceneJson().parse(result.text)["additional"]["layers"]
        assertEquals(SceneJson().parse(text)["additional"]["layers"][0], layers[0])
        assertEquals(2, layers[1]["id"].asInt())
        val removed = FoliageMetaEdits().edit(result.text, added, before) as TextEditOutcome.Edited
        assertEquals(text, removed.text)
    }

    @Test fun equalSettingsKeepOriginalTextAndOmittedDefaults() {
        assertEquals(TextEditOutcome.Unchanged, FoliageMetaEdits().edit(text, before, before))
    }

    @Test fun reorderingKeepsLayerExtensions() {
        val added = before.copy(layers = before.layers + FoliageLayerMeta(id = 2))
        val first = FoliageMetaEdits().edit(text, before, added) as TextEditOutcome.Edited
        val swapped = FoliageMetaEdits().edit(first.text, added, added.copy(layers = added.layers.reversed())) as TextEditOutcome.Edited
        assertEquals("north slope", SceneJson().parse(swapped.text)["additional"]["layers"][1]["note"].asText())
    }

    @Test fun changingNestedValuesKeepsExtensionsAndUntouchedDefaults() {
        val layer = before.layers.single().copy(models = listOf(FoliageModelMeta("tree", 3f)), scale = FoliageScale(max = 2f))
        val result = FoliageMetaEdits().edit(text, before, before.copy(layers = listOf(layer))) as TextEditOutcome.Edited
        val node = SceneJson().parse(result.text)["additional"]["layers"][0]
        assertEquals(42, node["models"][0]["custom"].asInt())
        assertEquals("tree", node["models"][0]["asset"].asText())
        assertFalse(node["scale"].has("min"))
        assertEquals("60.0", node["drawDistance"].asText())
    }

    @Test fun unsupportedDocumentRefusesEvenEqualSettings() {
        assertTrue(FoliageMetaEdits().edit(text.replace("\"formatVersion\":1", "\"formatVersion\":2"), before, before) is TextEditOutcome.Refused)
    }

    private val colliders = FoliageColliderEdits()
    private val layer = before.layers.single()
    private fun edit(source: String, from: FoliageMeta, to: FoliageMeta) = (FoliageMetaEdits().edit(source, from, to) as TextEditOutcome.Edited).text

    @Test fun addingAColliderWritesOnlyItAndKeepsTheRestOfTheText() {
        val capsule = FoliageCollider(FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 3f)
        val after = before.copy(layers = listOf(layer.copy(collider = colliders.patch(null, capsule))))
        val out = edit(text, before, after)
        val node = SceneJson().parse(out)["additional"]["layers"][0]
        assertEquals("CAPSULE", node["collider"]["shape"].asText())
        assertEquals(0.4, node["collider"]["radius"].asDouble(), 1e-6)
        assertEquals(3.0, node["collider"]["halfHeight"].asDouble(), 0.0)
        assertFalse(node["collider"].has("offset"))
        assertEquals("60.0", node["drawDistance"].asText())
        assertEquals(42, node["models"][0]["custom"].asInt())
        assertEquals(text.length < out.length, true)
        assertEquals("""{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","extension":60.0,"additional":{"terrain":"meadow","layers":[{"id":1,"note":"north slope","density":0.05,"models":[{"asset":"tree","weight":1.0,"custom":42}],"drawDistance":60.0,"collider":""", out.substringBefore("""{"shape""") )
    }

    @Test fun changingAColliderKeepsNestedUnknownMembersAndOrder() {
        val source = text.replace("\"drawDistance\":60.0", "\"drawDistance\":60.0,\"collider\":{\"shape\":\"BOX\",\"vendor\":{\"k\":2.50},\"halfExtents\":{\"x\":1.0,\"y\":2.0,\"z\":3.0}}")
        val read = FoliageSettingsReader(net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages())
            .read(SceneJson().parse(source)["additional"], setOf("tree")).settings
        val box = (FoliageColliderReader().read(read.layers.single().collider) as FoliageColliderRead.Valid).collider
        val moved = box.copy(halfY = 4f)
        val after = read.copy(layers = listOf(read.layers.single().copy(collider = colliders.patch(read.layers.single().collider, moved))))
        val node = SceneJson().parse(edit(source, read, after))["additional"]["layers"][0]["collider"]
        assertEquals(listOf("shape", "vendor", "halfExtents"), node.fieldNames().asSequence().toList())
        assertEquals("2.50", node["vendor"]["k"].asText())
        assertEquals(4.0, node["halfExtents"]["y"].asDouble(), 0.0)
        assertEquals(1.0, node["halfExtents"]["x"].asDouble(), 0.0)
    }

    @Test fun noneRemovesOnlyTheCollider() {
        val source = text.replace("\"drawDistance\":60.0", "\"drawDistance\":60.0,\"collider\":{\"shape\":\"SPHERE\",\"radius\":1.0}")
        val read = FoliageSettingsReader(net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages())
            .read(SceneJson().parse(source)["additional"], setOf("tree")).settings
        val after = read.copy(layers = listOf(read.layers.single().copy(collider = colliders.patch(read.layers.single().collider, null))))
        assertEquals(text, edit(source, read, after))
    }

    @Test fun omittedDefaultsStayOmittedAndAnOffsetIsWrittenOnlyWhenSet() {
        val sphere = colliders.patch(null, FoliageCollider(FoliageColliderShape.SPHERE, radius = 2f))!!
        assertEquals(listOf("shape", "radius"), sphere.fieldNames().asSequence().toList())
        val offset = colliders.patch(sphere, FoliageCollider(FoliageColliderShape.SPHERE, radius = 2f, offsetY = 1.5f))!!
        assertEquals(1.5, offset["offset"]["y"].asDouble(), 0.0)
        assertEquals(0.0, offset["offset"]["x"].asDouble(), 0.0)
    }

    @Test fun anInvalidStoredColliderIsKeptUnlessTheUserReplacesIt() {
        val source = text.replace("\"drawDistance\":60.0", "\"drawDistance\":60.0,\"collider\":{\"shape\":\"HULL\"}")
        val read = FoliageSettingsReader(net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages())
            .read(SceneJson().parse(source)["additional"], setOf("tree")).settings
        val after = read.copy(layers = listOf(read.layers.single().copy(density = 0.2f)))
        assertEquals(source.replace("\"density\":0.05", "\"density\":0.2"), edit(source, read, after))
    }
}
