package net.nevinsky.abyssus.lib.core.util

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent

class EcsUtils {
    companion object {
        @JvmStatic
        val NO_ENTITY = -1

        @JvmStatic
        val LIGHT_INTENSITY = 1f

        /** Reach of a point or spot light that does not name one. */
        @JvmStatic
        val LIGHT_RANGE = 100f

        @JvmStatic
        val LIGHT_CONE_ANGLE = 45f

        @JvmStatic
        val LIGHT_EDGE_SOFTNESS = 0.2f

        /** libGDX `PerspectiveCamera` defaults, for the fields a camera leaves out. */
        @JvmStatic
        val CAMERA_NEAR = 1f

        @JvmStatic
        val CAMERA_FAR = 100f

        @JvmStatic
        val CAMERA_FOV = 67f

        @JvmStatic
        fun assetName(entity: Entity, type: MetaType): String? {
            val renderable = entity.getComponent(RenderComponent::class.java) ?: return null
            return renderable.assetName.takeIf { renderable.type == type }
        }
    }
}