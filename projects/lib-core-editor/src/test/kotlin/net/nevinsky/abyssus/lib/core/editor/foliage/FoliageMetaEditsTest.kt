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
}
