/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComboBox
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice
import net.nevinsky.abyssus.plugin.sceneview.skybox.CloudFrameBudget
import net.nevinsky.abyssus.plugin.sceneview.skybox.CloudViewState
import javax.swing.JLabel

/**
 * The Clouds choice of one Scene view and its fallback note: the toolbar combo, [state] (this view's choice and
 * fallbacks; a reopened view gets new controls, so it starts at Asset again) and the frame budget that falls back to
 * shells when volumetric clouds are too slow. [experimenting] tells whether the ray experiment has the view. AWT thread.
 */
internal class CloudControls(private val scene: SceneRenderer, private val experimenting: () -> Boolean) {
    val state = CloudViewState()
    private val budget = CloudFrameBudget()
    private var available = false
    private var updating = false

    val combo = ComboBox(CloudChoice.entries.map(::CloudChoiceItem).toTypedArray()).apply {
        name = "clouds"
        isFocusable = false
        addActionListener {
            if (updating) return@addActionListener
            val item = selectedItem as? CloudChoiceItem ?: return@addActionListener
            state.choose(item.choice)
            scene.state.cloudTechnique = state.override
            budget.reset()
            sync()
        }
    }
    val note = JLabel().apply { name = "clouds-note" }

    /** Brings the Clouds choice and the fallback note in line with [state] and the scene's sky. */
    fun sync() {
        available = scene.cloudsEnabled()
        val was = updating
        updating = true
        try {
            combo.isEnabled = available && !experimenting()
            combo.selectedItem = combo.model.let { model ->
                (0 until model.size).map(model::getElementAt).first { it.choice == state.choice }
            }
            combo.toolTipText = AbyssusBundle.message(
                when {
                    !available -> "sceneViewCloudsUnavailableTooltip"
                    state.sticky -> "sceneViewCloudsStickyTooltip"
                    else -> "sceneViewCloudsTooltip"
                }
            )
            note.text = if (state.fallbackNote) AbyssusBundle.message("sceneViewCloudsFallback") else ""
            note.isVisible = state.fallbackNote
        } finally {
            updating = was
        }
    }

    /**
     * After each rendered frame: counts it against the volumetric frame budget ([interval] seconds since the previous
     * one; [volumetric] whether it drew volumetric clouds) and falls back to shells when the view has been too slow,
     * and follows the sky gaining or losing clouds.
     */
    fun frameRendered(
        interval: Float,
        volumetric: Boolean = scene.drawnCloudTechnique == CloudTechnique.VOLUMETRIC,
    ) {
        val measured = if (volumetric && SIMULATE_SLOW_CLOUDS) maxOf(interval, SLOW_FRAME_SECONDS) else interval
        if (budget.frame(measured, volumetric)) {
            state.fallBack()
            scene.state.cloudTechnique = state.override
            thisLogger().info("Volumetric clouds were too slow in this view; drawing shells (fallback ${state.fallbacks})")
            sync()
        } else if (scene.cloudsEnabled() != available) {
            sync()
        }
    }
}

/** `-Dabyssus.clouds.simulateSlow=true` makes every volumetric frame count as slow, to try the fallback by hand. */
private val SIMULATE_SLOW_CLOUDS = java.lang.Boolean.getBoolean("abyssus.clouds.simulateSlow")

private const val SLOW_FRAME_SECONDS = 0.05f

/** A Clouds choice as the toolbar's combo lists it. */
internal class CloudChoiceItem(val choice: CloudChoice) {
    override fun toString(): String = AbyssusBundle.message(
        when (choice) {
            CloudChoice.ASSET -> "sceneViewCloudsAsset"
            CloudChoice.LAYERED -> "sceneViewCloudsLayered"
            CloudChoice.CUMULUS -> "sceneViewCloudsCumulus"
            CloudChoice.SHELLS -> "sceneViewCloudsShells"
            CloudChoice.VOLUMETRIC -> "sceneViewCloudsVolumetric"
        }
    )
}
