/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.diagnostic.Logger
import net.nevinsky.abyssus.AbyssusCore
import java.util.concurrent.Executor

/**
 * What a scene view needs to offer Ray Tracing: the application's backend service, per-view CPU asset interest, the
 * converter thread and the sky exposure. Constructing it creates nothing native; a view's [newFeed] only registers an
 * idle runtime, and a backend is probed when its toggle is switched on.
 */
internal class RayIntegration(
    private val service: RayBackendService,
    private val assets: () -> RaySceneAssets,
    private val executor: Executor,
    private val exposure: () -> Float = { 1f },
    private val reportFailure: (Throwable) -> Unit = {},
) {
    fun newFeed(viewId: String): RayViewFeed = RayViewFeed(service.newView(viewId), assets(), executor, exposure, reportFailure = reportFailure)

    companion object {
        fun of(core: AbyssusCore) = RayIntegration(
            core.rayService, { RaySceneAssets(core.loading) }, core.rayConverter, { core.loading.toneCurve.exposure },
            { Logger.getInstance("Abyssus.ray").warn("Ray tracing stopped", it) },
        )
    }
}
