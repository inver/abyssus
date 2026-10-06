/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.editor.ray

import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.core.assets.terrain.RayTerrainSnapshot
import java.io.File

/** Per-view optional CPU interest. Reconciliation and polling never wait for asset preparation or touch GL. */
class RaySceneAssets(
    private val acquireModel: (File, String) -> RayAssetLease<RayModelSnapshot>,
    private val acquireTerrain: (File, String) -> RayAssetLease<RayTerrainSnapshot>,
    private val invalidateAssets: (File, Set<String>, Set<String>) -> Unit = { _, _, _ -> },
    private val acquireSky: (File, String) -> RayAssetLease<RaySkySnapshot>? = { _, _ -> null },
    private val invalidateSky: (File, String) -> Unit = { _, _ -> },
) : AutoCloseable {
    private var project: String? = null
    private val models = linkedMapOf<String, RayAssetLease<RayModelSnapshot>>()
    private val terrains = linkedMapOf<String, RayAssetLease<RayTerrainSnapshot>>()
    private var skyName: String? = null
    private var sky: RayAssetLease<RaySkySnapshot>? = null

    fun update(projectDir: File?, content: SceneContent) {
        val key = projectDir?.absoluteFile?.toPath()?.normalize()?.toString()
        if (key != project) { close(); project = key }
        if (projectDir == null) return
        reconcile(models, content.models.map { it.assetName }.toSet()) { acquireModel(projectDir, it) }
        reconcile(terrains, content.terrains.map { it.assetName }.toSet()) { acquireTerrain(projectDir, it) }
        if (content.skybox != skyName) {
            sky?.close()
            skyName = content.skybox
            sky = content.skybox?.let { acquireSky(projectDir, it) }
        }
    }

    fun poll(): RaySceneAssetState {
        val failures = models.mapNotNull { (name, lease) -> lease.failure?.let { "model:$name" to it } } +
            terrains.mapNotNull { (name, lease) -> lease.failure?.let { "terrain:$name" to it } }
        if (failures.isNotEmpty()) return RaySceneAssetState.Failed(failures.toMap())
        val preparedModels = models.mapNotNull { (name, lease) -> lease.snapshot?.let { name to it } }.toMap()
        val preparedTerrains = terrains.mapNotNull { (name, lease) -> lease.snapshot?.let { name to it } }.toMap()
        val pending = models.keys.filter { it !in preparedModels }.map { "model:$it" } +
            terrains.keys.filter { it !in preparedTerrains }.map { "terrain:$it" }
        // A sky that is still reading or cannot be transferred (a procedural sky) never blocks or fails the view: it shows
        // the background colour until, or instead of, the sky.
        return if (pending.isNotEmpty()) RaySceneAssetState.Preparing(pending)
        else RaySceneAssetState.Ready(preparedModels, preparedTerrains, sky?.snapshot)
    }

    /** VFS asset edits drop this view's old revision; stale companion preparations cannot publish into new leases. */
    fun invalidate() {
        project?.let { invalidateAssets(File(it), models.keys.toSet(), terrains.keys.toSet())
            skyName?.let { name -> invalidateSky(File(it), name) } }
        close()
    }

    override fun close() {
        models.values.forEach(AutoCloseable::close)
        terrains.values.forEach(AutoCloseable::close)
        sky?.close()
        models.clear(); terrains.clear(); project = null; sky = null; skyName = null
    }

    private fun <T : Any> reconcile(leases: MutableMap<String, RayAssetLease<T>>, names: Set<String>, acquire: (String) -> RayAssetLease<T>) {
        val removed = leases.keys.filter { it !in names }
        removed.forEach { leases.remove(it)?.close() }
        names.forEach { name -> if (name !in leases) leases[name] = acquire(name) }
    }
}

class RayAssetLease<T : Any>(private val read: () -> T?, private val failed: () -> Throwable?, private val release: () -> Unit) : AutoCloseable {
    private var closed = false
    val snapshot: T? get() = if (closed) null else read()
    val failure: Throwable? get() = if (closed) null else failed()
    override fun close() { if (!closed) { closed = true; release() } }
}

sealed interface RaySceneAssetState {
    data class Ready @JvmOverloads constructor(val models: Map<String, RayModelSnapshot>, val terrains: Map<String, RayTerrainSnapshot>, val sky: RaySkySnapshot? = null) : RaySceneAssetState
    data class Preparing(val assets: List<String>) : RaySceneAssetState
    data class Failed(val failures: Map<String, Throwable>) : RaySceneAssetState
}
