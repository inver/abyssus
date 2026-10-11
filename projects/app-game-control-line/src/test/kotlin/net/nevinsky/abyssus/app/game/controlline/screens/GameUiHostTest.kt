/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What the game publishes to, and accepts from, the authored screens, without graphics. */
class GameUiHostTest {
    @get:Rule
    val temp = TemporaryFolder()

    private var quit = 0
    private fun flow() = GameFlow(emptyList(), ScoreTable(temp.root.toPath().resolve("s.json")), PatternScoreTable(temp.root.toPath().resolve("p.json")))
    private fun model(flow: GameFlow = flow()) = GameUiModel(flow) { quit++ }

    @Test fun everySnapshotPublishesExactlyTheContract() {
        val snapshot = model().snapshot(null, null, 0f)
        val published = snapshot.toMap().mapValues { (_, v) ->
            when (v) {
                is UiValue.Rows -> "list:" + (v.items.firstOrNull()?.keys?.joinToString(",").orEmpty())
                else -> v.type.json
            }
        }
        // a list with no rows names no fields; compare names and kinds
        assertEquals(PUBLISHED_BINDINGS.keys, published.keys)
        for ((name, expected) in PUBLISHED_BINDINGS) assertEquals(name, expected.substringBefore(':'), published.getValue(name).substringBefore(':'))
    }

    @Test fun everyActionOfTheAssetIsHandledAndAnUnknownOneIsNot() {
        val model = model()
        for (action in ACTIONS) assertTrue(action, model.emit(if (action == "openRow") "openRow#0" else action))
        assertFalse(model.emit("nothing"))
        assertFalse(model.emit("openRow#x"))
        assertEquals("quit closes the game", 1, quit)
    }

    @Test fun onlyAShortPlayerNameCanBeWritten() {
        val model = model()
        assertEquals(UiWriteResult.Accepted(UiValue.Text("Ann")), model.write(UiWrite("playerName", UiValue.Text("Ann"), "ne-field")))
        assertEquals("Ann", model.playerName)
        assertTrue(model.write(UiWrite("playerName", UiValue.Text("A".repeat(13)), "ne-field")) is UiWriteResult.Rejected)
        assertTrue(model.write(UiWrite("score", UiValue.Text("1"), "x")) is UiWriteResult.Rejected)
        assertEquals("Ann", model.playerName)
    }

    @Test fun layersFollowTheScreen() {
        val model = model()
        assertEquals(GameLayers(SCREEN_MAIN_MENU), model.layersOf(Screen.MainMenu))
        assertEquals(GameLayers(SCREEN_ASSIST), model.layersOf(Screen.AssistSelect))
    }

    @Test fun theGameKeysAreTheModelsAndTheRestIsThePresenters() {
        val model = model()
        assertTrue(model.key(Input.Keys.ESCAPE))
        assertFalse("Up and Down move focus on menus", model.key(Input.Keys.UP))
        assertFalse(model.key(Input.Keys.LEFT))
        assertFalse(model.key(Input.Keys.ENTER))
    }
}
