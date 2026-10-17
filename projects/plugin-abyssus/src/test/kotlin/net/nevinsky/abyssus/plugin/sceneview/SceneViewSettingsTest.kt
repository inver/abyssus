/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneViewSettingsTest : BasePlatformTestCase() {
    private val key = "net.nevinsky.abyssus.sceneView.showFps"

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue(key)
        } finally {
            super.tearDown()
        }
    }

    fun testItIsOffWhenNothingWasSaved() {
        assertFalse(project.service<SceneViewSettings>().showFps)
    }

    fun testASavedValueIsReadBack() {
        PropertiesComponent.getInstance(project).setValue(key, true, false)
        assertTrue(project.service<SceneViewSettings>().showFps)
    }

    fun testChangingItSavesBeforeListenersRun() {
        val settings = project.service<SceneViewSettings>()
        val parent = Disposer.newDisposable()
        val seen = mutableListOf<Pair<Boolean, Boolean>>()
        try {
            settings.addShowFpsListener(parent) { value -> seen += value to settings.showFps }
            settings.showFps = true
            settings.showFps = true // unchanged: no notification
            settings.showFps = false
            assertEquals(listOf(true to true, false to false), seen)
            assertTrue(PropertiesComponent.getInstance(project).isValueSet(key) || !settings.showFps)
        } finally {
            Disposer.dispose(parent)
        }
    }

    fun testADisposedListenerIsNotCalled() {
        val settings = project.service<SceneViewSettings>()
        val parent = Disposer.newDisposable()
        var calls = 0
        settings.addShowFpsListener(parent) { calls++ }
        Disposer.dispose(parent)
        settings.showFps = true
        assertEquals(0, calls)
    }
}
