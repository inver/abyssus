package net.nevinsky.abyssus.lib.runtime.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.runtime.ecs.EcsUtils.Companion.NO_ENTITY

class ParentComponent(var parentEntityId: Int = NO_ENTITY) : Component
