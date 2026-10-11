/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/** The bundled `screens` UI asset is admitted as it is and matches the contract [GameUiHost] publishes against. */
class ControlLineUiAssetTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val file = File("project/ControlLine/assets/$SCREENS_ASSET/meta.json")

    private val collection: UiCollection by lazy {
        val result = UiCollectionDecoder().decode(json.readObject(file.readText()))
        check(result is UiAdmission.Admitted) { "refused: ${(result as UiAdmission.Refused).issues}" }
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        result.collection
    }

    private fun walk(widget: UiWidget, visit: (UiWidget) -> Unit) {
        visit(widget)
        widget.children.forEach { walk(it, visit) }
        widget.template?.let { walk(it, visit) }
        widget.empty?.let { walk(it, visit) }
    }

    @Test
    fun everyScreenOfTheContractExists() {
        assertEquals(SCREEN_IDS.toSet(), collection.screens.map { it.id }.toSet())
    }

    @Test
    fun declaredBindingsAreExactlyThePublishedOnes() {
        val declared = collection.bindings.associate { b ->
            b.name to if (b.fields.isEmpty()) b.type.json else "list:" + b.fields.joinToString(",") { it.name }
        }
        assertEquals(PUBLISHED_BINDINGS, declared)
    }

    @Test
    fun everyEmittedActionIsKnown() {
        val names = mutableSetOf<String>()
        collection.screens.forEach { screen ->
            walk(screen.root) { w -> w.actions.values.forEach { a -> a.name?.let(names::add) } }
        }
        assertTrue(names.toString(), ACTIONS.containsAll(names))
        assertTrue(names.containsAll(ACTIONS))
    }
}
