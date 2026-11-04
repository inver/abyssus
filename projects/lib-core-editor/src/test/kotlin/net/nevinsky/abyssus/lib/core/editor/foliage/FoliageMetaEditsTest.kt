/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.foliage.*
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/**
 * The foliage block is edited as a typed [FoliageMeta]: a layer or collider member the typed block does not model (a
 * `note`, a vendor key) is not kept when the meta is written back.
 */
class FoliageMetaEditsTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val parsing = DocumentParsing(json, NOPLogger.NOP_LOGGER)
    private val edits = FoliageMetaEdits(AssetMetaTextEditor(AssetMetaParser(json)))
    private val colliders = FoliageColliderEdits(json)

    private val text =
        """{"formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"meadow","layers":[{"id":1,"density":0.05,
"models":[{"asset":"tree","weight":1.0}],"drawDistance":60.0}]}}"""
    private val before =
        FoliageMeta(
            terrain = "meadow",
            layers = listOf(FoliageLayerMeta(id = 1, models = listOf(FoliageModelMeta("tree")), drawDistance = 60f)),
        )
    private val layer = before.layers.single()

    private fun settings(editedText: String) = parsing.foliageMeta(editedText)

    private fun edit(source: String, from: FoliageMeta, to: FoliageMeta) =
        (edits.edit(source, from, to) as TextEditOutcome.Edited).text

    @Test
    fun aDensityChangeIsWrittenAndTheRestIsRead() {
        val after = before.copy(layers = listOf(layer.copy(density = 0.2f)))
        val written = settings(edit(text, before, after))
        assertEquals(0.2f, written.layers.single().density, 0f)
        assertEquals("tree", written.layers.single().models.single().asset)
        assertEquals(60f, written.layers.single().drawDistance, 0f)
        assertEquals("meadow", written.terrain)
    }

    @Test
    fun addingAppendsAndRemovingDeletesOnlyTheLayer() {
        val added =
            before.copy(layers = before.layers + FoliageLayerMeta(id = 2, models = listOf(FoliageModelMeta("rock"))))
        val withTwo = edit(text, before, added)
        assertEquals(listOf(1, 2), settings(withTwo).layers.map { it.id })
        val back = edit(withTwo, added, before)
        assertEquals(before.layers.map { it.id }, settings(back).layers.map { it.id })
    }

    @Test
    fun equalSettingsAreUnchanged() {
        assertEquals(TextEditOutcome.Unchanged, edits.edit(text, before, before))
    }

    @Test
    fun reorderingKeepsEachLayer() {
        val added = before.copy(layers = before.layers + FoliageLayerMeta(id = 2))
        val first = edit(text, before, added)
        val swapped = edit(first, added, added.copy(layers = added.layers.reversed()))
        assertEquals(listOf(2, 1), settings(swapped).layers.map { it.id })
    }

    @Test
    fun nestedValuesChange() {
        val changed = layer.copy(models = listOf(FoliageModelMeta("tree", 3f)), scale = FoliageScale(max = 2f))
        val written = settings(edit(text, before, before.copy(layers = listOf(changed)))).layers.single()
        assertEquals(3f, written.models.single().weight, 0f)
        assertEquals(2f, written.scale.max, 0f)
    }

    @Test
    fun aTextThatIsNotAJsonObjectIsRefused() {
        assertTrue(edits.edit("not json", before, before.copy(terrain = "other")) is TextEditOutcome.Refused)
    }

    private fun colliderOf(l: FoliageLayerMeta) = FoliageColliderReader().read(l.collider)

    @Test
    fun addingAColliderWritesIt() {
        val capsule = FoliageCollider(FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 3f)
        val after = before.copy(layers = listOf(colliders.patch(layer, capsule)))
        val read = settings(edit(text, before, after)).layers.single()
        val collider = (colliderOf(read) as FoliageColliderRead.Valid).collider
        assertEquals(FoliageColliderShape.CAPSULE, collider.shape)
        assertEquals(0.4f, collider.radius, 1e-6f)
        assertEquals(3f, collider.halfHeight, 0f)
        assertEquals(0f, collider.offsetX, 0f)
        assertEquals(60f, read.drawDistance, 0f)
    }

    @Test
    fun changingAColliderKeepsTheOtherSizes() {
        val box = FoliageCollider(FoliageColliderShape.BOX, halfX = 1f, halfY = 2f, halfZ = 3f)
        val withBox = layer.copy(collider = colliders.patch(layer, box).collider)
        val moved = colliders.patch(withBox, box.copy(halfY = 4f))
        val collider = (colliderOf(moved) as FoliageColliderRead.Valid).collider
        assertEquals(1f, collider.halfX, 0f)
        assertEquals(4f, collider.halfY, 0f)
        assertEquals(3f, collider.halfZ, 0f)
    }

    @Test
    fun noneRemovesOnlyTheCollider() {
        val sphere = colliders.patch(layer, FoliageCollider(FoliageColliderShape.SPHERE, radius = 1f))
        val removed = colliders.patch(sphere, null)
        assertNull(removed.collider)
        assertEquals(layer, removed)
    }

    @Test
    fun anOffsetIsWrittenOnlyWhenSet() {
        val sphere = colliders.patch(layer, FoliageCollider(FoliageColliderShape.SPHERE, radius = 2f))
        val plain = (colliderOf(sphere) as FoliageColliderRead.Valid).collider
        assertEquals(0f, plain.offsetY, 0f)
        assertFalse(sphere.collider.toString().contains("offset"))
        val offset = colliders.patch(sphere, FoliageCollider(FoliageColliderShape.SPHERE, radius = 2f, offsetY = 1.5f))
        assertEquals(1.5f, (colliderOf(offset) as FoliageColliderRead.Valid).collider.offsetY, 0f)
    }

    @Test
    fun onlyCollidersIsTrueWhenNothingElseChanged() {
        val capsule = FoliageCollider(FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 3f)
        val withCollider = before.copy(layers = listOf(colliders.patch(layer, capsule)))
        assertTrue(colliders.onlyColliders(before, withCollider))
        assertFalse(colliders.onlyColliders(before, withCollider.copy(terrain = "other")))
        assertFalse(colliders.onlyColliders(before, before))
    }

    @Test
    fun anInvalidStoredColliderIsReadAsInvalid() {
        val source = text.replace("\"drawDistance\":60.0", "\"drawDistance\":60.0,\"collider\":{\"shape\":\"HULL\"}")
        val read = FoliageSettingsReader(ResourceEditorMessages()).read(settings(source), setOf("tree"))
        assertTrue(colliderOf(read.settings.layers.single()) is FoliageColliderRead.Invalid)
        assertTrue(read.problems.any { it.field == FOLIAGE_COLLIDER })
    }
}
