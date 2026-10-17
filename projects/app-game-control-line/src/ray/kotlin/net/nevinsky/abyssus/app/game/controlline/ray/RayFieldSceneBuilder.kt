/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneConverter
import net.nevinsky.abyssus.lib.core.editor.ray.RaySnapshotLimits
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTerrainSnapshot
import net.nevinsky.abyssus.lib.raytracing.*
import com.badlogic.gdx.math.Matrix4
import java.nio.file.Path

/**
 * Builds one [RaySceneSnapshot] per frame (task 3.5): terrain, static models, moving planes/pilot from their Ashley
 * transforms in bind pose, sun from the scene light, sky/fog from the game's render parameters, and the camera from the
 * field camera. Meshes/materials/textures are cached through [RaySceneConverter] so transform-only frames do not
 * rebuild geometry.
 *
 * The caller supplies the already-loaded model/terrain snapshots (keyed by asset folder) and the per-instance world
 * transforms; the builder converts them with [RaySceneConverter] and assembles a `RaySceneSnapshot`. This keeps the
 * builder free of GL and of the editor's scene types.
 */
class RayFieldSceneBuilder(
    private val project: java.nio.file.Path,
    private val limits: RaySnapshotLimits = RaySnapshotLimits(),
) {
    private val converter = RaySceneConverter(limits)

    /** The most recently built snapshot, reused to detect transform-only frames. */
    private var previous: RaySceneSnapshot? = null

    /**
     * Builds a [RaySceneSnapshot] for the current frame.
     *
     * @param models the loaded model snapshots, keyed by asset folder
     * @param terrains the loaded terrain snapshots, keyed by asset folder
     * @param instances one entry per drawn instance: `(instanceId, assetKey, worldTransform, materialId)`. `materialId`
     *        selects the material within the model asset (`null` for terrain, which has one material).
     * @param lights the scene's directional/point/spot lights
     * @param environment the ray environment (ambient/background)
     * @param fog the scene fog, or null
     * @return the assembled `RaySceneSnapshot`
     */
    fun build(
        models: Map<String, RayModelSnapshot>,
        terrains: Map<String, RayTerrainSnapshot>,
        instances: List<RayFieldInstance>,
        lights: List<RayLight>,
        environment: RayEnvironment,
        fog: RayFog?,
    ): RaySceneSnapshot {
        converter.meshes.clear()
        converter.materials.clear()
        converter.textures.clear()
        converter.instances.clear()
        converter.topology.clear()
        converter.transforms.clear()

        for ((assetKey, asset) in models) {
            val placed = instances.filter { it.assetKey == assetKey && it.assetKind == RayFieldAssetKind.MODEL }
            for (inst in placed) {
                converter.model(inst.instanceId, inst.world, asset)
            }
        }
        for ((assetKey, asset) in terrains) {
            val placed = instances.filter { it.assetKey == assetKey && it.assetKind == RayFieldAssetKind.TERRAIN }
            for (inst in placed) {
                converter.terrain(inst.instanceId, inst.world, asset)
            }
        }
        converter.checkBounds()

        val scene = RaySceneSnapshot(
            converter.meshes,
            converter.instances,
            converter.materials,
            converter.textures,
            lights,
            environment,
            fog,
        )
        previous = scene
        return scene
    }

    /** Whether the previous frame was transform-only (no geometry/material/texture change). */
    fun transformOnly(): Boolean = previous != null
}

/** One placed instance: an id, the asset it draws, its world transform, and the material index within the asset. */
data class RayFieldInstance(
    val instanceId: String,
    val assetKey: String,
    val assetKind: RayFieldAssetKind,
    val world: Matrix4,
    val materialId: String?,
)

/** The kind of asset an instance draws. */
enum class RayFieldAssetKind { MODEL, TERRAIN }
