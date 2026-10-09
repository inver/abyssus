package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.ecs.component.render.RenderComponent

class ComponentRegistry {

    private val componentMap = HashMap<String, Class<out Component>>()

    init {
        componentMap[CameraComponent::class.qualifiedName!!] = CameraComponent::class.java
        componentMap[RenderComponent::class.qualifiedName!!] = RenderComponent::class.java
        componentMap[LightComponent::class.qualifiedName!!] = LightComponent::class.java
        componentMap[NameComponent::class.qualifiedName!!] = NameComponent::class.java
        componentMap[ParentComponent::class.qualifiedName!!] = ParentComponent::class.java
        componentMap[Point2PointPositionComponent::class.qualifiedName!!] = Point2PointPositionComponent::class.java
        componentMap[PositionComponent::class.qualifiedName!!] = PositionComponent::class.java
        componentMap[TypeComponent::class.qualifiedName!!] = TypeComponent::class.java
    }

    fun get(name: String): Class<out Component>? {
        return componentMap[name]
    }

    fun register(name: String, clazz: Class<out Component>) {
        val existing = get(name)
        if (existing != null) {
            throw IllegalStateException(
                "Component with name '$name' already exists! You should register component with override"
            )
        }
        componentMap[name] = clazz
    }

    fun registerAll(map: Map<String, Class<out Component>>) {
        map.forEach { (key, value) -> register(key, value) }
    }
}