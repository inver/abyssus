/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.panel

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.meta.EntityAssetChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.readEntitySections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The layouts of the Properties panel against the design board, on the Control Line fixture. */
class PanelBuildersTest {
    private val messages = ResourceEditorMessages()
    private val game = setOf("PlaneComponent", "PilotComponent")
    private val builders = PanelBuilders(messages) { it in game }
    private val project = File("../app-game-control-line/project/ControlLine")
    private val abss = SceneJson().parse(File(project, "ControlLine.abss").readText())
    private val field = SceneJson().parse(File(project, "scenes/Field.scene").readText())
    private val editor = ComponentEditor(messages)

    private fun read(id: String, kind: String? = null): EntitySections.Read = readEntitySections(
        field, id, kind, editor, EntityAssetChoices(listOf("model_racer", "model_pilot"), emptyMap()), messages,
    ) as EntitySections.Read

    private fun PanelModel.titles() = sections.map { it.title }

    private fun Section.labels() = rows.map { it.label }

    private inline fun <reified R : Row> Section.row(label: String): R = rows.single { it.label == label } as R

    // ---- project, folder, scene ----

    @Test
    fun projectHasProjectAndMainCameraSections() {
        val panel = builders.project(abss, "ControlLine.abss", listOf("Field"))
        assertEquals(emptyList<String>(), panel.header.breadcrumb)
        assertEquals("ControlLine.abss", panel.header.title)
        assertEquals("Project · ControlLine.abss", panel.header.subtitle)
        assertEquals(listOf("Project", "Main camera"), panel.titles())
        assertEquals(
            listOf("name", "activeSceneName", "physicsEnabled", "", "", "uiAssetNames"), panel.sections[0].labels(),
        )
        assertEquals(listOf("position", "viewPointPosition", "near", "far", "fieldOfView"), panel.sections[1].labels())
        assertEquals("°", panel.sections[1].row<NumberRow>("fieldOfView").unit)
        assertEquals("Field", panel.sections[0].row<ChoiceRow>("activeSceneName").value)
        assertTrue(panel.footer.startsWith("Dim values are defaults"))
    }

    @Test
    fun aMissingProjectValueIsDimAndAWrittenOneIsNot() {
        val partial = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"name":"P","mainCamera":{"near":0.1}}""")
        val panel = builders.project(partial, "P.abss", emptyList())
        val camera = panel.sections[1]
        assertFalse(camera.row<NumberRow>("near").dim)
        assertTrue(camera.row<NumberRow>("far").dim)
        assertEquals(CAMERA_FAR.toString(), camera.row<NumberRow>("far").value)
        assertTrue(camera.row<VectorRow>("position").dim)
    }

    @Test
    fun aFolderIsReadOnlyWithItsItemCount() {
        val panel = builders.folder(FolderKind.ASSETS, "Assets", 41, listOf("ControlLine.abss"))
        assertEquals("Folder · 41 items", panel.header.subtitle)
        assertEquals(listOf(BadgeKind.READ_ONLY), panel.header.badges.map { it.kind })
        assertEquals(listOf("Folder"), panel.titles())
        assertEquals(listOf("name", "items"), panel.sections.single().labels())
        assertTrue(panel.sections.single().rows.all { it is ReadonlyRow })
        assertEquals("Folder · 1 item", builders.folder(FolderKind.UI, "UI", 1, emptyList()).header.subtitle)
    }

    @Test
    fun aSceneListsItsSettingsWithEnabledBoxes() {
        val panel = builders.scene(field, "ControlLine.abss", "scenes/Field.scene")
        assertEquals(listOf("ControlLine.abss"), panel.header.breadcrumb)
        assertEquals("Field", panel.header.title)
        assertEquals("Scene · scenes/Field.scene", panel.header.subtitle)
        assertEquals(listOf("Scene", "Ambient light", "Fog", "Skybox"), panel.titles())
        assertEquals(listOf("name", "id"), panel.sections[0].labels())
        assertNull(panel.sections[0].enable)
        assertEquals("ambientLightEnabled", panel.sections[1].enable?.settingKey)
        assertEquals(listOf("color", "intensity"), panel.sections[1].labels())
        assertEquals(listOf("color", "density", "gradient"), panel.sections[2].labels())
        assertEquals("#BFD1E6", panel.sections[2].row<ColorRow>("color").hex)
        assertEquals("skybox_physical", panel.sections[3].row<AssetRow>("skyboxName").name)
        assertTrue(panel.sections[3].row<AssetRow>("skyboxName").browse)
    }

    @Test
    fun oneSettingShowsOnlyItsSectionAndAFalseFlagDimsIt() {
        val off = SceneJson().parse(field.toString().replace("\"fogEnabled\":true", "\"fogEnabled\":false"))
        val panel = builders.setting(off, SceneSetting.FOG, "ControlLine.abss")
        assertEquals(listOf("Fog"), panel.titles())
        assertEquals("Scene setting · Field", panel.header.subtitle)
        assertEquals(listOf("ControlLine.abss", "Field"), panel.header.breadcrumb)
        assertFalse(panel.sections.single().enable!!.checked)
        assertTrue(panel.sections.single().disabled)
        assertEquals("fogEnabled", panel.sections.single().enable?.settingKey)
    }

    @Test
    fun ecsCountsEntitiesAndTypesReadOnly() {
        val panel = builders.ecs(field, "ControlLine.abss")
        val entities = panel.sections.single()
        assertEquals("Entities", entities.title)
        assertEquals("186", entities.row<ReadonlyRow>("entities").value)
        assertTrue(entities.rows.last() is NoteRow)
        assertTrue((entities.rows.last() as NoteRow).text.startsWith("Types in this scene: OBJECT "))
        assertEquals(BadgeKind.READ_ONLY, panel.header.badges.single().kind)
        assertEquals(listOf("ControlLine.abss", "Field"), panel.header.breadcrumb)
        assertTrue(panel.header.subtitle.startsWith("ecs · 186 entities, "))
    }

    // ---- entities and components ----

    @Test
    fun theRacerEntityHasOneSectionPerComponent() {
        val panel = builders.entity(field, "3", read("3"), "ControlLine.abss")
        assertEquals(listOf("ControlLine.abss", "Field", "ecs"), panel.header.breadcrumb)
        assertEquals("Racer", panel.header.title)
        assertEquals("Entity (3) · OBJECT", panel.header.subtitle)
        assertEquals("object", panel.header.iconKey)
        assertEquals(
            listOf("Name", "Position", "Render", "Rigid body", "Collider", "Plane"), panel.titles(),
        )
        val name = panel.sections.first()
        assertEquals(listOf("Name"), name.labels())
        assertNull(name.enable)
    }

    @Test
    fun positionIsVectorsWithAxes() {
        val position = builders.entity(field, "3", read("3"), "ControlLine.abss").sections.first { it.title == "Position" }
        assertNull(position.enable)
        val pos = position.row<VectorRow>("Local position")
        assertEquals(listOf("x", "y", "z"), pos.axes.map { it.name })
        assertEquals(listOf("4", "0.1329", "-2.5"), pos.axes.map { it.value })
        assertFalse(pos.dim)
        assertEquals(FieldRef("PositionComponent", "localPosition.x"), pos.axes.first().field)
        assertEquals(listOf("x", "y", "z", "w"), position.row<VectorRow>("Local rotation").axes.map { it.name })
        assertTrue(position.row<VectorRow>("Local scale").dim)
    }

    @Test
    fun renderShowsItsAssetWithABrowseButtonAndTheShaderDimWhenAbsent() {
        val render = builders.entity(field, "3", read("3"), "ControlLine.abss").sections.first { it.title == "Render" }
        assertEquals("RenderComponent", render.classHint)
        assertNotNull(render.enable)
        assertEquals(EnabledTarget.Component("3", "RenderComponent"), render.enable?.target)
        val asset = render.row<AssetRow>("Asset name")
        assertEquals("model_racer", asset.name)
        assertTrue(asset.browse)
    }

    @Test
    fun gameComponentsAreMarkedAndKeepTheirClassHint() {
        val sections = builders.entity(field, "3", read("3"), "ControlLine.abss").sections
        val plane = sections.last()
        assertEquals("Plane", plane.title)
        assertTrue(plane.game)
        assertEquals("PlaneComponent", plane.classHint)
        assertTrue(sections.dropLast(1).none { it.game })
    }

    @Test
    fun anEmptyGameComponentSaysItUsesDefaults() {
        val panel = builders.entity(field, "1", read("1"), "ControlLine.abss")
        val pilot = panel.sections.single { it.title == "Pilot" }
        assertTrue(pilot.game)
        assertEquals(
            "Uses the defaults of PilotComponent. No fields are written to the scene file.",
            (pilot.rows.single() as NoteRow).text,
        )
    }

    @Test
    fun aComponentHasItsOwnHeaderAndSection() {
        val panel = builders.component(field, "3", read("3", "PlaneComponent"), "ControlLine.abss")
        assertEquals(listOf("ControlLine.abss", "Field", "ecs", "Racer"), panel.header.breadcrumb)
        assertEquals("Game component · PlaneComponent on Racer", panel.header.subtitle)
        assertEquals(listOf("Plane"), panel.titles())
        val component = builders.component(field, "3", read("3", "RenderComponent"), "ControlLine.abss")
        assertEquals("Component · RenderComponent on Racer", component.header.subtitle)
        assertEquals("render", component.header.iconKey)
    }

    @Test
    fun theSunIsALightEntity() {
        val panel = builders.entity(field, "2", read("2"), "ControlLine.abss")
        assertEquals("Entity (2) · LIGHT_DIRECTIONAL", panel.header.subtitle)
        assertEquals("dlight", panel.header.iconKey)
        val light = panel.sections.single { it.title == "Light" }
        val color = light.row<ColorRow>("color")
        assertEquals("#FFF5E0", color.hex)
        assertEquals(listOf("r", "g", "b", "a"), color.axes.map { it.name })
        assertEquals(EnabledTarget.Component("2", "LightComponent"), light.enable?.target)
    }

    @Test
    fun aDisabledComponentAndAnEntityDimTheirSections() {
        val off = SceneJson().parse(
            field.toString().replace("\"RenderComponent\":{\"shaderKey\":\"defaultShader\",\"type\":\"MODEL\",\"assetName\":\"model_racer\"}",
                "\"RenderComponent\":{\"shaderKey\":\"defaultShader\",\"type\":\"MODEL\",\"assetName\":\"model_racer\",\"enabled\":false}"),
        )
        val sections = builders.entity(off, "3", readFrom(off, "3"), "ControlLine.abss").sections
        val render = sections.single { it.title == "Render" }
        assertFalse(render.enable!!.checked)
        assertTrue(render.disabled)
        assertFalse(sections.single { it.title == "Position" }.disabled)
    }

    private fun readFrom(root: JsonNode, id: String) = readEntitySections(
        root, id, null, editor, EntityAssetChoices(emptyList(), emptyMap()), messages,
    ) as EntitySections.Read

    // ---- UI screens and assets ----

    @Test
    fun aUiScreenLinksToTheScreenEditor() {
        val panel = builders.uiScreen("Main menu", "screens", "ControlLine.abss")
        assertEquals("UI screen · assets/screens", panel.header.subtitle)
        assertEquals(listOf("Screen"), panel.titles())
        assertEquals(listOf("name", "asset", ""), panel.sections.single().labels())
        assertEquals(LinkTarget.SCREEN_EDITOR, (panel.sections.single().rows.last() as LinkRow).target)
    }

    private fun meta(asset: String) = SceneJson().parse(File(project, "assets/$asset/meta.json").readText())

    @Test
    fun aModelHasMetaAndAdditionalSections() {
        val panel = builders.asset("model_racer", meta("model_racer"), used = true, editable = emptySet(), "ControlLine.abss")
        assertEquals("MODEL asset", panel.header.subtitle)
        assertEquals("model", panel.header.iconKey)
        assertEquals(listOf("meta.json", "additional"), panel.titles())
        assertEquals(listOf("version", "lastModified", "uuid", "type"), panel.sections[0].labels())
        assertEquals(listOf(BadgeKind.READ_ONLY), panel.header.badges.map { it.kind })
    }

    @Test
    fun anUnusedAssetCarriesTheBadge() {
        val panel = builders.asset("clouds_fair", meta("clouds_fair"), used = false, editable = setOf("technique"), "ControlLine.abss")
        assertTrue(panel.header.badges.any { it.kind == BadgeKind.UNUSED })
        assertEquals("no scene uses this", panel.header.badges.single { it.kind == BadgeKind.UNUSED }.text)
    }

    @Test
    fun aFoliageAssetListsItsLayersAsAReadOnlyTable() {
        val panel = builders.asset("foliage_airfield_site", meta("foliage_airfield_site"), true, setOf("maskResolution"), "ControlLine.abss")
        assertEquals("foliage", panel.header.iconKey)
        val additional = panel.sections[1]
        val terrain = additional.row<AssetRow>("terrain")
        assertEquals("terrain", terrain.iconKey)
        val table = additional.rows.filterIsInstance<TableRow>().single()
        assertEquals(listOf("id", "kind", "model", "density"), table.columns)
        assertTrue(table.rows.isNotEmpty())
    }

    @Test
    fun anAssetWithEditorsDoesNotCarryTheReadOnlyBadgeAndSaysEditsAreShared() {
        val panel = builders.asset("foliage_airfield_site", meta("foliage_airfield_site"), true, setOf("maskResolution"), "ControlLine.abss")
        assertTrue(panel.header.badges.isEmpty())
        assertEquals("Edits change every instance that uses this asset.", panel.header.note)
        assertTrue(panel.footer.startsWith("The meta.json identity fields"))
    }

    private fun sceneWithLight(light: String) = SceneJson().parse(
        """{"format":"abyssus","formatVersion":1,"name":"S","ecs":{"entities":{"7":{"components":{"NameComponent":{"name":"L"},"LightComponent":{"light":$light}}}}}}""",
    )

    @Test
    fun aValueTheFileLacksIsDimAndAnExplicitDefaultIsNot() {
        val missing = sceneWithLight("""{"color":{"r":1,"g":1,"b":1,"a":1}}""")
        val absent = builders.entity(missing, "7", readFrom(missing, "7"), "p.abss").sections.single { it.title == "Light" }
        assertTrue(absent.row<NumberRow>("intensity").dim)
        assertFalse(absent.row<ColorRow>("color").dim)
        val written = sceneWithLight("""{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1}""")
        val explicit = builders.entity(written, "7", readFrom(written, "7"), "p.abss").sections.single { it.title == "Light" }
        assertEquals(absent.row<NumberRow>("intensity").value, explicit.row<NumberRow>("intensity").value)
        assertFalse(explicit.row<NumberRow>("intensity").dim)
    }

    @Test
    fun everyRowKindIsBuiltFromTheFixture() {
        val kinds = mutableSetOf<Class<*>>()
        for (id in listOf("0", "1", "2", "3")) {
            builders.entity(field, id, read(id), "p.abss").sections.forEach { s -> s.rows.forEach { kinds += it.javaClass } }
        }
        builders.project(abss, "p.abss", listOf("Field")).sections.forEach { s -> s.rows.forEach { kinds += it.javaClass } }
        builders.ecs(field, "p").sections.forEach { s -> s.rows.forEach { kinds += it.javaClass } }
        builders.uiScreen("a", "b", "p").sections.forEach { s -> s.rows.forEach { kinds += it.javaClass } }
        for (kind in listOf(
            TextRow::class, NumberRow::class, VectorRow::class, ChoiceRow::class, CheckRow::class, ColorRow::class,
            AssetRow::class, ReadonlyRow::class, LinkRow::class, NoteRow::class,
        )) assertTrue(kind.simpleName, kind.java in kinds)
    }

    private val untitled = File("../plugin-abyssus/src/test/testData/project/Untitled")

    private fun assetPanel(name: String, root: File = project, editable: Set<String> = emptySet(), used: Boolean = true) =
        builders.asset(name, SceneJson().parse(File(root, "assets/$name/meta.json").readText()), used, editable, "p.abss")

    @Test
    fun aModelListsItsFileFormatAndMaterials() {
        val additional = assetPanel("fence").sections[1]
        assertEquals(listOf("file", "format", "binary", "materials"), additional.labels())
        assertTrue(additional.row<ReadonlyRow>("materials").value.startsWith("[] ("))
    }

    @Test
    fun aTerrainPutsTheBaseTextureFirstAsAnAssetAndKeepsNullSplatsDim() {
        val additional = assetPanel("terrain_airfield_outer", editable = setOf("size")).sections[1]
        assertEquals(
            listOf("terrainFile", "size", "uv", "splatBase", "splatMap", "splatR", "splatG", "splatB", "splatA", ""),
            additional.labels(),
        )
        assertEquals("texture", additional.row<AssetRow>("splatBase").iconKey)
        assertTrue(additional.row<ReadonlyRow>("splatG").dim)
        assertEquals("size", (additional.row<TextRow>("size").field)?.field)
        assertEquals(CustomRow("terrain"), additional.rows.last())
    }

    @Test
    fun aFoliageAssetHasLayersWithTheModelAndDensity() {
        val additional = assetPanel("foliage_airfield_outer", editable = setOf("maskResolution")).sections[1]
        assertEquals(listOf("terrain", "maskResolution", "", "", ""), additional.labels())
        assertEquals("Layers (3)", (additional.rows[2] as SubHeadingRow).text)
        val table = additional.rows[3] as TableRow
        assertEquals(listOf("id", "kind", "model", "density"), table.columns)
        assertEquals(listOf("1", "OBJECT", "model_airfield_tree_broadleaf", "7.5625E-4"), table.rows.first().take(3) + table.rows.first()[3])
        assertEquals(CustomRow("foliage"), additional.rows.last())
    }

    @Test
    fun aTextureShowsItsFile() {
        val additional = assetPanel("texture_airfield_ground_asphalt").sections[1]
        assertEquals(listOf("file"), additional.labels())
        assertEquals("texture", assetPanel("texture_airfield_ground_asphalt").header.iconKey)
    }

    @Test
    fun aCubeSkyboxListsItsSixFacesAndHostsTheFacePreviews() {
        val additional = assetPanel("skybox_default", untitled).sections[1]
        assertEquals(listOf("top", "bottom", "left", "right", "front", "back", ""), additional.labels())
        assertEquals(CustomRow("faces"), additional.rows.last())
    }

    @Test
    fun anHdrSkyboxHostsItsPreview() {
        val panel = assetPanel("skybox_hdr", untitled)
        assertEquals("SKYBOX_HDR asset", panel.header.subtitle)
        assertEquals(CustomRow("hdr"), panel.sections[1].rows.last())
    }

    @Test
    fun aProceduralSkyGroupsTheAtmosphereAndNamesItsClouds() {
        val additional = assetPanel("skybox_physical", editable = setOf("mieG")).sections[1]
        assertEquals(listOf("vertex", "fragment", "clouds"), additional.labels().take(3))
        assertEquals("clouds", additional.row<AssetRow>("clouds").iconKey)
        val sub = additional.rows.indexOfFirst { it is SubHeadingRow }
        assertEquals("Atmosphere", (additional.rows[sub] as SubHeadingRow).text)
        assertEquals("planetRadius", additional.rows[sub + 1].label)
        assertEquals("mieG", (additional.row<TextRow>("mieG").field)?.field)
    }

    @Test
    fun cloudsListTheirLayersAsATable() {
        val additional = assetPanel("clouds_fair", editable = setOf("technique")).sections[1]
        assertEquals("technique", additional.rows.first().label)
        val table = additional.rows.filterIsInstance<TableRow>().single()
        assertEquals(listOf("layer", "type", "coverage", "density", "wind x, z"), table.columns)
        assertEquals(listOf("low", "CUMULUS", "0.3", "0.7", "4.0, 1.0"), table.rows[0])
        assertEquals(listOf("high", "CIRRUS", "0.25", "0.1", "20.0, 4.0"), table.rows[1])
    }

    @Test
    fun aUiAssetListsItsScreensAndLinksToTheEditor() {
        val panel = assetPanel("screens", editable = setOf("entryScreen"))
        val additional = panel.sections[1]
        assertEquals("additional.ui", additional.title)
        assertEquals(listOf("schemaVersion", "entryScreen"), additional.labels().take(2))
        val sub = additional.rows.filterIsInstance<SubHeadingRow>().single()
        assertTrue(sub.text, sub.text.startsWith("Screens ("))
        val table = additional.rows.filterIsInstance<TableRow>().single()
        assertEquals(listOf("name"), table.columns)
        assertEquals("Main menu", table.rows.first().single())
        assertEquals(LinkTarget.SCREEN_EDITOR, (additional.rows.last() as LinkRow).target)
    }

    @Test
    fun theMetaSectionShowsTheIdentityFieldsReadOnly() {
        val meta = assetPanel("fence").sections[0]
        assertTrue(meta.rows.all { it is ReadonlyRow })
        assertEquals("1", meta.row<ReadonlyRow>("version").value)
        assertTrue(meta.row<ReadonlyRow>("lastModified").value.endsWith(" UTC"))
        assertEquals("MODEL", meta.row<ReadonlyRow>("type").value)
    }

    @Test
    fun aMissingUuidReadsNotSetAndDim() {
        val sky = assetPanel("skybox_physical").sections[0]
        assertEquals("not set", sky.row<ReadonlyRow>("uuid").value)
        assertTrue(sky.row<ReadonlyRow>("uuid").dim)
    }

    @Test
    fun theTogglableMatrixFollowsTheBoard() {
        val withParent = SceneJson().parse(
            field.toString().replace(
                "\"NameComponent\":{\"name\":\"Racer\"}",
                "\"NameComponent\":{\"name\":\"Racer\"},\"ParentComponent\":{\"parentEntityId\":0}",
            ),
        )
        val racer = builders.entity(withParent, "3", readFrom(withParent, "3"), "p.abss").sections.associateBy { it.title }
        for (title in listOf("Render", "Rigid body", "Collider", "Plane")) assertNotNull(title, racer.getValue(title).enable)
        for (title in listOf("Name", "Position", "Parent")) assertNull(title, racer.getValue(title).enable)
        assertNotNull(builders.entity(field, "2", read("2"), "p.abss").sections.single { it.title == "Light" }.enable)
        assertNotNull(builders.entity(field, "1", read("1"), "p.abss").sections.single { it.title == "Pilot" }.enable)
        assertNotNull(builders.entity(field, "0", read("0"), "p.abss").sections.single { it.title == "Foliage" }.enable)
    }

    @Test
    fun settingsToggleTheirOwnEnabledKeyAndReflectItsState() {
        val panel = builders.scene(field, "p.abss", "scenes/Field.scene")
        val keys = panel.sections.mapNotNull { it.enable?.settingKey }
        assertEquals(listOf("ambientLightEnabled", "fogEnabled", "skyboxEnabled"), keys)
        assertTrue(panel.sections.mapNotNull { it.enable }.all { it.checked && it.target == null })
        val off = SceneJson().parse(field.toString().replace("\"ambientLightEnabled\":true", "\"ambientLightEnabled\":false"))
        val ambient = builders.scene(off, "p.abss", "scenes/Field.scene").sections.single { it.title == "Ambient light" }
        assertFalse(ambient.enable!!.checked)
        assertTrue(ambient.disabled)
    }

    @Test
    fun collapseKeysAreStableAndUniquePerNode() {
        val first = builders.entity(field, "3", read("3"), "p.abss").sections.map { it.key }
        val second = builders.entity(field, "3", read("3"), "p.abss").sections.map { it.key }
        assertEquals(first, second)
        assertEquals(first.size, first.toSet().size)
        val scene = builders.scene(field, "p.abss", "s").sections.map { it.key }
        assertEquals(scene.size, scene.toSet().size)
    }
}
