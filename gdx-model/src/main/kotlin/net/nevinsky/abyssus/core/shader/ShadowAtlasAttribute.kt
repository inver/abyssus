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

package net.nevinsky.abyssus.core.shader

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.environment.BaseLight
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4

enum class ShadowLightKind { DIRECTIONAL, POINT, SPOT }

data class ShadowAtlasView(val matrix: Matrix4, val uvTransform: Vector4) {
    fun copyOwned() = ShadowAtlasView(matrix.cpy(), Vector4(uvTransform))
}

data class ShadowLightRecord(
    val lightId: String,
    val kind: ShadowLightKind,
    val light: BaseLight<*>,
    val views: List<ShadowAtlasView>,
    val position: Vector3,
    val far: Float,
    val depthBias: Float = 0.0008f,
) {
    fun copyOwned() = copy(light = light, views = views.map(ShadowAtlasView::copyOwned), position = Vector3(position))
}

/** One atlas binding with per-light records associated by light object identity, never array order. */
class ShadowAtlasAttribute(val atlas: GLTexture, records: List<ShadowLightRecord>) : Attribute(Type) {
    val records: List<ShadowLightRecord> = records.map(ShadowLightRecord::copyOwned)
    fun recordFor(light: BaseLight<*>): ShadowLightRecord? = records.firstOrNull { it.light === light }
    override fun copy(): Attribute = ShadowAtlasAttribute(atlas, records)
    override fun hashCode(): Int = 31 * System.identityHashCode(atlas) + records.fold(1) { h, r -> 31 * h + r.lightId.hashCode() }
    override fun equals(other: Any?): Boolean = other is ShadowAtlasAttribute && atlas === other.atlas && records == other.records
    override fun compareTo(other: Attribute): Int = if (type != other.type) type.compareTo(other.type) else
        System.identityHashCode(atlas).compareTo(System.identityHashCode((other as ShadowAtlasAttribute).atlas))
    companion object {
        const val Alias = "shadowAtlas"
        @JvmField val Type: Long = register(Alias)
    }
}
