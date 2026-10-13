/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.AbyssusBundle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The Play, Pause, Step and Stop buttons of one Scene view with their status text. [simulationRequest] builds what Play
 * starts from (read when Play is pressed); without it Play does nothing. [beforePlay] runs first (the view leaves the
 * foliage brush) and [afterPlay] last (the view takes focus so keys reach the simulation). AWT thread.
 */
internal class PlayControlsStrip(
    private val play: PlayState,
    private val renderer: SceneRenderer,
    private val simulationRequest: ((selection: String?) -> SimulationRequest)?,
    private val beforePlay: () -> Unit,
    private val afterPlay: () -> Unit,
) {
    private val playButton = JButton(AbyssusBundle.message("sceneViewPlay")).apply { name = "play" }
    private val pauseButton = JButton(AbyssusBundle.message("sceneViewPause")).apply { name = "pause" }
    private val stepButton = JButton(AbyssusBundle.message("sceneViewStep")).apply { name = "step" }
    private val stopButton = JButton(AbyssusBundle.message("sceneViewStop")).apply { name = "stop" }
    private val status = JLabel()
    private val all: List<JComponent> get() = listOf(playButton, pauseButton, stepButton, stopButton, status)

    fun addTo(toolbar: JPanel) {
        playButton.toolTipText = AbyssusBundle.message("sceneViewPlayTooltip")
        pauseButton.toolTipText = AbyssusBundle.message("sceneViewPauseTooltip")
        stepButton.toolTipText = AbyssusBundle.message("sceneViewStepTooltip")
        stopButton.toolTipText = AbyssusBundle.message("sceneViewStopTooltip")
        for (button in listOf(playButton, pauseButton, stepButton, stopButton)) {
            button.isFocusable = false
            toolbar.add(button)
        }
        toolbar.add(status)
        playButton.addActionListener {
            val request = simulationRequest ?: return@addActionListener
            beforePlay()
            play.play { request(renderer.state.selectedId) }
            afterPlay()
        }
        pauseButton.addActionListener { play.pause() }
        stepButton.addActionListener { play.step() }
        stopButton.addActionListener { play.stop() }
    }

    fun sync() {
        for (c in all) c.isVisible = play.available
        if (!play.available) return
        val phase = play.phase
        val controls = playControls(phase, play.active)
        playButton.isEnabled = controls.play
        pauseButton.isEnabled = controls.pause
        stepButton.isEnabled = controls.step
        stopButton.isEnabled = controls.stop
        status.text = when (phase) {
            PlayState.Phase.IDLE -> ""
            PlayState.Phase.STARTING -> AbyssusBundle.message("sceneViewPlayStarting")
            PlayState.Phase.PLAYING -> AbyssusBundle.message("sceneViewPlaying")
            PlayState.Phase.PAUSED -> AbyssusBundle.message("sceneViewPaused")
            PlayState.Phase.FAILED -> AbyssusBundle.message("sceneViewPlayFailed")
        }
    }
}
