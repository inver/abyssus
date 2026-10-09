/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AmbiguousLoaderException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetAbsentException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetPipelineException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.CyclicDependencyException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.DependencyFailedException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.MissingDependencyException
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import org.slf4j.Logger
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
    override fun discard(model: Unit) = Unit
}

private class OffGlExecutor(val executor: ExecutorService, val virtualThreads: Boolean)

private val prepareThreadIds = AtomicInteger()

/**
 * Virtual threads when `Executors.newVirtualThreadPerTaskExecutor` exists and does not throw
 * (JDK 21+ desktop). Otherwise a daemon fixed pool named `asset-prepare-*`.
 * The public [AssetStorage] API does not change between the two.
 */
private fun createOffGlExecutor(
    parallelism: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
): OffGlExecutor {
    try {
        val method = Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor")
        val executor = method.invoke(null) as ExecutorService
        return OffGlExecutor(executor, virtualThreads = true)
    } catch (e: NoSuchMethodException) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    } catch (e: UnsupportedOperationException) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    } catch (e: InvocationTargetException) {
        val cause = e.cause
        if (cause is UnsupportedOperationException || cause is NoSuchMethodError) {
            return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
        }
        throw e
    } catch (e: NoSuchMethodError) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    }
}

private fun platformPreparePool(parallelism: Int): ExecutorService =
    Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "asset-prepare-${prepareThreadIds.incrementAndGet()}").apply { isDaemon = true }
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

    private val glThread = AtomicReference<Thread?>(null)
    private var callDepth = 0

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

    /** The current asset of each requested name. */
    private val lives = LinkedHashMap<String, Live>()

    /** The replacement of a built asset that [invalidate] marked changed, while it loads. */
    private val revisions = LinkedHashMap<String, Live>()
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
        return lives.values.any { it.loading() } || revisions.isNotEmpty()
    }

    /** True while [name] is requested but neither built (and current) nor failed; false for a name never requested. */
    fun isLoading(name: String): Boolean {
        bindThread()
        return lives[name]?.loading() == true || revisions.containsKey(name)
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
                AssetState.Waiting(deps, unmet(deps))
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
            val keep = withDependencies(names)
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
                val queued = ArrayList<Inbound>()
                while (true) queued += inbound.poll() ?: break
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
            for (message in queued) {
                if (message is Inbound.Staged) errors += discardStagedQuiet(message.registration, message.staged)
            }
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
                    val queued = ArrayList<Inbound>()
                    while (true) queued += inbound.poll() ?: break
                    snapshot to queued
                }
            }
            val errors = ArrayList<Throwable>()
            if (work != null) {
                for (live in work.first) errors += releaseLive(live, notifyDiscarded = live.asset != null)
                for (message in work.second) {
                    if (message is Inbound.Staged) errors += discardStagedQuiet(message.registration, message.staged)
                }
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
        for (message in dropped) {
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
            val route = route(dependent, name)
            startLoad(route.registration, name, route.meta)
            null
        } catch (e: Exception) {
            e
        }
    }

    /** The loader of [name], and the meta it prepares from when [metas] read one. */
    private class Route(val registration: LoaderRegistration<*, *, *>, val meta: AssetMeta<Any>?)

    private fun route(dependent: String?, name: String): Route {
        val meta = metas?.invoke(name)
        val matches = registrations.filter { it.handles(name, meta?.type) }
        return when (matches.size) {
            0 -> if (metas != null) Route(absent, null) else throw MissingDependencyException(dependent ?: name, name)
            1 -> Route(matches[0], meta)
            else -> throw AmbiguousLoaderException(name)
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
        val route = route(null, name)
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
    private fun liveOf(name: String, id: Long): Live? =
        (revisions[name]?.takeIf { it.id == id } ?: lives[name]?.takeIf { it.id == id })
            ?.takeUnless { cancelledIds.contains(id) }

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
            if (!live.blocksOnDependencies()) continue
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
            val live = active().firstOrNull { it.phase == Phase.UploadDone && it.depsSatisfied() } ?: return
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
                if (!live.blocksOnDependencies()) continue
                val dependencies = live.dependencies ?: continue
                for (dependency in dependencies) {
                    val other = lives[dependency]
                    val reason = when {
                        other == null -> MissingDependencyException(live.name, dependency)
                        other.phase == Phase.Failed -> DependencyFailedException(
                            live.name,
                            dependency,
                            other.error ?: AssetPipelineException("dependency failed"),
                        )

                        else -> null
                    }
                    if (reason != null) {
                        fail(live, reason)
                        changed = true
                        break
                    }
                }
            }
        }
    }

    private fun failCycles() {
        val color = HashMap<String, Int>()
        val stack = ArrayList<String>()
        val cyclic = LinkedHashSet<String>()

        fun entry(name: String): Live? = revisions[name] ?: lives[name]

        fun outgoing(name: String): List<String> {
            val dependencies = entry(name)?.dependencies ?: return emptyList()
            return dependencies.filter { dependency ->
                val other = entry(dependency)
                other != null && other.phase != Phase.Ready && other.phase != Phase.Failed
            }
        }

        fun dfs(name: String) {
            color[name] = 1
            stack.add(name)
            for (dependency in outgoing(name)) {
                when (color[dependency] ?: 0) {
                    0 -> dfs(dependency)
                    1 -> {
                        val at = stack.indexOf(dependency)
                        if (at >= 0) cyclic.addAll(stack.subList(at, stack.size))
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            color[name] = 2
        }

        for (live in active().filter { it.phase == Phase.Waiting }) {
            if ((color[live.name] ?: 0) == 0) dfs(live.name)
        }
        for (name in cyclic) {
            val live = entry(name) ?: continue
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

    private fun active(): List<Live> = lives.values.toList() + revisions.values

    private fun unmet(dependencies: Set<String>): Set<String> =
        dependencies.filterNot { lives[it]?.settled() == true }.toSet()

    /** [names] and, transitively, what the loading and built assets among them need. */
    private fun withDependencies(names: Set<String>): Set<String> {
        val keep = HashSet(names)
        var frontier: Collection<String> = names
        while (frontier.isNotEmpty()) {
            frontier = frontier
                .flatMap { (lives[it]?.dependencies.orEmpty()) + (revisions[it]?.dependencies.orEmpty()) }
                .filter(keep::add)
        }
        return keep
    }

    private fun Live.canUpload(): Boolean {
        if (staged == null) return false
        if (phase != Phase.Waiting && phase != Phase.Uploading) return false
        return depsSatisfied()
    }

    private fun Live.depsSatisfied(): Boolean {
        val dependencies = dependencies ?: return false
        return dependencies.all { lives[it]?.settled() == true && !revisions.containsKey(it) }
    }

    /** Built and not marked changed. */
    private fun Live.settled(): Boolean = phase == Phase.Ready && !stale

    private fun Live.loading(): Boolean = when (phase) {
        Phase.Failed -> false
        Phase.Ready -> stale
        else -> true
    }

    private fun Live.blocksOnDependencies(): Boolean =
        phase == Phase.Waiting || phase == Phase.Uploading || phase == Phase.UploadDone

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

    private fun bindThread() {
        val current = Thread.currentThread()
        while (true) {
            val expected = glThread.get()
            if (expected == null) {
                if (glThread.compareAndSet(null, current)) {
                    return
                }
            } else {
                if (expected === current) {
                    return
                }
                throw IllegalStateException(
                    "AssetStorage is bound to '${expected.name}' but was called from '${current.name}'",
                )
            }
        }
    }

    private inline fun <T> onGl(block: () -> T): T {
        bindThread()
        check(callDepth == 0) { "AssetStorage must not be re-entered from a loader or a hook" }
        callDepth++
        try {
            return block()
        } finally {
            callDepth--
        }
    }

    private enum class Phase {
        Preparing,
        Waiting,
        Uploading,
        UploadDone,
        Ready,
        Failed,
    }

    private class Live(
        val id: Long,
        val name: String,
        val registration: LoaderRegistration<*, *, *>,
        var phase: Phase,
        /** True while this load is the replacement of a built asset (see [revisions]). */
        var revision: Boolean = false,
        var staged: Any? = null,
        var dependencies: Set<String>? = null,
        var asset: Disposable? = null,
        var error: Throwable? = null,
        /** [invalidate] marked the built asset changed; the next [request] loads its replacement. */
        var stale: Boolean = false,
        val handlerErrors: MutableList<Throwable> = ArrayList(),
    )

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

    inner class LoaderRegistration<P : Any, U : Any, T : Disposable> internal constructor(
        internal val loader: AssetLoader<P, U, T>,
        internal val handles: (String, MetaType?) -> Boolean,
    ) {
        private val preparedHooks = CopyOnWriteArrayList<(String, P) -> Unit>()
        private val preparedByName = ConcurrentHashMap<String, CopyOnWriteArrayList<(P) -> Unit>>()
        private val uploadedHooks = CopyOnWriteArrayList<(String, U) -> Unit>()
        private val uploadedByName = ConcurrentHashMap<String, CopyOnWriteArrayList<(U) -> Unit>>()
        private val builtHooks = CopyOnWriteArrayList<(String, T) -> Unit>()
        private val builtByName = ConcurrentHashMap<String, CopyOnWriteArrayList<(T) -> Unit>>()
        private val discardedHooks = CopyOnWriteArrayList<(String, T) -> Unit>()
        private val discardedByName = ConcurrentHashMap<String, CopyOnWriteArrayList<(T) -> Unit>>()

        fun load(name: String) = onGl { startLoad(this, name, null) }

        fun load(name: String, meta: AssetMeta<Any>) = onGl { startLoad(this, name, meta) }

        /** Loader-wide hooks, in registration order. Per-name hooks run after these. */
        fun hooks(block: HookScope.() -> Unit) {
            bindThread()
            HookScope().block()
        }

        fun onPrepared(handler: (name: String, model: P) -> Unit): Subscription = add(preparedHooks, handler)

        fun onPrepared(name: String, handler: (model: P) -> Unit): Subscription =
            addNamed(preparedByName, name, handler)

        fun onUploaded(handler: (name: String, staged: U) -> Unit): Subscription = add(uploadedHooks, handler)

        fun onUploaded(name: String, handler: (staged: U) -> Unit): Subscription =
            addNamed(uploadedByName, name, handler)

        fun onBuilt(handler: (name: String, asset: T) -> Unit): Subscription = add(builtHooks, handler)

        fun onBuilt(name: String, handler: (asset: T) -> Unit): Subscription = addNamed(builtByName, name, handler)

        /** Runs on the GL thread when a built asset is unloaded or the storage is disposed, before [T.dispose]. */
        fun onDiscarded(handler: (name: String, asset: T) -> Unit): Subscription = add(discardedHooks, handler)

        fun onDiscarded(name: String, handler: (asset: T) -> Unit): Subscription =
            addNamed(discardedByName, name, handler)

        private fun <H : Any> add(list: CopyOnWriteArrayList<H>, handler: H): Subscription {
            bindThread()
            list.add(handler)
            return Subscription {
                bindThread()
                list.remove(handler)
            }
        }

        private fun <H : Any> addNamed(
            lists: ConcurrentHashMap<String, CopyOnWriteArrayList<H>>,
            name: String,
            handler: H,
        ): Subscription {
            bindThread()
            val list = lists.computeIfAbsent(name) { CopyOnWriteArrayList() }
            list.add(handler)
            return Subscription {
                bindThread()
                list.remove(handler)
            }
        }

        inner class HookScope {
            fun onPrepared(handler: (name: String, model: P) -> Unit) {
                this@LoaderRegistration.onPrepared(handler)
            }

            fun onPrepared(name: String, handler: (model: P) -> Unit) {
                this@LoaderRegistration.onPrepared(name, handler)
            }

            fun onUploaded(handler: (name: String, staged: U) -> Unit) {
                this@LoaderRegistration.onUploaded(handler)
            }

            fun onUploaded(name: String, handler: (staged: U) -> Unit) {
                this@LoaderRegistration.onUploaded(name, handler)
            }

            fun onBuilt(handler: (name: String, asset: T) -> Unit) {
                this@LoaderRegistration.onBuilt(handler)
            }

            fun onBuilt(name: String, handler: (asset: T) -> Unit) {
                this@LoaderRegistration.onBuilt(name, handler)
            }

            fun onDiscarded(handler: (name: String, asset: T) -> Unit) {
                this@LoaderRegistration.onDiscarded(handler)
            }

            fun onDiscarded(name: String, handler: (asset: T) -> Unit) {
                this@LoaderRegistration.onDiscarded(name, handler)
            }
        }

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
            return fire(preparedHooks.toList(), preparedByName[name]?.toList().orEmpty(), name, model as P)
        }

        internal fun fireUploaded(name: String, staged: Any): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return fire(uploadedHooks.toList(), uploadedByName[name]?.toList().orEmpty(), name, staged as U)
        }

        internal fun fireBuilt(name: String, asset: Disposable): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return fire(builtHooks.toList(), builtByName[name]?.toList().orEmpty(), name, asset as T)
        }

        internal fun fireDiscarded(name: String, asset: Disposable): List<Throwable> {
            @Suppress("UNCHECKED_CAST")
            return fire(discardedHooks.toList(), discardedByName[name]?.toList().orEmpty(), name, asset as T)
        }

        private fun <V> fire(
            global: List<(String, V) -> Unit>,
            named: List<(V) -> Unit>,
            name: String,
            value: V,
        ): List<Throwable> {
            val errors = ArrayList<Throwable>()
            for (handler in global) {
                try {
                    handler(name, value)
                } catch (e: Exception) {
                    errors += e
                }
            }
            for (handler in named) {
                try {
                    handler(value)
                } catch (e: Exception) {
                    errors += e
                }
            }
            return errors
        }
    }
}
