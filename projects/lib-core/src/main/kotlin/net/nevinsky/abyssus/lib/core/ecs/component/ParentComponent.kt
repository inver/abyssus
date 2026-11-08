package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY

/**
 * Makes this entity a child of [parentEntityId]: its `PositionComponent` is local to that entity, or, when [node] is
 * set, to the rest transform of that node of the parent's model. [node] is omitted from the file when null.
 */
class ParentComponent(var parentEntityId: Int = NO_ENTITY, var node: String? = null) : Component
