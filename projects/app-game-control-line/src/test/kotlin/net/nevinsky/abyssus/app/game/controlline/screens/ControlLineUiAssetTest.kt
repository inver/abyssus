/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import net.nevinsky.abyssus.lib.core.assets.ui.UiResourcePreparer
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourceResolver
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

    @Test
    fun everyResourceLoadsAndEveryStyledFontSizeIsItsFontsLineHeight() {
        val folder = file.parentFile
        val prepared = UiResourcePreparer(UiResourceResolver(folder)).prepare(collection)
        assertEquals(emptyList<Any>(), prepared.issues)
        // a style's fontSize is measured in line heights: a size that is not a font's own would scale a bitmap font
        val native = json.readObject(File(folder, "fonts/index.json").readText())
        for (style in collection.styles) {
            val font = style.values.get("font")?.textValue() ?: continue
            val size = style.values.get("fontSize")?.asInt()
            assertEquals("${style.name}: fontSize of $font", native.get(font).asInt(), size)
        }
    }

    @Test
    fun everyWidgetStyleIsDefined() {
        val known = collection.styles.map { it.name }.toSet()
        val missing = mutableSetOf<String>()
        collection.screens.forEach { screen -> walk(screen.root) { w -> w.style?.let { if (it !in known) missing += it } } }
        assertTrue("undefined styles $missing", missing.isEmpty())
    }
}
