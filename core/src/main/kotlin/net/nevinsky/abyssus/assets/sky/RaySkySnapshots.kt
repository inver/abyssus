/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.sky

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import java.util.concurrent.Executor

/** Shared optional CPU sky companions, keyed by project and asset; no GL readback and no GPU cache invalidation. */
class RaySkySnapshots(
    private val executor: Executor,
    private val read: (AssetFiles, String) -> RaySkySnapshot?,
    private val maxBytes: Long = 128L * 1024 * 1024,
) {
    private data class Key(val project: String, val name: String)
    private class Entry(var references: Int = 0, var snapshot: RaySkySnapshot? = null, var failure: Throwable? = null)

    private val lock = Any()
    private val entries = HashMap<Key, Entry>()
    private var bytes = 0L
    val retainedBytes: Long get() = synchronized(lock) { bytes }

    init {
        require(maxBytes > 0)
    }

    /** Closing the lease on disable, deletion or project replacement releases the bytes with the last reference. */
    fun acquire(files: AssetFiles, name: String): RaySkySnapshotLease {
        val key = Key(files.projectDir.toPath().normalize().toString(), name)
        var fresh = false
        val entry = synchronized(lock) { entries.getOrPut(key) { fresh = true; Entry() }.also { it.references++ } }
        val lease = RaySkySnapshotLease(
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
            },
        )
        if (fresh) try {
            executor.execute {
                if (!wanted(key, entry)) return@execute
                publish(
                    key,
                    entry,
                    runCatchingKeepingCancellation {
                        checkNotNull(
                            read(
                                files,
                                name
                            )
                        ) { "CPU sky '$name' is unavailable" }
                    })
            }
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
        return lease
    }

    /** Drops the old revision and any in-flight read, then allows a fresh acquisition. */
    fun invalidate(files: AssetFiles, name: String) = synchronized(lock) {
        val entry = entries.remove(Key(files.projectDir.toPath().normalize().toString(), name)) ?: return@synchronized
        bytes -= entry.snapshot?.byteSize ?: 0
        entry.snapshot = null
        entry.failure = IllegalStateException("CPU sky '$name' was invalidated")
    }

    private fun wanted(key: Key, entry: Entry) =
        synchronized(lock) { entries[key] === entry && entry.references > 0 && entry.snapshot == null && entry.failure == null }

    private fun publish(key: Key, entry: Entry, result: Result<RaySkySnapshot>) = synchronized(lock) {
        if (!wanted(key, entry)) return@synchronized
        val snapshot = result.getOrNull()
        if (snapshot == null) entry.failure = result.exceptionOrNull()
        else if (snapshot.byteSize > maxBytes - bytes) entry.failure =
            IllegalStateException("Shared CPU sky snapshots exceed $maxBytes bytes")
        else {
            entry.snapshot = snapshot; bytes += snapshot.byteSize
        }
    }
}

/** Polling never waits for IO. Closing drops this lease's reference and is idempotent. */
class RaySkySnapshotLease internal constructor(
    snapshot: () -> RaySkySnapshot?,
    failure: () -> Throwable?,
    release: () -> Unit
) : AutoCloseable {
    private var snapshotReader: (() -> RaySkySnapshot?)? = snapshot
    private var failureReader: (() -> Throwable?)? = failure
    private var release: (() -> Unit)? = release
    val snapshot: RaySkySnapshot? get() = synchronized(this) { snapshotReader?.invoke() }
    val failure: Throwable? get() = synchronized(this) { failureReader?.invoke() }
    override fun close() = synchronized(this) {
        release?.invoke()
        release = null; snapshotReader = null; failureReader = null
    }
}
