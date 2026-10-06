/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.meta


import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class AssetReferenceChoicesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val choices = AssetReferenceChoices(JsonProcessor())

    private fun asset(name: String, type: String, uuid: String?, file: String? = "tex.png", write: Boolean = true): File {
        val dir = File(tmp.root, "assets/$name").apply { mkdirs() }
        val id = uuid?.let { """"uuid":"$it",""" } ?: ""
        val f = file?.let { """"file":"$it"""" } ?: """"x":1"""
        File(dir, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,$id"type":"$type","additional":{$f}}""")
        if (write && file != null) File(dir, file).apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        return dir
    }

    private val assetsDir get() = File(tmp.root, "assets")

    @Test
    fun `no current value offers none and the readable textures by folder name`() {
        asset("b_tex", "TEXTURE", "22222222-2222-2222-2222-222222222222")
        asset("a_pix", "PIXMAP_TEXTURE", "11111111-1111-1111-1111-111111111111", "p.jpg")
        assertEquals(
            listOf(
                AssetChoice(null, ""),
                AssetChoice("11111111-1111-1111-1111-111111111111", "a_pix"),
                AssetChoice("22222222-2222-2222-2222-222222222222", "b_tex"),
            ),
            choices.textures(assetsDir, null),
        )
    }

    @Test
    fun `assets of other types, without a uuid or without a readable image are not offered`() {
        asset("model", "MODEL", "33333333-3333-3333-3333-333333333333")
        asset("nouuid", "TEXTURE", null)
        asset("missing", "TEXTURE", "44444444-4444-4444-4444-444444444444", write = false)
        asset("nofile", "TEXTURE", "55555555-5555-5555-5555-555555555555", file = null)
        asset("notimage", "TEXTURE", "66666666-6666-6666-6666-666666666666", "data.bin")
        File(assetsDir, "plain").mkdirs()
        File(assetsDir, "broken").apply { mkdirs() }.resolve("meta.json").writeText("{ not json")
        assertEquals(listOf(AssetChoice(null, "")), choices.textures(assetsDir, null))
    }

    @Test
    fun `a missing assets folder offers only none`() {
        assertEquals(listOf(AssetChoice(null, "")), choices.textures(File(tmp.root, "nothing"), null))
    }

    @Test
    fun `a current uuid that resolves is not duplicated`() {
        asset("tex", "TEXTURE", "11111111-1111-1111-1111-111111111111")
        val list = choices.textures(assetsDir, "11111111-1111-1111-1111-111111111111")
        assertEquals(2, list.size)
        assertTrue(list.all { it.resolved })
    }

    @Test
    fun `an unresolved current uuid stays visible but is marked`() {
        asset("tex", "TEXTURE", "11111111-1111-1111-1111-111111111111")
        asset("model", "MODEL", "33333333-3333-3333-3333-333333333333")
        for (current in listOf("99999999-9999-9999-9999-999999999999", "33333333-3333-3333-3333-333333333333")) {
            val list = choices.textures(assetsDir, current)
            assertEquals(AssetChoice(current, current, resolved = false), list[1])
            assertEquals(listOf("tex"), list.filter { it.resolved && it.value != null }.map { it.label })
        }
    }

    @Test
    fun `faces lists images inside the folder with slash paths`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        File(dir, "front.png").writeBytes(byteArrayOf(1))
        File(dir, "sub").mkdirs()
        File(dir, "sub/Back.JPG").writeBytes(byteArrayOf(1))
        File(dir, "notes.txt").writeText("x")
        File(dir, "model.tga").writeBytes(byteArrayOf(1))
        assertEquals(
            listOf(null, "front.png", "sub/Back.JPG"),
            choices.faces(dir, "front.png").map { it.value },
        )
    }

    @Test
    fun `an unresolved face is kept first and marked`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        File(dir, "a.png").writeBytes(byteArrayOf(1))
        val list = choices.faces(dir, "gone.png")
        assertEquals(listOf(null, "gone.png", "a.png"), list.map { it.value })
        assertFalse(list[1].resolved)
    }

    @Test
    fun `a name resolving outside the folder is rejected`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        File(tmp.root, "outside.png").writeBytes(byteArrayOf(1))
        assertFalse(choices.isImage(dir, "../../outside.png"))
        assertNull(choices.inside(dir, "../../outside.png"))
        assertNull(choices.inside(dir, "."))
        assertNull(choices.inside(dir, ""))
    }

    @Test
    fun `a sibling folder sharing the name prefix is outside`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        val sibling = asset("sky2", "SKYBOX", null, file = null)
        File(sibling, "x.png").writeBytes(byteArrayOf(1))
        assertNull(choices.inside(dir, "../sky2/x.png"))
        assertFalse(choices.isImage(dir, "../sky2/x.png"))
    }

    @Test
    fun `a symlink leaving the folder is rejected and not listed`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        val outside = File(tmp.root, "outside.png").apply { writeBytes(byteArrayOf(1)) }
        val link = File(dir, "link.png").toPath()
        assumeTrue(runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess)
        assertFalse(choices.isImage(dir, "link.png"))
        assertNull(choices.inside(dir, "link.png"))
        assertEquals(listOf<String?>(null), choices.faces(dir, null).map { it.value })
    }

    @Test
    fun `a symlink staying inside the folder is accepted`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        val real = File(dir, "real.png").apply { writeBytes(byteArrayOf(1)) }
        assumeTrue(runCatching { Files.createSymbolicLink(File(dir, "alias.png").toPath(), real.toPath()) }.isSuccess)
        assertTrue(choices.isImage(dir, "alias.png"))
        assertNotNull(choices.inside(dir, "alias.png"))
    }

    @Test
    fun `non image extensions and blank names are rejected`() {
        val dir = asset("sky", "SKYBOX", null, file = null)
        File(dir, "a.txt").writeText("x")
        assertFalse(choices.isImage(dir, "a.txt"))
        assertFalse(choices.isImage(dir, "missing.png"))
        assertFalse(choices.isImage(dir, null))
        assertFalse(choices.isImage(dir, " "))
    }
}
