/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams

/** The frame rate in the stats strip: when it is shown, what it samples and what resets it, with a fake clock and no GL. */
class SceneViewPanelFpsTest : BasePlatformTestCase() {
    private val second = 1_000_000_000L

    private fun panel(): SceneViewPanel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())

    /** Runs [frames] frames, each taking [step] nanoseconds, from the fake clock's [start]. */
    private fun SceneViewPanel.frames(clock: LongArray, frames: Int, step: Long) {
        for (i in 0 until frames) {
            fpsFrameStarted()
            clock[0] += step
            fpsFrameCompleted()
        }
    }

    fun testItIsAbsentUntilEnabled() {
        val p = panel()
        try {
            assertFalse(p.statsText(), p.statsText().contains("fps"))
        } finally { p.dispose() }
    }

    fun testEnablingShowsAPlaceholderThenTheMeasuredRate() {
        val p = panel()
        try {
            val clock = longArrayOf(10 * second)
            p.nanoClock = { clock[0] }
            p.setFpsEnabled(true)
            assertTrue(p.statsText(), p.statsText().startsWith("-- fps"))
            p.frames(clock, 61, second / 60)
            assertTrue(p.statsText(), p.statsText().startsWith("60 fps"))
        } finally { p.dispose() }
    }

    fun testASlowViewShowsItsOwnRate() {
        val p = panel()
        try {
            val clock = longArrayOf(second)
            p.nanoClock = { clock[0] }
            p.setFpsEnabled(true)
            p.frames(clock, 10, second / 5) // 5 per second for two seconds
            assertTrue(p.statsText(), p.statsText().startsWith("5 fps"))
        } finally { p.dispose() }
    }

    fun testDisablingRemovesItAndEnablingAgainStartsFresh() {
        val p = panel()
        try {
            val clock = longArrayOf(second)
            p.nanoClock = { clock[0] }
            p.setFpsEnabled(true)
            p.frames(clock, 61, second / 60)
            p.setFpsEnabled(false)
            assertFalse(p.statsText().contains("fps"))
            p.frames(clock, 120, second / 60) // not counted while off
            p.setFpsEnabled(true)
            assertTrue("a fresh placeholder, not the old value", p.statsText().startsWith("-- fps"))
        } finally { p.dispose() }
    }

    fun testAnInterruptionStartsAFreshSampleWithoutTheHiddenTime() {
        val p = panel()
        try {
            val clock = longArrayOf(second)
            p.nanoClock = { clock[0] }
            p.setFpsEnabled(true)
            p.frames(clock, 61, second / 60)
            assertTrue(p.statsText().startsWith("60 fps"))
            p.fpsInterrupted() // hidden, minimized, zero-size, failed or re-created
            clock[0] += 10 * second
            assertTrue(p.statsText(), p.statsText().startsWith("-- fps"))
            p.frames(clock, 61, second / 60)
            assertTrue("the ten hidden seconds are not in the sample", p.statsText().startsWith("60 fps"))
        } finally { p.dispose() }
    }

    fun testAnUnavailableViewResetsIt() {
        val p = panel()
        try {
            val clock = longArrayOf(second)
            p.nanoClock = { clock[0] }
            p.setFpsEnabled(true)
            p.frames(clock, 61, second / 60)
            p.showUnavailable("no GL")
            assertTrue(p.statsText(), p.statsText().startsWith("-- fps"))
        } finally { p.dispose() }
    }

    fun testItSitsBeforeTheObjectCountAndTheTool() {
        val p = panel()
        try {
            p.setFpsEnabled(true)
            assertEquals("-- fps \u00b7 0 objects \u00b7 Move \u00b7 World", p.statsText())
        } finally { p.dispose() }
    }

    fun testTheProjectSettingIsReadEveryFrameWithoutAChangeEvent() {
        var shown = false
        val p = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(), showFps = { shown })
        try {
            p.refreshFpsSetting()
            assertFalse(p.statsText().contains("fps"))
            shown = true
            p.refreshFpsSetting()
            assertTrue(p.statsText(), p.statsText().startsWith("-- fps"))
            shown = false
            p.refreshFpsSetting()
            assertFalse(p.statsText().contains("fps"))
        } finally { p.dispose() }
    }
}
