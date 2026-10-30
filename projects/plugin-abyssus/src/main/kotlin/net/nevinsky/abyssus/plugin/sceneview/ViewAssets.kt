/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.ray.RayAssetLease
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneAssets
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.plugin.AssetLoading
import net.nevinsky.abyssus.plugin.ProjectAssets
import java.io.File

/**
 * The assets of one scene view: the [ProjectAssets] of the project it shows, which a new project replaces, shared by the
 * kinds of asset the view draws ([AssetView]) and by its ray mode. One storage owns every built asset, so assets can
 * depend on each other (a terrain on its textures); each [AssetView] says which names it still wants and the storage
 * keeps the union. GL thread only.
 */
class ViewAssets(private val loading: AssetLoading) : Disposable {
    private var project: ProjectAssets? = null

    @Volatile
    private var unsaved: Map<File, String> = emptyMap()
    private val views = LinkedHashMap<AssetView<*>, Set<String>>()

    /** The assets of the project in [projectDir]; those of another project are released first. */
    fun project(projectDir: File): ProjectAssets {
        val dir = projectDir.absoluteFile
        project?.takeIf { it.projectDir == dir }?.let { return it }
        project?.dispose()
        views.replaceAll { _, _ -> emptySet() }
        loading.log.info("Loading assets of ${dir.absolutePath}")
        return loading.project(dir, unsaved).also { project = it }
    }

    /** The assets of the project now shown; null before the first [project] and after [abandon]. */
    val current: ProjectAssets? get() = project

    val isLoading: Boolean get() = project?.storage?.isLoading() ?: false

    /** What later loads read of unsaved `meta.json` text (the files as the editors hold them). */
    fun replaceUnsaved(files: Map<File, String>) {
        unsaved = files
        project?.unsaved = files
    }

    /** Marks the assets [names] as changed on disk; the old ones stay in use until each replacement is built. */
    fun invalidate(names: Set<String>) {
        project?.storage?.invalidate(names)
    }

    internal fun update(view: AssetView<*>, projectDir: File?, names: Set<String>) {
        if (projectDir == null) {
            project?.dispose()
            project = null
            views.replaceAll { _, _ -> emptySet() }
            return
        }
        val storage = project(projectDir).storage
        views[view] = names
        names.forEach(storage::request)
        storage.retain(views.values.flatMapTo(HashSet()) { it })
        storage.update()
    }

    internal fun get(name: String): Disposable? = project?.storage?.get(name)

    internal fun register(view: AssetView<*>) {
        views[view] = emptySet()
    }

    internal fun release(view: AssetView<*>) {
        views.remove(view)
        if (views.isEmpty()) dispose()
    }

    /** Forgets every asset without GL calls: the context they were built in is gone. */
    fun abandon() {
        project?.storage?.abandon()
        project = null
        views.replaceAll { _, _ -> emptySet() }
    }

    override fun dispose() {
        project?.dispose()
        project = null
    }
}

/** The assets of one kind ([T], a model, a terrain, a sky) a view draws, over its [ViewAssets]. GL thread only. */
class AssetView<T : Disposable>(private val owner: ViewAssets, private val type: Class<T>) : Disposable {
    init {
        owner.register(this)
    }

    val isLoading: Boolean get() = owner.isLoading

    /** Loads [names] from the project in [projectDir] (none without one), drops the rest, and does one slice of GPU work. */
    fun update(projectDir: File?, names: Set<String>) = owner.update(this, projectDir, names)

    /** The built asset [name]; null while it loads, when it failed or when it is of another kind. */
    fun get(name: String): T? = owner.get(name)?.takeIf(type::isInstance)?.let(type::cast)

    /** Forgets everything without GL calls; see [ViewAssets.abandon]. */
    fun abandon() = owner.abandon()

    override fun dispose() = owner.release(this)
}

/** The CPU ray assets of [assets], the scene view's own: leases come from its project's ray caches. */
fun raySceneAssetsOf(assets: ViewAssets): RaySceneAssets = RaySceneAssets(
    { project, name -> assets.project(project).rayModels.acquire(name).let { lease ->
        RayAssetLease({ lease.snapshot }, { lease.failure }, lease::close)
    } },
    { project, name -> assets.project(project).rayTerrains.acquire(name).let { lease ->
        RayAssetLease({ lease.snapshot }, { lease.failure }, lease::close)
    } },
    { project, models, terrains ->
        assets.current?.takeIf { it.projectDir == project.absoluteFile }?.let {
            models.forEach(it.rayModels::invalidate)
            terrains.forEach(it.rayTerrains::invalidate)
        }
    },
    { project, name -> assets.project(project).raySkies.acquire(name).let { lease ->
        RayAssetLease({ lease.snapshot }, { lease.failure }, lease::close)
    } },
    { project, name -> assets.current?.takeIf { it.projectDir == project.absoluteFile }?.raySkies?.invalidate(name) },
)
