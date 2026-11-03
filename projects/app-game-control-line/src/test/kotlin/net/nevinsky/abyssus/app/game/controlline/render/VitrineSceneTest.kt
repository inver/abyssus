/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.components.VitrineTableComponent
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plane select's vitrine on the bundled field: every plane on a table of its own, its lowest point on the table-top. */
class VitrineSceneTest {
    private val assets = PhysicsAssets(bundledProject().toFile())

    private fun parked() = FieldScene(loadField()).also { it.standOnTables(assets::modelPoints) }

    private val FieldScene.tables
        get() = engine.ids.ids.sorted().map { checkNotNull(engine.ids[it]) }
            .filter { it.getComponent(VitrineTableComponent::class.java) != null }

    @Test
    fun everyBundledPlaneRestsOnTheTopOfItsOwnTable() {
        val scene = parked()
        val tables = scene.tables
        assertEquals(4, tables.size)
        assertEquals(listOf("Mig-3", "Racer", "Stunter", "Trainer"), scene.planes.map { it.name })
        scene.planes.forEachIndexed { i, choice ->
            val plane = checkNotNull(scene.entity(choice.entityId))
            val at = plane.getComponent(PositionComponent::class.java)
            val table = tables[i]
            val tableAt = table.getComponent(PositionComponent::class.java).localPosition
            val top = tableAt.y + table.getComponent(VitrineTableComponent::class.java).topHeight
            val points = checkNotNull(assets.modelPoints(scene.models.single { it.first === plane }.second))
            val lowest = points.minOf { at.localRotation.transform(Vector3(it)).y + at.localPosition.y }
            assertEquals("${choice.name} lowest point vs table-top", top, lowest, 0.001f)
            assertEquals("${choice.name} x", tableAt.x, at.localPosition.x, 1e-4f)
            assertEquals("${choice.name} z", tableAt.z, at.localPosition.z, 1e-4f)
        }
    }

    @Test
    fun theTablesAreOutsideTheFlightCircleOfEveryPlane() {
        val scene = parked()
        val pilot = checkNotNull(scene.pilot).getComponent(PositionComponent::class.java).localPosition
        val widest = scene.planes.maxOf { it.lineLength }
        for (table in scene.tables) {
            val at = table.getComponent(PositionComponent::class.java).localPosition
            val nearest = Math.hypot((at.x - pilot.x).toDouble(), (at.z - pilot.z).toDouble()) - 1.2
            assertTrue("a display table is ${nearest} m from the pilot, inside the $widest m circle", nearest > widest + 2f)
        }
    }

    @Test
    fun aFlightsOwnLoadKeepsThePlanesWhereTheSceneHasThem() {
        val authored = FieldScene(loadField())
        val parked = parked()
        val choice = authored.planes.first()
        val before = authored.position(checkNotNull(authored.entity(choice.entityId))).localPosition
        val after = parked.position(checkNotNull(parked.entity(choice.entityId))).localPosition
        assertTrue("the parked plane moved onto its table", before.dst(after) > 10f)
        // a second, fresh load is unaffected by the first one's rearranging
        assertEquals(before, FieldScene(loadField()).let { it.position(checkNotNull(it.entity(choice.entityId))).localPosition })
    }
}
