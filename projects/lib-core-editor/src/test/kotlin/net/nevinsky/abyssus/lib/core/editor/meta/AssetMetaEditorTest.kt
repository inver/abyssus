/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.meta

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetMetaEditorTest {
    private val descriptions = AssetFieldDescriptions()
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val editor = AssetMetaEditor(descriptions, json)
    private val parser = AssetMetaParser(json)
    private val untitled = testProject("Untitled")

    private fun meta(folder: String): AssetMeta<Any> = parser.read(folder, File(untitled, "assets/$folder/meta.json").readText())
    private fun terrain() = meta("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
    private fun field(type: MetaType, key: String) = descriptions.field(type, key)!!
    private fun cur(meta: AssetMeta<Any>, type: MetaType, key: String) = editor.current(meta.additional, field(type, key))

    private fun edit(meta: AssetMeta<Any>, type: MetaType, key: String, value: AssetFieldValue): TypedAssetEdit =
        editor.edit(meta, key, cur(meta, type, key), value)

    /** [edit] that is applied: the edited meta, for a chain of edits. */
    private fun changed(meta: AssetMeta<Any>, type: MetaType, key: String, value: AssetFieldValue): AssetMeta<Any> {
        val edit = edit(meta, type, key, value)
        assertEquals(EditOutcome.Changed, edit.outcome)
        return edit.meta!!
    }

    private fun rejected(edit: TypedAssetEdit): EditError = (edit.outcome as EditOutcome.Rejected).error

    @Test fun legacyMetadataIsRefusedWithoutAnEdit() {
        val text = """{"type":"TERRAIN","additional":{"size":100}}"""
        val outcome = editor.editText(text, "size", AssetFieldValue.Int(100), AssetFieldValue.Int(200))!!
        assertEquals(EditOutcome.Rejected(EditError.UNSUPPORTED_FORMAT), outcome.outcome)
        assertNull(outcome.text)
        assertNull(editor.editText("not json", "size", AssetFieldValue.None, AssetFieldValue.Int(1)))
    }

    @Test
    fun `terrain size and uv are read as the file holds them`() {
        val meta = terrain()
        assertEquals(MetaType.TERRAIN, meta.type)
        assertEquals(AssetFieldValue.Int(1600), cur(meta, MetaType.TERRAIN, "size"))
        assertEquals(AssetFieldValue.Real(60f), cur(meta, MetaType.TERRAIN, "uv"))
        assertEquals(AssetFieldValue.None, cur(meta, MetaType.TERRAIN, "splatBase"))
    }

    @Test
    fun `an integer edit changes only that field`() {
        val before = terrain()
        val after = changed(before, MetaType.TERRAIN, "size", AssetFieldValue.Int(800))
        assertEquals((before.additional as TerrainMeta).copy(size = 800), after.additional)
        assertEquals(before.uuid, after.uuid)
        assertEquals(before.version, after.version)
        assertEquals(before.lastModified, after.lastModified)
    }

    @Test
    fun `a float edit and a reference edit`() {
        var meta = changed(terrain(), MetaType.TERRAIN, "uv", AssetFieldValue.Real(30f))
        assertEquals(30f, (meta.additional as TerrainMeta).uv, 0f)
        val id = "2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
        meta = changed(meta, MetaType.TERRAIN, "splatBase", AssetFieldValue.Text(id))
        assertEquals(id, (meta.additional as TerrainMeta).splatBase)
        meta = changed(meta, MetaType.TERRAIN, "splatBase", AssetFieldValue.None)
        assertNull((meta.additional as TerrainMeta).splatBase)
    }

    @Test
    fun `cube faces take local file names`() {
        val meta = meta("skybox_default")
        assertEquals(AssetFieldValue.Text("skybox_default.png"), cur(meta, MetaType.SKYBOX, "left"))
        val edited = changed(meta, MetaType.SKYBOX, "left", AssetFieldValue.Text("other.png")).additional as SkyboxMeta
        assertEquals("other.png", edited.left)
        assertEquals("skybox_default.png", edited.right)
        for (bad in listOf("../x.png", "/etc/passwd", "C:x.png", "a//b.png", "a/./b.png")) {
            assertEquals(bad, EditError.BAD_FILE_NAME, rejected(edit(meta, MetaType.SKYBOX, "top", AssetFieldValue.Text(bad))))
        }
        assertEquals(EditError.EMPTY, rejected(edit(meta, MetaType.SKYBOX, "top", AssetFieldValue.Text(" "))))
    }

    @Test
    fun `an omitted atmosphere field shows its default and an edit sets only that one`() {
        val meta = parser.read("", """{"formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f","planetRadius":6360000.0}}""")
        assertEquals(AssetFieldValue.Real(20f), cur(meta, MetaType.SKYBOX_PROCEDURAL, "sunIntensity"))
        assertEquals(EditOutcome.NoChange, edit(meta, MetaType.SKYBOX_PROCEDURAL, "sunIntensity", AssetFieldValue.Real(20f)).outcome)
        val edited = changed(meta, MetaType.SKYBOX_PROCEDURAL, "sunIntensity", AssetFieldValue.Real(25f)).additional as ProceduralSkyMeta
        assertEquals(25f, edited.sunIntensity)
        assertEquals("v", edited.vertex)
        assertEquals(6360000f, edited.planetRadius)
        assertNull(edited.mieG)
    }

    @Test
    fun `an equal value is not a change`() {
        val meta = terrain()
        assertEquals(EditOutcome.NoChange, edit(meta, MetaType.TERRAIN, "size", AssetFieldValue.Int(1600)).outcome)
        assertEquals(EditOutcome.NoChange, edit(meta, MetaType.TERRAIN, "splatMap", AssetFieldValue.None).outcome)
    }

    @Test
    fun `identity bookkeeping and file keys are not editable`() {
        val meta = terrain()
        for (key in listOf("uuid", "type", "version", "lastModified", "terrainFile", "additional", "missing")) {
            assertEquals(key, EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD), editor.edit(meta, key, AssetFieldValue.None, AssetFieldValue.Text("x")).outcome)
        }
        assertEquals(EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD), editor.edit(meta("tree"), "size", AssetFieldValue.None, AssetFieldValue.Int(1)).outcome)
    }

    @Test
    fun `a stale expected value is a conflict`() {
        val outcome = editor.edit(terrain(), "size", AssetFieldValue.Int(1000), AssetFieldValue.Int(800)).outcome
        assertEquals(EditOutcome.Conflict(AssetFieldValue.Int(1600)), outcome)
    }

    @Test
    fun `invalid numbers are rejected`() {
        val root = terrain()
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "size", AssetFieldValue.Int(0))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "size", AssetFieldValue.Int(-4))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, MetaType.TERRAIN, "uv", AssetFieldValue.Real(0f))))
        assertEquals(EditError.NOT_FINITE, rejected(edit(root, MetaType.TERRAIN, "uv", AssetFieldValue.Real(Float.NaN))))
        assertEquals(EditError.NOT_FINITE, rejected(edit(root, MetaType.TERRAIN, "uv", AssetFieldValue.Real(Float.POSITIVE_INFINITY))))
        assertEquals(EditError.NOT_A_UUID, rejected(edit(root, MetaType.TERRAIN, "splatMap", AssetFieldValue.Text("nope"))))
    }

    @Test
    fun `asset reference text must be a uuid`() {
        val root = terrain()
        val uuid = "2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
        assertEquals(EditError.NOT_A_UUID, rejected(edit(root, MetaType.TERRAIN, "splatMap", AssetFieldValue.Text(""))))
        assertEquals(EditError.NOT_A_UUID, rejected(edit(root, MetaType.TERRAIN, "splatMap", AssetFieldValue.Text("not-a-uuid"))))
        assertEquals(false, edit(root, MetaType.TERRAIN, "splatMap", AssetFieldValue.Text(uuid)).outcome is EditOutcome.Rejected)
    }

    @Test
    fun `an edit of the text writes a pretty native meta`() {
        val text = File(untitled, "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/meta.json").readText()
        val result = editor.editText(text, "size", AssetFieldValue.Int(1600), AssetFieldValue.Int(800))!!
        assertEquals(EditOutcome.Changed, result.outcome)
        val written = parser.read("", result.text!!)
        assertEquals(800, (written.additional as TerrainMeta).size)
        assertTrue(result.text!!.contains("\n  \"formatVersion\": 1"))
        assertNull(editor.editText(text, "size", AssetFieldValue.Int(1600), AssetFieldValue.Int(1600))!!.text)
    }

    @Test
    fun `typed text is parsed by kind`() {
        val size = field(MetaType.TERRAIN, "size")
        assertEquals(ParseOutcome.Parsed(AssetFieldValue.Int(800)), editor.parse(size, " 800 "))
        assertEquals(ParseOutcome.Failed(EditError.NOT_AN_INTEGER), editor.parse(size, "12.5"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_AN_INTEGER), editor.parse(size, "99999999999"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(size, "abc"))
        val uv = field(MetaType.TERRAIN, "uv")
        assertEquals(ParseOutcome.Parsed(AssetFieldValue.Real(0.5f)), editor.parse(uv, "0.5"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(uv, "NaN"))
        assertEquals(ParseOutcome.Failed(EditError.NOT_A_NUMBER), editor.parse(uv, "1e999"))
        val beta = field(MetaType.SKYBOX_PROCEDURAL, "betaRayleigh")
        assertEquals(ParseOutcome.Parsed(AssetFieldValue.Reals(listOf(1f, 2f, 3f))), editor.parse(beta, "1, 2, 3"))
        assertEquals(ParseOutcome.Failed(EditError.WRONG_COUNT), editor.parse(beta, "1, 2"))
        assertEquals(ParseOutcome.Parsed(AssetFieldValue.None), editor.parse(field(MetaType.TERRAIN, "splatA"), " "))
    }

    @Test
    fun `atmosphere parameters are validated as a set`() {
        val root = meta("skybox_physical")
        val type = MetaType.SKYBOX_PROCEDURAL
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "atmosphereRadius", AssetFieldValue.Real(6360000f))))
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "planetRadius", AssetFieldValue.Real(6420000f))))
        assertEquals(EditError.ATMOSPHERE_ORDER, rejected(edit(root, type, "planetRadius", AssetFieldValue.Real(7000000f))))
        assertEquals(EditError.NOT_POSITIVE, rejected(edit(root, type, "heightMie", AssetFieldValue.Real(0f))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "betaMie", AssetFieldValue.Real(-1f))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "sunIntensity", AssetFieldValue.Real(-0.5f))))
        assertEquals(EditError.MIE_G_RANGE, rejected(edit(root, type, "mieG", AssetFieldValue.Real(1f))))
        assertEquals(EditError.MIE_G_RANGE, rejected(edit(root, type, "mieG", AssetFieldValue.Real(-1f))))
        assertEquals(EditError.WRONG_COUNT, rejected(edit(root, type, "betaRayleigh", AssetFieldValue.Reals(listOf(1f, 2f)))))
        assertEquals(EditError.NEGATIVE, rejected(edit(root, type, "betaRayleigh", AssetFieldValue.Reals(listOf(1f, -2f, 3f)))))
        assertEquals(EditOutcome.Changed, edit(root, type, "mieG", AssetFieldValue.Real(0.5f)).outcome)
        assertEquals(EditOutcome.Changed, edit(root, type, "betaRayleigh", AssetFieldValue.Reals(listOf(1e-6f, 2e-6f, 3e-6f))).outcome)
        assertEquals(EditOutcome.Changed, edit(root, type, "atmosphereRadius", AssetFieldValue.Real(6500000f)).outcome)
    }

    @Test
    fun `an omitted radius is checked against the default of the other`() {
        val root = parser.read("", """{"formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{}}""")
        assertEquals(
            EditError.ATMOSPHERE_ORDER,
            rejected(edit(root, MetaType.SKYBOX_PROCEDURAL, "planetRadius", AssetFieldValue.Real(6500000f))),
        )
    }

    @Test
    fun `a stored value of the wrong shape reads as the default and can be replaced`() {
        val meta = parser.read("", """{"formatVersion":1,"type":"TERRAIN","additional":{"size":"big","uv":1.0}}""")
        val size = cur(meta, MetaType.TERRAIN, "size")
        assertEquals(AssetFieldValue.None, size)
        val edited = editor.edit(meta, "size", size, AssetFieldValue.Int(100))
        assertEquals(EditOutcome.Changed, edited.outcome)
        assertEquals(100, (edited.meta!!.additional as TerrainMeta).size)
    }

    @Test
    fun `an unknown type has no editable fields`() {
        assertTrue(descriptions.fields(MetaType.MODEL).isEmpty())
        assertTrue(descriptions.fields(MetaType.SKYBOX_HDR).isEmpty())
        assertEquals(MetaType.UNKNOWN, parser.read("", """{"formatVersion":1,"type":"NEW_THING","additional":{}}""").type)
    }
}
