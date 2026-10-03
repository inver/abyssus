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

package net.nevinsky.abyssus.assets.sky

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/**
 * A built sky that draws itself as the background: following [Camera]'s orientation but not its position. The caller
 * turns depth testing, depth writes and culling off around [draw]. GL thread only, with the context current.
 */
interface Sky : Disposable {
    /** Draws the sky seen from [camera]; a sky lit by the sun shines from [sun] (a unit vector toward it). */
    fun draw(camera: Camera, sun: Vector3)
}
