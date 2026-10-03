/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.intellij.openapi.vfs.VirtualFile

data class AssetReadResult<T>(
    val success: Boolean,
    val obj: T?,
    val message: String?
) {
    companion object {
        fun <T> failure(message: String) = AssetReadResult<T>(false, null, message)
        fun <T> success(obj: T) = AssetReadResult<T>(true, obj, null)
    }
}

interface ConfigFileReader<T> {
    fun read(file: VirtualFile): AssetReadResult<T>

    /** Part of the cache key: changes whenever anything this reader depends on changes. */
    fun stamp(file: VirtualFile): Long = file.modificationStamp
}

fun VirtualFile.text() = String(contentsToByteArray(), charset)