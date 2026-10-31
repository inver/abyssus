/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudFrameBudget
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.simplerThan

/**
 * Decides which cloud technique the game's sky steps down to: each frame it is told the technique drawn and the frame
 * time, and answers the next simpler technique when [CloudFrameBudget] says the game has been too slow for long enough.
 * Layered, or no clouds, is never stepped down from.
 */
class CloudStepDown(private val budget: CloudFrameBudget = CloudFrameBudget()) {
    fun frame(seconds: Float, drawn: CloudTechnique?): CloudTechnique? {
        val watched = drawn != null && simplerThan(drawn) != null
        if (!budget.frame(seconds, watched)) return null
        return drawn?.let(::simplerThan)
    }
}
