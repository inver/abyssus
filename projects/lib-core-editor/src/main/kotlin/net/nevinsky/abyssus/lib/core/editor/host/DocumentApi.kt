/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.host

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyCount
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyIds
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementOwnerOf
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.ComponentKind
import net.nevinsky.abyssus.lib.core.editor.components.FieldValue
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldDescriptions
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldState
import net.nevinsky.abyssus.lib.core.editor.meta.AssetMetaEditor
import net.nevinsky.abyssus.lib.core.editor.meta.AssetReferenceChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntityAssetChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.assetFieldStates
import net.nevinsky.abyssus.lib.core.editor.meta.readEntitySections
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.lib.core.editor.panel.PanelModel
import net.nevinsky.abyssus.lib.core.editor.panel.SceneSetting
import net.nevinsky.abyssus.lib.core.editor.water.WaterRead
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import java.io.File

/*
 * The editing API for hosts that hold a [Document] and never name Jackson's tree types: each function here is the
 * [Document] form of one that takes the tree.
 */

/** The entity row [id] of an `ecs` row [node], without typed component access. */
fun sceneEntityView(id: String, node: Document): EntityView = sceneEntityView(id, node.tree)

/** The scene read view over the `ecs` object [ecs]. */
fun sceneDocumentFromEcs(ecs: Document): SceneDocument = sceneDocumentFromEcs(ecs.tree)

/** Whether [node] is the scene's `ecs` entry (see the tree rows). */
fun isSceneEcsEntry(name: String, parents: List<String>, node: Document?): Boolean = isSceneEcsEntry(name, parents, node?.tree)

/** Whether [node] is an entity row. */
fun isSceneEntityEntry(name: String, parents: List<String>, node: Document?): Boolean = isSceneEntityEntry(name, parents, node?.tree)

/** The `type` an entity row [entity] holds, as written. */
fun entityTypeOf(entity: Document?): String? = net.nevinsky.abyssus.lib.core.format.entityTypeOf(entity?.tree)

/** The number of copies a path arrangement [entity] row owns. */
fun arrangementCopyCount(entity: Document): Int = arrangementCopyCount(entity.tree)

/** The ids of the linked copies of every path arrangement under the `ecs` object [ecs]. */
fun arrangementCopyIds(ecs: Document): Set<String> = arrangementCopyIds(ecs.tree)

/** The arrangement entity whose copy [entityId] is, else null. */
fun arrangementOwnerOf(ecs: Document, entityId: String): String? = arrangementOwnerOf(ecs.tree, entityId)

/** The kinds [entityId] of the scene [root] lacks. */
fun ComponentEditor.missingKinds(root: Document, entityId: String): List<ComponentKind<*>> = missingKinds(root.tree, entityId)

/** The fields of the component [kindName] of [entityId]. */
fun ComponentEditor.read(root: Document, entityId: String, kindName: String): List<FieldValue>? = read(root.tree, entityId, kindName)

/** The entity's sections as the Properties panel shows them. */
fun readEntitySections(
    root: Document, entityId: String, kind: String?, editor: ComponentEditor, assets: EntityAssetChoices, messages: EditorMessages,
): EntitySections = readEntitySections(root.tree, entityId, kind, editor, assets, messages)

/** The editable asset field states of the asset `meta.json` [json]. */
fun assetFieldStates(
    folder: File, type: MetaType, json: Document, descriptions: AssetFieldDescriptions, editor: AssetMetaEditor, choices: AssetReferenceChoices,
): List<AssetFieldState> = assetFieldStates(folder, type, json.tree, descriptions, editor, choices)

fun PanelBuilders.project(abss: Document, fileName: String, sceneNames: List<String>, editable: Set<String> = emptySet()): PanelModel =
    project(abss.tree, fileName, sceneNames, editable)

fun PanelBuilders.scene(scene: Document, projectFile: String, scenePath: String): PanelModel = scene(scene.tree, projectFile, scenePath)

fun PanelBuilders.setting(scene: Document, setting: SceneSetting, projectFile: String): PanelModel = setting(scene.tree, setting, projectFile)

fun PanelBuilders.ecs(scene: Document, projectFile: String): PanelModel = ecs(scene.tree, projectFile)

fun PanelBuilders.entity(scene: Document, entityId: String, read: EntitySections.Read, projectFile: String): PanelModel =
    entity(scene.tree, entityId, read, projectFile)

fun PanelBuilders.component(scene: Document, entityId: String, read: EntitySections.Read, projectFile: String): PanelModel =
    component(scene.tree, entityId, read, projectFile)

fun PanelBuilders.asset(
    name: String, meta: Document, used: Boolean, editable: Set<String>, projectFile: String, hasEditors: Boolean = editable.isNotEmpty(),
): PanelModel = asset(name, meta.tree, used, editable, projectFile, hasEditors)

/** The ray settings the scene document [root] saves. */
fun SceneRaySettingsCodec.read(root: Document): SceneRaySettingsState = read(root.tree)

/** The Foliage settings of an `additional` block. */
fun FoliageSettingsBinding.read(node: Document): FoliageMeta = read(node.tree)

/** Admits the UI document [meta]. */
fun UiCollectionDecoder.decode(meta: Document): UiAdmission = decode(meta.tree)

/** The surface entry [id] under the scene document [root]'s `abyssus` block, or null. */
fun waterSurfaceNode(root: Document, id: String): Document? = root.at("abyssus", "waterSurfaces", id)

/** The surface [id] read from [node]. */
fun WaterCodec.surface(id: String, node: Document) = surface(id, node.tree)

/** The editable texts of the water surface [surface]. */
fun net.nevinsky.abyssus.lib.core.editor.water.WaterText.texts(surface: Document) = texts(surface.tree)

/** The scene panel model of the scene document [root]. */
fun scenePanelModel(root: Document, inProject: Boolean) = net.nevinsky.abyssus.lib.core.editor.scene.scenePanelModel(root.tree, inProject)

/** The property rows of the asset `meta.json` document [meta]. */
fun metaRowsOf(meta: Document, messages: EditorMessages) = net.nevinsky.abyssus.lib.core.editor.meta.metaRowsOf(meta.tree, messages)

/** The view parameters of [scene], seen through [camera], with the editor-only settings read from [root]. */
fun renderParamsOf(scene: net.nevinsky.abyssus.lib.core.dto.SceneDto, camera: net.nevinsky.abyssus.lib.core.editor.scene.CameraParams, projectDir: File?, root: Document?) =
    net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf(scene, camera, projectDir, root?.tree)

/** The water surfaces under the scene's `abyssus` block [abyssus]. */
fun WaterCodec.read(abyssus: Document?): WaterRead = read(abyssus?.tree)

/** Edits ray setting [field] of the scene tree [root] when it still holds [expected]. */
fun SceneRaySettingsCodec.edit(root: com.fasterxml.jackson.databind.JsonNode, field: SceneRayField, expected: Document?, text: String): RayDataEdit =
    edit(root as com.fasterxml.jackson.databind.node.ObjectNode, field, expected?.tree, text)

/** Edits the optical override [field] of material [id] in the render component object [render] when it still holds [expected]. */
fun RayMaterialOverrides.edit(
    render: com.fasterxml.jackson.databind.node.ObjectNode, id: String, field: RayOpticalField, expected: Document?, text: String,
    materials: List<RayMaterialIdentity>,
): RayDataEdit = edit(render, id, field, expected?.tree, text, materials)

/** One row of the document tree: [name], its [node] and the JSON keys the tree skips between its parent and it. */
class DocumentRow(val name: String, val node: Document, val via: List<String> = emptyList())

/** The rows under an `ecs` object: its entities. */
fun sceneEcsRows(ecs: Document): List<DocumentRow> = sceneEcsRows(ecs.tree).map { DocumentRow(it.name, Document(it.node), it.via) }

/** The rows under an entity: its components. */
fun sceneComponentRows(entity: Document): List<DocumentRow> =
    sceneComponentRows(entity.tree).map { DocumentRow(it.name, Document(it.node), it.via) }

/** How many entities the `ecs` object [ecs] holds. */
fun entityCountOf(ecs: Document): Int = sceneDocumentFromEcs(ecs.tree).entitiesNode()?.size() ?: 0

/** How many components the entity row [entity] holds. */
fun componentCountOf(entity: Document): Int = EntityView("", entity.tree, null).components?.size() ?: 0

/** False when the entity row [entity] has the disabled bit in its `flags`. */
fun isEntityEnabled(entity: Document): Boolean = net.nevinsky.abyssus.lib.core.format.isEntityEnabled(entity.tree)

/** False when the entity row [entity] lists the component [key] as disabled. */
fun isComponentEnabled(entity: Document, key: String): Boolean = net.nevinsky.abyssus.lib.core.format.isComponentEnabled(entity.tree, key)

/** True when the entity row [entity] can be switched: anything that is not a `GROUP`. */
fun net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction.isTogglable(entity: Document): Boolean = isTogglable(entity.tree)

/** Whether the entity row [entity] carries a component called [name] (short or fully qualified). */
fun hasComponent(entity: Document, name: String): Boolean = entity.hasAt("components", name)

/** Reads the Foliage settings from the `additional` block [node] (null: none saved). */
fun net.nevinsky.abyssus.lib.core.editor.foliage.FoliageSettingsReader.read(node: Document?, modelAssets: Set<String>) =
    read(node?.tree, modelAssets)

/** The clipboard text for entity [entityId] of the scene [root]. */
fun net.nevinsky.abyssus.lib.core.editor.components.EntityClipboard.copy(root: Document, entityId: String) = copy(root.tree, entityId)

/** Reads the component [type] from its JSON [text] (a raw entry the scene carried). */
fun <C : com.badlogic.ashley.core.Component> net.nevinsky.abyssus.lib.core.editor.components.ComponentReader.readText(type: Class<C>, text: String): C =
    read(type, SceneJson().parse(text))

/** [text] formatted the way the editor writes native documents, or null when it is not a supported native [kind]. */
fun prettyIfSupported(text: String, kind: DocumentKind): String? = runCatchingKeepingCancellation {
    val root = SceneJson().parse(text)
    if (net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat().validate(root, kind) != null) null else SceneJson().pretty(root)
}.getOrNull()

/** The `type` of the asset `meta.json` [text] and the file it names under `additional.file`, or null when unreadable. */
class AssetFileRef(val type: String?, val file: String?)

fun assetFileRef(text: String): AssetFileRef? = runCatchingKeepingCancellation {
    val doc = parseDocument(text)
    AssetFileRef(doc.textAt("type"), doc.textAt("additional", "file"))
}.getOrNull()
