/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assimp

import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.AIScene

/**
 * Converts Assimp animations into [ModelAnimation]s. Key times are converted from ticks to seconds.
 */
internal class AnimationProcessor(private val nodes: NodeProcessor) {
    private val usedIds: MutableSet<String> = HashSet<String>()

    fun process(scene: AIScene): Array<ModelAnimation> {
        val result = Array<ModelAnimation>()
        for (i in 0..<scene.mNumAnimations()) {
            result.add(convert(AIAnimation.create(scene.mAnimations()!!.get(i)), i))
        }
        return result
    }

    private fun convert(aiAnimation: AIAnimation, index: Int): ModelAnimation {
        val tps = if (aiAnimation.mTicksPerSecond() > 0) aiAnimation.mTicksPerSecond() else DEFAULT_TICKS_PER_SECOND

        val animation = ModelAnimation()
        animation.id = uniqueId(aiAnimation.mName().dataString(), index)
        animation.nodeAnimations = Array<ModelNodeAnimation>()
        for (c in 0..<aiAnimation.mNumChannels()) {
            val channel = AINodeAnim.create(aiAnimation.mChannels()!!.get(c))
            val nodeAnimation = ModelNodeAnimation()
            nodeAnimation.nodeId = nodes.idForName(channel.mNodeName().dataString())

            val positions = channel.mPositionKeys()
            if (positions != null && channel.mNumPositionKeys() > 0) {
                nodeAnimation.translation = Array<ModelNodeKeyframe<Vector3>?>()
                for (k in 0..<channel.mNumPositionKeys()) {
                    val key = positions.get(k)
                    val v = key.mValue()
                    nodeAnimation.translation.add(keyframe<Vector3?>(key.mTime() / tps, Vector3(v.x(), v.y(), v.z())))
                }
            }
            val rotations = channel.mRotationKeys()
            if (rotations != null && channel.mNumRotationKeys() > 0) {
                nodeAnimation.rotation = Array<ModelNodeKeyframe<Quaternion>?>()
                for (k in 0..<channel.mNumRotationKeys()) {
                    val key = rotations.get(k)
                    val q = key.mValue()
                    nodeAnimation.rotation.add(
                        keyframe<Quaternion?>(
                            key.mTime() / tps,
                            Quaternion(q.x(), q.y(), q.z(), q.w())
                        )
                    )
                }
            }
            val scalings = channel.mScalingKeys()
            if (scalings != null && channel.mNumScalingKeys() > 0) {
                nodeAnimation.scaling = Array<ModelNodeKeyframe<Vector3>?>()
                for (k in 0..<channel.mNumScalingKeys()) {
                    val key = scalings.get(k)
                    val v = key.mValue()
                    nodeAnimation.scaling.add(keyframe<Vector3?>(key.mTime() / tps, Vector3(v.x(), v.y(), v.z())))
                }
            }
            animation.nodeAnimations.add(nodeAnimation)
        }
        return animation
    }

    private fun uniqueId(name: String?, index: Int): String {
        val base = if (name == null || name.isEmpty()) "animation_" + index else name
        var id = base
        var n = 1
        while (!usedIds.add(id)) {
            id = base + "_" + n
            n++
        }
        return id
    }

    companion object {
        private const val DEFAULT_TICKS_PER_SECOND = 25.0

        private fun <T> keyframe(seconds: Double, value: T?): ModelNodeKeyframe<T> {
            val keyframe = ModelNodeKeyframe<T>()
            keyframe.keytime = seconds.toFloat()
            keyframe.value = value
            return keyframe
        }
    }
}
