/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterIssue
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterEditsTest {
    private val json = SceneJson()
    private var counter = 0
    private val edits = WaterEdits(newId = { "id-${counter++}" })
    private val scene = """{"format":"abyssus","formatVersion":1,"ecs":{"1":{"components":{"A":{"n":1.50}}}},"skyboxEnabled":false}"""

    private fun root(text: String = scene) = json.parse(text)
    private fun text(node: JsonNode) = json.compact(node)

    private fun withLake(root: JsonNode = root()): Pair<JsonNode, String> {
        val settings = edits.create(root, WaterPreset.LAKE, 1.0, 2.0, 3.0)
        assertEquals(WaterEdit.Changed, edits.add(root, settings))
        return root to settings.id
    }

    @Test fun addCreatesTheNamespaceAndKeepsOtherData() {
        val (root, id) = withLake()
        assertEquals(setOf(id), root.get("abyssus").get("waterSurfaces").fieldNames().asSequence().toSet())
        assertEquals("""{"n":1.50}""", text(root.get("ecs").get("1").get("components").get("A")))
        assertEquals(listOf("format", "formatVersion", "ecs", "skyboxEnabled", "abyssus"), root.fieldNames().asSequence().toList())
    }

    @Test fun addKeepsUnknownAbyssusMembersAndTheirOrder() {
        val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":{"first":1.0,"waterSurfaces":{"old":{"preset":"SEA","future":[1]}},"last":{"a":1}}}""")
        assertEquals(WaterEdit.Changed, edits.add(root, edits.create(root, WaterPreset.SEA, 0.0, 0.0, 0.0)))
        assertEquals(listOf("first", "waterSurfaces", "last"), root.get("abyssus").fieldNames().asSequence().toList())
        assertEquals("""{"preset":"SEA","future":[1]}""", text(root.get("abyssus").get("waterSurfaces").get("old")))
        assertEquals("1.0", text(root.get("abyssus").get("first")))
    }

    @Test fun incompatibleContainersAreNeverReplaced() {
        for (abyssus in listOf("[]", "5", "null", """{"waterSurfaces":[]}""", """{"waterSurfaces":"x"}""")) {
            val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":$abyssus}""")
            val before = text(root)
            val result = edits.add(root, edits.create(root, WaterPreset.LAKE, 0.0, 0.0, 0.0))
            assertEquals(abyssus, WaterEdit.Rejected(WaterEditError.IncompatibleContainer), result)
            assertEquals(before, text(root))
        }
    }

    @Test fun idCollisionIsRejectedWithoutChange() {
        val (root, id) = withLake()
        val before = text(root)
        val duplicate = edits.create(root, WaterPreset.SEA, 0.0, 0.0, 0.0).copy(id = id)
        assertEquals(WaterEdit.Rejected(WaterEditError.IdCollision), edits.add(root, duplicate))
        assertEquals(before, text(root))
    }

    @Test fun allocatedIdsSkipExistingOnes() {
        counter = 0
        val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":{"waterSurfaces":{"id-0":{"preset":"LAKE"},"id-1":{"preset":"LAKE"}}}}""")
        assertEquals("id-2", edits.allocateId(root))
    }

    @Test fun namesGetNumericSuffixes() {
        val root = root()
        edits.add(root, edits.create(root, WaterPreset.LAKE, 0.0, 0.0, 0.0))
        assertEquals("Lake 2", edits.uniqueName(root, WaterPreset.LAKE))
        assertEquals("Sea", edits.uniqueName(root, WaterPreset.SEA))
        edits.add(root, edits.create(root, WaterPreset.LAKE, 0.0, 0.0, 0.0))
        assertEquals("Lake 3", edits.uniqueName(root, WaterPreset.LAKE))
    }

    @Test fun invalidSurfacesCannotBeAdded() {
        val root = root()
        val bad = edits.create(root, WaterPreset.LAKE, 0.0, 0.0, 0.0).copy(width = 0.0)
        val result = edits.add(root, bad) as WaterEdit.Rejected
        assertEquals(WaterEditError.InvalidSurface(mapOf(WaterField.WIDTH to WaterIssue.NOT_POSITIVE)), result.error)
        assertEquals(text(json.parse(scene)), text(root))
        assertTrue(root.get("abyssus") == null)
    }

    @Test fun setFieldWritesOnlyThatNumber() {
        val (root, id) = withLake()
        val before = text(root.get("abyssus").get("waterSurfaces").get(id))
        assertEquals(WaterEdit.Changed, edits.setField(root, id, WaterField.LEVEL, 3.0))
        val after = root.get("abyssus").get("waterSurfaces").get(id)
        assertEquals(3.0, after.get("position").get("y").doubleValue(), 0.0)
        assertEquals(before.replace("\"y\":2", "\"y\":3"), text(after))
    }

    @Test fun sameValueIsANoOp() {
        val (root, id) = withLake()
        val before = text(root)
        assertEquals(WaterEdit.Unchanged, edits.setField(root, id, WaterField.LEVEL, 2.0))
        assertEquals(WaterEdit.Unchanged, edits.rename(root, id, "Lake"))
        assertEquals(WaterEdit.Unchanged, edits.setEnabled(root, id, true))
        assertEquals(before, text(root))
    }

    @Test fun invalidValuesAreRejectedAndNothingChanges() {
        val (root, id) = withLake()
        val before = text(root)
        assertEquals(WaterEdit.Rejected(WaterEditError.InvalidValue(WaterField.WIDTH, WaterIssue.NOT_POSITIVE)), edits.setField(root, id, WaterField.WIDTH, 0.0))
        assertEquals(WaterEdit.Rejected(WaterEditError.InvalidValue(WaterField.CLARITY, WaterIssue.NOT_FINITE)), edits.setField(root, id, WaterField.CLARITY, Double.NaN))
        val mixed = edits.setFields(root, id, mapOf(WaterField.LEVEL to 9.0, WaterField.FOAM_AMOUNT to 4.0))
        assertTrue(mixed is WaterEdit.Rejected)
        assertEquals(before, text(root))
    }

    @Test fun missingFieldsAndPartsAreCreatedOnlyForTheEditedValue() {
        val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":{"waterSurfaces":{"a":{"preset":"LAKE","unknown":true}}}}""")
        assertEquals(WaterEdit.Changed, edits.setField(root, "a", WaterField.TINT_G, 0.5))
        assertEquals("""{"preset":"LAKE","unknown":true,"tint":{"g":0.5}}""", text(root.get("abyssus").get("waterSurfaces").get("a")))
    }

    @Test fun nonObjectPartsRejectTheEdit() {
        val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":{"waterSurfaces":{"a":{"preset":"LAKE","position":5}}}}""")
        val before = text(root)
        assertEquals(WaterEdit.Rejected(WaterEditError.IncompatibleContainer), edits.setField(root, "a", WaterField.LEVEL, 1.0))
        assertEquals(before, text(root))
    }

    @Test fun moveWritesOnlyPositionMembersThatChange() {
        val (root, id) = withLake()
        val before = root.get("abyssus").get("waterSurfaces").get(id).deepCopy<JsonNode>()
        assertEquals(WaterEdit.Changed, edits.move(root, id, 1.0, 7.5, 3.0))
        val after = root.get("abyssus").get("waterSurfaces").get(id)
        assertEquals(7.5, after.get("position").get("y").doubleValue(), 0.0)
        for (key in listOf("name", "preset", "width", "length", "tint", "clarity", "enabled")) assertEquals(before.get(key), after.get(key))
        assertEquals(before.get("position").get("x"), after.get("position").get("x"))
    }

    @Test fun renameTrimsAndRejectsBlank() {
        val (root, id) = withLake()
        assertEquals(WaterEdit.Changed, edits.rename(root, id, "  Pond "))
        assertEquals("Pond", root.get("abyssus").get("waterSurfaces").get(id).get("name").textValue())
        assertEquals(WaterEdit.Rejected(WaterEditError.BlankName), edits.rename(root, id, "   "))
    }

    @Test fun toggleChangesOnlyEnabled() {
        val (root, id) = withLake()
        assertEquals(WaterEdit.Changed, edits.setEnabled(root, id, false))
        assertEquals(false, root.get("abyssus").get("waterSurfaces").get(id).get("enabled").booleanValue())
        assertEquals(WaterEdit.Changed, edits.setEnabled(root, id, true))
    }

    @Test fun unknownIdsAreNotFound() {
        val (root, _) = withLake()
        val before = text(root)
        for (result in listOf(
            edits.setField(root, "zzz", WaterField.LEVEL, 1.0), edits.rename(root, "zzz", "x"),
            edits.setEnabled(root, "zzz", false), edits.remove(root, "zzz"),
        )) assertEquals(WaterEdit.Rejected(WaterEditError.NotFound), result)
        assertEquals(before, text(root))
        assertEquals(WaterEdit.Rejected(WaterEditError.NotFound), edits.remove(root(), "a"))
    }

    @Test fun removingTheLastSurfaceKeepsAnEmptyContainerAndSiblings() {
        val root = root("""{"format":"abyssus","formatVersion":1,"abyssus":{"keep":[1,2],"waterSurfaces":{"a":{"preset":"LAKE"}}}}""")
        assertEquals(WaterEdit.Changed, edits.remove(root, "a"))
        assertEquals("""{"keep":[1,2],"waterSurfaces":{}}""", text(root.get("abyssus")))
    }

    @Test fun removeOnlyDropsTheNamedSurface() {
        val root = root()
        val a = edits.create(root, WaterPreset.LAKE, 0.0, 0.0, 0.0).also { edits.add(root, it) }
        val b = edits.create(root, WaterPreset.SEA, 0.0, 0.0, 0.0).also { edits.add(root, it) }
        edits.remove(root, a.id)
        assertEquals(listOf(b.id), root.get("abyssus").get("waterSurfaces").fieldNames().asSequence().toList())
    }

    @Test fun ecsDataAndNativeMarkersNeverChange() {
        val root = root()
        val ecsBefore = text(root.get("ecs"))
        val (_, id) = withLake(root)
        edits.setField(root, id, WaterField.LEVEL, 5.0)
        edits.remove(root, id)
        assertEquals(ecsBefore, text(root.get("ecs")))
        assertEquals("abyssus", root.get("format").textValue())
        assertEquals(1, root.get("formatVersion").intValue())
    }
}
