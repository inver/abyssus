package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.ecs.NO_ENTITY

class ParentComponent(var parentEntityId: Int = NO_ENTITY) : Component
