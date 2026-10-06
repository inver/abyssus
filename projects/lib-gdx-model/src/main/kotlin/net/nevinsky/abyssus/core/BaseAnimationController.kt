/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.core.node.Animation
import net.nevinsky.abyssus.lib.core.node.Node
import net.nevinsky.abyssus.lib.core.node.NodeAnimation

/**
 * Base class for applying one or more [Animation]s to a [ModelInstance]. This class only applies the actual
 * [Node] transformations, it does not manage animations or keep track of animation states. See
 * [AnimationController] for an implementation of this class which does manage animations.
 *
 * @author Xoppa
 */
open class BaseAnimationController
/**
 * Construct a new BaseAnimationController.
 *
 * @param target The [ModelInstance] on which the animations are being performed.
 */(
    /**
     * The [ModelInstance] on which the animations are being performed.
     */
    val target: ModelInstance
) {
    class Transform : Pool.Poolable {
        val translation: Vector3 = Vector3()
        val rotation: Quaternion = Quaternion()
        val scale: Vector3 = Vector3(1f, 1f, 1f)

        fun idt(): Transform {
            translation.set(0f, 0f, 0f)
            rotation.idt()
            scale.set(1f, 1f, 1f)
            return this
        }

        fun set(t: Vector3?, r: Quaternion?, s: Vector3?): Transform {
            translation.set(t)
            rotation.set(r)
            scale.set(s)
            return this
        }

        fun set(other: Transform): Transform {
            return set(other.translation, other.rotation, other.scale)
        }

        fun lerp(target: Transform, alpha: Float): Transform {
            return lerp(target.translation, target.rotation, target.scale, alpha)
        }

        fun lerp(
            targetT: Vector3?, targetR: Quaternion?, targetS: Vector3?,
            alpha: Float
        ): Transform {
            translation.lerp(targetT, alpha)
            rotation.slerp(targetR, alpha)
            scale.lerp(targetS, alpha)
            return this
        }

        fun toMatrix4(out: Matrix4): Matrix4? {
            return out.set(translation, rotation, scale)
        }

        override fun reset() {
            idt()
        }

        override fun toString(): String {
            return translation.toString() + " - " + rotation + " - " + scale
        }
    }

    private val transformPool: Pool<Transform> = object : Pool<Transform>() {
        override fun newObject(): Transform {
            return Transform()
        }
    }
    private var applying = false

    /**
     * Begin applying multiple animations to the instance, must followed by one or more calls to {
     * [.apply] and finally {[.end].
     */
    protected fun begin() {
        if (applying) {
            throw GdxRuntimeException("You must call end() after each call to being()")
        }
        applying = true
    }

    /**
     * Apply an animation, must be called between {[.begin] and {[.end].
     *
     * @param weight The blend weight of this animation relative to the previous applied animations.
     */
    protected fun apply(animation: Animation, time: Float, weight: Float) {
        if (!applying) {
            throw GdxRuntimeException("You must call begin() before adding an animation")
        }
        applyAnimation(transforms, transformPool, weight, animation, time)
    }

    /**
     * End applying multiple animations to the instance and update it to reflect the changes.
     */
    protected fun end() {
        if (!applying) {
            throw GdxRuntimeException("You must call begin() first")
        }
        for (entry in transforms.entries()) {
            entry.value!!.toMatrix4(entry.key.localTransform)
            transformPool.free(entry.value)
        }
        transforms.clear()
        target.calculateTransforms()
        applying = false
    }

    /**
     * Apply a single animation to the [ModelInstance] and update the it to reflect the changes.
     */
    protected fun applyAnimation(animation: Animation, time: Float) {
        if (applying) {
            throw GdxRuntimeException("Call end() first")
        }
        Companion.applyAnimation(null, null, 1f, animation, time)
        target.calculateTransforms()
    }

    /**
     * Apply two animations, blending the second onto to first using weight.
     */
    protected fun applyAnimations(
        anim1: Animation?, time1: Float, anim2: Animation?, time2: Float,
        weight: Float
    ) {
        if (anim2 == null || weight == 0f) {
            applyAnimation(anim1!!, time1)
        } else if (anim1 == null || weight == 1f) {
            applyAnimation(anim2, time2)
        } else if (applying) {
            throw GdxRuntimeException("Call end() first")
        } else {
            begin()
            apply(anim1, time1, 1f)
            apply(anim2, time2, weight)
            end()
        }
    }

    /**
     * Remove the specified animation, by marking the affected nodes as not animated. When switching animation, this
     * should be call prior to applyAnimation(s).
     */
    protected fun removeAnimation(animation: Animation) {
        for (nodeAnim in animation.nodeAnimations) {
            nodeAnim!!.node!!.isAnimated = false
        }
    }

    companion object {
        private val transforms = ObjectMap<Node, Transform>()
        private val tmpT = Transform()

        /**
         * Find first key frame index just before a given time
         *
         * @param arr  Key frames ordered by time ascending
         * @param time Time to search
         * @return key frame index, 0 if time is out of key frames time range
         */
        fun <T> getFirstKeyframeIndexAtTime(arr: Array<NodeKeyframe<T>>, time: Float): Int {
            val lastIndex = arr.size - 1

            // edges cases : time out of range always return first index
            if (lastIndex <= 0 || time < arr.get(0).keytime || time > arr.get(lastIndex).keytime) {
                return 0
            }

            // binary search
            var minIndex = 0
            var maxIndex = lastIndex

            while (minIndex < maxIndex) {
                val i = (minIndex + maxIndex) / 2
                if (time > arr.get(i + 1).keytime) {
                    minIndex = i + 1
                } else if (time < arr.get(i).keytime) {
                    maxIndex = i - 1
                } else {
                    return i
                }
            }
            return minIndex
        }

        private fun getTranslationAtTime(
            nodeAnim: NodeAnimation, time: Float,
            out: Vector3
        ): Vector3? {
            if (nodeAnim.translation == null) {
                return out.set(nodeAnim.node!!.translation)
            }
            if (nodeAnim.translation!!.size == 1) {
                return out.set(nodeAnim.translation!!.get(0)!!.value)
            }

            var index: Int = getFirstKeyframeIndexAtTime<Vector3>(nodeAnim.translation!!, time)
            val firstKeyframe: NodeKeyframe<*> = nodeAnim.translation!!.get(index)
            out.set(firstKeyframe.value as Vector3?)

            if (++index < nodeAnim.translation!!.size) {
                val secondKeyframe = nodeAnim.translation!!.get(index)
                val t = (time - firstKeyframe.keytime) / (secondKeyframe!!.keytime - firstKeyframe.keytime)
                out.lerp(secondKeyframe.value, t)
            }
            return out
        }

        private fun getRotationAtTime(
            nodeAnim: NodeAnimation, time: Float,
            out: Quaternion
        ): Quaternion? {
            if (nodeAnim.rotation == null) {
                return out.set(nodeAnim.node!!.rotation)
            }
            if (nodeAnim.rotation!!.size == 1) {
                return out.set(nodeAnim.rotation!!.get(0)!!.value)
            }

            var index: Int = getFirstKeyframeIndexAtTime<Quaternion>(nodeAnim.rotation!!, time)
            val firstKeyframe: NodeKeyframe<*> = nodeAnim.rotation!!.get(index)
            out.set(firstKeyframe.value as Quaternion?)

            if (++index < nodeAnim.rotation!!.size) {
                val secondKeyframe = nodeAnim.rotation!!.get(index)
                val t = (time - firstKeyframe.keytime) / (secondKeyframe!!.keytime - firstKeyframe.keytime)
                out.slerp(secondKeyframe.value, t)
            }
            return out
        }

        private fun getScalingAtTime(nodeAnim: NodeAnimation, time: Float, out: Vector3): Vector3? {
            if (nodeAnim.scaling == null) {
                return out.set(nodeAnim.node!!.scale)
            }
            if (nodeAnim.scaling!!.size == 1) {
                return out.set(nodeAnim.scaling!!.get(0)!!.value)
            }

            var index: Int = getFirstKeyframeIndexAtTime<Vector3>(nodeAnim.scaling!!, time)
            val firstKeyframe: NodeKeyframe<*> = nodeAnim.scaling!!.get(index)
            out.set(firstKeyframe.value as Vector3?)

            if (++index < nodeAnim.scaling!!.size) {
                val secondKeyframe = nodeAnim.scaling!!.get(index)
                val t = (time - firstKeyframe.keytime) / (secondKeyframe!!.keytime - firstKeyframe.keytime)
                out.lerp(secondKeyframe.value, t)
            }
            return out
        }

        private fun getNodeAnimationTransform(nodeAnim: NodeAnimation, time: Float): Transform {
            val transform: Transform = tmpT
            getTranslationAtTime(nodeAnim, time, transform.translation)
            getRotationAtTime(nodeAnim, time, transform.rotation)
            getScalingAtTime(nodeAnim, time, transform.scale)
            return transform
        }

        private fun applyNodeAnimationDirectly(nodeAnim: NodeAnimation, time: Float) {
            val node = nodeAnim.node
            node!!.isAnimated = true
            val transform: Transform = getNodeAnimationTransform(nodeAnim, time)
            transform.toMatrix4(node.localTransform)
        }

        private fun applyNodeAnimationBlending(
            nodeAnim: NodeAnimation,
            out: ObjectMap<Node, Transform>,
            pool: Pool<Transform>, alpha: Float,
            time: Float
        ) {
            val node = nodeAnim.node
            node!!.isAnimated = true
            val transform: Transform = getNodeAnimationTransform(nodeAnim, time)

            val t = out.get(node, null)
            if (t != null) {
                if (alpha > 0.999999f) {
                    t.set(transform)
                } else {
                    t.lerp(transform, alpha)
                }
            } else {
                if (alpha > 0.999999f) {
                    out.put(node, pool.obtain()!!.set(transform))
                } else {
                    out.put(
                        node,
                        pool.obtain()!!.set(node.translation, node.rotation, node.scale).lerp(transform, alpha)
                    )
                }
            }
        }

        /**
         * Helper method to apply one animation to either an objectmap for blending or directly to the bones.
         */
        protected fun applyAnimation(
            out: ObjectMap<Node, Transform>?, pool: Pool<Transform>?,
            alpha: Float,
            animation: Animation, time: Float
        ) {
            if (out == null) {
                for (nodeAnim in animation.nodeAnimations) {
                    applyNodeAnimationDirectly(nodeAnim!!, time)
                }
            } else {
                for (node in out.keys()) {
                    node.isAnimated = false
                }
                for (nodeAnim in animation.nodeAnimations) {
                    applyNodeAnimationBlending(nodeAnim!!, out, pool!!, alpha, time)
                }
                for (e in out.entries()) {
                    if (!e.key.isAnimated) {
                        e.key.isAnimated = true
                        e.value!!.lerp(e.key.translation, e.key.rotation, e.key.scale, alpha)
                    }
                }
            }
        }
    }
}
