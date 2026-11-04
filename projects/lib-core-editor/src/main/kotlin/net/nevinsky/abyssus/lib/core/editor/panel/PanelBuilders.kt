/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.panel

import net.nevinsky.abyssus.lib.core.editor.scene.SCENE_SETTING_KIND
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_NEAR
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.*
import net.nevinsky.abyssus.lib.core.editor.meta.ComponentSection
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.scalarText
import net.nevinsky.abyssus.lib.core.editor.util.valuesOf
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
        abss: ProjectDto,
        fileName: String,
        sceneNames: List<String>,
        editable: Set<String> = emptySet()
    ): PanelModel {
        fun ref(key: String) = if (key in editable) {
            FieldRef("project", key)
        } else {
            null
        }

        val camera = abss.mainCamera
        val settings = Section(
            "project.main", messages.message("panelSectionProject"), "project",
            listOf(
                TextRow("name", abss.name.orEmpty(), dim = abss.name == null, field = ref("name")),
                ChoiceRow(
                    "activeSceneName", abss.activeSceneName.orEmpty(), sceneNames, abss.activeSceneName == null,
                    ref("activeSceneName"),
                ),
                CheckRow(
                    "physicsEnabled",
                    abss.physicsEnabled ?: false,
                    messages.message("panelEnabled"),
                    abss.physicsEnabled == null,
                    ref("physicsEnabled"),
                ),
                CustomRow("antialiasing"),
                CustomRow("showFps"),
                ReadonlyRow(
                    "uiAssetNames", abss.uiAssetNames.joinToString(",", "[", "]") { "\"$it\"" },
                    abss.uiAssetNames.isEmpty(),
                ),
            ),
        )
        val main = Section(
            "project.camera", messages.message("panelSectionMainCamera"), "render",
            listOf(
                vectorRow("position", camera?.position, listOf(0f, 0f, 0f)),
                vectorRow("viewPointPosition", camera?.viewPointPosition, listOf(0f, 0f, -1f)),
                numberRow("near", camera?.near, CAMERA_NEAR.toString(), "", null),
                numberRow("far", camera?.far, CAMERA_FAR.toString(), "", null),
                numberRow("fieldOfView", camera?.fieldOfView, CAMERA_FOV.toString(), "°", null),
            ),
        )
        return PanelModel(
            Header(emptyList(), "project", fileName, messages.message("panelSubtitleProject", fileName)),
            listOf(settings, main), messages.message("panelFooterDefaults"),
        )
    }

    /** A read-only vector row for a typed value; it is dim while the value is absent. */
    private fun vectorRow(label: String, v: com.badlogic.gdx.math.Vector3?, default: List<Float>): VectorRow {
        val values = v?.let { listOf(it.x, it.y, it.z) } ?: default
        val axes = listOf("x", "y", "z").mapIndexed { i, n -> Axis(n, decimalText(values[i]), null) }
        return VectorRow(label, axes, dim = v == null)
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
    fun scene(scene: SceneDto, projectFile: String, scenePath: String): PanelModel {
        val name = scene.name.orEmpty()
        val main = Section(
            "scene.main", messages.message("panelSectionScene"), "scene",
            listOf(
                TextRow("name", name, dim = scene.name == null),
                ReadonlyRow("id", scene.id),
            ),
        )
        return PanelModel(
            Header(listOf(projectFile), "scene", name, messages.message("panelSubtitleScene", scenePath)),
            listOf(main) + SceneSetting.entries.map { settingSection(scene, it) },
            messages.message("panelFooterDefaults"),
        )
    }

    /** One scene setting row: its section only. */
    fun setting(scene: SceneDto, setting: SceneSetting, projectFile: String): PanelModel {
        val sceneName = scene.name.orEmpty()
        val section = settingSection(scene, setting)
        return PanelModel(
            Header(
                listOf(projectFile, sceneName), setting.iconKey, setting.key,
                messages.message("panelSubtitleSetting", sceneName),
            ),
            listOf(section), messages.message("panelFooterDefaults"),
        )
    }

    private fun settingSection(scene: SceneDto, setting: SceneSetting): Section {
        val rows: List<Row> = when (setting) {
            SceneSetting.AMBIENT -> listOf(
                colorRow("color", scene.ambientLight?.color, "ambientLight.color"),
                settingNumberRow("intensity", scene.ambientLight?.intensity, "ambientLight.intensity"),
            )

            SceneSetting.FOG -> listOf(
                colorRow("color", scene.fog?.color, "fog.color"),
                settingNumberRow("density", scene.fog?.density, "fog.density"),
                settingNumberRow("gradient", scene.fog?.gradient, "fog.gradient"),
            )

            SceneSetting.SKYBOX -> listOf(
                AssetRow(
                    "skyboxName", scene.skyboxName.orEmpty(), "psky", browse = true, dim = scene.skyboxName == null,
                    field = FieldRef("scene", "skyboxName"),
                ),
            )
        }
        val on = when (setting) {
            SceneSetting.AMBIENT -> scene.ambientLightEnabled
            SceneSetting.FOG -> scene.fogEnabled
            SceneSetting.SKYBOX -> scene.skyboxEnabled
        }
        return Section(
            "scene.${setting.key}", messages.message(setting.titleKey), setting.iconKey, rows,
            classHint = setting.toggleKey, enable = EnableToggle(on, settingKey = setting.toggleKey), disabled = !on,
        )
    }

    /** The `ecs` row: the entity count, read only, with the count of each entity type as a note. */
    fun ecs(scene: SceneDto, projectFile: String): PanelModel {
        val entities = scene.entities()
        val types = entities.groupingBy { (_, e) -> e.header().type?.name ?: "OBJECT" }
            .eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key }))
        val rows = listOf(
            ReadonlyRow("entities", entities.size.toString()),
            NoteRow(messages.message("panelNoteEcsTypes", types.joinToString(", ") { "${it.key} ${it.value}" })),
        )
        return PanelModel(
            Header(
                listOf(projectFile, scene.name.orEmpty()), "ecs", "ecs",
                messages.message("panelSubtitleEcs", entities.size),
                listOf(Badge(BadgeKind.READ_ONLY, messages.message("panelBadgeReadOnly"))),
            ),
            listOf(Section("ecs", messages.message("panelSectionEntities"), "ecs", rows)),
        )
    }

    // ---- entities and components ----

    /** An entity: one section per component. [read] is what the entity's sections read as. */
    fun entity(scene: SceneDto, entityId: Long, read: EntitySections.Read, projectFile: String): PanelModel {
        val type = typeOf(scene, entityId)
        val entityOn = entityEnabled(scene, entityId)
        return PanelModel(
            Header(
                listOf(projectFile, scene.name.orEmpty(), "ecs"), entityIconKey(type, read.sections.map { it.kind }), read.name,
                messages.message("panelSubtitleEntity", entityId, type ?: "OBJECT"),
            ),
            read.sections
                .map { componentSection(scene, entityId, it, read.name, type, entityOn) },
            messages.message("panelFooterDefaults"),
        )
    }

    /** One component of an entity: its section only. */
    fun component(scene: SceneDto, entityId: Long, read: EntitySections.Read, projectFile: String): PanelModel {
        val type = typeOf(scene, entityId)
        val section = read.sections.single()
        val game = isGame(section.kind)
        val subtitle = messages.message(
            if (game) {
                "panelSubtitleGameComponent"
            } else {
                "panelSubtitleComponent"
            }, section.kind, read.name,
        )
        return PanelModel(
            Header(
                listOf(projectFile, scene.name.orEmpty(), "ecs", read.name), componentIconKey(section.kind),
                section.label, subtitle,
            ),
            listOf(componentSection(scene, entityId, section, read.name, type, entityEnabled(scene, entityId))),
            messages.message("panelFooterDefaults"),
        )
    }

    private fun typeOf(scene: SceneDto, entityId: Long): String? =
        scene.entity(entityId)?.header()?.type?.name

    private fun entityEnabled(scene: SceneDto, entityId: Long): Boolean =
        scene.entity(entityId)?.header()?.entityEnabled ?: true

    /** Whether the component [kind] of [entity] is enabled: not named by the header's disabled components. */
    private fun componentEnabled(entity: EntityDto?, kind: String): Boolean {
        entity ?: return true
        val component = entity.components.firstOrNull { it.javaClass.simpleName == kind }
        if (component != null) {
            return component.javaClass !in entity.header().disabledComponents
        }
        return entity.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }
            .firstOrNull { it.name.substringAfterLast('.') == kind }?.disabled?.not() ?: true
    }

    private fun componentSection(
        scene: SceneDto,
        entityId: Long,
        section: ComponentSection,
        entityName: String,
        type: String?,
        entityOn: Boolean,
    ): Section {
        val componentOn = componentEnabled(scene.entity(entityId), section.kind)
        val togglable = toggles.isTogglable(section.kind)
        val rows = if (section.raw != null) {
            rawRows(section)
        } else {
            rows(section, entityId, entityName, type)
        }
        return Section(
            "$entityId.${section.kind}", humanize(section.label), componentIconKey(section.kind), rows, section.kind,
            isGame(section.kind), section.kind,
            if (togglable) {
                EnableToggle(componentOn, EnabledTarget.Component(entityId, section.kind))
            } else {
                null
            },
            disabled = !entityOn || !componentOn,
        )
    }

    private fun rawRows(section: ComponentSection): List<Row> =
        if (section.raw?.matches(EMPTY_OBJECT) == true) {
            listOf(
                NoteRow(
                    messages.message(
                        "panelNoteDefaults",
                        section.kind
                    )
                )
            )
        } else {
            listOf(NoteRow(messages.message("panelUnmodeledNote")), ReadonlyRow("", section.raw.orEmpty()))
        }

    /** "RigidBody" as "Rigid body": the title the design shows for a component whose label is its class name. */
    private fun humanize(label: String): String {
        if (' ' in label) {
            return label
        }
        val words = label.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        return words.take(1) + words.drop(1).lowercase()
    }

    /**
     * "localPosition" as "Local position", "lookAtId" as "Look at Id": a camelCase field name read as words; other
     * labels stay.
     */
    private fun readable(label: String): String {
        if (' ' in label || !CAMEL.containsMatchIn(label)) {
            return label
        }
        val words = label.split(CAMEL).map { if (it == "Id") {
            it
        } else {
            it.lowercase()
        } }
        return words.first().replaceFirstChar { it.uppercase() } + words.drop(1).joinToString("") { " $it" }
    }

    private fun rows(section: ComponentSection, entityId: Long, entityName: String, type: String?): List<Row> {
        if (section.kind == "NameComponent") {
            return nameRows(section)
        }
        val out = mutableListOf<Row>()
        var group = ""
        var i = 0
        val fields = section.fields
        while (i < fields.size) {
            val field = fields[i]
            if (field.group != group) {
                group = field.group
                if (group.isNotEmpty()) {
                    out += SubHeadingRow(group)
                }
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
            f.kind == FieldKind.MESH_ID -> {
                val unknown = f.value.isNotEmpty() && f.value !in f.choices
                ChoiceRow(
                    readable(f.label), f.value, f.choices, f.isDefault, ref, true, unknown,
                    messages.message("panelMeshWholeModel"),
                    if (unknown) {
                        messages.message("panelMeshUnknown", f.value)
                    } else {
                        null
                    },
                )
            }

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
      * `additional` that the plugin edits; they become rows carrying a [FieldRef] of kind `asset`. [used] is false for
      * an
      * asset no scene reaches. [hasEditors] is false for an asset the plugin only shows, which gets the `read-only`
      * badge.
     */
    fun asset(
        name: String, meta: JsonValues, used: Boolean, editable: Set<String>, projectFile: String,
        hasEditors: Boolean = editable.isNotEmpty(),
    ): PanelModel {
        val type = meta["type"] as? String
        val readOnly = !hasEditors
        val badges = buildList {
            if (readOnly) {
                add(Badge(BadgeKind.READ_ONLY, messages.message("panelBadgeReadOnly")))
            }
            if (!used) {
                add(Badge(BadgeKind.UNUSED, messages.message("panelBadgeUnused")))
            }
        }
        val metaSection = Section(
            "meta", messages.message("panelSectionMeta"), null,
            listOf(
                ReadonlyRow("version", meta["version"]?.toString() ?: "1"),
                ReadonlyRow("lastModified", modified(meta["lastModified"])),
                (meta["uuid"] as? String)?.let { ReadonlyRow("uuid", it) }
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
                note = if (readOnly) {
                    ""
                } else {
                    messages.message("panelNoteSharedAsset")
                },
            ),
            listOf(metaSection, additionalSection(type, meta, editable)),
            messages.message(if (readOnly) {
                "panelFooterReadOnlyAsset"
            } else {
                "panelFooterAsset"
            }),
        )
    }

    private fun additionalSection(type: String?, meta: JsonValues, editable: Set<String>): Section {
        val additional = valuesOf(meta["additional"])
        val ui = type == "UI"
        val node = if (ui) {
            valuesOf(additional?.get("ui"))
        } else {
            additional
        }
        // a supported property a file omits is listed with its effective value, dim, so it can be written
        val props: List<Pair<String, Any?>> = node?.entries?.map { it.key to it.value }.orEmpty() +
                editable.filter { node == null || !node.containsKey(it) }.map { it to MISSING }
        val rows = mutableListOf<Row>()
        when (type) {
            "SKYBOX_PROCEDURAL" -> {
                val (head, atmosphere) = props.partition { (k, _) -> k in listOf("vertex", "fragment", "clouds") }
                head.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (atmosphere.isNotEmpty()) {
                    rows += SubHeadingRow(messages.message("panelAtmosphere"))
                }
                atmosphere.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
            }

            "TERRAIN" -> {
                val order =
                    listOf("terrainFile", "size", "uv", "splatBase", "splatMap", "splatR", "splatG", "splatB", "splatA")
                props.sortedBy { (k, _) -> order.indexOf(k).let { if (it < 0) {
                    order.size
                } else {
                    it
                } } }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                rows += CustomRow("terrain")
            }

            "FOLIAGE" -> {
                props.filter { (k, v) -> !(k == "layers" && v is List<*>) }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                (additional?.get("layers") as? List<*>)?.let {
                    rows += SubHeadingRow(messages.message("panelLayers", it.size))
                    rows += foliageTable(it)
                }
                rows += CustomRow("foliage")
            }

            "CLOUDS" -> {
                val (layers, rest) = props.partition { (_, v) -> valuesOf(v)?.containsKey("type") == true }
                rest.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (layers.isNotEmpty()) {
                    rows += cloudTable(layers)
                }
            }

            "UI" -> {
                props.filter { (_, v) -> v !is List<*> && v !is Map<*, *> }
                    .forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                (node?.get("screens") as? List<*>)?.let { screens ->
                    rows += SubHeadingRow(messages.message("panelScreens", screens.size))
                    rows += TableRow(
                        listOf("name"),
                        screens.map { screen ->
                            val values = valuesOf(screen)
                            listOf((values?.get("name") ?: values?.get("id"))?.toString().orEmpty())
                        })
                }
                rows += LinkRow(messages.message("panelLinkScreenEditor"), LinkTarget.SCREEN_EDITOR)
            }

            else -> {
                props.forEach { (k, v) -> rows += assetRow(k, v, editable, type) }
                if (type == "SKYBOX") {
                    rows += CustomRow("faces")
                }
                if (type == "SKYBOX_HDR") {
                    rows += CustomRow("hdr")
                }
            }
        }
        return Section(
            "additional", messages.message(if (ui) {
                "panelSectionAdditionalUi"
            } else {
                "panelSectionAdditional"
            }),
            assetIconKey(type), rows,
        )
    }

    private fun assetRow(key: String, value: Any?, editable: Set<String>, type: String?): Row {
        val ref = FieldRef("asset", key)
        val text = when {
            value == null -> "null"
            value is List<*> && value.isNotEmpty() && value.all { it is Number } ->
                value.joinToString(", ", "[", "]") { scalarText(it) }

            value is List<*> -> "[] (${value.size})"
            value is Map<*, *> -> "{} (${value.size})"
            else -> scalarText(value)
        }
        val missing = value === MISSING
        val reference = ASSET_REFERENCES[type to key]
        return when {
            reference != null && (value != null || key in editable) -> AssetRow(
                key,
                if (missing) {
                    ""
                } else {
                    text
                },
                assetIconKey(reference),
                key in editable,
                emptyList(),
                false,
                if (key in editable) {
                    ref
                } else {
                    null
                },
            )

            key in editable -> TextRow(key, if (missing) {
                ""
            } else {
                text
            }, value == null || missing, ref)
            else -> ReadonlyRow(key, text, value == null)
        }
    }

    /** The first model of a layer, with how many more it mixes in. */
    private fun modelOf(layer: JsonValues?): String {
        val models = (layer?.get("models") as? List<*>)?.mapNotNull { valuesOf(it)?.get("asset")?.toString() }.orEmpty()
        return when {
            models.isEmpty() -> ""
            models.size == 1 -> models.single()
            else -> "${models.first()} +${models.size - 1}"
        }
    }

    private fun foliageTable(layers: List<*>): Row = TableRow(
        listOf("id", "kind", "model", "density"),
        layers.mapIndexed { i, layer ->
            val l = valuesOf(layer)
            listOf(
                l?.get("id")?.toString() ?: (i + 1).toString(),
                l?.get("kind")?.toString() ?: "",
                modelOf(l),
                l?.get("density")?.let(::scalarText) ?: ""
            )
        },
    )

    private fun cloudTable(layers: List<Pair<String, Any?>>): Row = TableRow(
        listOf("layer", "type", "coverage", "density", "wind x, z"),
        layers.map { (name, layer) ->
            val l = valuesOf(layer)
            listOf(
                name,
                l?.get("type")?.toString() ?: "",
                l?.get("coverage")?.let(::scalarText) ?: "",
                l?.get("density")?.let(::scalarText) ?: "",
                "${l?.get("windX")?.let(::scalarText) ?: ""}, ${l?.get("windZ")?.let(::scalarText) ?: ""}",
            )
        },
    )

    // ---- small helpers ----

    private fun modified(value: Any?): String =
        (value as? Number)?.takeIf { it is Int || it is Long }
            ?.let { MODIFIED.format(Instant.ofEpochMilli(it.toLong())) } ?: ""

    /** A number row for a typed value; it is dim while the value is absent. */
    private fun numberRow(label: String, value: Float?, default: String, unit: String, kind: String?): NumberRow =
        NumberRow(label, value?.let(::decimalText) ?: default, unit, value == null, kind?.let { FieldRef(it, label) })

    private fun settingNumberRow(label: String, value: Float?, path: String): NumberRow =
        NumberRow(label, value?.let(::decimalText) ?: "", "", value == null, FieldRef(SCENE_SETTING_KIND, path))

    /** [path] makes the channels editable (`<setting>.color`); null leaves them read only. */
    private fun colorRow(label: String, color: Color?, path: String? = null): ColorRow {
        val axes = listOf("r" to color?.r, "g" to color?.g, "b" to color?.b, "a" to color?.a)
            .map { (name, value) ->
                Axis(name, value?.let(::decimalText) ?: "", path?.let { FieldRef(SCENE_SETTING_KIND, "$it.$name") })
            }
        return ColorRow(
            label,
            if (color == null) {
                ""
            } else {
                hex(color.r, color.g, color.b)
            },
            dim = color == null,
            axes = axes
        )
    }

    private fun hex(r: Float, g: Float, b: Float): String {
        fun c(v: Float) = Math.round(v.coerceIn(0f, 1f) * 255)
        return "#%02X%02X%02X".format(c(r), c(g), c(b))
    }
}

/** Stands in for a property the file omits that the plugin can still edit. */
private val MISSING = Any()

private val AXIS = Regex("(.+)\\.([xyzwrgba])")
private val CAMEL = Regex("(?<=[a-z])(?=[A-Z])")

private val EMPTY_OBJECT = Regex("\\s*\\{\\s*}\\s*")

/** The `additional` keys that name another asset, with the type of asset they name. */
private val ASSET_REFERENCES: Map<Pair<String, String>, String> =
    (SPLAT_FIELDS + "splatBase").distinct().associate { ("TERRAIN" to it) to "TEXTURE" } + mapOf(
        ("FOLIAGE" to "terrain") to "TERRAIN",
        ("SKYBOX_PROCEDURAL" to "clouds") to "CLOUDS",
    )
