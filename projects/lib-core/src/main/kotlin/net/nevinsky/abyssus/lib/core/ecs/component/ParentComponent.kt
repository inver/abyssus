package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY

class ParentComponent(var parentEntityId: Int = NO_ENTITY) : Component
