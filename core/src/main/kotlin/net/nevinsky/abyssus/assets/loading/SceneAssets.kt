/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.files.AssetFiles
import java.io.File
import java.util.concurrent.Executor

/**
 * The assets of one project, loaded through [loader] and shared by every entity using them. A new project starts a
 * new cache, so a pool thread always prepares from the project it was asked for. GL thread only.
 */
class SceneAssets<P : Any, T : Disposable>(
    private val executor: Executor,
    private val loader: AssetLoader<P, T>,
    private val filesOf: (File) -> AssetFiles,
    private val log: AssetLog,
) : Disposable {
    private var files: AssetFiles? = null
    private var cache: AssetCache<P, T>? = null

    val isLoading: Boolean get() = cache?.isLoading() ?: false

    /** Loads [names] from the project in [projectDir] (none without one), drops the rest, and does one slice of GPU work. */
    fun update(projectDir: File?, names: Set<String>) {
        if (projectDir?.absoluteFile != files?.projectDir) {
            cache?.dispose()
            files = projectDir?.let(filesOf)
            cache = files?.let(::newCache)
        }
        val cache = cache ?: return
        names.forEach(cache::request)
        cache.retain(names)
        cache.pump()
    }

    private fun newCache(files: AssetFiles) = AssetCache<P, T>(
        executor,
        prepare = { name -> loader.prepare(files, name) },
        loader = loader,
        log = log,
    )

    fun get(name: String): T? = cache?.get(name)

    /** Forgets everything without GL calls; see [AssetCache.abandon]. */
    fun abandon() {
        cache?.abandon()
        cache = null
        files = null
    }

    override fun dispose() {
        cache?.dispose()
        cache = null
        files = null
    }
}
