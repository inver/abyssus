/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.assets.assimp

import org.slf4j.Logger
import net.nevinsky.abyssus.core.ModelLogging

import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.core.model.ModelMesh
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIScene

private val log: Logger get() = ModelLogging.logger

/**
 * Loads a model file through Assimp into a [ModelData]. No OpenGL context is needed.
 */
class AssimpModelDataLoader
/**
 * @param convertUnits scale models to meters if the file reports its unit (FBX). Off by default: the units of FBX
 * files are used inconsistently, so a conversion can make models a hundred times smaller.
 * The up axis is always converted to Y.
 */ @JvmOverloads constructor(private val convertUnits: Boolean = false) {
    /**
     * @param embeddedTextureDir where embedded textures are written; embedded textures are skipped if `null`
     */
    @JvmOverloads
    fun load(
        modelId: String?,
        file: FileHandle,
        flags: Int = AssimpFlags.DEFAULT,
        embeddedTextureDir: FileHandle? = null
    ): ModelData {
        val start = System.currentTimeMillis()
        AssimpImporter.importScene(file.path(), flags).use { imported ->
            val data: ModelData = convert(modelId, imported.scene()!!, parentDir(file), embeddedTextureDir, convertUnits)
            log.atDebug().log { "Model $modelId loaded in ${System.currentTimeMillis() - start} ms" }
            return data
        }
    }

    companion object {
        fun convert(
            modelId: String?, scene: AIScene, modelDir: String?, embeddedTextureDir: FileHandle?,
            convertUnits: Boolean
        ): ModelData {
            val data = ModelData()
            data.id = modelId

            val materialProcessor = MaterialProcessor(TextureProcessor(scene, modelDir!!, embeddedTextureDir))
            for (i in 0..<scene.mNumMaterials()) {
                data.materials.add(materialProcessor.process(AIMaterial.create(scene.mMaterials()!!.get(i)), i))
            }

            val meshProcessor = MeshProcessor()
            val meshes: MutableMap<Int, ModelMesh> = HashMap<Int, ModelMesh>()
            val materialIds: MutableMap<Int, String> = HashMap<Int, String>()
            for (i in 0..<scene.mNumMeshes()) {
                val aiMesh = AIMesh.create(scene.mMeshes()!!.get(i))
                val mesh = meshProcessor.process(aiMesh, i)
                if (mesh == null) {
                    log.warn("Skip non-triangle mesh $i of model $modelId")
                    continue
                }
                data.addMesh(mesh)
                meshes.put(i, mesh)
                materialIds.put(i, data.materials.get(aiMesh.mMaterialIndex())!!.id)
            }

            val skins: MutableMap<Int, MeshProcessor.Skin> = HashMap<Int, MeshProcessor.Skin>()
            for (meshIndex in meshes.keys) {
                val skin = meshProcessor.skinOf(meshIndex)
                if (skin != null) {
                    skins.put(meshIndex, skin)
                }
            }

            val nodeProcessor = NodeProcessor(meshes, materialIds, skins)
            val root = nodeProcessor.process(scene.mRootNode()!!)
            SceneNormalizer.apply(root, SceneNormalizer.correction(scene, convertUnits))
            data.nodes.add(root)
            data.animations.addAll(AnimationProcessor(nodeProcessor).process(scene))
            return data
        }

        private fun parentDir(file: FileHandle): String {
            val parent = file.file().getParent()
            return if (parent == null) "" else parent.replace('\\', '/')
        }
    }
}
