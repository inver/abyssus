/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.assets.AssetLoading
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.json.JsonProcessor

/**
 * The plugin's composition root for the `core` module: builds its objects once for the IDE and hands them out. Code in
 * `core` never looks this up; plugin code passes what it gets from here into `core` constructors.
 */
@Service(Service.Level.APP)
class AbyssusCore {
    val json = JsonProcessor()

    /** The scene view's own GLSL (grid lines, overlay, terrain), from the plugin's resources. */
    val sceneShaders = ShaderSource("/shader/scene", AbyssusCore::class.java)

    /** Asset loading for every scene view: problems go to the IDE log, `prepare` runs on the IDE's pool. */
    val loading = AssetLoading(
        json,
        AssetLog { message, error -> Logger.getInstance("Abyssus.assets").warn(message, error) },
        AppExecutorUtil.getAppExecutorService(),
        ShaderSource("/shader/sky", AssetLoading::class.java),
    )
}
