/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.gltf

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.JsonWriter
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap
import kotlin.math.abs

/** The model data cannot be written as valid glTF; nothing was written. */
class GltfWriteException(message: String) : IllegalArgumentException(message)

/**
 * Writes a [ModelData] as one glTF 2.0 binary (GLB): a JSON chunk, then one BIN chunk.
 *
 * - **Nodes** keep their ids as names, their hierarchy and their translation, rotation and scale. A node's parts
 *   become the primitives of its mesh. Parts of one node that need different skins (or none) go to extra child nodes
 *   named `<id>_skin<n>`, which glTF needs: one mesh, one skin.
 * - **Meshes** are split by attribute into accessors: POSITION (with min and max), NORMAL, TANGENT (vec4, its w from
 *   the binormal), TEXCOORD_n, COLOR_0, JOINTS_n (unsigned short) and WEIGHTS_n. Indices are unsigned short when
 *   they fit, unsigned int otherwise.
 * - **Skins:** one per distinct bone set, with the joints, the inverse bind matrices from the part's `bones` and the
 *   joints' common ancestor as skeleton.
 * - **Animations:** one per [com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation], LINEAR samplers for
 *   translation, rotation and scale.
 * - **Materials:** metallic-roughness from [PbrModelMaterial]; a plain [ModelMaterial] goes through [PhongToPbr].
 *   Textures are external URIs from `images` (texture file name to URI); a texture missing from it is left out.
 *
 * The output depends only on the input: the same data gives the same bytes. The data is validated first (indices and
 * joints in range, finite positions, weights summing to 1 ± 1e-3); invalid data throws [GltfWriteException].
 * Morph targets, cameras, lights and extensions are never written.
 */
class GltfWriter(private val phongToPbr: PhongToPbr = PhongToPbr()) {
    fun write(data: ModelData, images: Map<String, String>, generator: String): ByteArray =
        Writer(data, images, phongToPbr).write(generator)

    private class Writer(val data: ModelData, val imageUris: Map<String, String>, val phongToPbr: PhongToPbr) {
        val bin = ByteArrayOutputStream()
        val accessors = ArrayList<Map<String, Any>>()
        val bufferViews = ArrayList<Map<String, Any>>()
        val nodes = ArrayList<MutableMap<String, Any>>()
        val meshes = ArrayList<Map<String, Any>>()
        val skins = ArrayList<Map<String, Any>>()
        val skinKeys = HashMap<List<Any>, Int>()
        val animations = ArrayList<Map<String, Any>>()
        val materials = ArrayList<Map<String, Any>>()
        val materialIndex = HashMap<String, Int>()
        val images = LinkedHashMap<String, Int>()

        val nodeIndex = IdentityHashMap<ModelNode, Int>()
        val nodeById = HashMap<String, Int>()
        val parent = HashMap<Int, Int>()
        val meshOfPart = HashMap<String, Pair<ModelMesh, ModelMeshPart>>()
        val attributeAccessors = HashMap<Pair<String, String>, Int>()
        val indexAccessors = HashMap<String, Int>()
        val timeAccessors = HashMap<List<Float>, Int>()

        fun write(generator: String): ByteArray {
            for (mesh in data.meshes) for (part in mesh.parts) meshOfPart.putIfAbsent(part.id ?: continue, mesh to part)
            data.materials.forEach(::material)

            // node indices in pre-order, so that bones and animations can refer to any node
            fun index(node: ModelNode, parentIndex: Int?) {
                val i = nodes.size
                nodeIndex[node] = i
                node.id?.let { nodeById.putIfAbsent(it, i) }
                parentIndex?.let { parent[i] = it }
                nodes += LinkedHashMap()
                node.children?.forEach { index(it, i) }
            }
            data.nodes.forEach { index(it, null) }
            for (node in nodeIndex.keys.sortedBy { nodeIndex[it] }) fillNode(node)
            data.animations.forEach(::animation)

            val doc = LinkedHashMap<String, Any>()
            doc["asset"] = mapOf("version" to "2.0", "generator" to generator)
            doc["scene"] = 0
            doc["scenes"] = listOf(mapOf("nodes" to data.nodes.map { nodeIndex[it]!! }))
            doc["nodes"] = nodes
            if (meshes.isNotEmpty()) doc["meshes"] = meshes
            if (skins.isNotEmpty()) doc["skins"] = skins
            if (animations.isNotEmpty()) doc["animations"] = animations
            if (materials.isNotEmpty()) doc["materials"] = materials
            if (images.isNotEmpty()) {
                doc["textures"] = images.values.map { mapOf("sampler" to 0, "source" to it) }
                doc["images"] = images.keys.map { mapOf("uri" to it) }
                doc["samplers"] = listOf(mapOf("magFilter" to 9729, "minFilter" to 9987, "wrapS" to 10497, "wrapT" to 10497))
            }
            if (accessors.isNotEmpty()) doc["accessors"] = accessors
            if (bufferViews.isNotEmpty()) doc["bufferViews"] = bufferViews
            pad(bin, 0)
            if (bin.size() > 0) doc["buffers"] = listOf(mapOf("byteLength" to bin.size()))
            return glb(json(doc), bin.toByteArray())
        }

        // --- nodes and meshes ---------------------------------------------------------------------------------------

        fun fillNode(node: ModelNode) {
            val index = nodeIndex[node]!!
            val out = nodes[index]
            out["name"] = node.id ?: "node$index"
            val children = node.children?.map { nodeIndex[it]!! }.orEmpty().toMutableList()
            val groups = groups(node)
            groups.firstOrNull()?.let { meshInto(out, node.meshId ?: node.id ?: "mesh$index", it) }
            for ((k, group) in groups.withIndex().drop(1)) {
                val extra = LinkedHashMap<String, Any>()
                extra["name"] = "${node.id}_skin$k"
                nodes += extra
                parent[nodes.size - 1] = index
                children += nodes.size - 1
                meshInto(extra, "${node.meshId ?: node.id}_skin$k", group)
            }
            if (children.isNotEmpty()) out["children"] = children
            node.translation?.takeIf { !it.isZero }?.let { out["translation"] = finite(listOf(it.x, it.y, it.z), node) }
            node.rotation?.takeIf { !it.isIdentity }?.let {
                val q = Quaternion(it).nor()
                out["rotation"] = finite(listOf(q.x, q.y, q.z, q.w), node)
            }
            node.scale?.takeIf { !it.epsilonEquals(1f, 1f, 1f, 0f) }?.let { out["scale"] = finite(listOf(it.x, it.y, it.z), node) }
        }

        /** Parts that can share one mesh and one skin: the bones (node index and inverse bind matrix) by part. */
        class Group(val skinned: Boolean) {
            val parts = ArrayList<ModelNodePart>()
            val joints = ArrayList<Int>()
            val matrices = ArrayList<Matrix4>()

            fun take(part: ModelNodePart, bones: List<Pair<Int, Matrix4>>?): Boolean {
                if ((bones != null) != skinned) return false
                if (bones != null) {
                    for ((joint, matrix) in bones) {
                        val at = joints.indexOf(joint)
                        if (at >= 0 && !same(matrices[at], matrix)) return false
                    }
                    for ((joint, matrix) in bones) if (joint !in joints) { joints += joint; matrices += matrix }
                }
                parts += part
                return true
            }

            fun jointOf(nodeIndex: Int) = joints.indexOf(nodeIndex)
        }

        fun bonesOf(part: ModelNodePart): List<Pair<Int, Matrix4>>? {
            val bones = part.bones ?: return null
            val mesh = meshOfPart[part.meshPartId]?.first ?: return null
            if (mesh.attributes.none { it.usage == Usage.BoneWeight } || bones.size == 0) return null
            return (0 until bones.size).map { i ->
                val id = bones.getKeyAt(i)
                val joint = nodeById[id] ?: throw GltfWriteException("bone $id of ${part.meshPartId} names no node")
                joint to bones.getValueAt(i)
            }
        }

        fun groups(node: ModelNode): List<Group> {
            val groups = ArrayList<Group>()
            for (part in node.parts.orEmpty()) {
                if (meshOfPart[part.meshPartId] == null) throw GltfWriteException("part ${part.meshPartId} of ${node.id} names no mesh part")
                val bones = bonesOf(part)
                if (groups.none { it.take(part, bones) }) groups += Group(bones != null).also { it.take(part, bones) }
            }
            return groups
        }

        fun meshInto(node: MutableMap<String, Any>, name: String, group: Group) {
            val primitives = group.parts.map { primitive(it, group) }
            meshes += mapOf("name" to name, "primitives" to primitives)
            node["mesh"] = meshes.size - 1
            if (group.skinned) node["skin"] = skin(group)
        }

        fun primitive(part: ModelNodePart, group: Group): Map<String, Any> {
            val (mesh, meshPart) = meshOfPart[part.meshPartId]!!
            val layout = Layout(mesh)
            val count = layout.count
            for (i in meshPart.indices) if (i < 0 || i >= count) throw GltfWriteException("index $i of ${meshPart.id} is outside its $count vertices")

            val attributes = LinkedHashMap<String, Any>()
            attributes["POSITION"] = attributeAccessor(mesh, "POSITION") {
                val p = layout.floats(layout.offset(Usage.Position, 0) ?: throw GltfWriteException("${mesh.id} has no positions"), 3)
                if (p.any { !it.isFinite() }) throw GltfWriteException("${mesh.id} has a position that is not finite")
                floatAccessor(p, 3, "VEC3", ARRAY_BUFFER, bounds = true)
            }
            layout.offset(Usage.Normal, 0)?.let { at ->
                attributes["NORMAL"] = attributeAccessor(mesh, "NORMAL") { floatAccessor(layout.floats(at, 3), 3, "VEC3", ARRAY_BUFFER) }
                layout.offset(Usage.Tangent, 0)?.let { tangentAt ->
                    attributes["TANGENT"] = attributeAccessor(mesh, "TANGENT") {
                        floatAccessor(layout.tangents(at, tangentAt, layout.offset(Usage.BiNormal, 0)), 4, "VEC4", ARRAY_BUFFER)
                    }
                }
            }
            for (unit in layout.units(Usage.TextureCoordinates)) {
                attributes["TEXCOORD_$unit"] = attributeAccessor(mesh, "TEXCOORD_$unit") {
                    floatAccessor(layout.floats(layout.offset(Usage.TextureCoordinates, unit)!!, 2), 2, "VEC2", ARRAY_BUFFER)
                }
            }
            layout.offset(Usage.ColorUnpacked, null)?.let { at ->
                attributes["COLOR_0"] = attributeAccessor(mesh, "COLOR_0") { floatAccessor(layout.floats(at, 4), 4, "VEC4", ARRAY_BUFFER) }
            } ?: layout.offset(Usage.ColorPacked, null)?.let { at ->
                attributes["COLOR_0"] = attributeAccessor(mesh, "COLOR_0") { floatAccessor(layout.packedColors(at), 4, "VEC4", ARRAY_BUFFER) }
            }
            if (group.skinned) skinAttributes(part, mesh, layout, group, attributes)

            val primitive = LinkedHashMap<String, Any>()
            primitive["attributes"] = attributes
            primitive["indices"] = indexAccessors.getOrPut(meshPart.id ?: "") { indexAccessor(meshPart.indices, count) }
            materialIndex[part.materialId]?.let { primitive["material"] = it }
            primitive["mode"] = meshPart.primitiveType
            return primitive
        }

        fun attributeAccessor(mesh: ModelMesh, name: String, build: () -> Int): Int =
            attributeAccessors.getOrPut((mesh.id ?: "") to name, build)

        /** JOINTS_n and WEIGHTS_n, the part's bone indices mapped to the group's joints. */
        fun skinAttributes(part: ModelNodePart, mesh: ModelMesh, layout: Layout, group: Group, attributes: MutableMap<String, Any>) {
            val units = layout.units(Usage.BoneWeight)
            val bones = bonesOf(part)!!
            val toJoint = IntArray(bones.size) { group.jointOf(bones[it].first) }
            val sets = (units.size + 3) / 4
            val count = layout.count
            val joints = Array(sets) { IntArray(count * 4) }
            val weights = Array(sets) { FloatArray(count * 4) }
            for (v in 0 until count) {
                var sum = 0f
                units.forEachIndexed { slot, unit ->
                    val at = v * layout.stride + layout.offset(Usage.BoneWeight, unit)!!
                    val bone = layout.vertices[at].toInt()
                    val weight = layout.vertices[at + 1]
                    if (!weight.isFinite() || weight < 0f) throw GltfWriteException("${mesh.id} vertex $v has weight $weight")
                    if (weight > 0f && (bone < 0 || bone >= toJoint.size)) {
                        throw GltfWriteException("${mesh.id} vertex $v names bone $bone of ${toJoint.size}")
                    }
                    joints[slot / 4][v * 4 + slot % 4] = if (weight > 0f) toJoint[bone] else 0
                    weights[slot / 4][v * 4 + slot % 4] = weight
                    sum += weight
                }
                if (abs(sum - 1f) > WEIGHT_TOLERANCE) throw GltfWriteException("${mesh.id} vertex $v has weights summing to $sum, not 1")
            }
            for (s in 0 until sets) {
                attributes["JOINTS_$s"] = shortAccessor(joints[s], 4, "VEC4", ARRAY_BUFFER)
                attributes["WEIGHTS_$s"] = floatAccessor(weights[s], 4, "VEC4", ARRAY_BUFFER)
            }
        }

        fun skin(group: Group): Int {
            val key = group.joints.flatMapIndexed { i, joint -> listOf<Any>(joint) + group.matrices[i].`val`.toList() }
            return skinKeys.getOrPut(key) {
                val matrices = FloatArray(group.joints.size * 16)
                group.matrices.forEachIndexed { i, m -> m.`val`.copyInto(matrices, i * 16) }
                val skin = LinkedHashMap<String, Any>()
                skin["inverseBindMatrices"] = floatAccessor(matrices, 16, "MAT4", null)
                skin["joints"] = group.joints
                commonAncestor(group.joints)?.let { skin["skeleton"] = it }
                skins += skin
                skins.size - 1
            }
        }

        fun commonAncestor(joints: List<Int>): Int? {
            fun chain(node: Int): List<Int> = generateSequence(node) { parent[it] }.toList().reversed()
            val chains = joints.map(::chain)
            var result: Int? = null
            for (depth in 0 until chains.minOf { it.size }) {
                val candidate = chains[0][depth]
                if (chains.all { it[depth] == candidate }) result = candidate else break
            }
            return result
        }

        // --- animations -------------------------------------------------------------------------------------------

        fun animation(animation: com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation) {
            val channels = ArrayList<Map<String, Any>>()
            val samplers = ArrayList<Map<String, Any>>()
            fun <T> channel(node: Int, path: String, keys: Iterable<ModelNodeKeyframe<T>?>?, components: Int, value: (T) -> List<Float>) {
                val times = ArrayList<Float>()
                val values = ArrayList<Float>()
                for (key in keys ?: return) {
                    val v = key?.value ?: continue
                    if (!key.keytime.isFinite() || key.keytime < 0f || (times.isNotEmpty() && key.keytime <= times.last())) continue
                    val components = value(v)
                    if (components.any { !it.isFinite() }) throw GltfWriteException("animation ${animation.id} has a value that is not finite")
                    times += key.keytime
                    values += components
                }
                if (times.isEmpty()) return
                val input = timeAccessors.getOrPut(times) { floatAccessor(times.toFloatArray(), 1, "SCALAR", null, bounds = true) }
                val output = floatAccessor(values.toFloatArray(), components, if (components == 4) "VEC4" else "VEC3", null)
                samplers += mapOf("input" to input, "interpolation" to "LINEAR", "output" to output)
                channels += mapOf("sampler" to samplers.size - 1, "target" to mapOf("node" to node, "path" to path))
            }
            for (nodeAnimation in animation.nodeAnimations ?: return) {
                val node = nodeById[nodeAnimation.nodeId] ?: continue
                channel(node, "translation", nodeAnimation.translation, 3) { v: Vector3 -> listOf(v.x, v.y, v.z) }
                channel(node, "rotation", nodeAnimation.rotation, 4) { q: Quaternion ->
                    val n = Quaternion(q).nor()
                    listOf(n.x, n.y, n.z, n.w)
                }
                channel(node, "scale", nodeAnimation.scaling, 3) { v: Vector3 -> listOf(v.x, v.y, v.z) }
            }
            if (channels.isEmpty()) return
            val out = LinkedHashMap<String, Any>()
            animation.id?.let { out["name"] = it }
            out["channels"] = channels
            out["samplers"] = samplers
            animations += out
        }

        // --- materials ----------------------------------------------------------------------------------------------

        fun material(source: ModelMaterial) {
            val material = phongToPbr.convert(source).material
            val out = LinkedHashMap<String, Any>()
            out["name"] = source.id ?: "material${materials.size}"
            val pbr = LinkedHashMap<String, Any>()
            val base = material.baseColor ?: material.diffuse?.let { Color(it).also { c -> c.a = material.opacity } } ?: Color.WHITE
            pbr["baseColorFactor"] = listOf(base.r, base.g, base.b, base.a)
            texture(material, ModelTexture.USAGE_DIFFUSE)?.let { pbr["baseColorTexture"] = it }
            material.metallic?.let { pbr["metallicFactor"] = it }
            material.roughness?.let { pbr["roughnessFactor"] = it }
            texture(material, PbrModelMaterial.USAGE_METALLIC_ROUGHNESS)?.let { pbr["metallicRoughnessTexture"] = it }
            out["pbrMetallicRoughness"] = pbr
            texture(material, ModelTexture.USAGE_NORMAL)?.let { out["normalTexture"] = it }
            texture(material, PbrModelMaterial.USAGE_OCCLUSION)?.let { out["occlusionTexture"] = it }
            val emissiveTexture = texture(material, ModelTexture.USAGE_EMISSIVE)
            emissiveTexture?.let { out["emissiveTexture"] = it }
            val emissive = material.emissive?.takeIf { it.r > 0f || it.g > 0f || it.b > 0f }
            when {
                emissive != null -> out["emissiveFactor"] = listOf(emissive.r, emissive.g, emissive.b)
                emissiveTexture != null -> out["emissiveFactor"] = listOf(1f, 1f, 1f)
            }
            if (material.alphaMode != PbrModelMaterial.AlphaMode.OPAQUE) out["alphaMode"] = material.alphaMode.name
            if (material.alphaMode == PbrModelMaterial.AlphaMode.MASK) out["alphaCutoff"] = material.alphaCutoff
            if (material.doubleSided) out["doubleSided"] = true
            source.id?.let { materialIndex.putIfAbsent(it, materials.size) }
            materials += out
        }

        fun texture(material: ModelMaterial, usage: Int): Map<String, Any>? {
            val texture = material.textures?.firstOrNull { it.usage == usage } ?: return null
            val uri = imageUris[texture.fileName ?: return null] ?: return null
            return mapOf("index" to images.getOrPut(uri) { images.size })
        }

        // --- accessors and buffers ----------------------------------------------------------------------------------

        fun view(bytes: ByteArray, target: Int?): Int {
            pad(bin, 0)
            val view = LinkedHashMap<String, Any>()
            view["buffer"] = 0
            view["byteOffset"] = bin.size()
            view["byteLength"] = bytes.size
            target?.let { view["target"] = it }
            bin.write(bytes)
            bufferViews += view
            return bufferViews.size - 1
        }

        fun floatAccessor(values: FloatArray, components: Int, type: String, target: Int?, bounds: Boolean = false): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putFloat)
            val accessor = LinkedHashMap<String, Any>()
            accessor["bufferView"] = view(buffer.array(), target)
            accessor["componentType"] = FLOAT
            accessor["count"] = values.size / components
            accessor["type"] = type
            if (bounds && values.isNotEmpty()) {
                accessor["min"] = (0 until components).map { c -> (c until values.size step components).minOf { values[it] } }
                accessor["max"] = (0 until components).map { c -> (c until values.size step components).maxOf { values[it] } }
            }
            accessors += accessor
            return accessors.size - 1
        }

        fun shortAccessor(values: IntArray, components: Int, type: String, target: Int?): Int {
            val buffer = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach { buffer.putShort(it.toShort()) }
            accessors += mapOf("bufferView" to view(buffer.array(), target), "componentType" to UNSIGNED_SHORT,
                "count" to values.size / components, "type" to type)
            return accessors.size - 1
        }

        /** Unsigned short when every index fits below 65535 (the primitive restart value), unsigned int otherwise. */
        fun indexAccessor(indices: IntArray, vertexCount: Int): Int {
            val short = vertexCount <= 0xFFFF
            val buffer = ByteBuffer.allocate(indices.size * if (short) 2 else 4).order(ByteOrder.LITTLE_ENDIAN)
            if (short) indices.forEach { buffer.putShort(it.toShort()) } else indices.forEach(buffer::putInt)
            accessors += mapOf("bufferView" to view(buffer.array(), ELEMENT_ARRAY_BUFFER),
                "componentType" to if (short) UNSIGNED_SHORT else UNSIGNED_INT, "count" to indices.size, "type" to "SCALAR")
            return accessors.size - 1
        }

        fun finite(values: List<Float>, node: ModelNode): List<Float> {
            if (values.any { !it.isFinite() }) throw GltfWriteException("node ${node.id} has a transform that is not finite")
            return values
        }
    }

    /** A mesh's interleaved vertices by attribute. */
    private class Layout(mesh: ModelMesh) {
        val attributes: Array<VertexAttribute> = mesh.attributes
        val vertices: FloatArray = mesh.vertices
        val stride = attributes.sumOf { it.numComponents }
        val count = if (stride == 0) 0 else vertices.size / stride

        /** @param unit the attribute's unit, or `null` for the first of that usage */
        fun offset(usage: Int, unit: Int?): Int? {
            var offset = 0
            for (a in attributes) {
                if (a.usage == usage && (unit == null || a.unit == unit)) return offset
                offset += a.numComponents
            }
            return null
        }

        fun units(usage: Int): List<Int> = attributes.filter { it.usage == usage }.map { it.unit }.distinct().sorted()

        fun floats(offset: Int, components: Int): FloatArray {
            val out = FloatArray(count * components)
            for (v in 0 until count) vertices.copyInto(out, v * components, v * stride + offset, v * stride + offset + components)
            return out
        }

        /** glTF tangents: xyz and the handedness w, +1 unless the binormal points against normal × tangent. */
        fun tangents(normalAt: Int, tangentAt: Int, binormalAt: Int?): FloatArray {
            val out = FloatArray(count * 4)
            val n = Vector3()
            val t = Vector3()
            for (v in 0 until count) {
                val base = v * stride
                t.set(vertices[base + tangentAt], vertices[base + tangentAt + 1], vertices[base + tangentAt + 2])
                var w = 1f
                if (binormalAt != null) {
                    n.set(vertices[base + normalAt], vertices[base + normalAt + 1], vertices[base + normalAt + 2])
                    val b = Vector3(vertices[base + binormalAt], vertices[base + binormalAt + 1], vertices[base + binormalAt + 2])
                    if (n.crs(t).dot(b) < 0f) w = -1f
                }
                out[v * 4] = t.x
                out[v * 4 + 1] = t.y
                out[v * 4 + 2] = t.z
                out[v * 4 + 3] = w
            }
            return out
        }

        fun packedColors(offset: Int): FloatArray {
            val out = FloatArray(count * 4)
            val c = Color()
            for (v in 0 until count) {
                Color.abgr8888ToColor(c, vertices[v * stride + offset])
                out[v * 4] = c.r
                out[v * 4 + 1] = c.g
                out[v * 4 + 2] = c.b
                out[v * 4 + 3] = c.a
            }
            return out
        }
    }

    companion object {
        private const val FLOAT = 5126
        private const val UNSIGNED_SHORT = 5123
        private const val UNSIGNED_INT = 5125
        private const val ARRAY_BUFFER = 34962
        private const val ELEMENT_ARRAY_BUFFER = 34963
        private const val WEIGHT_TOLERANCE = 1e-3f

        private fun same(a: Matrix4, b: Matrix4): Boolean =
            a.`val`.indices.all { abs(a.`val`[it] - b.`val`[it]) <= 1e-5f * maxOf(1f, abs(a.`val`[it])) }

        private fun pad(out: ByteArrayOutputStream, byte: Int) {
            while (out.size() % 4 != 0) out.write(byte)
        }

        private fun json(doc: Map<String, Any>): ByteArray {
            val text = StringWriter()
            val writer = JsonWriter(text)
            writer.setOutputType(JsonWriter.OutputType.json)
            fun emit(value: Any?) {
                when (value) {
                    is Map<*, *> -> {
                        writer.`object`()
                        for ((k, v) in value) {
                            writer.name(k as String)
                            emit(v)
                        }
                        writer.pop()
                    }
                    is List<*> -> {
                        writer.array()
                        value.forEach(::emit)
                        writer.pop()
                    }
                    is Float -> writer.value(value)
                    is Int -> writer.value(value)
                    is Boolean -> writer.value(value)
                    is String -> writer.value(value)
                    else -> throw IllegalStateException("unexpected JSON value $value")
                }
            }
            emit(doc)
            writer.close()
            return text.toString().toByteArray(Charsets.UTF_8)
        }

        private fun glb(json: ByteArray, bin: ByteArray): ByteArray {
            val jsonPadded = if (json.size % 4 == 0) json else json + ByteArray(4 - json.size % 4) { ' '.code.toByte() }
            val withBin = bin.isNotEmpty()
            val length = 12 + 8 + jsonPadded.size + if (withBin) 8 + bin.size else 0
            val out = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
            out.putInt(0x46546C67).putInt(2).putInt(length)
            out.putInt(jsonPadded.size).putInt(0x4E4F534A).put(jsonPadded)
            if (withBin) out.putInt(bin.size).putInt(0x004E4942).put(bin)
            return out.array()
        }
    }
}
