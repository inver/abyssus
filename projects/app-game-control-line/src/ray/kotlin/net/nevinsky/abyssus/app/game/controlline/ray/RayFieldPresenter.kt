/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.render.FieldView
import net.nevinsky.abyssus.app.game.controlline.render.LineSegment
import com.badlogic.gdx.graphics.Camera
import net.nevinsky.abyssus.lib.raytracing.RayBackend
import net.nevinsky.abyssus.lib.raytracing.RaySession
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * The game's ray-traced field presenter (design decision 10).
 *
 * Owns the [RayBackend] and one [RaySession]. Until the game's asset storage can supply ray model/terrain snapshots,
 * [draw] reports a limitation so [ControlLineGame] falls back to raster with a reason rather than crashing or
 * switching silently. When snapshots are wired, this presenter submits a [RaySceneSnapshot] per frame through
 * [RayFieldSceneBuilder] and draws the result as a fullscreen quad in the game's GL 3.2 core context.
 */
class RayFieldPresenter(
    private val project: Path,
    private val backend: RayBackend,
    private val session: RaySession,
) : FieldView {
    private val log = LoggerFactory.getLogger("control-line.ray")
    private val builder = RayFieldSceneBuilder(project)

    /** The last reported failure, if any: `null` while ray mode is healthy. */
    var failure: Throwable? = null
        private set

    override fun draw(field: FieldScene, camera: Camera, lines: List<LineSegment>,
        hidden: com.badlogic.ashley.core.Entity?,
        clips: Map<com.badlogic.ashley.core.Entity, String>, seconds: Float,
    ) {
        failure = null
        // Ray model/terrain snapshots are loaded from the game's asset storage elsewhere; until that is wired, ray
        // mode cannot produce a frame. Report the limitation so Main falls back to raster with a reason.
        failure = IllegalStateException("ray model/terrain snapshots are not loaded into the game's asset storage")
    }

    override fun resize(width: Float, height: Float) {
        // The presenter draws from the camera passed to [draw]; no stored viewport state to resize.
    }

    override fun dispose() {
        session.dispose()
        backend.dispose()
    }

    /** Whether ray mode is currently healthy (no unreported failure). */
    fun isHealthy(): Boolean = failure == null
}
