package net.nevinsky.abyssus.sceneview

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.scene.SceneDto
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float)

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)

/** [direction] is the unit vector the camera looks along. */
data class CameraParams(
    val position: Vec3,
    val direction: Vec3,
    val near: Float,
    val far: Float,
    val fieldOfView: Float,
) {
    companion object {
        val DEFAULT = CameraParams(Vec3(8f, 6f, 8f), normalized(Vec3(-8f, -6f, -8f))!!, 0.1f, 1000f, 67f)
    }
}

data class FogParams(val color: Rgba, val near: Float, val far: Float, val exponent: Float)

data class SceneRenderParams(val clear: Rgba, val ambient: Rgba?, val fog: FogParams?, val camera: CameraParams) {
    companion object {
        val DEFAULT_CLEAR = Rgba(0.1f, 0.1f, 0.15f, 1f)
        val DEFAULT = SceneRenderParams(DEFAULT_CLEAR, null, null, CameraParams.DEFAULT)

        fun from(scene: SceneDto, camera: CameraParams): SceneRenderParams {
            val fog = fogOf(scene)
            return SceneRenderParams(fog?.color ?: DEFAULT_CLEAR, ambientOf(scene), fog, camera)
        }

        private fun ambientOf(scene: SceneDto): Rgba? {
            if (scene.ambientLightEnabled != true) return null
            val light = scene.ambientLight ?: return null
            val c = light.color ?: return null
            val k = (light.intensity ?: 1f).coerceAtLeast(0f)
            return Rgba(c.r * k, c.g * k, c.b * k, 1f)
        }

        /** Mundus fog is `exp(-(d * density)^gradient)`; the view approximates it with a ramp reaching full fog at `2 / density`. */
        private fun fogOf(scene: SceneDto): FogParams? {
            if (scene.fogEnabled != true) return null
            val fog = scene.fog ?: return null
            val c = fog.color ?: return null
            val density = fog.density?.takeIf { it > 0f && it.isFinite() } ?: return null
            val gradient = fog.gradient?.takeIf { it > 0f && it.isFinite() } ?: 1f
            return FogParams(Rgba(c.r, c.g, c.b, 1f), 0f, 2f / density, gradient)
        }
    }
}

private fun normalized(v: Vec3): Vec3? {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (len > 1e-6f && len.isFinite()) Vec3(v.x / len, v.y / len, v.z / len) else null
}

/** The `mainCamera` of the `.abss` project a scene belongs to. */
object MainCamera {
    fun parse(abssText: String): CameraParams? = runCatching {
        val root = JsonParser.parseString(abssText).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val cam = root.get("mainCamera")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val position = cam.vec("position") ?: return null
        val direction = normalized(cam.vec("viewPointPosition") ?: return null) ?: return null
        val defaults = CameraParams.DEFAULT
        CameraParams(
            position,
            direction,
            cam.get("near")?.asFloat?.takeIf { it > 0f } ?: defaults.near,
            cam.get("far")?.asFloat?.takeIf { it > 0f } ?: defaults.far,
            cam.get("fieldOfView")?.asFloat?.takeIf { it > 0f && it < 180f } ?: defaults.fieldOfView,
        )
    }.getOrNull()

    /** The camera of the `.abss` beside the scene's `scenes` folder; the default camera when there is none or it is unreadable. */
    fun forScene(sceneFile: VirtualFile): CameraParams {
        val abss = sceneFile.parent?.parent?.children?.firstOrNull { it.extension == "abss" } ?: return CameraParams.DEFAULT
        return runCatching { parse(String(abss.contentsToByteArray(), abss.charset)) }.getOrNull() ?: CameraParams.DEFAULT
    }

    private fun JsonObject.vec(name: String): Vec3? {
        val o = get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun f(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        return Vec3(f("x"), f("y"), f("z"))
    }
}
