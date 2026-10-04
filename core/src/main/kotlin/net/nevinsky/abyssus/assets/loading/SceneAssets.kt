/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.utils.Disposable
import org.slf4j.Logger
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
    private val log: Logger,
) : Disposable {
    /** The files one cache prepares from: read by its pool threads, replaced on the GL thread by [replaceFiles]. */
    private class FilesRef(@Volatile var files: AssetFiles)

    private var ref: FilesRef? = null
    private val files: AssetFiles? get() = ref?.files
    private var cache: AssetCache<P, T>? = null

    val isLoading: Boolean get() = cache?.isLoading() ?: false

    /** Loads [names] from the project in [projectDir] (none without one), drops the rest, and does one slice of GPU work. */
    fun update(projectDir: File?, names: Set<String>) {
        if (projectDir?.absoluteFile != files?.projectDir) {
            cache?.dispose()
            log.info("Loading assets of ${projectDir?.absolutePath ?: "no project"}")
            ref = projectDir?.let { FilesRef(filesOf(it)) }
            cache = ref?.let(::newCache)
        }
        val cache = cache ?: return
        names.forEach(cache::request)
        cache.retain(names)
        cache.pump()
    }

    /**
     * Makes [fresh] (a snapshot of the same project, see [AssetFiles.refreshed]) what later loads read, without touching
     * any loaded asset: invalidate the names it changes. Ignored for another project or when there is no cache.
     */
    fun replaceFiles(fresh: AssetFiles) {
        val current = ref ?: return
        if (cache != null && fresh.projectDir == current.files.projectDir) current.files = fresh
    }

    private fun newCache(source: FilesRef) = AssetCache<P, T>(
        executor,
        // the snapshot current when the pool thread starts the load, and always of this cache's own project
        prepare = { name ->
            val files = source.files
            files.metadataFailure(name)?.let { throw it }
            loader.prepare(files, name)
        },
        loader = loader,
        log = log,
    )

    fun get(name: String): T? = cache?.get(name)

    /** How many assets were built under [name]; see [AssetCache.version]. */
    fun version(name: String): Long = cache?.version(name) ?: 0L

    /** Marks [names] of this project as changed on disk; see [AssetCache.invalidate]. */
    fun invalidate(names: Set<String>) {
        cache?.invalidate(names)
    }

    /** Forgets everything without GL calls; see [AssetCache.abandon]. */
    fun abandon() {
        cache?.abandon()
        cache = null
        ref = null
    }

    override fun dispose() {
        cache?.dispose()
        cache = null
        ref = null
    }
}
