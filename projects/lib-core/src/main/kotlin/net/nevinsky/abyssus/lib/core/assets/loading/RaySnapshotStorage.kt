package net.nevinsky.abyssus.lib.core.assets.loading

import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import java.util.concurrent.Executor

/** Immutable optional CPU copy of an asset for ray tracing. */
interface RaySnapshot {
    /** Retained payload, counted against the store's budget. */
    val byteSize: Long
}

/**
 * Builds the CPU snapshot of one asset: [load] reads it afresh from its meta (null when the asset has none), and
 * [capture] copies the [D] a raster preparation already holds, so nothing is read twice.
 */
interface RaySnapshotLoader<S : RaySnapshot, in D> {
    fun load(meta: AssetMeta<Any>): S?

    fun capture(source: D): S = throw UnsupportedOperationException("This asset kind has no raster capture")
}

/**
 * Shared optional CPU snapshots of one project's assets, keyed by asset name. No GPU cache invalidation or GL
 * readback. [label] ("sky", "terrain", "model") names the asset kind in failure messages.
 */
class RaySnapshotStore<S : RaySnapshot, D>(
    private val executor: Executor,
    private val metaLoader: AssetMetaLoader,
    private val loader: RaySnapshotLoader<S, D>,
    private val label: String,
    private val maxBytes: Long = 256L * 1024 * 1024,
) {
    private class Entry<S>(var references: Int = 0, var snapshot: S? = null, var failure: Throwable? = null)

    private val lock = Any()
    private val entries = HashMap<String, Entry<S>>()
    private var bytes = 0L
    val retainedBytes: Long get() = synchronized(lock) { bytes }

    init {
        require(maxBytes > 0)
    }

    /** A lease is closed on disable/deletion/project replacement; native workers receive only its immutable snapshot. */
    fun acquire(name: String): RaySnapshotLease<S> {
        var fresh = false
        val entry = synchronized(lock) {
            entries.getOrPut(name) { fresh = true; Entry() }.also { it.references++ }
        }
        val lease = RaySnapshotLease(
            { synchronized(lock) { entry.snapshot } },
            { synchronized(lock) { entry.failure } },
            {
                synchronized(lock) {
                    if (--entry.references == 0) {
                        if (entries[name] === entry) entries.remove(name)
                        bytes -= entry.snapshot?.byteSize ?: 0
                        entry.snapshot = null
                        entry.failure = null
                    }
                }
            }
        )
        if (fresh) try {
            executor.execute {
                if (!wanted(name, entry)) return@execute
                publish(name, entry, runCatchingKeepingCancellation { read(name) })
            }
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
        return lease
    }

    /** Invalidate every view's old revision and any in-flight preparation, then allow a fresh acquisition. */
    fun invalidate(name: String) = synchronized(lock) {
        val entry = entries.remove(name) ?: return@synchronized
        bytes -= entry.snapshot?.byteSize ?: 0
        entry.snapshot = null
        entry.failure = IllegalStateException("CPU $label '$name' was invalidated")
    }

    /** Capture request identity before IO so an old preparation cannot fill a replacement lease. */
    fun preparation(name: String): RaySnapshotCapture<D>? {
        val entry = synchronized(lock) { entries[name] } ?: return null
        if (!wanted(name, entry)) return null
        return RaySnapshotCapture { source ->
            if (wanted(name, entry)) publish(name, entry, runCatchingKeepingCancellation { loader.capture(source) })
        }
    }

    private fun read(name: String): S {
        val meta = checkNotNull(metaLoader.loadBaseMeta(name)) { "CPU $label '$name' is unreadable" }
        return checkNotNull(loader.load(meta)) { "CPU $label '$name' is unreadable" }
    }

    private fun wanted(name: String, entry: Entry<S>) = synchronized(lock) {
        entries[name] === entry && entry.references > 0 && entry.snapshot == null && entry.failure == null
    }

    private fun publish(name: String, entry: Entry<S>, result: Result<S>) = synchronized(lock) {
        if (!wanted(name, entry)) return@synchronized
        val snapshot = result.getOrNull()
        if (snapshot == null) entry.failure = result.exceptionOrNull()
        else if (snapshot.byteSize > maxBytes - bytes) entry.failure =
            IllegalStateException("Shared CPU $label snapshots exceed $maxBytes bytes")
        else {
            entry.snapshot = snapshot; bytes += snapshot.byteSize
        }
    }
}

/** One preparation's optional CPU interest; offering after cancellation/reacquisition is a no-op. */
class RaySnapshotCapture<D> internal constructor(private val capture: (D) -> Unit) {
    fun offer(source: D) = capture(source)
}

/** Polling does not wait for IO. Closing drops all references held by this lease and is idempotent. */
class RaySnapshotLease<S : RaySnapshot> internal constructor(
    snapshot: () -> S?, failure: () -> Throwable?, release: () -> Unit,
) : AutoCloseable {
    private var snapshotReader: (() -> S?)? = snapshot
    private var failureReader: (() -> Throwable?)? = failure
    private var release: (() -> Unit)? = release
    val snapshot: S? get() = synchronized(this) { snapshotReader?.invoke() }
    val failure: Throwable? get() = synchronized(this) { failureReader?.invoke() }
    override fun close() = synchronized(this) {
        release?.invoke()
        release = null
        snapshotReader = null
        failureReader = null
    }
}
