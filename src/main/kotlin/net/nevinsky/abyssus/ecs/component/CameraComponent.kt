package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.PerspectiveCamera

/** [camera]'s `direction` is the file's `viewPointPosition`. */
class CameraComponent : Component {
    val camera = PerspectiveCamera().also {
        it.near = CAMERA_NEAR
        it.far = CAMERA_FAR
        it.fieldOfView = CAMERA_FOV
    }
}