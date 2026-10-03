/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
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
