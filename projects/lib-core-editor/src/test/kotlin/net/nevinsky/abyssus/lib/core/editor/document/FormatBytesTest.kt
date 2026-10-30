/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.security.MessageDigest

/**
 * Pins the bytes of every fixture document through each JSON writer, so that moving the shared Jackson settings cannot
 * change number text, key order or indentation. The expected digests live in `format-bytes.sha256`; set the environment
 * variable `ABYSSUS_WRITE_GOLDEN` to the absolute path of that file to rewrite it after an intended format change.
 */
class FormatBytesTest {
    private val json = SceneJson()
    private val processor = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val projects = testProject("").parentFile

    private fun documents(): List<File> = projects.walkTopDown()
        .filter { it.isFile && (it.extension == "scene" || it.extension == "abss" || it.name == "meta.json") }
        .sortedBy { it.relativeTo(projects).path }
        .toList()

    private fun digest(text: String?): String =
        text?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } } ?: "none"

    private fun lineFor(file: File): String {
        val text = file.readText()
        val pretty = json.pretty(text)
        val compact = json.parse(text).let(json::compact)
        val processorPretty = processor.pretty(processor.readObject(text))
        return "${file.relativeTo(projects).path}|pretty=${digest(pretty)}|compact=${digest(compact)}|processor=${digest(processorPretty)}"
    }

    @Test
    fun everyFixtureDocumentFormatsToTheStoredBytes() {
        val actual = documents().map(::lineFor)
        System.getenv("ABYSSUS_WRITE_GOLDEN")?.let {
            File(it).writeText(actual.joinToString("\n", postfix = "\n"))
            return
        }
        val expected = checkNotNull(javaClass.getResource("/format-bytes.sha256")) { "format-bytes.sha256 is missing" }
            .readText().trim().lines()
        assertEquals(expected, actual)
    }
}
