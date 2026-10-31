/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetAbsentException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetPipelineException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.CyclicDependencyException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.DependencyFailedException
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import org.slf4j.Logger
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.iterator
import kotlin.concurrent.withLock

/**
 * Split return of [AssetLoader.prepare] / [AssetLoader.loadPrepared].
 *
 * [model] is for the `onPrepared` chain only. After that chain, storage calls
 * [AssetLoader.discard] and never reads [model] again.
 *
 * [staged] is what `dependencies`, `upload`, and `build` receive on the GL thread.
 * It must stay valid after [model] is discarded — copy or detach anything shared.
 */
data class Prepared<P : Any, U : Any>(val model: P, val staged: U)

/** A loader with nothing for the `onPrepared` hooks stages everything: its model is [Unit]. */
fun <U : Any> Prepared(staged: U): Prepared<Unit, U> = Prepared(Unit, staged)


sealed interface AssetState {
    data object Preparing : AssetState
    data class Waiting(val dependencies: Set<String>, val unmet: Set<String>) : AssetState
    data object Uploading : AssetState
    data object Ready : AssetState
    data class Failed(val reason: Throwable) : AssetState
}

fun interface Subscription {
    fun cancel()
}

private class AbsentLoader : AssetLoader<Unit, Unit, Disposable> {
    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, Unit>? = null
    override fun prepare(name: String): Prepared<Unit, Unit>? = null
    override fun build(staged: Unit, assets: BuiltAssets): Disposable = error("nothing to build")
}

/** A storage with [loader] registered for every name, for assets that need no `meta.json` to pick their loader. */
fun <P : Any, U : Any, T : Disposable> AssetStorage(
    executor: Executor,
    loader: AssetLoader<P, U, T>,
    log: Logger,
): AssetStorage =
    AssetStorage(log, executor).also { it.register(loader) { _, _ -> true } }

/**
 * Loads assets named by the scene: [AssetLoader.prepare] runs off the GL thread (file IO, parsing; no GL), then
 * [update] does one [AssetLoader.upload] slice per call and [AssetLoader.build] on the GL thread once the
 * dependencies are built. Every name is loaded once and shared by all entities using it; a name that fails is
 * remembered as failed (and logged once) so it is not retried every frame.
 *
 * Call every method from the render thread except the work the storage itself moves off-thread
 * (`prepare`, `onPrepared`, `discard(model)`). The first call binds this instance to that thread.
 *
 * An asset may need others (a terrain's splat textures): [AssetLoader.dependencies] names them, the storage
 * requests them when the asset has been prepared and holds its upload and build until each is built or has failed, and
 * [retain] keeps what a retained asset needs. A loader reads a dependency at use time through the [BuiltAssets] it is
 * handed, never by calling another loader. Assets that depend on each other in a cycle cannot be built, so each
 * member fails (with a message naming the cycle) and is absent for whatever needs it.
 *
 * [invalidate] marks names as changed on disk. A loaded asset stays in use until its replacement is built, then the
 * two are swapped in one step, so a consumer never sees a half-replaced asset; a name that was loading or failed is
 * forgotten and loaded again by the next [request]. A load that finishes for a superseded request is discarded and
 * can never replace the latest one.
 *
 * Handlers must not call back into the storage (`load`, `update`, `unload`, `dispose`).
 * `onPrepared` must copy anything it needs out of the model; [AssetLoader.discard] runs
 * immediately after that chain and upload/build never see the model.
 *
 * ```
 * val meshes = storage.register(meshLoader) { name -> name.endsWith(".mesh") }
 * meshes.hooks {
 *     onPrepared { name, model ->
 *         physicsCache.put(name, model.vertices.copyOf(), model.indices.copyOf())
 *     }
 *     onDiscarded { name, _ -> physicsCache.remove(name) }
 * }
 * meshes.load("hero.mesh")
 * // each frame, on the GL thread:
 * storage.update()
 * ```
 */
class AssetStorage(
    private val log: Logger,
    executor: Executor? = null,
    /**
     * Reads the base metadata of an asset folder, on the render thread: its `type` picks the loader (see
     * [register]) and the loader then prepares from that meta. Null when there is none. Without it a loader can
     * only be chosen by name.
     */
    private val metas: ((String) -> AssetMeta<Any>?)? = null,
) : Disposable, BuiltAssets {
    private val executor: Executor
    private val ownedExecutor: ExecutorService?

    /** True when off-GL work uses virtual threads. False on the platform-pool fallback or a caller-supplied executor. */
    val virtualThreads: Boolean

    init {
        if (executor != null) {
            this.executor = executor
            ownedExecutor = null
            virtualThreads = false
        } else {
            val owned = createOffGlExecutor()
            this.executor = owned.executor
            ownedExecutor = owned.executor
            virtualThreads = owned.virtualThreads
        }
    }

    private val callGuard = StorageCallGuard()

    private val publishLock = ReentrantLock()

    @Volatile
    private var disposed = false
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()
    private val nextId = AtomicLong()
    private val inbound = ConcurrentLinkedQueue<Inbound>()
    private val strayErrors = CopyOnWriteArrayList<Throwable>()

    private val registrations = ArrayList<LoaderRegistration<*, *, *>>()

    /** Stands in for a name nothing can load: it prepares to nothing, so the asset fails as absent. */
    private val absent = LoaderRegistration(AbsentLoader(), { _, _ -> false })
    private val dependencies = DependencyResolver(metas, registrations, absent) { registration, name, type ->
        registration.handles(name, type)
    }

    private val book = LoadBook<Live>()

    /** The current asset of each requested name. */
    private val lives = book.lives

    /** The replacement of a built asset that [invalidate] marked changed, while it loads. */
    private val revisions = book.revisions
    private val builtMap = LinkedHashMap<String, Disposable>()

    /** How many assets were published under each name; changes exactly when [get] starts returning another asset. */
    private val versions = HashMap<String, Long>()
    private var uploadCursor = 0

    /** Counts built and failed assets, for [update] to tell whether anything changed. */
    private var changes = 0L

    /**
     * Registers [loader] for the assets whose `meta.json` has one of [types]. With [metas] set, a name no loader is
     * registered for (or without a meta) fails as absent, like any unreadable asset; two loaders for one type fail it
     * as ambiguous.
     */
    fun <P : Any, U : Any, T : Disposable> register(
        loader: AssetLoader<P, U, T>,
        vararg types: MetaType,
    ): LoaderRegistration<P, U, T> = register(loader) { _, type -> type != null && type in types }

    /** Registers each loader of [loaders] for its meta type. */
    @Suppress("UNCHECKED_CAST")
    fun registerAll(loaders: Map<MetaType, AssetLoader<*, *, *>>) {
        for ((type, loader) in loaders) register(loader as AssetLoader<Any, Any, Disposable>, type)
    }

    /**
     * Registers [loader]. [handles] is consulted for names nobody has `load`ed yet: [request] and the dependencies of
     * other assets, with the asset's meta type when [metas] knows it. Return true when this loader should `prepare`
     * that name. Zero matches fails the request; two or more matches fail it as ambiguous.
     */
    fun <P : Any, U : Any, T : Disposable> register(
        loader: AssetLoader<P, U, T>,
        handles: (name: String, type: MetaType?) -> Boolean,
    ): LoaderRegistration<P, U, T> {
        bindThread()
        check(!disposed) { "AssetStorage is disposed" }
        val registration = LoaderRegistration(loader, handles)
        registrations += registration
        return registration
    }

    /**
     * Starts loading [name] with the one loader whose `handles` accepts it, unless it is already loading, loaded and
     * current, or failed. A loaded asset that [invalidate] marked changed starts loading its replacement.
     */
    fun request(name: String) {
        onGl {
            check(!disposed) { "AssetStorage is disposed" }
            requestName(null, name)?.let { throw it }
        }
    }

    /**
     * Advances the pipeline on the render thread.
     * Drains finished prepares, loads newly discovered dependencies, uploads up to [maxSteps] slices
     * (one asset per slice, the one that has waited longest first), then builds every asset whose upload has
     * finished and whose dependencies are already built. Returns true when at least one asset changed state.
     */
    fun update(maxSteps: Int = 1): Boolean = onGl {
        if (disposed) return@onGl false
        val before = changes
        surfaceStray()
        drain()
        propagateFailures()
        requestStaleDependencies()
        failCycles()
        repeat(maxSteps) {
            uploadOne()
            propagateFailures()
            buildAllReady()
        }
        changes != before
    }

    /** True while any requested asset has not finished loading (neither built and current nor failed). */
    fun isLoading(): Boolean {
        bindThread()
        return lives.values.any { book.loading(it) } || revisions.isNotEmpty()
    }

    /** True while [name] is requested but neither built (and current) nor failed; false for a name never requested. */
    fun isLoading(name: String): Boolean {
        bindThread()
        return lives[name]?.let(book::loading) == true || revisions.containsKey(name)
    }

    /** The number of assets built under [name] so far: another value means [get] returns a new asset. */
    fun version(name: String): Long {
        bindThread()
        return versions[name] ?: 0L
    }

    fun state(name: String): AssetState? {
        bindThread()
        val live = lives[name] ?: return null
        return when (live.phase) {
            Phase.Preparing -> AssetState.Preparing
            Phase.Waiting -> {
                val deps = live.dependencies ?: emptySet()
                AssetState.Waiting(deps, book.unmet(deps))
            }

            Phase.Uploading, Phase.UploadDone -> AssetState.Uploading
            Phase.Ready -> AssetState.Ready
            Phase.Failed -> AssetState.Failed(live.error ?: AssetPipelineException("failed"))
        }
    }

    fun isReady(name: String): Boolean {
        bindThread()
        return lives[name]?.phase == Phase.Ready
    }

    /** The built asset of [name]; null when it is not built (or has failed). */
    override fun get(name: String): Disposable? {
        bindThread()
        return builtMap[name]
    }

    /** The built asset of [name] as an [R]; null when it is not built or is of another kind. */
    inline fun <reified R : Any> getAs(name: String): R? = get(name) as? R

    /** Hook failures for [name], in fire order. Empty when the name is unknown. Does not fail the asset. */
    fun handlerErrors(name: String): List<Throwable> {
        bindThread()
        return lives[name]?.handlerErrors?.toList().orEmpty()
    }

    /**
     * Marks [names] as changed. A loaded asset is replaced once the next [request] has loaded and built its new
     * revision (the old one is disposed then); a loading request is dropped and a failed name forgotten, so the next
     * [request] loads it again from the current files. Unknown names and every other asset are untouched.
     */
    fun invalidate(names: Set<String>) {
        onGl {
            check(!disposed) { "AssetStorage is disposed" }
            val errors = ArrayList<Throwable>()
            for (name in names) {
                val live = lives[name] ?: continue
                if (live.phase == Phase.Ready) {
                    errors += dropRevision(name)
                    live.stale = true
                } else {
                    errors += forget(name)
                }
            }
            propagateFailures()
            throwIfAny(errors, "invalidate")
        }
    }

    /** Disposes every asset neither in [names] nor needed by one that is, and forgets it (a later request loads it again). */
    fun retain(names: Set<String>) {
        onGl {
            check(!disposed) { "AssetStorage is disposed" }
            val keep = book.withDependencies(names)
            val errors = ArrayList<Throwable>()
            for (name in (lives.keys + revisions.keys).toSet()) {
                if (name !in keep) errors += forget(name)
            }
            propagateFailures()
            throwIfAny(errors, "retain")
        }
    }

    /** Drops [name]. A built asset runs `onDiscarded` and then [Disposable.dispose] on this thread. */
    fun unload(name: String) {
        onGl {
            check(!disposed) { "AssetStorage is disposed" }
            val errors = forget(name)
            propagateFailures()
            throwIfAny(errors, "unload '$name'")
        }
    }

    /**
     * Forgets every asset without disposing it, so each is loaded again. For when the GL context the assets were
     * created in is gone (abandoned): its objects must not be used, and cannot be released either.
     */
    fun abandon() {
        onGl {
            val (all, queued) = publishLock.withLock {
                val all = lives.values.toList() + revisions.values
                lives.clear()
                revisions.clear()
                builtMap.clear()
                versions.clear()
                all.forEach { cancelledIds.add(it.id) }
                val queued = drainInbound()
                // A worker that has not published yet removes its own id; for the rest we own it.
                val queuedIds = queued.mapTo(HashSet()) { it.id }
                all.filter { it.phase != Phase.Preparing || it.id in queuedIds }.forEach { cancelledIds.remove(it.id) }
                all to queued
            }
            val errors = ArrayList<Throwable>()
            for (live in all) {
                live.asset = null
                live.staged?.let { errors += discardStagedQuiet(live.registration, it) }
                live.staged = null
            }
            errors += discardQueued(queued)
            errors.forEach { log.warn("Abandoning assets: a discard failed", it) }
        }
    }

    override fun dispose() {
        onGl {
            val work = publishLock.withLock {
                if (disposed) {
                    null
                } else {
                    disposed = true
                    val snapshot = lives.values.toList() + revisions.values
                    lives.clear()
                    revisions.clear()
                    builtMap.clear()
                    snapshot.forEach { cancelledIds.add(it.id) }
                    snapshot to drainInbound()
                }
            }
            val errors = ArrayList<Throwable>()
            if (work != null) {
                for (live in work.first) errors += releaseLive(live, notifyDiscarded = live.asset != null)
                errors += discardQueued(work.second)
                ownedExecutor?.shutdown()
            }
            errors += strayErrors.toList()
            strayErrors.clear()
            throwIfAny(errors, "dispose")
        }
    }

    /** Drops [name] and its pending replacement; returns what releasing them threw. */
    private fun forget(name: String): List<Throwable> {
        val errors = ArrayList<Throwable>()
        errors += dropRevision(name)
        val live = lives.remove(name) ?: return errors
        builtMap.remove(name)
        errors += cancel(live, notifyDiscarded = live.asset != null)
        return errors
    }

    private fun dropRevision(name: String): List<Throwable> {
        val revision = revisions.remove(name) ?: return emptyList()
        return cancel(revision, notifyDiscarded = false)
    }

    /** Stops the load of [live] and releases what it holds. */
    private fun cancel(live: Live, notifyDiscarded: Boolean): List<Throwable> {
        val dropped = ArrayList<Inbound>()
        publishLock.withLock {
            cancelledIds.add(live.id)
            val keep = ArrayList<Inbound>()
            while (true) {
                val message = inbound.poll() ?: break
                if (message.id == live.id) dropped += message else keep += message
            }
            keep.forEach { inbound.offer(it) }
            // The worker removes the id if it has not published yet. If it already did, we own the id.
            if (dropped.isNotEmpty() || live.phase != Phase.Preparing) cancelledIds.remove(live.id)
        }
        val errors = ArrayList<Throwable>()
        errors += releaseLive(live, notifyDiscarded)
        errors += discardQueued(dropped)
        return errors
    }

    /** Takes every queued message off [inbound]; the caller holds [publishLock]. */
    private fun drainInbound(): List<Inbound> {
        val drained = ArrayList<Inbound>()
        while (true) drained += inbound.poll() ?: break
        return drained
    }

    /** Discards what the staged messages among [messages] hold; returns what that threw. */
    private fun discardQueued(messages: List<Inbound>): List<Throwable> {
        val errors = ArrayList<Throwable>()
        for (message in messages) {
            if (message is Inbound.Staged) errors += discardStagedQuiet(message.registration, message.staged)
        }
        return errors
    }

    private fun releaseLive(live: Live, notifyDiscarded: Boolean): List<Throwable> {
        val errors = ArrayList<Throwable>()
        val asset = live.asset
        val staged = live.staged
        live.staged = null
        live.asset = null
        if (asset != null) {
            if (notifyDiscarded) errors += live.registration.fireDiscarded(live.name, asset)
            try {
                asset.dispose()
            } catch (e: Exception) {
                errors += e
            }
        } else if (staged != null) {
            errors += discardStagedQuiet(live.registration, staged)
        }
        return errors
    }

    /**
     * Starts [name] unless something already holds it: a fresh load through the one loader that handles it, or the
     * replacement of a built asset [invalidate] marked changed. A problem is returned, not thrown.
     */
    private fun requestName(dependent: String?, name: String): Throwable? {
        val existing = lives[name]
        if (existing != null) {
            if (existing.phase != Phase.Ready || !existing.stale) return null
            existing.stale = false
            return try {
                startRevision(existing)
                null
            } catch (e: Exception) {
                e
            }
        }
        return try {
            val route = dependencies.route(dependent, name)
            startLoad(route.registration, name, route.meta)
            null
        } catch (e: Exception) {
            e
        }
    }

    private fun startLoad(registration: LoaderRegistration<*, *, *>, name: String, meta: AssetMeta<*>?) {
        require(name.isNotEmpty()) { "asset name is empty" }
        check(!disposed) { "AssetStorage is disposed" }
        val existing = lives[name]
        if (existing != null) {
            if (existing.phase != Phase.Failed) {
                throw IllegalStateException("Asset '$name' is already ${existing.phase}")
            }
            lives.remove(name)
            builtMap.remove(name)
        }
        val live = Live(nextId.incrementAndGet(), name, registration, Phase.Preparing)
        lives[name] = live
        log.atDebug().log { "Loading asset '$name'" }
        try {
            execute(live, meta)
        } catch (e: RejectedExecutionException) {
            lives.remove(name)
            throw IllegalStateException("AssetStorage cannot accept '$name'; executor is shut down", e)
        }
    }

    /** Loads the replacement of the built [current]; it takes its place when built. */
    private fun startRevision(current: Live) {
        val name = current.name
        val route = dependencies.route(null, name)
        val revision = Live(nextId.incrementAndGet(), name, route.registration, Phase.Preparing, revision = true)
        revisions[name] = revision
        log.atDebug().log { "Reloading asset '$name'" }
        try {
            execute(revision, route.meta)
        } catch (e: RejectedExecutionException) {
            revisions.remove(name)
            throw IllegalStateException("AssetStorage cannot accept '$name'; executor is shut down", e)
        }
    }

    private fun execute(live: Live, meta: AssetMeta<*>?) {
        executor.execute { runPrepare(live.registration, live.id, live.name, meta) }
    }

    private fun runPrepare(
        registration: LoaderRegistration<*, *, *>,
        id: Long,
        name: String,
        meta: AssetMeta<*>?,
    ) {
        val skipped = publishLock.withLock { disposed || cancelledIds.contains(id) }
        if (skipped) {
            cancelledIds.remove(id)
            return
        }
        val started = System.nanoTime()
        val prepared = try {
            if (meta != null) registration.loadMeta(meta) else registration.prepareNew(name)
        } catch (e: Exception) {
            publishFailure(id, name, e)
            return
        }
        log.atDebug().log {
            "Prepared asset '$name' in ${(System.nanoTime() - started) / 1_000_000} ms" +
                    if (prepared == null) " (nothing to build)" else ""
        }
        if (prepared == null) {
            publishFailure(id, name, AssetAbsentException(name))
            return
        }
        val cancelledEarly = publishLock.withLock { disposed || cancelledIds.contains(id) }
        if (cancelledEarly) {
            discardModelQuiet(registration, prepared.model)
            strayErrors += discardStagedQuiet(registration, prepared.staged)
            cancelledIds.remove(id)
            return
        }
        val handlerErrors = registration.firePrepared(name, prepared.model)
        val discardError = try {
            registration.discardModel(prepared.model)
            null
        } catch (e: Exception) {
            e
        }
        if (discardError != null) {
            discardStagedQuiet(registration, prepared.staged).forEach { discardError.addSuppressed(it) }
            publishFailure(id, name, discardError, handlerErrors)
            return
        }
        val message = Inbound.Staged(id, name, registration, prepared.staged, handlerErrors)
        val published = publishLock.withLock {
            if (disposed || cancelledIds.contains(id)) {
                false
            } else {
                inbound.offer(message)
                true
            }
        }
        if (!published) {
            strayErrors += discardStagedQuiet(registration, prepared.staged)
            cancelledIds.remove(id)
        }
    }

    private fun publishFailure(
        id: Long,
        name: String,
        error: Throwable,
        handlerErrors: List<Throwable> = emptyList(),
    ) {
        publishLock.withLock {
            if (disposed || cancelledIds.contains(id)) return
            inbound.offer(Inbound.Failed(id, name, error, handlerErrors))
        }
    }

    private fun drain() {
        while (true) {
            val message = inbound.poll() ?: break
            when (message) {
                is Inbound.Failed -> applyFailure(message)
                is Inbound.Staged -> applyStaged(message)
            }
        }
    }

    /** The load that [id] belongs to, or null when it was forgotten or superseded meanwhile. */
    private fun liveOf(name: String, id: Long): Live? = book.owner(name, id)?.takeUnless { cancelledIds.contains(id) }

    private fun applyFailure(message: Inbound.Failed) {
        val live = liveOf(message.name, message.id)
        if (live == null) {
            cancelledIds.remove(message.id)
            return
        }
        live.handlerErrors += message.handlerErrors
        fail(live, message.error)
    }

    private fun applyStaged(message: Inbound.Staged) {
        val live = liveOf(message.name, message.id)
        if (live == null) {
            strayErrors += discardStagedQuiet(message.registration, message.staged)
            cancelledIds.remove(message.id)
            return
        }
        live.handlerErrors += message.handlerErrors
        live.staged = message.staged
        val dependencies = try {
            message.registration.dependencies(message.staged).toSet()
        } catch (e: Exception) {
            fail(live, e)
            return
        }
        live.dependencies = dependencies
        live.phase = Phase.Waiting
        for (dependency in dependencies) {
            val problem = requestName(live.name, dependency)
            if (problem != null) {
                fail(live, problem)
                return
            }
        }
    }

    /** A dependency [invalidate] marked changed after its dependent was prepared is reloaded here; nobody else asks. */
    private fun requestStaleDependencies() {
        for (live in active()) {
            if (!book.blocksOnDependencies(live)) continue
            for (dependency in live.dependencies.orEmpty()) {
                if (lives[dependency]?.stale != true) continue
                requestName(live.name, dependency)?.let { fail(live, it) }
            }
        }
    }

    private fun uploadOne() {
        val candidates = active().filter { it.canUpload() }
        if (candidates.isEmpty()) {
            uploadCursor = 0
            return
        }
        if (uploadCursor >= candidates.size) uploadCursor %= candidates.size
        val live = candidates[uploadCursor]
        uploadCursor = (uploadCursor + 1) % candidates.size
        val staged = live.staged ?: return
        val done = try {
            live.registration.upload(staged)
        } catch (e: Exception) {
            fail(live, e)
            return
        }
        live.phase = Phase.Uploading
        if (!done) return
        live.phase = Phase.UploadDone
        live.handlerErrors += live.registration.fireUploaded(live.name, staged)
    }

    private fun buildAllReady() {
        while (true) {
            val live = active().firstOrNull { it.phase == Phase.UploadDone && book.depsSatisfied(it) } ?: return
            val staged = live.staged
            if (staged == null) {
                fail(live, AssetPipelineException("Asset '${live.name}' lost its staged value"))
                continue
            }
            val built = try {
                live.registration.build(staged, this)
            } catch (e: Exception) {
                fail(live, e)
                continue
            }
            val replaced = if (live.revision) {
                revisions.remove(live.name)
                live.revision = false
                lives.put(live.name, live)
            } else {
                null
            }
            live.asset = built
            live.phase = Phase.Ready
            changes++
            builtMap[live.name] = built
            versions[live.name] = version(live.name) + 1
            log.atDebug().log { "Asset '${live.name}' is ready" }
            replaced?.let { live.handlerErrors += releaseLive(it, notifyDiscarded = it.asset != null) }
            live.handlerErrors += live.registration.fireBuilt(live.name, built)
            live.handlerErrors += discardStagedQuiet(live.registration, staged)
            live.staged = null
        }
    }

    private fun propagateFailures() {
        var changed = true
        while (changed) {
            changed = false
            for (live in active()) {
                if (!book.blocksOnDependencies(live)) continue
                val reason = book.dependencyProblem(live) ?: continue
                fail(live, reason)
                changed = true
            }
        }
    }

    private fun failCycles() {
        val cyclic = book.cyclicNames()
        for (name in cyclic) {
            val live = book.entry(name) ?: continue
            if (live.phase != Phase.Failed && live.phase != Phase.Ready) {
                fail(live, CyclicDependencyException(cyclic.toList()))
            }
        }
    }

    /**
     * Fails [live] and logs why once. A replacement that fails takes the place of the asset it would have replaced,
     * which is disposed: a revision that cannot load leaves nothing, not the stale asset.
     */
    private fun fail(live: Live, error: Throwable) {
        if (live.phase == Phase.Failed || live.phase == Phase.Ready) return
        live.phase = Phase.Failed
        live.error = error
        changes++
        if (error is AssetAbsentException) {
            log.warn("Asset '${live.name}' is missing or unreadable")
        } else {
            log.warn("Failed to load asset '${live.name}'", error)
        }
        val staged = live.staged
        live.staged = null
        if (staged != null) {
            live.handlerErrors += discardStagedQuiet(live.registration, staged)
        }
        if (live.revision) {
            revisions.remove(live.name)
            live.revision = false
            val old = lives.put(live.name, live)
            builtMap.remove(live.name)
            old?.let { live.handlerErrors += releaseLive(it, notifyDiscarded = it.asset != null) }
        }
    }

    private fun active(): List<Live> = book.active()

    private fun Live.canUpload(): Boolean {
        if (staged == null) return false
        if (phase != Phase.Waiting && phase != Phase.Uploading) return false
        return book.depsSatisfied(this)
    }

    private fun discardModelQuiet(registration: LoaderRegistration<*, *, *>, model: Any) {
        try {
            registration.discardModel(model)
        } catch (e: Exception) {
            strayErrors += e
        }
    }

    private fun discardStagedQuiet(registration: LoaderRegistration<*, *, *>, staged: Any): List<Throwable> =
        try {
            registration.discardStaged(staged)
            emptyList()
        } catch (e: Exception) {
            listOf(e)
        }

    private fun surfaceStray() {
        if (strayErrors.isEmpty()) {
            return
        }
        val error = AssetPipelineException("off-GL discard failed")
        strayErrors.forEach { error.addSuppressed(it) }
        strayErrors.clear()
        throw error
    }

    private fun throwIfAny(errors: List<Throwable>, where: String) {
        if (errors.isEmpty()) return
        val error = AssetPipelineException("$where hit ${errors.size} error(s)")
        errors.forEach { error.addSuppressed(it) }
        throw error
    }

    private fun bindThread() = callGuard.bindThread()

    private inline fun <T> onGl(block: () -> T): T = callGuard.onGl(block)

    private class Live(
        override val id: Long,
        override val name: String,
        val registration: LoaderRegistration<*, *, *>,
        override var phase: Phase,
        /** True while this load is the replacement of a built asset (see [revisions]). */
        var revision: Boolean = false,
        var staged: Any? = null,
        override var dependencies: Set<String>? = null,
        var asset: Disposable? = null,
        override var error: Throwable? = null,
        /** [invalidate] marked the built asset changed; the next [request] loads its replacement. */
        override var stale: Boolean = false,
        val handlerErrors: MutableList<Throwable> = ArrayList(),
    ) : LoadView

    private sealed interface Inbound {
        val id: Long
        val name: String

        class Staged(
            override val id: Long,
            override val name: String,
            val registration: LoaderRegistration<*, *, *>,
            val staged: Any,
            val handlerErrors: List<Throwable>,
        ) : Inbound

        class Failed(
            override val id: Long,
            override val name: String,
            val error: Throwable,
            val handlerErrors: List<Throwable>,
        ) : Inbound
    }

    /** The lifecycle hooks of one loader: each kind has a loader-wide overload and a per-name overload. */
    interface HookSink<P : Any, U : Any, T : Disposable> {
        fun onPrepared(handler: (name: String, model: P) -> Unit): Subscription
        fun onPrepared(name: String, handler: (model: P) -> Unit): Subscription
        fun onUploaded(handler: (name: String, staged: U) -> Unit): Subscription
        fun onUploaded(name: String, handler: (staged: U) -> Unit): Subscription
        fun onBuilt(handler: (name: String, asset: T) -> Unit): Subscription
        fun onBuilt(name: String, handler: (asset: T) -> Unit): Subscription

        /** Runs on the GL thread when a built asset is unloaded or the storage is disposed, before [T.dispose]. */
        fun onDiscarded(handler: (name: String, asset: T) -> Unit): Subscription
        fun onDiscarded(name: String, handler: (asset: T) -> Unit): Subscription
    }

    inner class LoaderRegistration<P : Any, U : Any, T : Disposable> internal constructor(
        internal val loader: AssetLoader<P, U, T>,
        internal val handles: (String, MetaType?) -> Boolean,
    ) : HookSink<P, U, T> {
        private val preparedHooks = HookRegistry<P>(::bindThread)
        private val uploadedHooks = HookRegistry<U>(::bindThread)
        private val builtHooks = HookRegistry<T>(::bindThread)
        private val discardedHooks = HookRegistry<T>(::bindThread)

        fun load(name: String) = onGl { startLoad(this, name, null) }

        fun load(name: String, meta: AssetMeta<Any>) = onGl { startLoad(this, name, meta) }

        /** Loader-wide hooks, in registration order. Per-name hooks run after these. */
        fun hooks(block: HookScope.() -> Unit) {
            bindThread()
            HookScope().block()
        }

        override fun onPrepared(handler: (name: String, model: P) -> Unit): Subscription = preparedHooks.add(handler)

        override fun onPrepared(name: String, handler: (model: P) -> Unit): Subscription =
            preparedHooks.add(name, handler)

        override fun onUploaded(handler: (name: String, staged: U) -> Unit): Subscription = uploadedHooks.add(handler)

        override fun onUploaded(name: String, handler: (staged: U) -> Unit): Subscription =
            uploadedHooks.add(name, handler)

        override fun onBuilt(handler: (name: String, asset: T) -> Unit): Subscription = builtHooks.add(handler)

        override fun onBuilt(name: String, handler: (asset: T) -> Unit): Subscription = builtHooks.add(name, handler)

        override fun onDiscarded(handler: (name: String, asset: T) -> Unit): Subscription = discardedHooks.add(handler)

        override fun onDiscarded(name: String, handler: (asset: T) -> Unit): Subscription =
            discardedHooks.add(name, handler)

        /** The hook overloads of this registration, for use inside [hooks]. */
        inner class HookScope : HookSink<P, U, T> by this@LoaderRegistration

        internal fun prepareNew(name: String): Prepared<Any, Any>? {
            @Suppress("UNCHECKED_CAST")
            return loader.prepare(name) as Prepared<Any, Any>?
        }

        internal fun loadMeta(meta: AssetMeta<*>): Prepared<Any, Any>? {
            @Suppress("UNCHECKED_CAST")
            return loader.loadPrepared(meta as AssetMeta<Any>) as Prepared<Any, Any>?
        }

        internal fun discardModel(model: Any) {
            @Suppress("UNCHECKED_CAST")
            loader.discard(model as P)
        }

        internal fun discardStaged(staged: Any) {
            @Suppress("UNCHECKED_CAST")
            loader.discardStaged(staged as U)
        }

        internal fun dependencies(staged: Any): Set<String> {
            @Suppress("UNCHECKED_CAST")
            return loader.dependencies(staged as U)
        }

        internal fun upload(staged: Any): Boolean {
            @Suppress("UNCHECKED_CAST")
            return loader.upload(staged as U)
        }

        internal fun build(staged: Any, assets: BuiltAssets): Disposable {
            @Suppress("UNCHECKED_CAST")
            return loader.build(staged as U, assets)
        }

        internal fun firePrepared(name: String, model: Any): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return preparedHooks.fire(name, model as P)
        }

        internal fun fireUploaded(name: String, staged: Any): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return uploadedHooks.fire(name, staged as U)
        }

        internal fun fireBuilt(name: String, asset: Disposable): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return builtHooks.fire(name, asset as T)
        }

        internal fun fireDiscarded(name: String, asset: Disposable): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return discardedHooks.fire(name, asset as T)
        }

    }
}
