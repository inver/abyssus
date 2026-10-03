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

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.files.AssetFiles

/**
 * Turns one asset folder into a GPU object, in the steps [AssetCache] runs: [prepare] off the GL thread (file IO,
 * decoding), then [upload] one slice per frame and [build] on the GL thread.
 */
interface AssetLoader<P : Any, T : Disposable> {
    /** Reads and decodes the asset [name]; null when it has no usable files. No GL; runs on a pool thread. */
    fun prepare(files: AssetFiles, name: String): P?

    /** Does one slice of the GPU upload; true when nothing is left. */
    fun upload(prepared: P): Boolean = true

    fun build(prepared: P): T

    /** Releases whatever [prepared] still holds. Safe to call more than once. */
    fun discard(prepared: P)
}
