/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.uidemo

import net.nevinsky.abyssus.lib.core.ui.state.UiSnapshot
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import kotlin.math.roundToInt

/**
 * The game side of the UI demo, with no graphics: the values it publishes, the writes it accepts, normalizes or rejects,
 * and the named actions it handles. It stands in for a real game so the authored `menus` asset runs against live,
 * changing data without touching Control Line's own screens.
 */
class UiDemoHost {
    var health = 100.0
        private set
    val maxHealth = 100.0
    var score = 0.0
        private set
    var volume = 0.5
        private set
    var muted = false
        private set
    var playerName = "Pilot"
        private set
    var started = false
        private set

    /** Every action the host received, handled or not, in order. */
    val actions = mutableListOf<String>()

    fun snapshot() = UiSnapshot(mapOf(
        "volume" to UiValue.Num(volume), "muted" to UiValue.Bool(muted), "playerName" to UiValue.Text(playerName),
        "health" to UiValue.Num(health), "maxHealth" to UiValue.Num(maxHealth), "score" to UiValue.Num(score),
    ))

    /** Volume snaps to 0.05 steps, a name must be non-blank and not "admin"; anything else is read-only. */
    fun write(write: UiWrite): UiWriteResult = when (write.binding) {
        "volume" -> (write.value as? UiValue.Num)?.let {
            volume = ((it.value.coerceIn(0.0, 1.0) * 20).roundToInt()) / 20.0
            UiWriteResult.Accepted(UiValue.Num(volume))
        } ?: UiWriteResult.Rejected("volume must be a number")
        "muted" -> (write.value as? UiValue.Bool)?.let { muted = it.value; UiWriteResult.Accepted(it) }
            ?: UiWriteResult.Rejected("muted must be on or off")
        "playerName" -> (write.value as? UiValue.Text)?.value?.trim()?.let {
            if (it.isEmpty() || it.equals("admin", ignoreCase = true)) UiWriteResult.Rejected("That name is not allowed")
            else { playerName = it; UiWriteResult.Accepted(UiValue.Text(it)) }
        } ?: UiWriteResult.Rejected("name must be text")
        else -> UiWriteResult.Rejected("${write.binding} is read-only")
    }

    /** `startGame`, `quit` and `toMenu` are handled; any other name is reported back as unhandled. */
    fun emit(name: String): Boolean {
        actions += name
        return when (name) {
            "startGame" -> { started = true; true }
            "toMenu" -> { started = false; true }
            "quit" -> true
            else -> false
        }
    }

    /** Advances the demo game: health drains and the score climbs while a game is running. */
    fun tick(seconds: Double) {
        if (!started) return
        health = (health - 5 * seconds).coerceAtLeast(0.0)
        score += 10 * seconds
    }
}
