/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import net.nevinsky.abyssus.lib.gdx.g3d.InstanceAttributes
import net.nevinsky.abyssus.lib.gdx.g3d.IntMesh
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import kotlin.math.abs

/**
 * A foliage asset's GL side: for every node part of a layer's model one instanced copy of that part, the per-copy
 * instance matrices, and the frustum and draw-distance culling (design decision 5). The shared model `Mesh` cannot
 * be drawn this way: instancing changes the vertex layout, and normal entities keep drawing it.
 *
 * [update] culls and refills the instance buffers; [getRenderables] then hands the visible parts to the batch. Its
 * renderables carry the identity `worldTransform`: the `instancedFlag` shaders take the world matrix of each copy
 * from the instance attributes. Copies are drawn in their bind pose, so no animation runs over the model.
 *
 * One instance buffer set belongs to a *(model folder, layer kind)* pair, not to the folder alone: the same model
 * can stand in an OBJECT and in a DETAIL layer, and a flat instance buffer cannot be drawn for one kind only.
 * [getRenderablesOf] hands over one kind's set, so the shadow depth pass can take the OBJECT layers' copies and
 * leave the DETAIL layers' to the lit pass.
 *
 * A light pass has its own culling: [updateCasters] keeps the OBJECT layers' copies a light camera's frustum meets, in
 * instance buffers of their own that [getCasterRenderables] hands over, so an OBJECT copy outside the viewer's frustum
 * still casts its shadow and the viewer's buffers, filled by [update], are never touched by it. DETAIL copies never
 * cast. A drawable nobody calls [updateCasters] on holds no caster buffers.
 *
 * The instance buffer of a model holds as many copies as the bake gives it, so a later, smaller bake reuses the
 * meshes; only a bake that grows past the capacity rebuilds them.
 */
class FoliageDrawable(
    private val staged: PreparedFoliage,
    private val models: BuiltAssets,
) : RenderableProvider, Disposable {

    /** The prepared data: settings, terrain, masks, bake and staleness, as the loader read them. */
    val foliage: PreparedFoliage get() = staged

    /** The model assets of the layers, by folder name; a missing one dropped only its own copies. */
    val layerModels: Map<String, Disposable?> = staged.meta.layers
        .flatMap { layer -> layer.models.map { it.asset } }
        .distinct()
        .associateWith { models.get(it) }

    /** The bake whose copies are drawn; a freshly generated one is handed over with [setBake]. */
    var bake: FoliageBake? = staged.bake
        private set

    /** The terrain the copies stand on; a regenerated one is handed over with [setTerrain]. */
    var terrain: TerrainData? = staged.terrain
        private set

    /** Each loaded layer model's box in its own space; a model with no geometry reaches nowhere and is left out. */
    private val modelBoxes: Map<String, BoundingBox> = buildMap {
        for ((folder, value) in layerModels) {
            val box = BoundingBox()
            (value as? Model)?.calculateBoundingBox(box)
            if (box.min.x <= box.max.x) put(folder, box)
        }
    }

    /**
     * How far a chunk's box reaches past the heights it spans: the largest reach of a layer model from its origin,
     * times the largest scale a layer gives its copies, so a copy never stands outside its own chunk's box and is
     * culled away with it.
     */
    val chunkPad: Float

    /**
     * The world box of the OBJECT-layer copies the last [update] drew, or null when it drew none. Copies can reach
     * outside the box of the copy matrix alone, so the box grows copy by copy as they are written; a DETAIL layer's
     * copies only receive a shadow, so their box is never kept. The box belongs to this drawable: copy it before you
     * keep or change it.
     */
    var objectBounds: BoundingBox? = null
        private set

    /** The number of instance-buffer fills so far; a frame that draws the same set causes none. */
    var rebuilds: Int = 0
        private set

    /** How many copies the instance buffers currently hold, over all layers. */
    var drawnCopies: Int = 0
        private set

    private val matrices = FoliageMatrices()
    private val layers: List<Layer> = staged.meta.layers.map { Layer(it) }
    private val meshes = HashMap<MeshKey, ModelMeshes>()
    private val instance = Matrix4()
    private val nearest = Vector3()
    private val cameraPass = EntityMemo()
    private val casterPass = EntityMemo()
    private val lastCamera = Vector3()
    private var hasLastCamera = false
    private var dirty = true

    /** True once [updateCasters] was called: only then are caster buffers kept. */
    private var casting = false
    private var casterDirty = true

    /** True until [rebuildMeshes] has fitted the meshes to the current bake. */
    private var meshesStale = true

    /** How many OBJECT copies the last [updateCasters] kept for the light. */
    var casterCopies: Int = 0
        private set

    init {
        chunkPad = computeChunkPad()
        refreshLayers()
    }

    /**
     * Culls the chunks against [camera] and refills the instance buffers when what is drawn has changed: a new bake
     * or terrain, a moved [entity], another set of visible chunks, or a moved camera under a DETAIL layer. Call it
     * once per frame on the GL thread, before [getRenderables].
     */
    fun update(camera: Camera, entity: Matrix4) {
        if (terrain == null || bake == null) {
            clear()
            return
        }
        var changed = false
        var detail = false
        for (layer in layers) {
            if (layer.cull(camera, entity)) changed = true
            if (layer.kind == FoliageLayerKind.DETAIL) detail = true
        }
        val entityMoved = cameraPass.moved(entity)
        // OBJECT copies are drawn to the far plane, so only a DETAIL layer cares where the camera stands
        // a metre and a half of drift moves the draw-distance edge too little to see, and a refill walks every copy
        val cameraMoved = detail && (!hasLastCamera || lastCamera.dst2(camera.position) > CAMERA_REFILL_DISTANCE2)
        if (dirty || changed || entityMoved || cameraMoved) {
            rebuildMeshes()
            fill(camera, entity)
            dirty = false
            lastCamera.set(camera.position)
            hasLastCamera = true
        }
        cameraPass.remember(entity)
    }

    /** Hands over a freshly generated bake: the next [update] draws its copies. */
    fun setBake(value: FoliageBake?) {
        bake = value
        dirty = true
        casterDirty = true
        refreshLayers()
    }

    /** Hands over regenerated heights: the next [update] stands the copies on the new surface. */
    fun setTerrain(value: TerrainData?) {
        terrain = value
        dirty = true
        casterDirty = true
        refreshLayers()
    }

    override fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
        for ((key, mesh) in meshes) if (!key.caster) mesh.renderables(renderables, pool)
    }

    /**
     * Culls the OBJECT layers' chunks against [light] (a camera whose frustum is the light's volume) and refills the
     * caster buffers when what they hold has changed. Call it once per frame on the GL thread, before
     * [getCasterRenderables]; it never changes what [update] filled.
     */
    fun updateCasters(light: Camera, entity: Matrix4) {
        if (!casting) {
            casting = true
            casterDirty = true
            meshesStale = true
        }
        if (terrain == null || bake == null) {
            clearCasters()
            return
        }
        var changed = false
        for (layer in layers) if (layer.castCull(light, entity)) changed = true
        val moved = casterPass.moved(entity)
        if (casterDirty || changed || moved) {
            rebuildMeshes()
            fillCasters(entity)
            casterDirty = false
        }
        casterPass.remember(entity)
    }

    /** The instanced parts of the copies [updateCasters] kept: OBJECT layers only. */
    fun getCasterRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
        for ((key, mesh) in meshes) if (key.caster) mesh.renderables(renderables, pool)
    }

    /**
     * The instanced parts of the copies of one layer [kind] only: the shadow depth pass asks for the OBJECT layers'
     * copies, which cast, and never for the DETAIL layers', which only receive.
     */
    fun getRenderablesOf(kind: FoliageLayerKind, renderables: Array<Renderable>, pool: Pool<Renderable>) {
        for ((key, mesh) in meshes) {
            if (!key.caster && key.kind == kind) mesh.renderables(renderables, pool)
        }
    }

    override fun dispose() {
        for (mesh in meshes.values) mesh.dispose()
        meshes.clear()
        drawnCopies = 0
        casterCopies = 0
        objectBounds = null
    }

    private fun refreshLayers() {
        meshesStale = true
        for (layer in layers) layer.refresh()
    }

    /** Drops everything drawn: what a terrain or bake handed over as null leaves to show. */
    private fun clear() {
        objectBounds = null
        if (drawnCopies == 0) return
        for ((key, mesh) in meshes) {
            if (key.caster) continue
            mesh.reset()
            mesh.upload()
        }
        drawnCopies = 0
    }

    /** Empties the caster buffers: what a terrain or bake handed over as null leaves to cast. */
    private fun clearCasters() {
        if (casterCopies == 0) return
        for ((key, mesh) in meshes) {
            if (!key.caster) continue
            mesh.reset()
            mesh.upload()
        }
        casterCopies = 0
    }

    /** Writes one instance matrix per copy the light sees into the caster buffers and uploads them. */
    private fun fillCasters(entity: Matrix4) {
        val ground = checkNotNull(terrain) { "a terrain to stand the copies on" }
        for ((key, mesh) in meshes) if (key.caster) mesh.reset()
        var kept = 0
        for (layer in layers) {
            if (layer.kind != FoliageLayerKind.OBJECT) continue
            val layerBake = layer.bakeLayer ?: continue
            if (layer.grid == null) continue
            for (index in layerBake.chunks.indices) {
                if (!layer.casterVisible[index]) continue
                for (copy in layerBake.chunks[index]) {
                    val key = layer.casterKeyAt(copy.model) ?: continue
                    val mesh = meshes[key] ?: continue
                    if (mesh.isEmpty) continue
                    val matrix = matrices.copy(ground, layer.align, entity, copy, instance) ?: continue
                    if (mesh.append(matrix)) kept++
                }
            }
        }
        for ((key, mesh) in meshes) if (key.caster) mesh.upload()
        casterCopies = kept
    }

    /** Writes one instance matrix per visible copy into the buffers and uploads them all. */
    private fun fill(camera: Camera, entity: Matrix4) {
        val ground = checkNotNull(terrain) { "a terrain to stand the copies on" }
        for ((key, mesh) in meshes) if (!key.caster) mesh.reset()
        var drawn = 0
        var objects: BoundingBox? = null
        for (layer in layers) {
            val layerBake = layer.bakeLayer ?: continue
            if (layer.grid == null) continue
            val distance2 = if (layer.kind == FoliageLayerKind.DETAIL) {
                layer.drawDistance * layer.drawDistance
            } else {
                -1f // OBJECT copies are drawn to the far plane
            }
            val casts = layer.kind == FoliageLayerKind.OBJECT
            for (index in layerBake.chunks.indices) {
                if (!layer.visible[index]) continue
                for (copy in layerBake.chunks[index]) {
                    val key = layer.keyAt(copy.model) ?: continue
                    val mesh = meshes[key] ?: continue // a missing model drops only its own copies
                    if (mesh.isEmpty) continue
                    val matrix = matrices.copy(ground, layer.align, entity, copy, instance) ?: continue
                    if (distance2 >= 0f) {
                        matrix.getTranslation(nearest)
                        if (nearest.dst2(camera.position) > distance2) continue
                    }
                    if (!mesh.append(matrix)) continue
                    drawn++
                    if (casts) {
                        val box = modelBoxes[key.folder]
                        if (box != null) {
                            val grown = objects ?: BoundingBox().also { objects = it }
                            grow(grown, box, matrix)
                        }
                    }
                }
            }
        }
        for ((key, mesh) in meshes) if (!key.caster) mesh.upload()
        drawnCopies = drawn
        objectBounds = objects
        rebuilds++
    }

    /** Fits the meshes to the current bake: an unreferenced model is dropped, a grown capacity is rebuilt. */
    private fun rebuildMeshes() {
        // the capacities only change with the bake, the terrain or the caster buffers: walking every copy per frame is waste
        if (!meshesStale) return
        meshesStale = false
        val capacities = capacities()
        val iterator = meshes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in capacities) {
                entry.value.dispose()
                iterator.remove()
            }
        }
        for ((key, capacity) in capacities) {
            val current = meshes[key]
            if (current != null && current.capacity >= capacity) continue // never shrink what already fits
            current?.dispose()
            meshes.remove(key)
            val model = layerModels[key.folder] as? Model ?: continue // a model that never loaded drops only its copies
            meshes[key] = ModelMeshes(capacity, buildParts(model, capacity))
        }
    }

    /** How many copies of each (model folder, layer kind) the current bake holds, over every layer that scatters it. */
    private fun capacities(): Map<MeshKey, Int> {
        val counts = HashMap<MeshKey, Int>()
        for (layer in layers) {
            val layerBake = layer.bakeLayer ?: continue
            for (chunk in layerBake.chunks) {
                for (copy in chunk) {
                    val key = layer.keyAt(copy.model) ?: continue
                    counts[key] = (counts[key] ?: 0) + 1
                    if (casting && layer.kind == FoliageLayerKind.OBJECT) {
                        val caster = layer.casterKeyAt(copy.model) ?: continue
                        counts[caster] = (counts[caster] ?: 0) + 1
                    }
                }
            }
        }
        return counts
    }

    /**
     * Grows [out] with [box] as [matrix] turns it into world space: the centre of the box through the matrix, its
     * half extents through the absolute value of the 3x3, which is the axis-aligned box of a rotated box exactly.
     * It runs once per drawn copy, so it stays clear of the eight-corner walk `BoundingBox.ext` does.
     */
    private fun grow(out: BoundingBox, box: BoundingBox, matrix: Matrix4) {
        val m = matrix.`val`
        val cx = (box.min.x + box.max.x) * 0.5f
        val cy = (box.min.y + box.max.y) * 0.5f
        val cz = (box.min.z + box.max.z) * 0.5f
        val hx = (box.max.x - box.min.x) * 0.5f
        val hy = (box.max.y - box.min.y) * 0.5f
        val hz = (box.max.z - box.min.z) * 0.5f
        val x = m[0] * cx + m[4] * cy + m[8] * cz + m[12]
        val y = m[1] * cx + m[5] * cy + m[9] * cz + m[13]
        val z = m[2] * cx + m[6] * cy + m[10] * cz + m[14]
        val dx = abs(m[0]) * hx + abs(m[4]) * hy + abs(m[8]) * hz
        val dy = abs(m[1]) * hx + abs(m[5]) * hy + abs(m[9]) * hz
        val dz = abs(m[2]) * hx + abs(m[6]) * hy + abs(m[10]) * hz
        out.ext(x - dx, y - dy, z - dz)
        out.ext(x + dx, y + dy, z + dz)
    }

    /** One instanced mesh per enabled node part of [model], each sized for [capacity] copies. */
    private fun buildParts(model: Model, capacity: Int): List<PartMesh> {
        val parts = ArrayList<PartMesh>()
        addParts(model.nodes, capacity, parts)
        return parts
    }

    private fun addParts(nodes: Iterable<Node>, capacity: Int, out: MutableList<PartMesh>) {
        for (node in nodes) {
            for (nodePart in node.parts) {
                if (!nodePart.enabled) continue
                val source = nodePart.meshPart ?: continue
                if (source.size <= 0) continue
                val material = nodePart.material ?: continue
                val built = instancedPart(source, capacity) ?: continue
                out += PartMesh(built, material, nodeWorld(nodePart, node), restPoseBones(nodePart), capacity)
            }
            addParts(node.getChildren(), capacity, out)
        }
    }

    /**
     * A new instanced mesh holding [part]'s own indices over the source mesh's vertices: instancing adds attributes
     * to the vertex layout, so the shared model mesh cannot be reused.
     */
    private fun instancedPart(part: MeshPart, capacity: Int): MeshPart? {
        val source = part.mesh ?: return null
        check(source is IntMesh) { "foliage models are built with 32-bit meshes (IntModel), not ${source.javaClass.name}" }
        val indexed = source.numIndices > 0
        val mesh = IntMesh(true, source.numVertices, if (indexed) part.size else 0, source.vertexAttributes)
        try {
            val vertices = FloatArray(source.numVertices * source.vertexSize / 4)
            source.getVertices(vertices)
            mesh.setVertices(vertices)
            if (indexed) {
                val indices = IntArray(part.size)
                source.getIndices(part.offset, part.size, indices, 0)
                mesh.setIndices(indices)
            }
            mesh.enableInstancedRendering(false, capacity, *InstanceAttributes.of())
        } catch (t: Throwable) {
            mesh.dispose()
            throw t
        }
        val built = MeshPart(part.id, mesh, if (indexed) 0 else part.offset, part.size, part.primitiveType)
        built.update()
        return built
    }

    /**
     * The node's world transform, folded into every instance of a plain part. A skinned part gets the identity:
     * its bones already carry the node hierarchy, and the shader applies them before the instance matrix.
     */
    private fun nodeWorld(nodePart: NodePart, node: Node): Matrix4 =
        if (nodePart.invBoneBindTransforms != null) Matrix4() else Matrix4(node.globalTransform)

    /** The model's rest pose for a skinned part: the same matrices `Node.calculateBoneTransforms` writes. */
    private fun restPoseBones(nodePart: NodePart): kotlin.Array<Matrix4>? {
        val binds = nodePart.invBoneBindTransforms ?: return null
        return kotlin.Array(binds.size) { index ->
            Matrix4(binds.keys[index]!!.globalTransform).mul(binds.values[index])
        }
    }

    /** How far the layer's boxes reach past their own heights: [chunkPad]. */
    private fun computeChunkPad(): Float {
        val scale = staged.meta.layers.maxOfOrNull { it.scale.max } ?: 1f
        var reach = 0f
        for (box in modelBoxes.values) {
            reach = maxOf(
                reach,
                abs(box.min.x), abs(box.max.x),
                abs(box.min.y), abs(box.max.y),
                abs(box.min.z), abs(box.max.z),
            )
        }
        return reach * scale
    }

    /** One layer of the foliage: its kind, its chunk grid over the terrain, and which chunks the camera sees. */
    private inner class Layer(val meta: FoliageLayerMeta) {
        val kind: FoliageLayerKind = meta.layerKind()

        /** How far DETAIL copies are drawn from the camera; not read for an OBJECT layer. */
        val drawDistance: Float = meta.drawDistance

        /** The layer's lean, from 0 (upright) through 1 (with the slope). */
        val align: Float = meta.alignToNormal

        /** The bake of this layer, or null when the bake holds no such layer. */
        var bakeLayer: FoliageLayerBake? = null
            private set

        /** The boxes of [bakeLayer] over the current terrain, or null when either of them is missing. */
        var grid: FoliageChunkGrid? = null
            private set

        /** Which chunks the last cull kept, in the bake's chunk order. */
        var visible: BooleanArray = BooleanArray(0)
            private set

        private var lastVisible: BooleanArray = BooleanArray(0)

        /** Which chunks the last light cull kept (OBJECT layers only), in the bake's chunk order. */
        var casterVisible: BooleanArray = BooleanArray(0)
            private set

        private var lastCasterVisible: BooleanArray = BooleanArray(0)

        private val casterKeys: List<MeshKey> = meta.models.map { MeshKey(it.asset, kind, caster = true) }

        /** The caster mesh set of the layer's [index]-th model; null when the bake names one the meta no longer holds. */
        fun casterKeyAt(index: Int): MeshKey? = casterKeys.getOrNull(index)

        /** The mesh set each model index draws into: one per folder and kind, so either kind can be drawn alone. */
        private val meshKeys: List<MeshKey> = meta.models.map { MeshKey(it.asset, kind) }

        /** The mesh set of the layer's [index]-th model; null when the bake names one the meta no longer holds. */
        fun keyAt(index: Int): MeshKey? = meshKeys.getOrNull(index)

        /** Rebuilds [bakeLayer] and [grid] from the current bake and terrain. */
        fun refresh() {
            val layerBake = bake?.layers?.firstOrNull { it.id == meta.id }
            val ground = terrain
            val size = bake?.chunkSize
            bakeLayer = layerBake
            grid = if (layerBake == null || ground == null || size == null) {
                null
            } else {
                foliageChunkGrid(layerBake, size, ground, chunkPad)
            }
            val chunks = grid?.boxes?.size ?: 0
            visible = BooleanArray(chunks)
            lastVisible = BooleanArray(chunks)
            casterVisible = BooleanArray(chunks)
            lastCasterVisible = BooleanArray(chunks)
        }

        /** Culls the chunks against the light [camera] and reports whether the kept set differs from the last one. */
        fun castCull(camera: Camera, entity: Matrix4): Boolean {
            if (kind != FoliageLayerKind.OBJECT) return false
            val boxes = grid
            if (boxes == null) {
                casterVisible.fill(false)
            } else {
                visibleFoliageChunks(boxes, entity, FoliageLayerKind.OBJECT, drawDistance, camera, casterVisible)
            }
            if (casterVisible.contentEquals(lastCasterVisible)) return false
            lastCasterVisible = casterVisible.copyOf()
            return true
        }

        /** Culls the chunks against [camera] and reports whether the kept set differs from the last one. */
        fun cull(camera: Camera, entity: Matrix4): Boolean {
            val boxes = grid
            if (boxes == null) {
                visible.fill(false)
            } else {
                visibleFoliageChunks(boxes, entity, kind, drawDistance, camera, visible)
            }
            if (visible.contentEquals(lastVisible)) return false
            lastVisible = visible.copyOf()
            return true
        }
    }

    /** One instance buffer set: the copies of one model folder drawn for one layer kind. */
    private data class MeshKey(val folder: String, val kind: FoliageLayerKind, val caster: Boolean = false)

    /** One model's instanced meshes for one layer kind: every node part of it, all drawing the same copies. */
    private class ModelMeshes(val capacity: Int, val parts: List<PartMesh>) {
        var count: Int = 0
            private set

        val isEmpty: Boolean get() = parts.isEmpty()

        /** Hands every part over as a renderable; nothing is handed over for a set the last fill left empty. */
        fun renderables(out: Array<Renderable>, pool: Pool<Renderable>) {
            if (count == 0) return
            for (part in parts) {
                val renderable = pool.obtain()
                renderable.meshPart.set(part.meshPart)
                renderable.material = part.material
                renderable.bones = part.bones
                renderable.worldTransform.idt()
                renderable.environment = null
                renderable.shader = null
                renderable.userData = null
                out.add(renderable)
            }
        }

        fun reset() {
            for (part in parts) part.reset()
            count = 0
        }

        /** Appends one copy's matrices; false when the buffers are full (the bake outgrew its capacity). */
        fun append(copy: Matrix4): Boolean {
            if (count >= capacity) return false
            for (part in parts) part.append(copy)
            count++
            return true
        }

        fun upload() {
            for (part in parts) part.upload()
        }

        fun dispose() {
            for (part in parts) part.dispose()
        }
    }

    /** One node part's instanced mesh: its geometry, its material, and the transform its copies carry. */
    private class PartMesh(
        val meshPart: MeshPart,
        val material: Material,
        /** The node's transform inside every instance; the identity for a skinned part, whose bones carry it. */
        private val nodeWorld: Matrix4,
        /** The model's rest pose for a skinned part, or null when the part is not skinned. */
        val bones: kotlin.Array<Matrix4>?,
        val capacity: Int,
    ) {
        private val buffer = FloatArray(capacity * InstanceAttributes.FLOATS)
        private val combined = Matrix4()
        var count: Int = 0
            private set

        fun reset() {
            count = 0
        }

        fun append(copy: Matrix4) {
            if (count >= capacity) return
            combined.set(copy).mul(nodeWorld)
            InstanceAttributes.write(combined, buffer, count * InstanceAttributes.FLOATS)
            count++
        }

        /** Hands the buffer over, empty or not: `getNumInstances` reads the buffer's limit, not a stored count. */
        fun upload() {
            checkNotNull(meshPart.mesh) { "an instanced mesh" }
                .setInstanceData(buffer, 0, count * InstanceAttributes.FLOATS)
        }

        fun dispose() {
            meshPart.mesh?.dispose()
        }
    }
}

/** The squared distance the camera must drift before a DETAIL layer refills its buffers. */
private const val CAMERA_REFILL_DISTANCE2 = 2.25f

/** The entity matrix a pass last drew with, to tell whether the entity has moved since. */
private class EntityMemo {
    private val last = FloatArray(16)
    private var known = false

    fun moved(entity: Matrix4): Boolean = !known || !same(last, entity.`val`)

    /** Whether two matrices hold the same sixteen floats; `Matrix4` has no `equals` to ask. */
    private fun same(a: FloatArray, b: FloatArray): Boolean {
        for (i in a.indices) if (a[i] != b[i]) return false
        return true
    }

    fun remember(entity: Matrix4) {
        entity.`val`.copyInto(last)
        known = true
    }
}
