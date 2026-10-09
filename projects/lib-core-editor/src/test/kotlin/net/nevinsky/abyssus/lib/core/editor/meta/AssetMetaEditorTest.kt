/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.meta

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetMetaEditorTest {
    private val descriptions = AssetFieldDescriptions()
    private val editor = AssetMetaEditor(descriptions)
    private val json = JsonProcessor()
    private val untitled = testProject("Untitled")

    private fun tree(folder: String): JsonNode = json.readObject(File(untitled, "assets/$folder/meta.json").readText())
    private fun terrain() = tree("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
    private fun field(type: MetaType, key: String) = descriptions.field(type, key)!!
    private fun cur(root: JsonNode, type: MetaType, key: String) = editor.current(root, field(type, key))

    private fun edit(root: JsonNode, type: MetaType, key: String, value: FieldValue): EditOutcome =
        editor.edit(root, key, cur(root, type, key), value)

    private fun rejected(outcome: EditOutcome): EditError = (outcome as EditOutcome.Rejected).error

    @Test fun legacyMetadataIsRefusedWithoutMutation() {
        val root = JsonProcessor().readObject("""{"type":"TERRAIN","additional":{"size":100}}""")
        val before = root.toString()
        val outcome = AssetMetaEditor(AssetFieldDescriptions()).edit(root, "size", FieldValue.Int(100), FieldValue.Int(200))
        org.junit.Assert.assertTrue(outcome is EditOutcome.Rejected)
        org.junit.Assert.assertEquals(before, root.toString())
    }

    @Test
    fun `terrain size and uv are read as the file holds them`() {
        val root = terrain()
        assertEquals(MetaType.TERRAIN, editor.typeOf(root))
        assertEquals(FieldValue.Int(1600), cur(root, MetaType.TERRAIN, "size"))
        assertEquals(FieldValue.Real(60f), cur(root, MetaType.TERRAIN, "uv"))
        assertEquals(FieldValue.None, cur(root, MetaType.TERRAIN, "splatBase"))
    }

    @Test
    fun `an integer edit changes only that key`() {
        val root = terrain()
        val before = root.deepCopy<JsonNode>()
        assertEquals(EditOutcome.Changed, edit(root, MetaType.TERRAIN, "size", FieldValue.Int(800)))
        (before.get("additional") as com.fasterxml.jackson.databind.node.ObjectNode).put("size", 800)
        assertEquals(before, root)
        assertEquals(
            listOf("terrainFile", "size", "uv", "splatMap", "splatBase", "splatR", "splatG", "splatB", "splatA"),
            root.get("additional").fieldNames().asSequence().toList(),
        )
    }

    @Test
    fun `a float edit and a reference edit`() {
        val root = terrain()
        assertEquals(EditOutcome.Changed, edit(root, MetaType.TERRAIN, "uv", FieldValue.Real(30f)))
        assertEquals(30.0, root.get("additional").get("uv").asDouble(), 0.0)
        val id = "2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
        assertEquals(EditOutcome.Changed, edit(root, MetaType.TERRAIN, "splatBase", FieldValue.Text(id)))
        assertEquals(id, root.get("additional").get("splatBase").asText())
        assertEquals(EditOutcome.Changed, edit(root, MetaType.TERRAIN, "splatBase", FieldValue.None))
        assertTrue(root.get("additional").get("splatBase").isNull)
    }

    @Test
    fun `cube faces take local file names`() {
        val root = tree("skybox_default")
        assertEquals(FieldValue.Text("skybox_default.png"), cur(root, MetaType.SKYBOX, "left"))
        assertEquals(EditOutcome.Changed, edit(root, MetaType.SKYBOX, "left", FieldValue.Text("other.png")))
        assertEquals("other.png", root.get("additional").get("left").asText())
        assertEquals("skybox_default.png", root.get("additional").get("right").asText())
        for (bad in listOf("../x.png", "/etc/passwd", "C:x.png", "a//b.png", "a/./b.png")) {
            assertEquals(bad, EditError.BAD_FILE_NAME, rejected(edit(root, MetaType.SKYBOX, "top", FieldValue.Text(bad))))
        }
        assertEquals(EditError.EMPTY, rejected(edit(root, MetaType.SKYBOX, "top", FieldValue.Text(" "))))
    }

    @Test
    fun `an omitted atmosphere field shows its default and an edit adds only that key`() {
        val root = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f","planetRadius":6360000.0}}""")
        assertEquals(FieldValue.Real(20f), cur(root, MetaType.SKYBOX_PROCEDURAL, "sunIntensity"))
        assertEquals(EditOutcome.NoChange, edit(root, MetaType.SKYBOX_PROCEDURAL, "sunIntensity", FieldValue.Real(20f)))
        assertEquals(listOf("vertex", "fragment", "planetRadius"), root.get("additional").fieldNames().asSequence().toList())
        assertEquals(EditOutcome.Changed, edit(root, MetaType.SKYBOX_PROCEDURAL, "sunIntensity", FieldValue.Real(25f)))
        assertEquals(listOf("vertex", "fragment", "planetRadius", "sunIntensity"), root.get("additional").fieldNames().asSequence().toList())
    }

    @Test
    fun `an equal value is not a change`() {
        val root = terrain()
        assertEquals(EditOutcome.NoChange, edit(root, MetaType.TERRAIN, "size", FieldValue.Int(1600)))
        assertEquals(EditOutcome.NoChange, edit(root, MetaType.TERRAIN, "splatMap", FieldValue.None))
        assertEquals(terrain(), root)
    }

    @Test
    fun `identity bookkeeping and file keys are not editable`() {
        val root = terrain()
        for (key in listOf("uuid", "type", "version", "lastModified", "terrainFile", "additional", "missing")) {
            assertEquals(key, EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD), editor.edit(root, key, FieldValue.None, FieldValue.Text("x")))
        }
        assertEquals(EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD), editor.edit(tree("tree"), "size", FieldValue.None, FieldValue.Int(1)))
        assertEquals(terrain(), root)
    }

    @Test
    fun `a stale expected value is a conflict`() {
        val root = terrain()
        val outcome = editor.edit(root, "size", FieldValue.Int(1000), FieldValue.Int(800))
        assertEquals(EditOutcome.Conflict(FieldValue.Int(1600)), outcome)
        assertEquals(terrain(), root)
    }

    @Test
    fun `invalid numbers are rejected`() {
        val root = terrain()
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "size", FieldValue.Int(0))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "size", FieldValue.Int(-4))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "uv", FieldValue.Real(0f))))
        assertEquals(EditError.NOT_FINITE, rejected(edit(root, MetaType.TERRAIN, "uv", FieldValue.Real(Float.NaN))))
        assertEquals(EditError.NOT_FINITE, rejected(edit(root, MetaType.TERRAIN, "uv", FieldValue.Real(Float.POSITIVE_INFINITY))))
        assertEquals(EditError.NOT_A_UUID, rejected(edit(root, MetaType.TERRAIN, "splatMap", FieldValue.Text("nope"))))
        assertEquals(terrain(), root)
    }

    @Test
    fun `typed text is parsed by kind`() {
        val size = field(MetaType.TERRAIN, "size")
        assertEquals(ParseOutcome.Parsed(FieldValue.Int(800)), editor.parse(size, " 800 "))
        assertEquals(ParseOutcome.Failed(EditError.NOT_AN_INTEGER), editor.parse(size, "12.5"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_AN_INTEGER), editor.parse(size, "99999999999"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(size, "abc"))
        val uv = field(MetaType.TERRAIN, "uv")
        assertEquals(ParseOutcome.Parsed(FieldValue.Real(0.5f)), editor.parse(uv, "0.5"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(uv, "NaN"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(uv, "1e999"))
        val beta = field(MetaType.SKYBOX_PROCEDURAL, "betaRayleigh")
        assertEquals(ParseOutcome.Parsed(FieldValue.Reals(listOf(1f, 2f, 3f))), editor.parse(beta, "1, 2, 3"))
        assertEquals(ParseOutcome.Failed(EditError.WRONG_COUNT), editor.parse(beta, "1, 2"))
        assertEquals(ParseOutcome.Parsed(FieldValue.None), editor.parse(field(MetaType.TERRAIN, "splatA"), " "))
    }

    @Test
    fun `atmosphere parameters are validated as a set`() {
        val root = tree("skybox_physical")
        val type = MetaType.SKYBOX_PROCEDURAL
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "atmosphereRadius", FieldValue.Real(6360000f))))
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "planetRadius", FieldValue.Real(6420000f))))
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "planetRadius", FieldValue.Real(7000000f))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, type, "heightMie", FieldValue.Real(0f))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "betaMie", FieldValue.Real(-1f))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "sunIntensity", FieldValue.Real(-0.5f))))
        assertEquals(EditError.MIE_G_RANGE, rejected(edit(root, type, "mieG", FieldValue.Real(1f))))
        assertEquals(EditError.MIE_G_RANGE, rejected(edit(root, type, "mieG", FieldValue.Real(-1f))))
        assertEquals(EditError.WRONG_COUNT, rejected(edit(root, type, "betaRayleigh", FieldValue.Reals(listOf(1f, 2f)))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "betaRayleigh", FieldValue.Reals(listOf(1f, -2f, 3f)))))
        assertEquals(tree("skybox_physical"), root)
        assertEquals(EditOutcome.Changed, edit(root, type, "mieG", FieldValue.Real(0.5f)))
        assertEquals(EditOutcome.Changed, edit(root, type, "betaRayleigh", FieldValue.Reals(listOf(1e-6f, 2e-6f, 3e-6f))))
        assertEquals(EditOutcome.Changed, edit(root, type, "atmosphereRadius", FieldValue.Real(6500000f)))
    }

    @Test
    fun `an omitted radius is checked against the default of the other`() {
        val root = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{}}""")
        assertEquals(
            EditError.ATMOSPHERE_ORDER,
            rejected(edit(root, MetaType.SKYBOX_PROCEDURAL, "planetRadius", FieldValue.Real(6500000f))),
        )
    }

    @Test
    fun `a stored value of the wrong shape is shown and can be replaced`() {
        val root = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"TERRAIN","uuid":"u","additional":{"size":"big","uv":1.0,"splatMap":5}}""")
        val size = cur(root, MetaType.TERRAIN, "size")
        assertEquals(FieldValue.Invalid("\"big\""), size)
        assertEquals(FieldValue.Invalid("5"), cur(root, MetaType.TERRAIN, "splatMap"))
        assertEquals(EditOutcome.Changed, editor.edit(root, "size", size, FieldValue.Int(100)))
        assertEquals(100, root.get("additional").get("size").asInt())
    }

    @Test
    fun `an unknown type has no editable fields`() {
        assertTrue(descriptions.fields(MetaType.MODEL).isEmpty())
        assertTrue(descriptions.fields(MetaType.SKYBOX_HDR).isEmpty())
        assertEquals(MetaType.UNKNOWN, editor.typeOf(json.readObject("""{"type":"NEW_THING"}""")))
    }
}
