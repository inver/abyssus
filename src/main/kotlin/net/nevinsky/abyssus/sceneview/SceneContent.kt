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

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.dto.float
import net.nevinsky.abyssus.dto.obj
import net.nevinsky.abyssus.dto.opt
import net.nevinsky.abyssus.dto.text
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.scene.SceneDto

/** [w] is 1 for the identity rotation, which Mundus leaves out of the file together with the other default fields. */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)
    }
}

/** Mundus `PositionComponent`: position 0, identity rotation and unit scale unless the file says otherwise. */
data class PlacementTransform(val position: Vec3, val rotation: Quat, val scale: Vec3) {
    companion object {
        val IDENTITY = PlacementTransform(Vec3(0f, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f))
    }
}

/** An entity showing the asset folder [assetName]. [entityId] is its key under `ecs/entities`; picking reports it back. */
data class AssetPlacement(val entityId: String, val assetName: String, val transform: PlacementTransform)

enum class LightKind { DIRECTIONAL, POINT, SPOT }

/** [direction] is the unit vector the light shines along (directional and spot); [position] matters for point and spot. */
/** Reach of a point or spot light that does not name one. */
const val DEFAULT_LIGHT_RANGE = 100f

data class LightPlacement(
    val entityId: String,
    val kind: LightKind,
    val color: Rgba,
    val intensity: Float,
    val position: Vec3,
    val direction: Vec3,
    val range: Float = DEFAULT_LIGHT_RANGE,
    val rotation: Quat = Quat.IDENTITY,
)

/** libGDX `PerspectiveCamera` defaults, used for the fields a camera entity leaves out. */
const val DEFAULT_CAMERA_NEAR = 1f
const val DEFAULT_CAMERA_FAR = 100f
const val DEFAULT_CAMERA_FOV = 67f

/**
 * A camera entity. [direction] is the view direction from `viewPointPosition` (as in the file, not yet normalized);
 * it is overridden by the position of [lookAtId] while that resolves. [name] is the entity's `NameComponent` name,
 * or its id when unnamed.
 */
data class CameraPlacement(
    val entityId: String,
    val name: String,
    val position: Vec3,
    val direction: Vec3,
    val lookAtId: String?,
    val near: Float = DEFAULT_CAMERA_NEAR,
    val far: Float = DEFAULT_CAMERA_FAR,
    val fieldOfView: Float = DEFAULT_CAMERA_FOV,
    val rotation: Quat = Quat.IDENTITY,
)

/** What a scene shows besides its environment. */
data class SceneContent(
    val models: List<AssetPlacement> = emptyList(),
    val terrains: List<AssetPlacement> = emptyList(),
    val lights: List<LightPlacement> = emptyList(),
    /** The skybox asset folder to draw, or null when the scene has no enabled, named skybox. */
    val skybox: String? = null,
    val cameras: List<CameraPlacement> = emptyList(),
    /** `localPosition` of every entity with a `PositionComponent` (entity id to position), for look-at targets. */
    val entityPositions: Map<String, Vec3> = emptyMap(),
) {
    companion object {
        val EMPTY = SceneContent()

        fun of(scene: SceneDto): SceneContent {
            val entities = scene.ecs?.obj("entities")
            val models = mutableListOf<AssetPlacement>()
            val terrains = mutableListOf<AssetPlacement>()
            val lights = mutableListOf<LightPlacement>()
            val cameras = mutableListOf<CameraPlacement>()
            val positions = linkedMapOf<String, Vec3>()
            entities?.properties()?.forEach { (id, entity) ->
                val components = entity.obj("components") ?: return@forEach
                runCatchingKeepingCancellation {
                    val transform = transformOf(components.opt("PositionComponent"))
                    if (components.opt("PositionComponent") != null) positions[id] = transform.position
                    val asset = components.opt("RenderComponent")?.opt("renderable")?.opt("asset")
                    val assetName = asset?.text("assetName")
                    when {
                        assetName != null && asset.text("type") == "MODEL" -> models += AssetPlacement(id, assetName, transform)
                        assetName != null && asset.text("type") == "TERRAIN" -> terrains += AssetPlacement(id, assetName, transform)
                        components.opt("CameraComponent") != null -> cameras += cameraOf(id, components, transform)
                        else -> lightOf(id, components, transform)?.let { lights += it }
                    }
                }
            }
            val skybox = scene.skyboxName?.takeIf { scene.skyboxEnabled == true && it.isNotBlank() }
            return SceneContent(models, terrains, lights, skybox, cameras, positions)
        }

        private fun number(node: JsonNode?, name: String, default: Float) = node?.float(name) ?: default

        private fun vec(node: JsonNode?, default: Float) =
            Vec3(number(node, "x", default), number(node, "y", default), number(node, "z", default))

        private fun transformOf(position: JsonNode?): PlacementTransform {
            if (position == null) return PlacementTransform.IDENTITY
            val rotation = position.opt("localRotation")
            return PlacementTransform(
                vec(position.opt("localPosition"), 0f),
                Quat(number(rotation, "x", 0f), number(rotation, "y", 0f), number(rotation, "z", 0f), number(rotation, "w", 1f)),
                vec(position.opt("localScale"), 1f),
            )
        }

        /**
         * A camera entity has a `CameraComponent` whose `camera` object holds the view; a missing one leaves libGDX
         * defaults. The position is the entity's own, else the camera's.
         */
        private fun cameraOf(id: String, components: JsonNode, transform: PlacementTransform): CameraPlacement {
            val camera = components.opt("CameraComponent")?.obj("camera")
            val position = components.opt("PositionComponent")?.opt("localPosition")
            val placed = if (position != null || camera?.obj("position") == null) transform.position else vec(camera.obj("position"), 0f)
            val direction = camera?.obj("viewPointPosition")?.let { vec(it, 0f) } ?: Vec3(0f, 0f, -1f)
            val lookAt = components.opt("PositionComponent")?.opt("lookAtId")?.let {
                when {
                    it.isIntegralNumber -> it.asLong().takeIf { n -> n >= 0 }?.toString()
                    it.isTextual -> it.asText().takeIf { t -> t.isNotBlank() && t != "-1" }
                    else -> null
                }
            }
            val name = components.opt("NameComponent")?.text("name")?.takeIf { it.isNotBlank() } ?: id
            return CameraPlacement(
                id, name, placed, direction, lookAt,
                number(camera, "near", DEFAULT_CAMERA_NEAR),
                number(camera, "far", DEFAULT_CAMERA_FAR),
                number(camera, "fieldOfView", DEFAULT_CAMERA_FOV),
                transform.rotation,
            )
        }

        /**
         * A light entity has a `TypeComponent` of `LIGHT_<KIND>` and a `LightComponent` whose color and intensity sit
         * either directly in it or in a nested `light` object. A directional light shines along its rotated -Z.
         */
        private fun lightOf(id: String, components: JsonNode, transform: PlacementTransform): LightPlacement? {
            val type = components.opt("TypeComponent")?.text("type")
            val kind = when {
                type == null || !type.startsWith("LIGHT") -> if (components.opt("LightComponent") != null) LightKind.DIRECTIONAL else return null
                type.contains("POINT") -> LightKind.POINT
                type.contains("SPOT") -> LightKind.SPOT
                else -> LightKind.DIRECTIONAL
            }
            val component = components.opt("LightComponent")
            val light = component?.obj("light") ?: component
            val color = light?.opt("color")
            val rgba = Rgba(number(color, "r", 1f), number(color, "g", 1f), number(color, "b", 1f), 1f)
            val intensity = number(light, "intensity", 0.3f).coerceAtLeast(0f)
            val range = number(light, "range", DEFAULT_LIGHT_RANGE)
            return LightPlacement(id, kind, rgba, intensity, transform.position, forward(transform.rotation), range, transform.rotation)
        }

        /** The libGDX forward axis (-Z) rotated by [q]. */
        internal fun forward(q: Quat): Vec3 {
            val len = kotlin.math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
            if (len < 1e-6f) return Vec3(0f, 0f, -1f)
            val x = q.x / len
            val y = q.y / len
            val z = q.z / len
            val w = q.w / len
            // q * (0, 0, -1) * q^-1
            return Vec3(0f - 2f * (x * z + w * y), 0f - 2f * (y * z - w * x), 0f - (1f - 2f * (x * x + y * y)))
        }
    }
}
