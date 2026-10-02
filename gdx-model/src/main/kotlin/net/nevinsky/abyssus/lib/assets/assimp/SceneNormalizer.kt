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

package net.nevinsky.abyssus.lib.assets.assimp

import org.slf4j.LoggerFactory

import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.lwjgl.assimp.AIMetaData
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import java.nio.ByteOrder
import kotlin.math.abs

private val log = LoggerFactory.getLogger(SceneNormalizer::class.java)

/**
 * Brings a scene into the coordinate system of the engine: Y up, X right, Z towards the viewer. Importers like the
 * FBX one only report the axes and the unit of the file in the scene metadata, they do not change the geometry.
 *
 *
 * The correction is applied to the transform of the root node, the vertices stay untouched.
 */
internal object SceneNormalizer {
    private const val METERS_PER_FBX_UNIT = 0.01f

    /**
     * @param convertUnits also scale the scene to meters (FBX: `UnitScaleFactor` is in centimeters)
     * @return the matrix that converts the scene, `null` if the scene is already in the right system
     */
    fun correction(scene: AIScene, convertUnits: Boolean): Matrix4? {
        val meta = readMetaData(scene.mMetaData())

        var result: Matrix4? = null
        if (meta.containsKey("UpAxis")) {
            result = axes(
                meta.getOrDefault("CoordAxis", 0).toInt(), meta.getOrDefault("CoordAxisSign", 1).toInt(),
                meta.get("UpAxis")!!.toInt(), meta.getOrDefault("UpAxisSign", 1).toInt(),
                meta.getOrDefault("FrontAxis", 2).toInt(), meta.getOrDefault("FrontAxisSign", 1).toInt()
            )
        }
        if (convertUnits && meta.containsKey("UnitScaleFactor")) {
            val scale = meterScale(meta.get("UnitScaleFactor")!!.toDouble())
            if (abs(scale - 1f) > 1e-6f) {
                val scaling = Matrix4().scl(scale)
                result = if (result == null) scaling else result.mulLeft(scaling)
            }
        }
        return result
    }

    /**
     * @return the rotation that maps the axes of a file (right = coord axis, up, front = towards the viewer, each
     * axis 0, 1, 2 for X, Y, Z with a sign of 1 or -1) to X right, Y up, Z towards the viewer; `null` for the
     * default Y up system, and for invalid or left handed axes
     */
    fun axes(coord: Int, coordSign: Int, up: Int, upSign: Int, front: Int, frontSign: Int): Matrix4? {
        if (!validAxis(coord, coordSign) || !validAxis(up, upSign) || !validAxis(
                front,
                frontSign
            ) || coord == up || coord == front || up == front
        ) {
            log.warn("Invalid axes in the scene metadata, skip the axes conversion")
            return null
        }
        val right = axis(coord, coordSign)
        val upVector = axis(up, upSign)
        val frontVector = axis(front, frontSign)
        if (right.cpy().crs(upVector).dot(frontVector) < 0f) {
            log.warn("The scene has a left handed coordinate system, skip the axes conversion")
            return null
        }
        if (coord == 0 && coordSign == 1 && up == 1 && upSign == 1 && front == 2 && frontSign == 1) {
            return null
        }
        // the rows are the new axes expressed in the file system: p' = (right . p, up . p, front . p)
        val m = Matrix4()
        m.`val`[Matrix4.M00] = right.x
        m.`val`[Matrix4.M01] = right.y
        m.`val`[Matrix4.M02] = right.z
        m.`val`[Matrix4.M10] = upVector.x
        m.`val`[Matrix4.M11] = upVector.y
        m.`val`[Matrix4.M12] = upVector.z
        m.`val`[Matrix4.M20] = frontVector.x
        m.`val`[Matrix4.M21] = frontVector.y
        m.`val`[Matrix4.M22] = frontVector.z
        return m
    }

    /** @return meters per unit for the FBX `UnitScaleFactor`, which is in centimeters
     */
    fun meterScale(unitScaleFactor: Double): Float {
        return if (unitScaleFactor > 0) unitScaleFactor.toFloat() * METERS_PER_FBX_UNIT else 1f
    }

    /** Multiplies the local transform of the node by the correction.  */
    fun apply(root: ModelNode, correction: Matrix4?) {
        if (correction == null) {
            return
        }
        val local = Matrix4().set(
            if (root.translation != null) root.translation else Vector3(),
            if (root.rotation != null) root.rotation else Quaternion(),
            if (root.scale != null) root.scale else Vector3(1f, 1f, 1f)
        )
        local.mulLeft(correction)

        val translation = local.getTranslation(Vector3())
        val rotation = local.getRotation(Quaternion(), true)
        val scale = local.getScale(Vector3())
        root.translation = if (translation.isZero(1e-9f)) null else translation
        root.rotation = if (rotation.isIdentity(1e-6f)) null else rotation
        root.scale = if (scale.epsilonEquals(1f, 1f, 1f, 1e-6f)) null else scale
    }

    private fun validAxis(axis: Int, sign: Int): Boolean {
        return axis >= 0 && axis <= 2 && (sign == 1 || sign == -1)
    }

    private fun axis(axis: Int, sign: Int): Vector3 {
        val v = Vector3()
        v.setZero()
        when (axis) {
            0 -> v.x = sign.toFloat()
            1 -> v.y = sign.toFloat()
            else -> v.z = sign.toFloat()
        }
        return v
    }

    /** Reads the numeric metadata entries (int, float and double) by key.  */
    private fun readMetaData(meta: AIMetaData?): MutableMap<String, Number> {
        val res = HashMap<String, Number>()
        if (meta == null) {
            return res
        }
        for (i in 0..<meta.mNumProperties()) {
            val key = meta.mKeys().get(i).dataString()
            val entry = meta.mValues().get(i)
            val data = entry.mData(8).order(ByteOrder.nativeOrder())
            when (entry.mType()) {
                Assimp.AI_INT32 -> res.put(key, data.getInt(0))
                Assimp.AI_FLOAT -> res.put(key, data.getFloat(0))
                Assimp.AI_DOUBLE -> res.put(key, data.getDouble(0))
                else -> {}
            }
        }
        return res
    }
}
