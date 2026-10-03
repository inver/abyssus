/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
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
