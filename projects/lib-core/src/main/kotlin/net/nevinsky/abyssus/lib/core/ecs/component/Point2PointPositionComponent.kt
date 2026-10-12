package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonIgnore
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY

class Point2PointPositionComponent(var entity1Id: Int = NO_ENTITY, var entity2Id: Int = NO_ENTITY) : Component {
    /** Derived from the two entities' positions on every frame; never part of the file. */
    @get:JsonIgnore
    val point1 = Vector3()

    @get:JsonIgnore
    val point2 = Vector3()
}