/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneDocumentCacheTest : BasePlatformTestCase() {
    private var parses = 0
    private val reader get() = service<SceneReader>()

    private fun cache() = SceneDocumentCache(project) { text -> parses++; SceneDocumentCache.parsedScene(reader, text) }
        .also { Disposer.register(testRootDisposable, it) }

    private fun scene(text: String = """{"format":"abyssus","formatVersion":1,"name":"a","ecs":{"entities":{}}}""") =
        myFixture.addFileToProject("c/Main.scene", text).virtualFile

    private fun setText(f: com.intellij.openapi.vfs.VirtualFile, text: String) {
        val doc = FileDocumentManager.getInstance().getDocument(f)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(text) }
    }

    fun testRepeatedReadsOfAnUnchangedDocumentParseOnce() {
        val cache = cache()
        val f = scene()
        val first = cache.read(f)
        repeat(20) { assertSame(first, cache.read(f)) }
        assertEquals(1, parses)
        assertEquals("a", first!!.scene.name)
    }

    fun testAnEditInvalidatesTheEntry() {
        val cache = cache()
        val f = scene()
        assertEquals("a", cache.read(f)!!.scene.name)
        setText(f, """{"format":"abyssus","formatVersion":1,"name":"b"}""")
        assertEquals("b", cache.read(f)!!.scene.name)
        assertEquals(2, parses)
        cache.read(f)
        assertEquals(2, parses)
    }

    fun testADeleteOrMoveDropsTheEntry() {
        val cache = cache()
        val f = scene()
        assertNotNull(cache.read(f))
        assertEquals(setOf(f), cache.cachedFiles())
        runWriteAction { f.delete(this) }
        assertEquals(emptySet<com.intellij.openapi.vfs.VirtualFile>(), cache.cachedFiles())
        val g = myFixture.addFileToProject("c/Other.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertNotNull(cache.read(g))
        val dir = myFixture.tempDirFixture.findOrCreateDir("c/moved")
        runWriteAction { g.move(this, dir) }
        assertEquals(emptySet<com.intellij.openapi.vfs.VirtualFile>(), cache.cachedFiles())
    }

    fun testInvalidTextIsUnreadableAndIsRememberedUntilItChanges() {
        val cache = cache()
        val f = scene("{oops")
        repeat(5) { assertNull(cache.read(f)) }
        assertEquals(1, parses)
        setText(f, """{"format":"abyssus","formatVersion":1,"name":"fixed"}""")
        assertEquals("fixed", cache.read(f)!!.scene.name)
        assertEquals(2, parses)
    }
}
