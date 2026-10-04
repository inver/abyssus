package net.nevinsky.abyssus.runtime.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.runtime.ecs.NO_ENTITY

class ParentComponent(var parentEntityId: Int = NO_ENTITY) : Component
