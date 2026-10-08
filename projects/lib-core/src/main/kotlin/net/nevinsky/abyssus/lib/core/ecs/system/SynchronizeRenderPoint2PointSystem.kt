package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import net.nevinsky.abyssus.lib.core.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.render.RenderComponent
import net.nevinsky.abyssus.lib.core.scene.SceneEntityIds

class SynchronizeRenderPoint2PointSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(Point2PointPositionComponent::class.java).get(), priority) {
    private val p2pMapper = ComponentMapper.getFor(Point2PointPositionComponent::class.java)
    private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val comp = p2pMapper[entity]
        val e1 = ids.positionOf(comp.entity1Id, PositionComponent::class.java) ?: return
        val e2 = ids.positionOf(comp.entity2Id, PositionComponent::class.java) ?: return
        comp.point1.set(e1.localPosition)
        comp.point2.set(e2.localPosition)
        renderMapper[entity]?.renderable?.set2PointPosition(e1.localPosition, e2.localPosition)
    }
}