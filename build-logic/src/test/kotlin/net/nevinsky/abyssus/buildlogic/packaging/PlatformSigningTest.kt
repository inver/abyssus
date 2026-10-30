/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlatformSigningTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the template fills the file placeholder per argument`() {
        val file = File("/x/Control Line.msi")
        assertEquals(
            listOf("signtool", "sign", "/tr", "http://ts.example", "/fd", "SHA256", "/x/Control Line.msi"),
            windowsSignCommand("signtool sign /tr http://ts.example /fd SHA256 {file}", file)
        )
        assertEquals(listOf("--in=/x/Control Line.msi"), windowsSignCommand("--in={file}", file))
        try {
            windowsSignCommand("signtool sign", file)
            fail("expected a failure")
        } catch (e: GradleException) {
            assertTrue(e.message!!.contains("{file}"))
        }
    }

    @Test
    fun `signable files are the launcher and the dlls`() {
        val dir = tmp.newFolder()
        File(dir, "app").mkdirs()
        listOf("Control Line.exe", "app/a.dll", "app/b.jar", "runtime.txt").forEach { File(dir, it).writeText("x") }
        assertEquals(listOf("Control Line.exe", "app/a.dll").sorted(), windowsSignableFiles(dir).map { it.relativeTo(dir).invariantSeparatorsPath }.sorted())
    }

    @Test
    fun `notarization names the missing credentials without printing values`() {
        try {
            notarizeCommand(File("a.dmg"), mapOf("APPLE_ID" to "me@example.com"))
            fail("expected a failure")
        } catch (e: GradleException) {
            assertTrue(e.message!!, e.message!!.contains("APPLE_TEAM_ID") && e.message!!.contains("APPLE_APP_PASSWORD") && !e.message!!.contains("me@example.com"))
        }
        val ok = notarizeCommand(File("a.dmg"), mapOf("APPLE_ID" to "a", "APPLE_TEAM_ID" to "t", "APPLE_APP_PASSWORD" to "p"))
        assertEquals(listOf("xcrun", "notarytool", "submit", "a.dmg"), ok.take(4))
        assertTrue("--wait" in ok)
    }
}
