/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import java.io.File
import java.util.concurrent.Executor

/**
 * Turns one asset folder into a GPU object, in the steps [AssetCache] runs: [prepare] off the GL thread (file IO,
 * decoding), then [upload] one slice per frame and [build] on the GL thread.
 */
interface AssetLoader<P : Any, T : Disposable> {
    /** Reads and decodes the asset [name]; null when it has no usable files. No GL; runs on a pool thread. */
    fun prepare(files: ProjectAssetFiles, name: String): P?

    /** Does one slice of the GPU upload; true when nothing is left. */
    fun upload(prepared: P): Boolean = true

    fun build(prepared: P): T

    /** Releases whatever [prepared] still holds. Safe to call more than once. */
    fun discard(prepared: P)
}

/**
 * The assets of one project, loaded through [loader] and shared by every entity using them. A new project starts a
 * new cache, so a pool thread always prepares from the project it was asked for. GL thread only.
 */
class SceneAssets<P : Any, T : Disposable>(private val executor: Executor, private val loader: AssetLoader<P, T>) : Disposable {
    private var files: ProjectAssetFiles? = null
    private var cache: AssetCache<P, T>? = null

    val isLoading: Boolean get() = cache?.isLoading() ?: false

    /** Loads [names] from the project in [projectDir] (none without one), drops the rest, and does one slice of GPU work. */
    fun update(projectDir: File?, names: Set<String>) {
        if (projectDir?.absoluteFile != files?.projectDir) {
            cache?.dispose()
            files = projectDir?.let(::ProjectAssetFiles)
            cache = files?.let(::newCache)
        }
        val cache = cache ?: return
        names.forEach(cache::request)
        cache.retain(names)
        cache.pump()
    }

    private fun newCache(files: ProjectAssetFiles) = AssetCache<P, T>(
        executor,
        prepare = { name -> loader.prepare(files, name) },
        build = { _, p -> loader.build(p) },
        advance = loader::upload,
        discard = loader::discard,
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

/** An entity drawn from a loaded asset. */
interface PlacedEntity<A> {
    val placement: AssetPlacement
    val asset: A
}

/**
 * One entity per placement whose asset is loaded, in scene order. An entity is kept while its placement and asset stay
 * the same; otherwise [place] makes the new one from the placement, the asset and the previous entity of that id when
 * it shows the same asset (so it can keep state such as a running animation).
 */
class PlacedEntities<A : Any, E : PlacedEntity<A>>(private val place: (AssetPlacement, A, E?) -> E) {
    private var entities = LinkedHashMap<String, E>()

    val drawn: Collection<E> get() = entities.values

    fun update(placements: List<AssetPlacement>, assetOf: (String) -> A?) {
        val next = LinkedHashMap<String, E>()
        for (p in placements) {
            val asset = assetOf(p.assetName) ?: continue
            val current = entities[p.entityId]?.takeIf { it.asset === asset }
            next[p.entityId] = if (current?.placement == p) current else place(p, asset, current)
        }
        entities = next
    }

    fun clear() = entities.clear()
}

/** The world matrix of [this] placement transform. */
fun PlacementTransform.toMatrix(out: Matrix4 = Matrix4()): Matrix4 = out.set(
    Vector3(position.x, position.y, position.z),
    Quaternion(rotation.x, rotation.y, rotation.z, rotation.w),
    Vector3(scale.x, scale.y, scale.z),
)
