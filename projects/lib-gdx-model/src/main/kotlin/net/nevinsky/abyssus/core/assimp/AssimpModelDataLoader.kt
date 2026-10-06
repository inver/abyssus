/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assimp

import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.lib.core.ModelLogging
import net.nevinsky.abyssus.lib.core.model.ModelData
import net.nevinsky.abyssus.lib.core.model.ModelMesh
import org.lwjgl.assimp.AICamera
import org.lwjgl.assimp.AILight
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import org.slf4j.Logger

private val log: Logger get() = ModelLogging.logger

/**
 * Loads a model file through Assimp into a [ModelData]. No OpenGL context is needed.
 */
class AssimpModelDataLoader
/**
 * @param convertUnits scale models to meters if the file reports its unit (FBX). Off by default: the units of FBX
 * files are used inconsistently, so a conversion can make models a hundred times smaller.
 * @param normalize convert the up axis the file states to Y (FBX metadata; DAE `<asset>`, which Assimp's Collada
 * importer applies itself; 3DS, which Assimp's importer always turns from Z up). Off, the root keeps the file's own
 * transform: neither the axis nor the DAE unit is applied, and the correction Assimp's 3DS and FBX importers put on the
 * root is taken back off. An import that lets the user choose them reads the stated frame from [loadScene] instead.
 */ @JvmOverloads constructor(private val convertUnits: Boolean = false, private val normalize: Boolean = true) {
    /**
     * @param embeddedTextureDir where embedded textures are written; embedded textures are skipped if `null`
     */
    @JvmOverloads
    fun load(
        modelId: String?,
        file: FileHandle,
        flags: Int = AssimpFlags.DEFAULT,
        embeddedTextureDir: FileHandle? = null
    ): ModelData = loadScene(modelId, file, flags, embeddedTextureDir).data

    /**
     * Loads the model with the frame its file states and what it leaves out.
     *
     * @param embeddedTextureDir where embedded textures are written; embedded textures are skipped if `null`
     */
    @JvmOverloads
    fun loadScene(
        modelId: String?,
        file: FileHandle,
        flags: Int = AssimpFlags.DEFAULT,
        embeddedTextureDir: FileHandle? = null
    ): LoadedScene {
        val start = System.currentTimeMillis()
        val properties = if (normalize) emptyMap() else UNNORMALIZED_PROPERTIES
        AssimpImporter.importScene(file.path(), flags, properties).use { imported ->
            val scene = imported.scene()!!
            val data: ModelData =
                convert(modelId, scene, parentDir(file), embeddedTextureDir, convertUnits, normalize)
            if (!normalize) {
                val root = data.nodes.first()
                SceneNormalizer.unapply(root, SceneNormalizer.importerCorrection(scene, root, file.extension().lowercase()))
            }
            log.atDebug().log { "Model $modelId loaded in ${System.currentTimeMillis() - start} ms" }
            return LoadedScene(data, SceneNormalizer.stated(scene), leftOut(scene))
        }
    }

    companion object {
        /** Stops Assimp's Collada importer applying the `<asset>` unit and up axis to the root. */
        private val UNNORMALIZED_PROPERTIES = mapOf(
            Assimp.AI_CONFIG_IMPORT_COLLADA_IGNORE_UNIT_SIZE to 1,
            Assimp.AI_CONFIG_IMPORT_COLLADA_IGNORE_UP_DIRECTION to 1,
        )

        @JvmOverloads
        fun convert(
            modelId: String?, scene: AIScene, modelDir: String?, embeddedTextureDir: FileHandle?,
            convertUnits: Boolean, normalize: Boolean = true
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
            if (normalize) {
                SceneNormalizer.apply(root, SceneNormalizer.correction(scene, convertUnits))
            }
            data.nodes.add(root)
            data.animations.addAll(AnimationProcessor(nodeProcessor).process(scene))
            return data
        }

        private fun leftOut(scene: AIScene): List<LeftOut> {
            val result = ArrayList<LeftOut>()
            for (i in 0..<scene.mNumCameras()) {
                result.add(LeftOut(named(AICamera.create(scene.mCameras()!!.get(i)).mName().dataString(), "camera_$i"), LeftOut.Kind.CAMERA))
            }
            for (i in 0..<scene.mNumLights()) {
                result.add(LeftOut(named(AILight.create(scene.mLights()!!.get(i)).mName().dataString(), "light_$i"), LeftOut.Kind.LIGHT))
            }
            for (i in 0..<scene.mNumMeshes()) {
                val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
                val name = named(mesh.mName().dataString(), "mesh_$i")
                if ((mesh.mPrimitiveTypes() and Assimp.aiPrimitiveType_TRIANGLE) == 0) {
                    result.add(LeftOut(name, LeftOut.Kind.NON_TRIANGLE_MESH))
                } else if (mesh.mNumAnimMeshes() > 0) {
                    result.add(LeftOut(name, LeftOut.Kind.MORPH_TARGETS))
                }
            }
            return result
        }

        private fun named(name: String, fallback: String): String = name.ifEmpty { fallback }

        private fun parentDir(file: FileHandle): String {
            val parent = file.file().getParent()
            return if (parent == null) "" else parent.replace('\\', '/')
        }
    }
}
