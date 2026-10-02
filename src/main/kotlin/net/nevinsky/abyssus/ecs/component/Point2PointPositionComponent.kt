package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.ecs.NO_ENTITY

class Point2PointPositionComponent(var entity1Id: Int = NO_ENTITY, var entity2Id: Int = NO_ENTITY) : Component {
    val point1 = Vector3()
    val point2 = Vector3()
}