/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.foliage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import net.nevinsky.abyssus.plugin.assetfiles.sha256Hex
import java.lang.ref.Cleaner
import java.nio.file.Files
import java.nio.file.Path

/** Project-owned disk backups for prior bakes that current inputs cannot reproduce. */
@Service(Service.Level.PROJECT)
class FoliageUndoCache : Disposable {
    private val cleaner = Cleaner.create()
    private val backups = mutableSetOf<Path>()
    private var disposed = false

    @Synchronized
    fun preserve(source: Path, expectedHash: String): Handle {
        check(!disposed)
        val path = Files.createTempFile("abyssus-foliage-undo-", ".data")
        try {
            Files.copy(source, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            require(sha256Hex(Files.readAllBytes(path)) == expectedHash) { "Foliage backup source changed" }
            backups.add(path)
            return Handle(path, expectedHash, cleaner) { release(path) }
        } catch (e: Throwable) {
            Files.deleteIfExists(path)
            throw e
        }
    }

    @Synchronized
    private fun release(path: Path) {
        Files.deleteIfExists(path)
        backups.remove(path)
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        backups.toList().forEach(::release)
    }

    /** Retaining this handle keeps its backup alive; abandoning the undo closure releases it through Cleaner. */
    class Handle internal constructor(
        val path: Path,
        private val expectedHash: String,
        cleaner: Cleaner,
        release: () -> Unit,
    ) : AutoCloseable {
        private val cleanup = cleaner.register(this, Runnable { release() })

        fun read(): ByteArray = Files.readAllBytes(path).also {
            require(sha256Hex(it) == expectedHash) { "Foliage undo backup changed" }
        }

        override fun close() = cleanup.clean()
    }
}
