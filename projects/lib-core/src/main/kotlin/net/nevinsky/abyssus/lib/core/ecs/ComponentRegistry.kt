package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent

class ComponentRegistry {

    private val componentMap = HashMap<String, Class<out Component>>()

    /** Every registered class by its short name, which the scene files use for the built-in components. */
    private val shortNames = HashMap<String, Class<out Component>>()

    /** The name each registered class is written under in a scene file: the short class name unless registered otherwise. */
    private val writtenNames = LinkedHashMap<Class<out Component>, String>()

    init {
        componentMap[CameraComponent::class.qualifiedName!!] = CameraComponent::class.java
        componentMap[RenderComponent::class.qualifiedName!!] = RenderComponent::class.java
        componentMap[LightComponent::class.qualifiedName!!] = LightComponent::class.java
        componentMap[NameComponent::class.qualifiedName!!] = NameComponent::class.java
        componentMap[ParentComponent::class.qualifiedName!!] = ParentComponent::class.java
        componentMap[Point2PointPositionComponent::class.qualifiedName!!] = Point2PointPositionComponent::class.java
        componentMap[PositionComponent::class.qualifiedName!!] = PositionComponent::class.java
        componentMap[TypeComponent::class.qualifiedName!!] = TypeComponent::class.java
        componentMap.values.forEach {
            shortNames.putIfAbsent(it.simpleName, it)
            writtenNames[it] = it.simpleName
        }
    }

    /** The class [name] names, fully qualified or by its short class name; null for a name nobody registered. */
    fun get(name: String): Class<out Component>? {
        return componentMap[name] ?: shortNames[name]
    }

    fun register(name: String, clazz: Class<out Component>) {
        val existing = get(name)
        if (existing != null) {
            throw IllegalStateException(
                "Component with name '$name' already exists! You should register component with override"
            )
        }
        componentMap[name] = clazz
        shortNames.putIfAbsent(clazz.simpleName, clazz)
        writtenNames.putIfAbsent(clazz, name)
    }

    /** Every registered component class: the built-in ones first, then the others in registration order. */
    val types: List<Class<out Component>> get() = writtenNames.keys.toList()

    /** The name [type] is written under in a scene file; its short class name for a class nobody registered. */
    fun nameOf(type: Class<out Component>): String = writtenNames[type] ?: type.simpleName

    fun registerAll(map: Map<String, Class<out Component>>) {
        map.forEach { (key, value) -> register(key, value) }
    }
}