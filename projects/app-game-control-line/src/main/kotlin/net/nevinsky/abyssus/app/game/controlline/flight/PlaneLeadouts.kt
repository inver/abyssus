/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.Dummies
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent

/** The dummy a plane's up line is attached to. */
const val LEADOUT_UP = "leadoutUp"

/** The dummy a plane's down line is attached to. */
const val LEADOUT_DOWN = "leadoutDown"

/**
 * Where a plane's lines enter it (OpenSpec `control-line-flight`, "Leadouts are the plane's dummies"): the points [up]
 * and [down] in the plane's frame (nose +Z, up +Y, inboard wing +X).
 */
class PlaneLeadouts(val up: Vector3, val down: Vector3)

/**
 * The leadouts of [plane]: its child dummies `leadoutUp` and `leadoutDown` among [entities], in the plane's frame.
 * Null,
 * with one line to [warn] naming the plane and the missing dummy, when it lacks either.
 */
fun leadoutsOf(entities: Iterable<Entity>, plane: Entity, dummies: Dummies, warn: (String) -> Unit): PlaneLeadouts? {
    val id = plane.getComponent(HeaderComponent::class.java)?.id ?: return null
    val points = listOf(LEADOUT_UP, LEADOUT_DOWN).map { name ->
        val dummy = dummies.find(entities, id, name)
        if (dummy == null) {
            val planeName = plane.getComponent(NameComponent::class.java)?.name ?: "Plane $id"
            warn("plane $planeName (entity $id) has no $name dummy, so it is not offered")
            return null
        }
        dummies.inParent(dummy, plane, Matrix4()).getTranslation(Vector3())
    }
    return PlaneLeadouts(points[0], points[1])
}
