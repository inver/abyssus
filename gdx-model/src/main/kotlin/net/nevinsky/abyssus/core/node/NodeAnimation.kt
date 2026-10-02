/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nevinsky.abyssus.core.node

import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

/**
 * A NodeAnimation defines keyframes for a [Node] in a [Model]. The keyframes
 * are given as a translation vector, a rotation quaternion and a scale vector. Keyframes are interpolated linearly for
 * now. Keytimes are given in seconds.
 *
 * @author badlogic, Xoppa
 */
open class NodeAnimation {
    /**
     * the Node affected by this animation
     */
    var node: Node? = null

    /**
     * the translation keyframes if any (might be null), sorted by time ascending
     */
    var translation: Array<NodeKeyframe<Vector3>>? = null

    /**
     * the rotation keyframes if any (might be null), sorted by time ascending
     */
    var rotation: Array<NodeKeyframe<Quaternion>>? = null

    /**
     * the scaling keyframes if any (might be null), sorted by time ascending
     */
    var scaling: Array<NodeKeyframe<Vector3>>? = null
}
