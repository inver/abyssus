/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.PreparedFoliage
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageChunk
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageDraft
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterOutcome
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterRefusal
import net.nevinsky.abyssus.lib.core.editor.foliage.mergeFoliageChunks
import net.nevinsky.abyssus.lib.core.editor.scene.FoliagePlacement
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.shader.ShaderProvider
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.plugin.log.IntellijLoggerFactory
import net.nevinsky.abyssus.plugin.sceneview.shadows.FoliageCastSource
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import net.nevinsky.abyssus.lib.gdx.ModelBatch as ContentBatch

/** One drawn foliage: the placement that named it, the asset it draws and the entity transform its copies stand under. */
class DrawnFoliage(val placement: FoliagePlacement, val foliage: FoliageDrawable, val entity: Matrix4)

/**
 * The foliage assets one Scene view draws, over its [AssetView][ViewAssets]: injected so a test needs no asset
 * storage. GL thread only, like the rest of the view's assets.
 */
interface FoliageAssets : Disposable {
    /** True while the view's assets (its foliage included) are still being built. */
    val isLoading: Boolean

    /** Loads [names] from the project in [projectDir] (none without one) and drops the rest. */
    fun update(projectDir: File?, names: Set<String>)

    /** The built foliage [name]; null while it loads, when it failed or when it is of another kind. */
    fun get(name: String): FoliageDrawable?

    /** Forgets everything without GL calls; see [ViewAssets.abandon]. */
    fun abandon()
}

/** [FoliageAssets] over one view's foliage [AssetView]. */
class ViewFoliageAssets(private val view: AssetView<FoliageDrawable>) : FoliageAssets {
    override val isLoading: Boolean get() = view.isLoading
    override fun update(projectDir: File?, names: Set<String>) = view.update(projectDir, names)
    override fun get(name: String): FoliageDrawable? = view.get(name)
    override fun abandon() = view.abandon()
    override fun dispose() = view.dispose()
}

/**
 * The foliage of the scene's terrain entities, beside [SceneTerrains]: it asks the view's assets for the foliage
 * assets the `FoliageComponent`s name, prefers the project's uncommitted [FoliageDrafts] draft over the stored
 * settings and masks, generates a stale, missing or draft-touched bake on the pool and hands the result to the
 * drawable, and logs what cannot be shown once instead of hiding the rest of the scene (design decision 4).
 *
 * GL thread only, inside `GdxRuntime.withContext`: [update] reads the assets, applies what the pool finished and
 * culls the copies against the camera; [draw] hands the renderables to the batch. Generation itself runs off that
 * thread and never blocks a frame.
 */
class SceneFoliage(
    private val assets: FoliageAssets,
    private val drafts: FoliageDrafts,
    private val log: Logger = IntellijLoggerFactory("Abyssus").getLogger("sceneview.foliage"),
    private val background: (Runnable) -> Unit = { AppExecutorUtil.getAppExecutorService().execute(it) },
) : Disposable, FoliageCastSource {

    /** The foliage of the last [update], in scene order: one entry per asset, however many entities place it. */
    var drawn: List<DrawnFoliage> = emptyList()
        private set

    val isLoading: Boolean get() = assets.isLoading

    private val states = HashMap<String, Generating>()
    private val pending = ConcurrentLinkedQueue<Generated>()
    private val scatter = FoliageScatter(FoliageFingerprint())

    /**
     * Brings the foliage in line with [placements]: loads the assets they name, generates what the drafts or a stale
     * bake ask for, and culls what is drawn against [camera]. A placement that cannot be shown is logged and left
     * out; the terrain and the models around it are drawn all the same.
     */
    fun update(placements: List<FoliagePlacement>, projectDir: File?, camera: Camera) {
        applyGenerated()
        assets.update(projectDir, placements.mapTo(HashSet()) { it.foliageName })
        val reasons = LinkedHashMap<String, MutableList<String>>()
        val visible = ArrayList<DrawnFoliage>(placements.size)
        val shown = HashSet<String>()
        for (placement in placements) {
            val name = placement.foliageName
            val state = states.getOrPut(name) { Generating() }
            val found = reasons.getOrPut(name) { ArrayList() }
            val foliage = assets.get(name)
            if (foliage == null) {
                if (!assets.isLoading) found += missingAsset(name)
                continue
            }
            if (foliage !== state.view) state.rebased(foliage)
            val staged = foliage.foliage
            val blocked = blockedReason(placement, staged)
            if (blocked != null) {
                found += blocked
                continue
            }
            for ((folder, model) in foliage.layerModels) if (model == null) found += missingModel(name, folder)
            if (foliage.bake == null) found += noBake(name)
            if (shown.add(name)) visible += DrawnFoliage(placement, foliage, placement.transform.toMatrix())
            generate(name, foliage, staged, state)
        }
        states.keys.retainAll(reasons.keys)
        for ((name, state) in states) {
            val of = HashSet<String>(reasons[name].orEmpty())
            of += state.problems
            report(state, of)
        }
        drawn = visible
        for (entry in drawn) entry.foliage.update(camera, entry.entity)
    }

    /** The renderables of the drawn foliage, with the entity transform [update] culled them under. */
    fun draw(batch: ContentBatch, environment: Environment) {
        for (entry in drawn) batch.render(entry.foliage, environment, ShaderProvider.DEFAULT_SHADER_KEY)
    }

    /** The OBJECT layers' instanced parts of the drawn foliage: the depth pass draws those, never a DETAIL layer's. */
    override fun getCastRenderables(out: Array<Renderable>, pool: Pool<Renderable>) {
        for (entry in drawn) entry.foliage.getRenderablesOf(FoliageLayerKind.OBJECT, out, pool)
    }

    /** The world box of those copies across every drawn foliage, or null when the view draws no OBJECT-layer copies. */
    override fun castBounds(): BoundingBox? {
        var bounds: BoundingBox? = null
        for (entry in drawn) {
            val of = entry.foliage.objectBounds ?: continue
            bounds = if (bounds == null) BoundingBox(of) else bounds.ext(of)
        }
        return bounds
    }

    /** Forgets everything without GL calls: the context the assets were built in is gone. */
    fun abandon() {
        assets.abandon()
        drawn = emptyList()
        states.clear()
        pending.clear()
    }

    override fun dispose() {
        assets.dispose()
        drawn = emptyList()
        states.clear()
        pending.clear()
    }

    /** What stops a placement from being shown; null when it may be. */
    private fun blockedReason(placement: FoliagePlacement, staged: PreparedFoliage): String? {
        val bound = staged.meta.terrain
        return when {
            bound != placement.terrainName ->
                "Foliage '${placement.foliageName}' is bound to terrain '$bound', but entity '${placement.entityId}' " +
                        "shows '${placement.terrainName}': nothing drawn for it"
            staged.terrain == null ->
                "Foliage '${placement.foliageName}' stands on terrain '$bound', which the project cannot read: " +
                        "nothing drawn for entity '${placement.entityId}'"
            else -> null
        }
    }

    /**
     * Schedules the generation the drawable still needs: a stale or missing bake whole, a moved draft only over the
     * chunks it touched. One generation at a time per foliage, so a stroke that lands while one runs is picked up by
     * the next one, when the draft has moved again.
     */
    private fun generate(name: String, foliage: FoliageDrawable, staged: PreparedFoliage, state: Generating) {
        if (state.inFlight) return
        val terrain = staged.terrain ?: return
        val draft = drafts.of(name)
        if (!state.baked && staged.stale) {
            state.baked = true
            state.draft = draft
            state.draftRevision = draft?.revision
            start(name, foliage, terrain, settingsOf(staged, draft), masksOf(staged, draft), null, state)
            return
        }
        if (draft == null || draft === state.draft && draft.revision == state.draftRevision) return
        state.draft = draft
        state.draftRevision = draft.revision
        // no bake to merge into, or nothing the brush touched: the stale branch above owns the whole bake
        if (foliage.bake == null || draft.dirtyChunks.isEmpty()) return
        start(name, foliage, terrain, settingsOf(staged, draft), masksOf(staged, draft), draft.dirtyChunks, state)
    }

    /** Runs [FoliageScatter] off the GL thread; nothing of the drawable is read from there, only handed over on return. */
    private fun start(
        name: String,
        foliage: FoliageDrawable,
        terrain: TerrainData,
        settings: FoliageMeta,
        masks: Map<Int, ByteArray>,
        chunks: Set<FoliageChunk>?,
        state: Generating,
    ) {
        state.inFlight = true
        background(Runnable {
            val outcome = runCatchingKeepingCancellation { scatter.scatter(terrain, settings, masks, chunks) }
            pending += Generated(name, foliage, outcome, chunks)
        })
    }

    /** Hands every finished generation to its drawable: the merged bake for the chunks the draft touched, the lot otherwise. */
    private fun applyGenerated() {
        while (true) {
            val result = pending.poll() ?: break
            val state = states[result.name] ?: continue
            if (state.view !== result.foliage) continue // the asset was rebuilt while this ran: drop the result
            state.inFlight = false
            val outcome = result.outcome.getOrNull()
            if (outcome == null || outcome.refusal != null) {
                result.outcome.exceptionOrNull()?.let { state.problems += generationFailed(result.name, it) }
                outcome?.refusal?.let { state.problems += refusal(result.name, it) }
                continue
            }
            val baked = outcome.bake
            val base = result.foliage.bake
            val merged = if (result.chunks != null && base != null) mergeFoliageChunks(base, baked, result.chunks) else baked
            result.foliage.setBake(merged)
        }
    }

    /** The settings the generation uses: the draft's when one is open, the stored asset's otherwise. */
    private fun settingsOf(staged: PreparedFoliage, draft: FoliageDraft?): FoliageMeta =
        draft?.settings ?: staged.meta

    /** The stored masks with the draft's own overrides laid over them. */
    private fun masksOf(staged: PreparedFoliage, draft: FoliageDraft?): Map<Int, ByteArray> =
        if (draft == null || draft.masks.isEmpty()) staged.masks else staged.masks + draft.masks

    /** Writes [reasons] that are new since the last frame and forgets the ones that no longer apply. */
    private fun report(state: Generating, reasons: Set<String>) {
        for (reason in reasons) if (state.logged.add(reason)) log.warn(reason)
        state.logged.retainAll(reasons)
    }

    private fun missingAsset(name: String) = "Foliage '$name' is missing or its meta.json cannot be read: skipped"

    private fun missingModel(name: String, folder: String) =
        "Foliage '$name' uses the model '$folder', which cannot be loaded: only its own copies are dropped"

    private fun noBake(name: String) =
        "Foliage '$name' has no readable foliage.data: copies are generated from its settings"

    private fun refusal(name: String, refusal: ScatterRefusal) = when (refusal) {
        ScatterRefusal.COPY_LIMIT -> "Foliage '$name' is over the copy limit: its copies are not generated"
        ScatterRefusal.CANDIDATE_LIMIT -> "Foliage '$name' has too many candidates: its copies are not generated"
    }

    private fun generationFailed(name: String, failure: Throwable) =
        "Foliage '$name' could not be generated: ${failure.message ?: failure.javaClass.simpleName}"

    /** One foliage's generation bookkeeping, kept until it leaves the scene or its asset is rebuilt. */
    private class Generating {
        /** The drawable the rest of this state belongs to; another one means the asset was rebuilt. */
        var view: FoliageDrawable? = null
        var draft: FoliageDraft? = null
        var draftRevision: Int? = null

        /** A whole-bake generation was already scheduled for [view], stale bake or not. */
        var baked = false
        var inFlight = false

        /** Problems that outlive a frame, such as a refusal, until [view] is replaced. */
        val problems = LinkedHashSet<String>()

        /** What was logged for [view] last frame, so a reason is written once and a resolved one can return. */
        val logged = HashSet<String>()

        fun rebased(view: FoliageDrawable) {
            this.view = view
            draft = null
            draftRevision = null
            baked = false
            inFlight = false
            problems.clear()
            logged.clear()
        }
    }

    /** One generation the pool finished, waiting for the next [update] to hand it to its drawable. */
    private class Generated(
        val name: String,
        val foliage: FoliageDrawable,
        val outcome: Result<ScatterOutcome>,
        val chunks: Set<FoliageChunk>?,
    )
}
