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