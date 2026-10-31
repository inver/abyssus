/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.panel

import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_NEAR
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.meta.ComponentSection
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.lib.core.editor.components.FieldKind
import net.nevinsky.abyssus.lib.core.editor.components.FieldValue
import net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A scene setting that has its own section and, in the tree, its own row. */
enum class SceneSetting(val key: String, val toggleKey: String, val titleKey: String, val iconKey: String) {
    AMBIENT("ambientLight", "ambientLightEnabled", "panelSectionAmbient", "ambient"),
    FOG("fog", "fogEnabled", "panelSectionFog", "fog"),
    SKYBOX("skyboxName", "skyboxEnabled", "panelSectionSkybox", "sky"),
}

/** The three folders the project row lists, plus the type groups under Assets. */
enum class FolderKind(val iconKey: String) {
    SCENES("fScenes"), UI("fUi"), ASSETS("fAssets"), GROUP("folder")
}

private val MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"))

/**
 * Builds the [PanelModel] of every kind of selection from the documents it shows, with no Swing: one pure function per
 * kind, so the content of each layout (section titles, row labels, order, footer) is unit-tested without a UI. [isGame]
 * tells a component key registered by the game project from a built-in one.
 */
class PanelBuilders(
    private val messages: EditorMessages,
    private val isGame: (String) -> Boolean = { false },
) {
    private val toggles = SetEnabledAction()

    // ---- project, folders, scenes ----

    /**
     * The project file [fileName]: the project settings and the main camera. Only the keys in [editable] carry a
     * [FieldRef] (the plugin writes them); every other value is shown read only.
     */
    fun project(
        abss: JsonNode,
        fileName: String,
        sceneNames: List<String>,
        editable: Set<String> = emptySet()
    ): PanelModel {
        fun ref(key: String) = if (key in editable) FieldRef("project", key) else null
        val camera = abss.get("mainCamera")
        val settings = Section(
            "project.main", messages.message("panelSectionProject"), "project",
            listOf(
                TextRow("name", abss.text("name"), dim = !abss.has("name"), field = ref("name")),
                ChoiceRow(
                    "activeSceneName", abss.text("activeSceneName"), sceneNames, !abss.has("activeSceneName"),
                    ref("activeSceneName"),
                ),
                CheckRow(
                    "physicsEnabled",
                    abss.get("physicsEnabled")?.asBoolean() ?: false,
                    messages.message("panelEnabled"),
                    !abss.has("physicsEnabled"),
                    ref("physicsEnabled"),
                ),
                CustomRow("antialiasing"),
                CustomRow("showFps"),
                ReadonlyRow("uiAssetNames", abss.get("uiAssetNames")?.toString() ?: "[]", !abss.has("uiAssetNames")),
            ),
        )
        val main = Section(
            "project.camera", messages.message("panelSectionMainCamera"), "render",
            listOf(
                vector("position", camera?.get("position"), listOf(0f, 0f, 0f), null),
                vector("viewPointPosition", camera?.get("viewPointPosition"), listOf(0f, 0f, -1f), null),
                number("near", camera, "near", CAMERA_NEAR.toString(), "", null),
                number("far", camera, "far", CAMERA_FAR.toString(), "", null),
                number("fieldOfView", camera, "fieldOfView", CAMERA_FOV.toString(), "°", null),
            ),
        )
        return PanelModel(
            Header(emptyList(), "project", fileName, messages.message("panelSubtitleProject", fileName)),
            listOf(settings, main), messages.message("panelFooterDefaults"),
        )
    }

    /** A folder row (Scenes, UI, Assets or a type group): read only, with [items] entries. */
    fun folder(kind: FolderKind, name: String, items: Int, breadcrumb: List<String>): PanelModel = PanelModel(
        Header(
            breadcrumb, kind.iconKey, name, messages.message("panelSubtitleFolder", items),
            listOf(Badge(BadgeKind.READ_ONLY, messages.message("panelBadgeReadOnly"))),
        ),
        listOf(
            Section(
                "folder", messages.message("panelSectionFolder"), kind.iconKey,
                listOf(ReadonlyRow("name", name), ReadonlyRow("items", items.toString())),
            ),
        ),
    )

    /** A scene: its name and id, then ambient light, fog and skybox with their `Enabled` check boxes. */
    fun scene(scene: JsonNode, projectFile: String, scenePath: String): PanelModel {
        val name = scene.text("name")
        val main = Section(
            "scene.main", messages.message("panelSectionScene"), "scene",
            listOf(
                TextRow("name", name, dim = !scene.has("name")),
                ReadonlyRow("id", scene.get("id")?.asText() ?: ""),
            ),
        )
        return PanelModel(
            Header(listOf(projectFile), "scene", name, messages.message("panelSubtitleScene", scenePath)),
            listOf(main) + SceneSetting.entries.map { settingSection(scene, it) },
            messages.message("panelFooterDefaults"),
        )
    }

    /** One scene setting row: its section only. */
    fun setting(scene: JsonNode, setting: SceneSetting, projectFile: String): PanelModel {
        val sceneName = scene.text("name")
        val section = settingSection(scene, setting)
        return PanelModel(
            Header(
                listOf(projectFile, sceneName), setting.iconKey, setting.key,
                messages.message("panelSubtitleSetting", sceneName),
            ),
            listOf(section), messages.message("panelFooterDefaults"),
        )
    }

    private fun settingSection(scene: JsonNode, setting: SceneSetting): Section {
        val node = scene.get(setting.key)
        val rows: List<Row> = when (setting) {
            SceneSetting.AMBIENT -> listOf(
                color("color", node?.get("color"), null),
                number("intensity", node, "intensity", "", "", null),
            )

            SceneSetting.FOG -> listOf(
                color("color", node?.get("color"), null),
                number("density", node, "density", "", "", null),
                number("gradient", node, "gradient", "", "", null),
            )

            SceneSetting.SKYBOX -> listOf(
                AssetRow(
                    "skyboxName", scene.text("skyboxName"), "psky", browse = true, dim = !scene.has("skyboxName"),
                    field = FieldRef("scene", "skyboxName"),
                ),
            )
        }
        val on = scene.get(setting.toggleKey)?.let { !it.isBoolean || it.booleanValue() } ?: false
        return Section(
            "scene.${setting.key}", messages.message(setting.titleKey), setting.iconKey, rows,
            classHint = setting.toggleKey, enable = EnableToggle(on, settingKey = setting.toggleKey), disabled = !on,
        )
    }

    /** The `ecs` row: the entity count, read only, with the count of each entity type as a note. */
    fun ecs(scene: JsonNode, projectFile: String): PanelModel {
        val entities = SceneDocument(scene).entitiesNode()?.properties().orEmpty().toList()
        val types = entities.groupingBy { (_, e) ->
            net.nevinsky.abyssus.lib.core.format.entityTypeOf(e) ?: "OBJECT"
        }
            .eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key }))
        val rows = listOf(
            ReadonlyRow("entities", entities.size.toString()),
            NoteRow(messages.message("panelNoteEcsTypes", types.joinToString(", ") { "${it.key} ${it.value}" })),
        )
        return PanelModel(
            Header(
                listOf(projectFile, scene.text("name")), "ecs", "ecs",
                messages.message("panelSubtitleEcs", entities.size),
                listOf(Badge(BadgeKind.READ_ONLY, messages.message("panelBadgeReadOnly"))),
            ),
            listOf(Section("ecs", messages.message("panelSectionEntities"), "ecs", rows)),
        )
    }

    // ---- entities and components ----

    /** An entity: one section per component. [read] is what the entity's sections read as. */
    fun entity(scene: JsonNode, entityId: String, read: EntitySections.Read, projectFile: String): PanelModel {
        val type = typeOf(scene, entityId)
        val entityOn = entityEnabled(scene, entityId)
        return PanelModel(
            Header(
                listOf(projectFile, scene.text("name"), "ecs"), entityIconKey(type), read.name,
                messages.message("panelSubtitleEntity", entityId, type ?: "OBJECT"),
            ),
            read.sections
                .map { componentSection(scene, entityId, it, read.name, type, entityOn) },
            messages.message("panelFooterDefaults"),
        )
    }

    /** One component of an entity: its section only. */
    fun component(scene: JsonNode, entityId: String, read: EntitySections.Read, projectFile: String): PanelModel {
        val type = typeOf(scene, entityId)
        val section = read.sections.single()
        val game = isGame(section.kind)
        val subtitle = messages.message(
            if (game) "panelSubtitleGameComponent" else "panelSubtitleComponent", section.kind, read.name,
        )
        return PanelModel(
            Header(
                listOf(projectFile, scene.text("name"), "ecs", read.name), componentIconKey(section.kind),
                section.label, subtitle,
            ),
            listOf(componentSection(scene, entityId, section, read.name, type, entityEnabled(scene, entityId))),
            messages.message("panelFooterDefaults"),
        )
    }

    private fun typeOf(scene: JsonNode, entityId: String): String? =
        SceneDocument(scene).entity(entityId)?.type

    private fun entityEnabled(scene: JsonNode, entityId: String): Boolean =
        SceneDocument(scene).entity(entityId)?.enabled ?: true

    private fun componentSection(
        scene: JsonNode,
        entityId: String,
        section: ComponentSection,
        entityName: String,
        type: String?,
        entityOn: Boolean,
    ): Section {
        val node = SceneDocument(scene).components(entityId)?.get(section.kind)
        val componentOn = SceneDocument(scene).entity(entityId)?.componentEnabled(section.kind) ?: true
        val togglable = toggles.isTogglable(section.kind)
        val rows = if (section.raw != null) rawRows(section) else rows(section, entityId, entityName, type)
        return Section(
            "$entityId.${section.kind}", humanize(section.label), componentIconKey(section.kind), rows, section.kind,
            isGame(section.kind), section.kind,
            if (togglable) EnableToggle(componentOn, EnabledTarget.Component(entityId, section.kind)) else null,
            disabled = !entityOn || !componentOn,
        )
    }

    private fun rawRows(section: ComponentSection): List<Row> =
        if (section.raw?.matches(EMPTY_OBJECT) == true) listOf(
            NoteRow(
                messages.message(
                    "panelNoteDefaults",
                    section.kind
                )
            )
        )
        else listOf(NoteRow(messages.message("panelUnmodeledNote")), ReadonlyRow("", section.raw.orEmpty()))

    /** "RigidBody" as "Rigid body": the title the design shows for a component whose label is its class name. */
    private fun humanize(label: String): String {
        if (' ' in label) return label
        val words = label.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        return words.take(1) + words.drop(1).lowercase()
    }

    /** "localPosition" as "Local position", "lookAtId" as "Look at Id": a camelCase field name read as words; other labels stay. */
    private fun readable(label: String): String {
        if (' ' in label || !CAMEL.containsMatchIn(label)) {
            return label
        }
        val words = label.split(CAMEL).map { if (it == "Id") it else it.lowercase() }
        return words.first().replaceFirstChar { it.uppercase() } + words.drop(1).joinToString("") { " $it" }
    }

    private fun rows(section: ComponentSection, entityId: String, entityName: String, type: String?): List<Row> {
        if (section.kind == "NameComponent") return nameRows(section)
        val out = mutableListOf<Row>()
        var group = ""
        var i = 0
        val fields = section.fields
        while (i < fields.size) {
            val field = fields[i]
            if (field.group != group) {
                group = field.group
                if (group.isNotEmpty()) out += SubHeadingRow(group)
            }
            val axis = AXIS.matchEntire(field.field)
            if (axis != null) {
                val prefix = axis.groupValues[1]
                val run = fields.drop(i).takeWhile { AXIS.matchEntire(it.field)?.groupValues?.get(1) == prefix }
                out += vectorRow(section.kind, prefix, run)
                i += run.size
                continue
            }
            out += rowOf(section.kind, field)
            i++
        }
        return out
    }

    /** The Name section is the one name field, labeled `Name`. */
    private fun nameRows(section: ComponentSection): List<Row> {
        val name = section.fields.firstOrNull { it.field == "name" }
        return listOfNotNull((name?.let {
            rowOf(
                section.kind,
                it
            )
        } as? TextRow)?.copy(label = messages.message("panelNameLabel")))
    }

    private fun vectorRow(kind: String, prefix: String, run: List<FieldValue>): Row {
        val label = readable(prefix.substringAfterLast('.'))
        val axes = run.map { Axis(AXIS.matchEntire(it.field)!!.groupValues[2], it.value, FieldRef(kind, it.field)) }
        val dim = run.all { it.isDefault }
        if (label == "color" && axes.map { it.name }.containsAll(listOf("r", "g", "b"))) {
            val rgb = axes.associate { it.name to (it.value.toFloatOrNull() ?: 0f) }
            return ColorRow(label, hex(rgb["r"] ?: 0f, rgb["g"] ?: 0f, rgb["b"] ?: 0f), dim, axes = axes)
        }
        return VectorRow(label, axes, dim)
    }

    private fun rowOf(kind: String, f: FieldValue): Row {
        val ref = FieldRef(kind, f.field)
        return when {
            f.kind == FieldKind.BOOLEAN -> CheckRow(readable(f.label), f.value == "true", "", f.isDefault, ref)
            f.kind == FieldKind.CHOICE -> ChoiceRow(readable(f.label), f.value, f.choices, f.isDefault, ref, f.optional)
            f.kind == FieldKind.ASSET_NAME ->
                AssetRow(
                    readable(f.label),
                    f.value,
                    assetIconKey(f.assetType ?: "MODEL"),
                    f.choices.size > 1,
                    f.choices,
                    f.isDefault,
                    ref
                )

            f.kind == FieldKind.FLOAT || f.kind == FieldKind.INT -> NumberRow(
                readable(f.label),
                f.value,
                f.unit,
                f.isDefault,
                ref,
                f.tooltip
            )

            else -> TextRow(readable(f.label), f.value, f.isDefault, ref, f.optional, f.tooltip)
        }
    }

    // ---- UI screens and assets ----

    /** A screen row under a UI asset: its name, the owning asset and the link that opens the Screen Editor. */
    fun uiScreen(screen: String, asset: String, projectFile: String): PanelModel = PanelModel(
        Header(listOf(projectFile, asset), "uiscreen", screen, messages.message("panelSubtitleUiScreen", asset)),
        listOf(
            Section(
                "screen", messages.message("panelSectionScreen"), "uiscreen",
                listOf(
                    TextRow("name", screen),
                    ReadonlyRow("asset", asset),
                    LinkRow(messages.message("panelLinkScreenEditor"), LinkTarget.SCREEN_EDITOR)
                ),
            ),
        ),
        messages.message("panelFooterDefaults"),
    )

    /**
     * An asset: the `meta.json` section (identity fields, read only), then `additional`. [editable] names the keys of
     * `additional` that the plugin edits; they become rows carrying a [FieldRef] of kind `asset`. [used] is false for an
     * asset no scene reaches. [hasEditors] is false for an asset the plugin only shows, which gets the `read-only` badge.
     */
    fun asset(
        name: String, meta: JsonNode, used: Boolean, editable: Set<String>, projectFile: String,
        hasEditors: Boolean = editable.isNotEmpty(),
    ): PanelModel {
        val type = meta.get("type")?.takeIf { it.isTextual }?.asText()
        val readOnly = !hasEditors
        val badges = buildList {
            if (readOnly) add(Badge(BadgeKind.READ_ONLY, messages.message("panelBadgeReadOnly")))
            if (!used) add(Badge(BadgeKind.UNUSED, messages.message("panelBadgeUnused")))
        }
        val metaSection = Section(
            "meta", messages.message("panelSectionMeta"), null,
            listOf(
                ReadonlyRow("version", meta.get("version")?.asText() ?: "1"),
                ReadonlyRow("lastModified", modified(meta.get("lastModified"))),
                meta.get("uuid")?.takeIf { it.isTextual }?.let { ReadonlyRow("uuid", it.asText()) }
                    ?: ReadonlyRow("uuid", messages.message("panelNotSet"), dim = true),
                ReadonlyRow("type", type ?: ""),
            ),
        )
        return PanelModel(
            Header(
                listOf(projectFile),
                assetIconKey(type),
                name,
                messages.message("panelSubtitleAsset", type ?: ""),
                badges,
                note = if (readOnly) "" else messages.message("panelNoteSharedAsset"),
            ),
            listOf(metaSection, additionalSection(type, meta, editable)),
            messages.message(if (readOnly) "panelFooterReadOnlyAsset" else "panelFooterAsset"),
        )
    }

    private fun additionalSection(type: String?, meta: JsonNode, editable: Set<String>): Section {
        val additional = meta.get("additional")
        val ui = type == "UI"
        val node = (if (ui) additional?.get("ui") else additional)?.takeIf { it.isObject }
        // a supported property a file omits is listed with its effective value, dim, so it can be written
        val props = node?.properties()?.toList().orEmpty() +
                editable.filter { node == null || !node.has(it) }
                    .map { java.util.AbstractMap.SimpleEntry<String, JsonNode>(it, MISSING) }
        val rows = mutableListOf<Row>()
        when (type) {
            "SKYBOX_PROCEDURAL" -> {
                val (head, atmosphere) = props.partition { (k, _) -> k in listOf("vertex", "fragment", "clouds") }
                head.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (atmosphere.isNotEmpty()) rows += SubHeadingRow(messages.message("panelAtmosphere"))
                atmosphere.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
            }

            "TERRAIN" -> {
                val order =
                    listOf("terrainFile", "size", "uv", "splatBase", "splatMap", "splatR", "splatG", "splatB", "splatA")
                props.sortedBy { (k, _) -> order.indexOf(k).let { if (it < 0) order.size else it } }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                rows += CustomRow("terrain")
            }

            "FOLIAGE" -> {
                props.filter { (k, v) -> !(k == "layers" && v.isArray) }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                additional?.get("layers")?.takeIf { it.isArray }?.let {
                    rows += SubHeadingRow(messages.message("panelLayers", it.size()))
                    rows += foliageTable(it)
                }
                rows += CustomRow("foliage")
            }

            "CLOUDS" -> {
                val (layers, rest) = props.partition { (_, v) -> v.isObject && v.has("type") }
                rest.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (layers.isNotEmpty()) rows += cloudTable(layers)
            }

            "UI" -> {
                props.filter { (_, v) -> !v.isContainerNode }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                node?.get("screens")?.takeIf { it.isArray }?.let { screens ->
                    rows += SubHeadingRow(messages.message("panelScreens", screens.size()))
                    rows += TableRow(
                        listOf("name"),
                        screens.map { listOf(it.get("name")?.asText() ?: it.get("id")?.asText() ?: "") })
                }
                rows += LinkRow(messages.message("panelLinkScreenEditor"), LinkTarget.SCREEN_EDITOR)
            }

            else -> {
                props.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (type == "SKYBOX") rows += CustomRow("faces")
                if (type == "SKYBOX_HDR") rows += CustomRow("hdr")
            }
        }
        return Section(
            "additional", messages.message(if (ui) "panelSectionAdditionalUi" else "panelSectionAdditional"),
            assetIconKey(type), rows,
        )
    }

    private fun assetRow(key: String, value: JsonNode, editable: Set<String>, type: String?): Row {
        val ref = FieldRef("asset", key)
        val text = when {
            value.isNull -> "null"
            value.isArray && value.size() > 0 && value.all { it.isNumber } -> value.joinToString(
                ", ",
                "[",
                "]"
            ) { it.asText() }

            value.isArray -> "[] (${value.size()})"
            value.isObject -> "{} (${value.size()})"
            else -> value.asText()
        }
        val reference = ASSET_REFERENCES[type to key]
        return when {
            reference != null && (!value.isNull || key in editable) -> AssetRow(
                key,
                text,
                assetIconKey(reference),
                key in editable,
                emptyList(),
                false,
                if (key in editable) ref else null,
            )

            key in editable -> TextRow(key, if (value === MISSING) "" else text, value.isNull || value === MISSING, ref)
            else -> ReadonlyRow(key, text, value.isNull)
        }
    }

    /** The first model of a layer, with how many more it mixes in. */
    private fun modelOf(layer: JsonNode): String {
        val models = layer.get("models")?.takeIf { it.isArray }?.mapNotNull { it.get("asset")?.asText() }.orEmpty()
        return when {
            models.isEmpty() -> ""
            models.size == 1 -> models.single()
            else -> "${models.first()} +${models.size - 1}"
        }
    }

    private fun foliageTable(layers: JsonNode): Row = TableRow(
        listOf("id", "kind", "model", "density"),
        layers.mapIndexed { i, l ->
            listOf(
                l.get("id")?.asText() ?: (i + 1).toString(),
                l.get("kind")?.asText() ?: "",
                modelOf(l),
                l.get("density")?.asText() ?: ""
            )
        },
    )

    private fun cloudTable(layers: List<Map.Entry<String, JsonNode>>): Row = TableRow(
        listOf("layer", "type", "coverage", "density", "wind x, z"),
        layers.map { (name, l) ->
            listOf(
                name,
                l.get("type")?.asText() ?: "",
                l.get("coverage")?.asText() ?: "",
                l.get("density")?.asText() ?: "",
                "${l.get("windX")?.asText() ?: ""}, ${l.get("windZ")?.asText() ?: ""}",
            )
        },
    )

    // ---- small helpers ----

    private fun JsonNode.text(key: String): String = get(key)?.takeIf { !it.isNull }?.asText() ?: ""

    private fun modified(value: JsonNode?): String =
        value?.takeIf { it.isIntegralNumber }?.let { MODIFIED.format(Instant.ofEpochMilli(it.longValue())) } ?: ""

    /** A number row; it is editable when [kind] names the object the plugin writes it under, else read only. */
    private fun number(
        label: String,
        node: JsonNode?,
        key: String,
        default: String,
        unit: String,
        kind: String?
    ): NumberRow {
        val value = node?.get(key)
        return NumberRow(label, value?.asText() ?: default, unit, value == null, kind?.let { FieldRef(it, key) })
    }

    private fun vector(label: String, node: JsonNode?, default: List<Float>, kind: String?): VectorRow {
        val names = listOf("x", "y", "z")
        val axes = names.mapIndexed { i, n ->
            Axis(
                n,
                node?.get(n)?.asText() ?: default[i].toString(),
                kind?.let { FieldRef(it, "$label.$n") })
        }
        return VectorRow(label, axes, dim = node == null || names.none { node.has(it) })
    }

    private fun color(label: String, node: JsonNode?, kind: String?): ColorRow {
        val channels = listOf("r", "g", "b", "a")
        val axes =
            channels.map { Axis(it, node?.get(it)?.asText() ?: "", kind?.let { k -> FieldRef(k, "$label.$it") }) }
        val rgb = listOf("r", "g", "b").map { node?.get(it)?.floatValue() ?: 0f }
        return ColorRow(label, if (node == null) "" else hex(rgb[0], rgb[1], rgb[2]), dim = node == null, axes = axes)
    }

    private fun hex(r: Float, g: Float, b: Float): String {
        fun c(v: Float) = Math.round(v.coerceIn(0f, 1f) * 255)
        return "#%02X%02X%02X".format(c(r), c(g), c(b))
    }
}

/** Stands in for a property the file omits that the plugin can still edit. */
private val MISSING: JsonNode = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("")

private val AXIS = Regex("(.+)\\.([xyzwrgba])")
private val CAMEL = Regex("(?<=[a-z])(?=[A-Z])")

private val EMPTY_OBJECT = Regex("\\s*\\{\\s*}\\s*")

/** The `additional` keys that name another asset, with the type of asset they name. */
private val ASSET_REFERENCES: Map<Pair<String, String>, String> =
    (SPLAT_FIELDS + "splatBase").distinct().associate { ("TERRAIN" to it) to "TEXTURE" } + mapOf(
        ("FOLIAGE" to "terrain") to "TERRAIN",
        ("SKYBOX_PROCEDURAL" to "clouds") to "CLOUDS",
    )
