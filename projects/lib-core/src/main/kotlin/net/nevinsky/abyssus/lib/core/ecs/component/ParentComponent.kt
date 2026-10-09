package net.nevinsky.abyssus.lib.gdx.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.NO_ENTITY

class ParentComponent(var parentEntityId: Int = NO_ENTITY) : Component
