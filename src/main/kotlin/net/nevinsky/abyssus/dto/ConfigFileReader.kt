/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.assets.displayMessage

/** What reading a config file gave: the parsed [obj], or the [message] of why it could not be read. */
sealed interface AssetReadResult<out T> {
    val success: Boolean
    val obj: T?
    val message: String?

    data class Ok<out T>(override val obj: T) : AssetReadResult<T> {
        override val success get() = true
        override val message: String? get() = null
    }

    data class Failed(override val message: String) : AssetReadResult<Nothing> {
        override val success get() = false
        override val obj: Nothing? get() = null
    }

    companion object {
        fun <T> success(obj: T): AssetReadResult<T> = Ok(obj)
        fun failure(message: String): AssetReadResult<Nothing> = Failed(message)

        /** [result] as a read result; a failure becomes its message, or its class name when it has none. */
        fun <T> of(result: Result<T>): AssetReadResult<T> =
            result.fold({ Ok(it) }, { Failed(it.displayMessage()) })
    }
}

interface ConfigFileReader<T> {
    fun read(file: VirtualFile): AssetReadResult<T>

    /** Part of the cache key: changes whenever anything this reader depends on changes. */
    fun stamp(file: VirtualFile): Long = file.modificationStamp
}

fun VirtualFile.text() = String(contentsToByteArray(), charset)

/** The editor's unsaved text when the file has an open document, else the file's content. */
fun textOf(file: VirtualFile): String =
    FileDocumentManager.getInstance().getCachedDocument(file)?.text ?: String(file.contentsToByteArray(), file.charset)
