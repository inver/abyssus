/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.model

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider.FileTextureProvider
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.*
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.lib.gdx.ModelLogging
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.mesh.MeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial.AlphaMode
import net.nevinsky.abyssus.lib.gdx.node.Animation
import net.nevinsky.abyssus.lib.gdx.node.Node
import net.nevinsky.abyssus.lib.gdx.node.NodeAnimation
import net.nevinsky.abyssus.lib.gdx.node.NodePart
import org.slf4j.Logger
import java.util.function.Consumer
import kotlin.math.max

private val log: Logger get() = ModelLogging.logger

/**
 * A model represents a 3D assets. It stores a hierarchy of nodes. A node has a transform and optionally a graphical
 * part in form of a [MeshPart] and [Material]. Mesh parts reference subsets of vertices in one of the
 * meshes of the model. Animations can be applied to nodes, to modify their transform (translation, rotation, scale)
 * over time.
 *
 *
 *
 * A model can be rendered by creating a [ModelInstance] from it. That instance has an additional transform to
 * position the model in the world, and allows modification of materials and nodes without destroying the original
 * model. The original model is the owner of any meshes and textures, all instances created from the model share these
 * resources. Disposing the model will automatically make all instances invalid!
 *
 *
 *
 * A model is created from [ModelData], which in turn is loaded by a [ModelLoader].
 *
 * @author badlogic, xoppa
 */
class Model() : Disposable {
    /**
     * Root nodes of the model
     */
    val nodes: MutableList<Node> = ArrayList<Node>()

    /**
     * Animations of the model, modifying node transformations
     */
    val animations: MutableList<Animation> = ArrayList<Animation>()

    /**
     * Materials of the model
     */
    val materials: MutableMap<String, Material> = HashMap<String, Material>()

    /**
     * Array of disposable resources like textures or meshes the Model is responsible for disposing
     */
    protected val disposables: MutableSet<Disposable> = HashSet()

    protected fun load(modelData: ModelData, textureProvider: TextureProvider) {
        val meshParts = loadMeshes(modelData.meshes)
        loadMaterials(modelData.materials, textureProvider)
        loadNodes(meshParts, modelData.nodes)
        loadAnimations(modelData.animations)
        calculateTransforms()
    }

    protected fun loadAnimations(modelAnimations: Iterable<ModelAnimation>) {
        for (anim in modelAnimations) {
            val animation = Animation(anim.id)
            for (nanim in anim.nodeAnimations) {
                val node = getNode(nanim.nodeId)
                if (node == null) {
                    continue
                }
                val nodeAnim = NodeAnimation()
                nodeAnim.node = node

                if (nanim.translation != null) {
                    nodeAnim.translation = Array<NodeKeyframe<Vector3>>()
                    nodeAnim.translation!!.ensureCapacity(nanim.translation.size)
                    for (kf in nanim.translation) {
                        if (kf.keytime > animation.duration) {
                            animation.duration = kf.keytime
                        }
                        nodeAnim.translation!!.add(
                            NodeKeyframe<Vector3>(
                                kf.keytime,
                                Vector3(if (kf.value == null) node.translation else kf.value)
                            )
                        )
                    }
                }

                if (nanim.rotation != null) {
                    nodeAnim.rotation = Array<NodeKeyframe<Quaternion>>()
                    nodeAnim.rotation!!.ensureCapacity(nanim.rotation.size)
                    for (kf in nanim.rotation) {
                        if (kf.keytime > animation.duration) {
                            animation.duration = kf.keytime
                        }
                        nodeAnim.rotation!!.add(
                            NodeKeyframe<Quaternion>(
                                kf.keytime,
                                Quaternion(if (kf.value == null) node.rotation else kf.value)
                            )
                        )
                    }
                }

                if (nanim.scaling != null) {
                    nodeAnim.scaling = Array<NodeKeyframe<Vector3>>()
                    nodeAnim.scaling!!.ensureCapacity(nanim.scaling.size)
                    for (kf in nanim.scaling) {
                        if (kf.keytime > animation.duration) {
                            animation.duration = kf.keytime
                        }
                        nodeAnim.scaling!!.add(
                            NodeKeyframe<Vector3>(
                                kf.keytime,
                                Vector3(if (kf.value == null) node.scale else kf.value)
                            )
                        )
                    }
                }

                if ((nodeAnim.translation != null && nodeAnim.translation!!.size > 0)
                    || (nodeAnim.rotation != null && nodeAnim.rotation!!.size > 0)
                    || (nodeAnim.scaling != null && nodeAnim.scaling!!.size > 0)
                ) {
                    animation.nodeAnimations.add(nodeAnim)
                }
            }
            if (animation.nodeAnimations.size > 0) {
                animations.add(animation)
            }
        }
    }

    private val nodePartBones = ObjectMap<NodePart, ArrayMap<String, Matrix4>?>()

    /**
     * Constructs a new Model based on the [ModelData].
     *
     * @param modelData       the [ModelData] got from e.g. [ModelLoader]
     * @param textureProvider the [TextureProvider] to use for loading the textures
     */
    /**
     * Constructs a new Model based on the [ModelData]. Texture files will be loaded from the internal file
     * storage via an [TextureProvider.FileTextureProvider].
     *
     * @param modelData the [ModelData] got from e.g. [ModelLoader]
     */
    @JvmOverloads
    constructor(modelData: ModelData, textureProvider: TextureProvider = FileTextureProvider()) : this() {
        load(modelData, textureProvider)
    }

    protected fun loadNodes(meshParts: MutableMap<String, MeshPart>, modelNodes: Iterable<ModelNode>) {
        nodePartBones.clear()
        for (node in modelNodes) {
            nodes.add(loadNode(meshParts, materials, node))
        }
        for (e in nodePartBones.entries()) {
            if (e.key!!.invBoneBindTransforms == null) {
                e.key!!.invBoneBindTransforms =
                    ArrayMap<Node, Matrix4>({ arrayOfNulls<Node>(it) }, { arrayOfNulls<Matrix4>(it) })
            }
            e.key!!.invBoneBindTransforms!!.clear()
            for (b in e.value!!.entries()) {
                e.key!!.invBoneBindTransforms!!.put(getNode(b.key), Matrix4(b.value).inv())
            }
        }
    }

    protected fun loadNode(
        meshParts: MutableMap<String, MeshPart>,
        materials: MutableMap<String, Material>,
        modelNode: ModelNode
    ): Node {
        val node = Node()
        node.id = modelNode.id

        if (modelNode.translation != null) {
            node.translation.set(modelNode.translation)
        }
        if (modelNode.rotation != null) {
            node.rotation.set(modelNode.rotation)
        }
        if (modelNode.scale != null) {
            node.scale.set(modelNode.scale)
        }
        // TODO create temporary maps for faster lookup?
        if (modelNode.parts != null) {
            for (modelNodePart in modelNode.parts) {
                var meshPart: MeshPart? = null
                var meshMaterial: Material? = null

                if (modelNodePart.meshPartId != null) {
                    meshPart = meshParts.get(modelNodePart.meshPartId)
                }

                if (modelNodePart.materialId != null) {
                    meshMaterial = materials.get(modelNodePart.materialId)
                }

                if (meshPart == null || meshMaterial == null) {
                    throw GdxRuntimeException(
                        ("Invalid node: " + node.id
                                + (if (meshPart == null) ", mesh part not found: " + modelNodePart.meshPartId else "")
                                + (if (meshMaterial == null) ", material not found: " + modelNodePart.materialId else ""))
                    )
                }

                val nodePart = NodePart()
                nodePart.meshPart = meshPart
                nodePart.material = meshMaterial
                node.parts.add(nodePart)
                if (modelNodePart.bones != null) {
                    nodePartBones.put(nodePart, modelNodePart.bones)
                }
            }
        }

        if (modelNode.children != null) {
            for (child in modelNode.children) {
                node.addChild<Node?>(loadNode(meshParts, materials, child))
            }
        }

        return node
    }

    protected fun loadMeshes(meshes: Iterable<ModelMesh>): MutableMap<String, MeshPart> {
        val res = HashMap<String, MeshPart>()
        for (mesh in meshes) {
            convertMesh(res, mesh)
        }
        return res
    }

    protected fun convertMesh(res: MutableMap<String, MeshPart>, modelMesh: ModelMesh) {
        var numIndices = 0
        for (part in modelMesh.parts) {
            numIndices += part.indices.size
        }
        val hasIndices = numIndices > 0
        val attributes = VertexAttributes(*modelMesh.attributes)
        val numVertices = modelMesh.vertices.size / (attributes.vertexSize / 4)

        val mesh = Mesh(true, numVertices, numIndices, attributes)
        //        meshes.add(mesh);
        disposables.add(mesh)

        BufferUtils.copy(modelMesh.vertices, mesh.verticesBuffer, modelMesh.vertices.size, 0)

        var offset = 0
        mesh.indicesBuffer.clear()

        var hasBoundingBox = true
        val meshParts = ArrayList<MeshPart>()
        for (part in modelMesh.parts) {
            val meshPart = MeshPart()
            meshPart.id = part.id
            meshPart.primitiveType = part.primitiveType
            meshPart.offset = offset
            meshPart.size = if (hasIndices) part.indices.size else numVertices
            meshPart.mesh = mesh
            if (hasIndices) {
                mesh.indicesBuffer.put(part.indices)
            }
            offset += meshPart.size
            if (part.boundingBox == null) {
                hasBoundingBox = false
            } else {
                meshPart.update(part.boundingBox!!)
            }
            res.put(meshPart.id!!, meshPart)
            meshParts.add(meshPart)
        }
        mesh.indicesBuffer.position(0)
        if (!hasBoundingBox) {
            // loaded model without bounding box: calculate it for the parts of this mesh
            for (part in meshParts) {
                part.update()
            }
        }
    }

    protected fun loadMaterials(modelMaterials: Iterable<ModelMaterial>, textureProvider: TextureProvider) {
        // one texture per file for the whole model: materials commonly share their images, and loading an image
        // once per material (as the Mundus original did) decodes and uploads it again and again
        val loaded = HashMap<String, Texture>()
        for (mtl in modelMaterials) {
            val m = convertMaterial(mtl, textureProvider, loaded)
            materials.put(m.id, m)
        }
    }

    protected fun convertMaterial(
        mtl: ModelMaterial, textureProvider: TextureProvider, textures: MutableMap<String, Texture> = HashMap()
    ): Material {
        val result = Material()
        result.id = mtl.id
        if (mtl.ambient != null) {
            result.set(ColorAttribute(ColorAttribute.Ambient, mtl.ambient))
        }
        if (mtl.diffuse != null) {
            result.set(ColorAttribute(ColorAttribute.Diffuse, mtl.diffuse))
        }
        if (mtl.specular != null) {
            result.set(ColorAttribute(ColorAttribute.Specular, mtl.specular))
        }
        if (mtl.emissive != null) {
            result.set(ColorAttribute(ColorAttribute.Emissive, mtl.emissive))
        }
        if (mtl.reflection != null) {
            result.set(ColorAttribute(ColorAttribute.Reflection, mtl.reflection))
        }
        if (mtl.shininess > 0f) {
            result.set(FloatAttribute(FloatAttribute.Shininess, mtl.shininess))
        }
        if (mtl.opacity != 1f && (mtl !is PbrModelMaterial || mtl.alphaMode != AlphaMode.MASK)) {
            result.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity))
        }

        if (mtl is PbrModelMaterial) {
            applyPbrProperties(mtl, result)
        }

        if (mtl.textures != null) {
            for (tex in mtl.textures) {
                val texture: Texture
                val known = textures[tex.fileName]
                if (known != null) {
                    texture = known
                } else {
                    texture = textureProvider.load(tex.fileName)
                    textures[tex.fileName] = texture
                    disposables.add(texture)
                }

                val descriptor = TextureDescriptor<Texture>(texture)
                descriptor.minFilter = texture.getMinFilter()
                descriptor.magFilter = texture.getMagFilter()
                descriptor.uWrap = texture.getUWrap()
                descriptor.vWrap = texture.getVWrap()

                val offsetU = if (tex.uvTranslation == null) 0f else tex.uvTranslation.x
                val offsetV = if (tex.uvTranslation == null) 0f else tex.uvTranslation.y
                val scaleU = if (tex.uvScaling == null) 1f else tex.uvScaling.x
                val scaleV = if (tex.uvScaling == null) 1f else tex.uvScaling.y

                when (tex.usage) {
                    ModelTexture.USAGE_DIFFUSE -> result.set(
                        TextureAttribute(
                            TextureAttribute.Diffuse, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_SPECULAR -> result.set(
                        TextureAttribute(
                            TextureAttribute.Specular, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_BUMP -> result.set(
                        TextureAttribute(
                            TextureAttribute.Bump, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_NORMAL -> result.set(
                        TextureAttribute(
                            TextureAttribute.Normal, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_AMBIENT -> result.set(
                        TextureAttribute(
                            TextureAttribute.Ambient, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_EMISSIVE -> result.set(
                        TextureAttribute(
                            TextureAttribute.Emissive, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    ModelTexture.USAGE_REFLECTION -> result.set(
                        TextureAttribute(
                            TextureAttribute.Reflection, descriptor, offsetU, offsetV, scaleU,
                            scaleV
                        )
                    )

                    PbrModelMaterial.Companion.USAGE_METALLIC_ROUGHNESS -> result.set(
                        Companion.pbrTexture(
                            PBRTextureAttribute.MetallicRoughnessTexture, descriptor, offsetU,
                            offsetV, scaleU, scaleV
                        )
                    )

                    PbrModelMaterial.Companion.USAGE_OCCLUSION -> result.set(
                        Companion.pbrTexture(
                            PBRTextureAttribute.OcclusionTexture, descriptor, offsetU, offsetV,
                            scaleU, scaleV
                        )
                    )

                    else -> log.atDebug().log { "Texture usage ${tex.usage} of material '${mtl.id}' is not supported" }
                }
                if (mtl is PbrModelMaterial) {
                    // PBR shaders read their own attributes, keep the legacy ones for the non PBR shaders
                    Companion.addPbrAlias(result, tex.usage, descriptor, offsetU, offsetV, scaleU, scaleV)
                }
            }
        }

        return result
    }

    /**
     * @return `true` if any material has PBR (metallic or roughness) properties, so the PBR shader should be
     * used for this model
     */
    fun hasPbrMaterials(): Boolean {
        for (material in materials.values) {
            if (material.has(PBRFloatAttribute.Metallic) || material.has(PBRFloatAttribute.Roughness)) {
                return true
            }
        }
        return false
    }

    val maxBones: Int
        /**
         * @return the largest number of bones used by a single node part, 0 if the model is not skinned. Use it to size
         * `ShaderConfig#numBones` for the model.
         */
        get() {
            var max = 0
            for (node in nodes) {
                max = max(max, maxBones(node))
            }
            return max
        }

    /**
     * Adds a [Disposable] to be managed and disposed by this Model. Can be used to keep track of manually loaded
     * textures for [ModelInstance].
     *
     * @param disposable the Disposable
     */
    fun manageDisposable(disposable: Disposable?) {
        disposables.add(disposable!!)
    }

    val managedDisposables: Iterable<Disposable?>
        /**
         * @return the [Disposable] objects that will be disposed when the [.dispose] method is called.
         */
        get() = disposables

    override fun dispose() {
        disposables.forEach(Consumer { obj: Disposable? -> obj!!.dispose() })
    }

    /**
     * Calculates the local and world transform of all [Node] instances in this model, recursively. First each
     * [Node.localTransform] transform is calculated based on the translation, rotation and scale of each Node.
     * Then each [Node.calculateWorldTransform] is calculated, based on the parent's world transform and the
     * local transform of each Node. Finally, the animation bone matrices are updated accordingly.
     *
     *
     *
     * This method can be used to recalculate all transforms if any of the Node's local properties (translation,
     * rotation, scale) was modified.
     */
    fun calculateTransforms() {
        for (node in nodes) {
            node.calculateTransforms(true)
        }
        for (node in nodes) {
            node.calculateBoneTransforms(true)
        }
    }

    /**
     * Calculate the bounding box of this model instance. This is a potential slow operation, it is advised to cache the
     * result.
     *
     * @param out the [BoundingBox] that will be set with the bounds.
     * @return the out parameter for chaining
     */
    fun calculateBoundingBox(out: BoundingBox): BoundingBox? {
        out.inf()
        return extendBoundingBox(out)
    }

    /**
     * Extends the bounding box with the bounds of this model instance. This is a potential slow operation, it is
     * advised to cache the result.
     *
     * @param out the [BoundingBox] that will be extended with the bounds.
     * @return the out parameter for chaining
     */
    fun extendBoundingBox(out: BoundingBox?): BoundingBox? {
        for (node in nodes) {
            node.extendBoundingBox(out)
        }
        return out
    }

    /**
     * @param id The ID of the animation to fetch (case sensitive).
     * @return The [Animation] with the specified id, or null if not available.
     */
    fun getAnimation(id: String?): Animation? {
        return getAnimation(id, true)
    }

    /**
     * @param id         The ID of the animation to fetch.
     * @param ignoreCase whether to use case sensitivity when comparing the animation id.
     * @return The [Animation] with the specified id, or null if not available.
     */
    fun getAnimation(id: String?, ignoreCase: Boolean): Animation? {
        var animation: Animation?
        if (ignoreCase) {
            for (value in animations) {
                if ((value.also { animation = it }).id.equals(id, ignoreCase = true)) {
                    return animation
                }
            }
        } else {
            for (value in animations) {
                if ((value.also { animation = it }).id == id) {
                    return animation
                }
            }
        }
        return null
    }

    /**
     * @param id The ID of the node to fetch.
     * @return The [Node] with the specified id, or null if not found.
     */
    fun getNode(id: String?): Node? {
        return getNode(id, true)
    }

    /**
     * @param id        The ID of the node to fetch.
     * @param recursive false to fetch a root node only, true to search the entire node tree for the specified node.
     * @return The [Node] with the specified id, or null if not found.
     */
    fun getNode(id: String?, recursive: Boolean): Node? {
        return getNode(id, recursive, false)
    }

    /**
     * @param id         The ID of the node to fetch.
     * @param recursive  false to fetch a root node only, true to search the entire node tree for the specified node.
     * @param ignoreCase whether to use case sensitivity when comparing the node id.
     * @return The [Node] with the specified id, or null if not found.
     */
    fun getNode(id: String?, recursive: Boolean, ignoreCase: Boolean): Node? {
        return Node.Companion.getNode(nodes, id, recursive, ignoreCase)
    }

    val meshes: MutableCollection<Mesh>
        // todo is caching of this result needed?
        get() {
            val set = HashSet<Mesh>()
            getMeshesFromNode(set, nodes)
            return set
        }

    private fun getMeshesFromNode(meshes: MutableCollection<Mesh>, nodes: Iterable<Node>) {
        nodes.forEach(Consumer { n: Node? ->
            for (p in n!!.parts) {
                meshes.add(p.meshPart!!.mesh!!)
            }
            if (n.hasChildren()) {
                getMeshesFromNode(meshes, n.getChildren())
            }
        })
    }

    companion object {
        private fun pbrTexture(
            type: Long, descriptor: TextureDescriptor<Texture>, offsetU: Float,
            offsetV: Float, scaleU: Float, scaleV: Float
        ): PBRTextureAttribute {
            val attribute = PBRTextureAttribute(type, descriptor)
            attribute.offsetU = offsetU
            attribute.offsetV = offsetV
            attribute.scaleU = scaleU
            attribute.scaleV = scaleV
            return attribute
        }

        private fun addPbrAlias(
            result: Material, usage: Int, descriptor: TextureDescriptor<Texture>, offsetU: Float,
            offsetV: Float, scaleU: Float, scaleV: Float
        ) {
            val type: Long
            when (usage) {
                ModelTexture.USAGE_DIFFUSE -> type = PBRTextureAttribute.BaseColorTexture
                ModelTexture.USAGE_NORMAL -> type = PBRTextureAttribute.NormalTexture
                ModelTexture.USAGE_EMISSIVE -> type = PBRTextureAttribute.EmissiveTexture
                else -> return
            }
            result.set(pbrTexture(type, descriptor, offsetU, offsetV, scaleU, scaleV))
        }

        private fun applyPbrProperties(mtl: PbrModelMaterial, result: Material) {
            mtl.baseColor?.let { baseColor ->
                result.set(PBRColorAttribute.createBaseColorFactor(baseColor))
                if (!result.has(ColorAttribute.Diffuse)) {
                    // the shaders read the base color factor from the diffuse color
                    result.set(ColorAttribute(ColorAttribute.Diffuse, baseColor))
                }
            }
            mtl.metallic?.let { result.set(PBRFloatAttribute.createMetallic(it)) }
            mtl.roughness?.let { result.set(PBRFloatAttribute.createRoughness(it)) }
            if (mtl.doubleSided) {
                result.set(IntAttribute.createCullFace(GL20.GL_NONE))
            }
            if (mtl.alphaMode == AlphaMode.MASK) {
                result.set(FloatAttribute.createAlphaTest(mtl.alphaCutoff))
            } else if (mtl.alphaMode == AlphaMode.BLEND && !result.has(BlendingAttribute.Type)) {
                result.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity))
            }
        }

        private fun maxBones(node: Node): Int {
            var max = 0
            for (part in node.parts) {
                if (part.invBoneBindTransforms != null) {
                    max = max(max, part.invBoneBindTransforms!!.size)
                }
            }
            for (child in node.getChildren()) {
                max = max(max, maxBones(child))
            }
            return max
        }
    }
}
