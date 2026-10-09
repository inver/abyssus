package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component

class TypeComponent(var type: Type? = null) : Component {
    enum class Type { GROUP, LIGHT_DIRECTIONAL, LIGHT_POINT, LIGHT_SPOT, OBJECT, TERRAIN, CAMERA, HANDLE }
}