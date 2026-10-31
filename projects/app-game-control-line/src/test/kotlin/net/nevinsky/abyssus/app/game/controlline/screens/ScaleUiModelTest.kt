/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import net.nevinsky.abyssus.app.game.controlline.components.PlaneClass
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.fieldSession
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.PlaneChoice
import net.nevinsky.abyssus.app.game.controlline.flow.ScaleFacts
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleJudge
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleMarks
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** What the screens show of Scale and the vitrine's replica card, without graphics. */
class ScaleUiModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val field = loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) }
        .filter { it.planeClass != PlaneClass.SCALE }

    private val replica = PlaneChoice(99, "Mig-3", PlaneClass.SCALE, 18f, 1.2f, 240f, ScaleFacts("MiG-3", 1940, 10.3f, 8))

    private fun flowOf(planes: List<PlaneChoice>) = GameFlow(
        planes, ScoreTable(temp.root.toPath().resolve("s.json")), PatternScoreTable(temp.root.toPath().resolve("p.json")),
        ScaleScoreTable(temp.root.toPath().resolve("sc.json")),
    ) { Instant.parse("2026-10-04T12:00:00Z") }

    private fun values(model: GameUiModel, session: net.nevinsky.abyssus.app.game.controlline.flight.FlightSession? = null, judge: ScaleJudge? = null) =
        model.snapshot(session, null, 0f, judge).toMap()

    private fun UiSnapshotValues.flag(name: String) = (getValue(name) as UiValue.Bool).value

    private fun UiSnapshotValues.text(name: String) = (getValue(name) as UiValue.Text).value

    private fun UiSnapshotValues.rows(name: String) = (getValue(name) as UiValue.Rows).items.map { r -> r.mapValues { (it.value as UiValue.Text).value } }

    private fun scaleReport() = FlightReport("Mig-3", FlightEnd.LANDED, 61f, FlightResult.Scale(ScaleMarks(400, 300, 200), 5))

    @Test
    fun theMenuOffersScaleOnlyWithAReplica() {
        assertTrue(values(GameUiModel(flowOf(field + replica)) {}).flag("scaleEnabled"))
        assertEquals(false, values(GameUiModel(flowOf(field)) {}).flag("scaleEnabled"))
    }

    @Test
    fun theScaleActionOpensPlaneSelectWithTheReplicaCard() {
        val model = GameUiModel(flowOf(field + replica)) {}
        assertTrue(model.emit("scale"))
        val v = values(model)
        assertEquals("Scale", v.text("modeLabel"))
        assertEquals("Choose a plane  (1 of 1)", v.text("planeCount"))
        assertEquals("Mig-3", v.text("planeName"))
        assertTrue(v.flag("hasScaleFacts"))
        assertEquals("Replica of the MiG-3    First flight 1940    Span 10.3 m    Scale 1:8", v.text("scaleFacts"))
        assertEquals(false, v.flag("watchAiVisible"))
    }

    @Test
    fun aPlaneWithoutReplicaFactsHasNoReplicaLine() {
        val flow = flowOf(field)
        val model = GameUiModel(flow) {}
        flow.freeFlight()
        assertEquals(false, values(model).flag("hasScaleFacts"))
    }

    @Test
    fun aReplicaWithoutAPrototypeSaysItIsUnknown() {
        val bare = replica.copy(scale = null)
        val flow = flowOf(field + bare)
        val model = GameUiModel(flow) {}
        flow.scale()
        val v = values(model)
        assertTrue(v.flag("hasScaleFacts"))
        assertEquals("Scale replica    Prototype unknown", v.text("scaleFacts"))
    }

    @Test
    fun aScaleFlightHasItsOwnHud() {
        val flow = flowOf(field + replica)
        val model = GameUiModel(flow) {}
        flow.scale()
        flow.choosePlane()
        assertEquals(GameLayers(SCREEN_HUD_SCALE), model.layersOf(flow.screen))
        flow.pause()
        assertEquals(GameLayers(SCREEN_HUD_SCALE, SCREEN_PAUSED), model.layersOf(flow.screen))
    }

    @Test
    fun theHudShowsTheLapsAndRealismSoFar() {
        val flow = flowOf(field + replica)
        val model = GameUiModel(flow) {}
        flow.scale()
        flow.choosePlane()
        val judge = ScaleJudge()
        var azimuth = 0f
        var t = 0f
        judge.sample(TrackSample(t, 0f, 5f, 0f, false, true))
        repeat(2) {
            repeat(20) {
                t += 0.5f
                azimuth += 18f
                judge.sample(TrackSample(t, ((azimuth + 180f) % 360f) - 180f, 5f, 0f, false, true))
            }
        }
        fieldSession("Trainer", judge).use { session ->
            val v = values(model, session, judge)
            assertEquals("2", v.text("laps"))
            assertEquals("800", v.text("score"))
        }
    }

    @Test
    fun aScaleGameOverShowsTheMarksNotTheFreeFacts() {
        val flow = flowOf(field + replica)
        val model = GameUiModel(flow) {}
        flow.scale()
        flow.choosePlane()
        flow.flightEnded(scaleReport())
        flow.nameEntered("Ann")
        val v = values(model)
        assertTrue(v.flag("isScale"))
        assertEquals(false, v.flag("isFree"))
        assertEquals(false, v.flag("isPattern"))
        assertEquals(false, v.flag("canDebrief"))
        val facts = v.rows("facts").associate { it.getValue("name") to it.getValue("value") }
        assertEquals("MiG-3", facts["Prototype"])
        assertEquals("900", facts["Realism score"])
        assertEquals("400", facts["Steady speed"])
        assertEquals("300", facts["Level laps"])
        assertEquals("200", facts["Landing"])
        assertEquals("5", facts["Laps"])
    }

    @Test
    fun theScaleTabListsTheScaleFlights() {
        val flow = flowOf(field + replica)
        val model = GameUiModel(flow) {}
        flow.scale()
        flow.choosePlane()
        flow.flightEnded(scaleReport())
        flow.nameEntered("Ann")
        flow.scores()
        val v = values(model)
        assertTrue(v.flag("isScaleTab"))
        assertEquals(false, v.flag("isFreeTab"))
        assertEquals(false, v.flag("isBoardTab"))
        assertEquals("tabSelected", v.text("tabScaleStyle"))
        assertEquals("tab", v.text("tabFreeStyle"))
        val row = v.rows("scaleRows").single()
        assertEquals(listOf("1", "Ann", "Mig-3", "900", "400", "300", "200"),
            listOf("rank", "name", "plane", "score", "speed", "level", "landing").map(row::getValue))
        assertEquals("highlight", row["style"])
        assertEquals(emptyList<Map<String, String>>(), v.rows("freeRows"))
    }

    @Test
    fun theTabScaleActionShowsTheScaleTab() {
        val flow = flowOf(field + replica)
        val model = GameUiModel(flow) {}
        flow.scores()
        assertTrue(model.emit("tabScale"))
        val v = values(model)
        assertTrue(v.flag("isScaleTab"))
        assertEquals("", v.text("damagedMessage"))
    }
}

private typealias UiSnapshotValues = Map<String, UiValue>
