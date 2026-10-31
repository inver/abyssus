/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document


/** The deliberately sparse initial UI document, without the optional defaults of a bound runtime widget. */
data class UiAssetAdditional(val ui: InitialUiCollection = InitialUiCollection())
data class InitialUiCollection(
    val schemaVersion: Int = 1,
    val entryScreen: String = "main",
    val screens: List<InitialUiScreen> = listOf(InitialUiScreen()),
)
data class InitialUiScreen(val id: String = "main", val name: String = "Main", val root: InitialUiPanel = InitialUiPanel())
data class InitialUiPanel(
    val id: String = "root",
    val type: String = "panel",
    val children: List<InitialUiLabel> = listOf(InitialUiLabel()),
)
data class InitialUiLabel(
    val id: String = "title",
    val type: String = "label",
    val layout: InitialUiLayout = InitialUiLayout(),
    val properties: InitialUiProperties = InitialUiProperties(),
)
data class InitialUiLayout(val anchor: String = "center")
data class InitialUiProperties(val text: String = "Hello")

data class TextureAssetAdditional(val file: String)
