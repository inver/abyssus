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

package net.nevinsky.abyssus.lib.assets.assimp

import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import java.lang.AutoCloseable

/**
 * Owns the native lifecycle of an imported Assimp scene.
 */
object AssimpImporter {
    /**
     * Imports the scene. The caller must close the result, which releases the native memory.
     *
     * @throws AssimpImportException if Assimp fails or the scene is incomplete
     */
    fun importScene(path: String, flags: Int): ImportedScene {
        val scene = Assimp.aiImportFile(path, flags)
        if (scene == null || scene.mRootNode() == null) {
            val reason = Assimp.aiGetErrorString()
            if (scene != null) {
                Assimp.aiReleaseImport(scene)
            }
            throw AssimpImportException("Error loading model [path: " + path + "]: " + reason)
        }
        return ImportedScene(scene)
    }

    class ImportedScene internal constructor(private val scene: AIScene?) : AutoCloseable {
        private var closed = false

        fun scene(): AIScene? {
            check(!closed) { "Scene already released" }
            return scene
        }

        override fun close() {
            if (!closed) {
                closed = true
                Assimp.aiReleaseImport(scene)
            }
        }
    }
}
