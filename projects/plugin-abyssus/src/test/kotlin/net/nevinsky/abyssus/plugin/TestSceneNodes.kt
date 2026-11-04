/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.DtoRow
import net.nevinsky.abyssus.plugin.projectView.EntityNode
import net.nevinsky.abyssus.plugin.projectView.entityEye

/** The Abyssus view node of entity [id] in the scene [text] of [file], as the scene tree builds it. */
internal fun entityNode(project: Project, file: VirtualFile, text: String, id: Long): DtoEntryNode {
    val scene = parseScene(text)
    val row = DtoRow(id.toString(), EntityNode(scene, id), enabled = entityEye(scene.entity(id)!!))
    return DtoEntryNode(project, file.path, row, file, listOf("ecs"))
}

/** [text] re-printed by the plugin's `JsonProcessor`, the layout every scene write produces. */
internal fun prettyScene(text: String): String {
    val documents = com.intellij.openapi.components.service<AbyssusPluginCtx>().documents
    return documents.json.pretty(documents.parsing.parse(text))
}

/** The scene [text] as the plugin parses it. */
internal fun parsedScene(text: String) =
    com.intellij.openapi.components.service<AbyssusPluginCtx>().documents.parsing.parse(text)

/** [scene] printed by the plugin's `JsonProcessor`. */
internal fun printScene(scene: net.nevinsky.abyssus.lib.core.dto.SceneDto): String =
    com.intellij.openapi.components.service<AbyssusPluginCtx>().documents.json.pretty(scene)

/** Parses the scene in [doc], applies [edit] to it and writes it back pretty-printed, as one command. */
internal fun editSceneText(
    project: Project,
    doc: com.intellij.openapi.editor.Document,
    edit: (net.nevinsky.abyssus.lib.core.dto.SceneDto) -> Unit,
) {
    val scene = parsedScene(doc.text)
    edit(scene)
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { doc.setText(printScene(scene)) }
}

/** [scene] printed by a `JsonProcessor` that knows the components of [registry]. */
internal fun printSceneWith(
    registry: net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry,
    scene: net.nevinsky.abyssus.lib.core.dto.SceneDto,
): String =
    net.nevinsky.abyssus.lib.core.io
        .JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
        .pretty(scene)

/** The asset `meta.json` [text] in the layout every edit prints (pretty, through the typed `AssetMeta`). */
internal fun canonicalMeta(text: String): String {
    val json = com.intellij.openapi.components.service<AbyssusPluginCtx>().json
    val parser = net.nevinsky.abyssus.lib.core.assets.AssetMetaParser(json)
    return parser.write(parser.read("m", text), pretty = true)
}
