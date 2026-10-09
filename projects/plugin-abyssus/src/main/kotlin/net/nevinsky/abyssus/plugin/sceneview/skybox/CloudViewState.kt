/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.gdx.assets.sky.clouds.CloudTechnique

/** The scene view's Clouds choice: the sky asset's own technique, or one that overrides it in this view. */
enum class CloudChoice(val technique: CloudTechnique?) {
    ASSET(null),
    LAYERED(CloudTechnique.LAYERED),
    SHELLS(CloudTechnique.SHELLS),
    VOLUMETRIC(CloudTechnique.VOLUMETRIC),
}

/**
 * One scene view's cloud technique: the user's [choice] and the automatic fallbacks from volumetric clouds that were
 * too slow. The first fallback switches to shells and the user may pick volumetric again; after the second the view
 * keeps shells ([sticky]) until it is reopened, which makes a new state. Writes no file. EDT only.
 */
class CloudViewState {
    var choice: CloudChoice = CloudChoice.ASSET
        private set

    /** How many times volumetric clouds fell back to shells in this view. */
    var fallbacks = 0
        private set

    /** True after the second fallback: volumetric clouds are refused for as long as the view is open. */
    val sticky: Boolean get() = fallbacks >= 2

    /** True while the toolbar should say why the view left volumetric clouds; cleared by the next choice. */
    var fallbackNote = false
        private set

    /** The technique to draw instead of the asset's, or null to draw the asset's. */
    val override: CloudTechnique? get() = choice.technique

    /** The technique drawn for a sky whose `meta.json` asks for [asset]. */
    fun effective(asset: CloudTechnique): CloudTechnique = override ?: asset

    /** Picks [choice]; false (and nothing changes) when it is volumetric and volumetric is [sticky]-refused. */
    fun choose(choice: CloudChoice): Boolean {
        if (choice == CloudChoice.VOLUMETRIC && sticky) return false
        this.choice = choice
        fallbackNote = false
        return true
    }

    /** Volumetric clouds were too slow: switches this view to shells and shows the note. */
    fun fallBack() {
        fallbacks++
        choice = CloudChoice.SHELLS
        fallbackNote = true
    }
}
