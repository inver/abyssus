/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.graphics.GL20
import net.nevinsky.abyssus.app.game.controlline.render.FieldView
import org.slf4j.Logger

/**
 * Runs the game's lifecycle calls (design decision D1) so that a failure leaves a crash record through [crashes] and
 * then ends the game through [exit]: `main` sees [CrashReporter.crashed] and exits with status 1. After a failure no
 * further frame runs. A plain `try`/`catch`: nothing here is cancellable.
 */
internal class FrameGuard(private val crashes: CrashReporter, private val exit: () -> Unit) {
    /** Whether a guarded call failed: later frames are skipped. */
    var failed: Boolean = false
        private set

    /** Runs [body] unless an earlier call failed; a failure is reported once and ends the game. */
    fun run(body: () -> Unit) {
        if (failed) return
        try {
            body()
        } catch (error: Throwable) {
            failed = true
            crashes.report(Thread.currentThread(), error)
            exit()
        }
    }

    /**
     * Runs [body] even after a failure (releasing what the game holds). Its own failure is reported when it is the
     * first; after an earlier one it only goes to [log], so the record keeps the failure that ended the game.
     */
    fun release(log: Logger, body: () -> Unit) {
        if (!failed) return run(body)
        try {
            body()
        } catch (error: Throwable) {
            log.warn("Releasing the game after a crash failed too", error)
        }
    }
}

/**
 * The environment of the running game with [gl]'s renderer and version and the [view] that draws the field. `create`
 * hands it to the crash reporter and logs [GameEnvironment.line] once (spec: "The environment is logged at start").
 */
internal fun startEnvironment(base: GameEnvironment, gl: GL20, view: FieldView?, log: Logger): GameEnvironment =
    base.withGl(gl.glGetString(GL20.GL_RENDERER), gl.glGetString(GL20.GL_VERSION), view?.kind ?: "raster")
        .also { log.info(it.line()) }
