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

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/** An entity drawn from a loaded asset. */
interface PlacedEntity<A> {
    val placement: AssetPlacement
    val asset: A
}

/**
 * One entity per placement whose asset is loaded, in scene order. An entity is kept while its placement and asset stay
 * the same; otherwise [place] makes the new one from the placement, the asset and the previous entity of that id when
 * it shows the same asset (so it can keep state such as a running animation).
 */
class PlacedEntities<A : Any, E : PlacedEntity<A>>(private val place: (AssetPlacement, A, E?) -> E) {
    private var entities = LinkedHashMap<String, E>()

    val drawn: Collection<E> get() = entities.values

    fun update(placements: List<AssetPlacement>, assetOf: (String) -> A?) {
        val next = LinkedHashMap<String, E>()
        for (p in placements) {
            val asset = assetOf(p.assetName) ?: continue
            val current = entities[p.entityId]?.takeIf { it.asset === asset }
            next[p.entityId] = if (current?.placement == p) current else place(p, asset, current)
        }
        entities = next
    }

    fun clear() = entities.clear()
}

/** The world matrix of [this] placement transform. */
fun PlacementTransform.toMatrix(out: Matrix4 = Matrix4()): Matrix4 = out.set(
    Vector3(position.x, position.y, position.z),
    Quaternion(rotation.x, rotation.y, rotation.z, rotation.w),
    Vector3(scale.x, scale.y, scale.z),
)
