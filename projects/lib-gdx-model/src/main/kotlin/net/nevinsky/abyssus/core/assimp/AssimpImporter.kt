/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assimp

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
     * @param properties integer import properties (`AI_CONFIG_*` names) for the importers
     *
     * @throws AssimpImportException if Assimp fails or the scene is incomplete
     */
    @JvmOverloads
    fun importScene(path: String, flags: Int, properties: Map<String, Int> = emptyMap()): ImportedScene {
        val scene = if (properties.isEmpty()) {
            Assimp.aiImportFile(path, flags)
        } else {
            val store = Assimp.aiCreatePropertyStore()!!
            try {
                properties.forEach { (name, value) -> Assimp.aiSetImportPropertyInteger(store, name, value) }
                Assimp.aiImportFileExWithProperties(path, flags, null, store)
            } finally {
                Assimp.aiReleasePropertyStore(store)
            }
        }
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
