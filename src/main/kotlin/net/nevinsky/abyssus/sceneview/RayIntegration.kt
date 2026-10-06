/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.core.assets.displayMessage

import net.nevinsky.abyssus.RayServices
import java.util.concurrent.Executor

/**
 * What a scene view needs to offer Ray Tracing: the application's backend service, per-view CPU asset interest, the
 * converter thread and the sky exposure. Constructing it creates nothing native; a view's [newFeed] only registers an
 * idle runtime, and a backend is probed when its toggle is switched on.
 */
internal class RayIntegration(
    private val service: RayBackendService,
    private val assets: (ViewAssets) -> RaySceneAssets,
    private val executor: Executor,
    private val exposure: () -> Float = { 1f },
    private val reportFailure: (Throwable) -> Unit = {},
) {
    /** A feed whose CPU asset interest goes through [viewAssets], the assets of the view it is for. */
    fun newFeed(viewId: String, viewAssets: ViewAssets): RayViewFeed =
        RayViewFeed(service.newView(viewId), assets(viewAssets), executor, exposure, reportFailure = reportFailure)

    companion object {
        fun of(ray: RayServices) = RayIntegration(
            ray.service, { viewAssets -> RaySceneAssets(viewAssets) }, ray.converter, ray.exposure,
            ray.log.let { log -> { failure: Throwable -> log.warn("Ray tracing stopped: ${failure.displayMessage()}", failure) } },
        )
    }
}
