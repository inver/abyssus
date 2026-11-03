/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import net.nevinsky.abyssus.lib.core.util.containsText

/** Questions about the names a document holds, answered from its text. */
class DocumentQueries(
    private val scenes: SceneParser,
    private val json: JsonProcessor,
) {
    /**
     * The asset and sky names the scene [text] names (a render component's `assetName` and `shaderKey`, a foliage
     * component's `assetName`, a path arrangement's `asset.name`, the scene's `skyboxName`), not counting the entity [ignoring]; null when [text] is not a
     * readable scene.
     */
    fun sceneNamedAssets(text: String, ignoring: String? = null): Set<String>? = runCatchingKeepingCancellation {
        val scene = scenes.parse(text)
        val skipped = ignoring?.toLongOrNull()
        buildSet {
            scene.ecs.filterKeys { it != skipped }.values.flatMap { it.components }.forEach { component ->
                when (component) {
                    is RenderComponent -> {
                        component.assetName.takeIf { it.isNotBlank() }?.let(::add)
                        component.shaderKey?.takeIf { it.isNotBlank() }?.let(::add)
                    }

                    is FoliageComponent -> component.assetName?.takeIf { it.isNotBlank() }?.let(::add)
                    is PathArrangementComponent -> component.asset.name.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            scene.skyboxName?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.getOrNull()

    /** Whether the `additional` block of the asset `meta.json` [text] holds the string [uuid]; null when [text] is not a JSON object. */
    fun metaReferencesUuid(text: String, uuid: String): Boolean? = runCatchingKeepingCancellation {
        json.readObject(text).get("additional")?.containsText(uuid) ?: false
    }.getOrNull()
}
