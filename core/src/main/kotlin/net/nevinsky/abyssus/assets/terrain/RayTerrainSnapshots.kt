/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.terrain

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import java.io.File
import java.util.concurrent.Executor

/** Shared optional CPU companions, keyed by project and asset. No GPU cache invalidation or GL readback. */
class RayTerrainSnapshots(
    private val executor: Executor,
    private val read: (AssetFiles, String) -> RayTerrainSnapshot?,
    private val capture: (TerrainData, Map<String, Pixmap>) -> RayTerrainSnapshot,
    private val maxBytes: Long = 256L * 1024 * 1024,
) {
    private data class Key(val project: String, val name: String)
    private class Entry(
        var references: Int = 0,
        var snapshot: RayTerrainSnapshot? = null,
        var failure: Throwable? = null
    )

    private val lock = Any()
    private val entries = HashMap<Key, Entry>()
    private var bytes = 0L
    val retainedBytes: Long get() = synchronized(lock) { bytes }

    init {
        require(maxBytes > 0)
    }

    /** A lease is closed on disable/deletion/project replacement; native workers receive only its immutable terrain snapshot. */
    fun acquire(files: AssetFiles, name: String): RayTerrainSnapshotLease {
        val key = key(files, name)
        var fresh = false
        val entry = synchronized(lock) {
            entries.getOrPut(key) { fresh = true; Entry() }.also { it.references++ }
        }
        val lease = RayTerrainSnapshotLease(
            { synchronized(lock) { entry.snapshot } },
            { synchronized(lock) { entry.failure } },
            {
                synchronized(lock) {
                    if (--entry.references == 0) {
                        if (entries[key] === entry) entries.remove(key)
                        bytes -= entry.snapshot?.byteSize ?: 0
                        entry.snapshot = null
                        entry.failure = null
                    }
                }
            }
        )
        if (fresh) try {
            executor.execute {
                if (!wanted(key, entry)) return@execute
                val result = runCatchingKeepingCancellation {
                    checkNotNull(
                        read(
                            files,
                            name
                        )
                    ) { "CPU terrain '$name' is unreadable" }
                }
                publish(key, entry, result)
            }
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
        return lease
    }

    /** Invalidate every view's old revision and any in-flight preparation, then allow a fresh acquisition. */
    fun invalidate(files: AssetFiles, name: String) = synchronized(lock) {
        val entry = entries.remove(key(files, name)) ?: return@synchronized
        bytes -= entry.snapshot?.byteSize ?: 0
        entry.snapshot = null
        entry.failure = IllegalStateException("CPU terrain '$name' was invalidated")
    }

    fun preparation(projectDir: File, name: String): RayTerrainSnapshotCapture? {
        val key = key(projectDir, name)
        val entry = synchronized(lock) { entries[key] } ?: return null
        if (!wanted(key, entry)) {
            return null
        }
        return RayTerrainSnapshotCapture { data, images ->
            if (wanted(key, entry)) {
                publish(key, entry, runCatchingKeepingCancellation { capture(data, images) })
            }
        }
    }

    private fun wanted(key: Key, entry: Entry) = synchronized(lock) {
        entries[key] === entry && entry.references > 0 && entry.snapshot == null && entry.failure == null
    }

    private fun publish(key: Key, entry: Entry, result: Result<RayTerrainSnapshot>) = synchronized(lock) {
        if (!wanted(key, entry)) return@synchronized
        val snapshot = result.getOrNull()
        if (snapshot == null) entry.failure = result.exceptionOrNull()
        else if (snapshot.byteSize > maxBytes - bytes) entry.failure =
            IllegalStateException("Shared CPU terrain snapshots exceed $maxBytes bytes")
        else {
            entry.snapshot = snapshot; bytes += snapshot.byteSize
        }
    }

    private fun key(projectDir: File, name: String) = Key(projectDir.toPath().normalize().toString(), name)
}

/** One preparation's optional CPU interest; offering after cancellation/reacquisition is a no-op. */
class RayTerrainSnapshotCapture internal constructor(private val capture: (TerrainData, Map<String, Pixmap>) -> Unit) {
    fun offer(data: TerrainData, images: Map<String, Pixmap>) = capture(data, images)
}

/** Polling does not wait for IO. Closing drops all references held by this lease and is idempotent. */
class RayTerrainSnapshotLease internal constructor(
    snapshot: () -> RayTerrainSnapshot?, failure: () -> Throwable?, release: () -> Unit,
) : AutoCloseable {
    private var snapshotReader: (() -> RayTerrainSnapshot?)? = snapshot
    private var failureReader: (() -> Throwable?)? = failure
    private var release: (() -> Unit)? = release
    val snapshot: RayTerrainSnapshot? get() = synchronized(this) { snapshotReader?.invoke() }
    val failure: Throwable? get() = synchronized(this) { failureReader?.invoke() }
    override fun close() = synchronized(this) {
        release?.invoke()
        release = null
        snapshotReader = null
        failureReader = null
    }
}
