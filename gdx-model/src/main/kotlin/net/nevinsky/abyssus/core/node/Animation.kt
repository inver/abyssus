/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core.node

import com.badlogic.gdx.utils.Array

/**
 * An Animation has an id and a list of [NodeAnimation] instances. Each NodeAnimation animates a single
 * [Node] in the [Model]. Every [NodeAnimation] is assumed to have the same amount of keyframes, at
 * the same timestamps, as all other node animations for faster keyframe searches.
 *
 * @author badlogic
 */
class Animation(
    /**
     * the unique id of the animation
     */
    val id: String
) {
    /**
     * the duration in seconds
     */
    var duration = 0f

    /**
     * the animation curves for individual nodes
     */
    val nodeAnimations: Array<NodeAnimation> = Array<NodeAnimation>()
}
