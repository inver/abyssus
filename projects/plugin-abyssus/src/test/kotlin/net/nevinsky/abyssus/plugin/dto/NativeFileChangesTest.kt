/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NativeFileChangesTest : BasePlatformTestCase() {
    fun testResourcesNativeRenamesAndSubscriberDisposalKeepTheirSeparateFilters() {
        val scene = myFixture.addFileToProject("P/Main.scene", "{}").virtualFile
        val image = myFixture.addFileToProject("P/assets/ui/icon.png", "image").virtualFile
        val parent = Disposer.newDisposable()
        val nativePaths = mutableListOf<String>()
        val allPaths = mutableListOf<String>()
        try {
            val changes = project.service<NativeFileChanges>()
            changes.subscribe(parent) { events -> nativePaths += events.map { it.path } }
            changes.subscribe(parent, nativeOnly = false) { events -> allPaths += events.map { it.path } }
            WriteAction.runAndWait<Exception> { image.setBinaryContent("updated".toByteArray()) }
            assertFalse(nativePaths.toString(), image.path in nativePaths)
            assertTrue(allPaths.toString(), image.path in allPaths)
            WriteAction.runAndWait<Exception> { scene.rename(this, "Main.txt") }
            assertTrue(nativePaths.toString(), scene.path in nativePaths)
            val before = allPaths.size
            Disposer.dispose(parent)
            WriteAction.runAndWait<Exception> { image.setBinaryContent("again".toByteArray()) }
            assertEquals(before, allPaths.size)
        } finally {
            if (!Disposer.isDisposed(parent)) Disposer.dispose(parent)
        }
    }
}
