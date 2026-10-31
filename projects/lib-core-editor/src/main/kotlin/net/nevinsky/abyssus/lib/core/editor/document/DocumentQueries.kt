/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.util.containsText
import net.nevinsky.abyssus.lib.core.util.textAt
import net.nevinsky.abyssus.lib.core.util.textValuesOf

/**
 * The asset and sky names the scene [text] names (`assetName`, `shaderKey`, `skyboxName`), not counting the entity
 * [ignoring]; null when [text] is not a supported native scene.
 */
fun sceneNamedAssets(text: String, ignoring: String? = null): Set<String>? = runCatchingKeepingCancellation {
    val root = SceneJson().parse(text)
    root.also { net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat().requireSupported(it, DocumentKind.SCENE) }
    if (ignoring != null) removeEntityNode(root, ignoring)
    root.textValuesOf("assetName", "shaderKey") + listOfNotNull(root.textAt("skyboxName"))
}.getOrNull()

/** Whether the `additional` block of the asset `meta.json` [text] holds the string [uuid]; null when [text] is not a supported asset document. */
fun metaReferencesUuid(text: String, uuid: String): Boolean? = runCatchingKeepingCancellation {
    val root = SceneJson().parse(text)
    net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat().requireSupported(root, DocumentKind.ASSET)
    root.get("additional")?.containsText(uuid) ?: false
}.getOrNull()
