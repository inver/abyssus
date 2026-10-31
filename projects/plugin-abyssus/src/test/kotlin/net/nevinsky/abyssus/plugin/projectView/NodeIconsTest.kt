/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.parseDocument
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.plugin.filetype.AssetIcons
import net.nevinsky.abyssus.plugin.filetype.ComponentIcons
import net.nevinsky.abyssus.plugin.filetype.EntityIcons
import net.nevinsky.abyssus.plugin.filetype.PropertyIcons
import net.nevinsky.abyssus.plugin.filetype.SceneIcons
import net.nevinsky.abyssus.plugin.filetype.ScenesIcons

class NodeIconsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        return node.presentation.coloredText.joinToString("") { it.text }
    }

    private fun icon(node: AbstractTreeNode<*>) = node.presentation.getIcon(false)

    /** [typeJson] is the JSON value of the entity's `type`; null for an entity without one. */
    private fun entityNode(typeJson: String?): DtoEntryNode {
        val entity = parseDocument("""{${typeJson?.let { """"type":$it,""" } ?: ""}"components":{}}""")
        return DtoEntryNode(project, "/scene/ecs", DtoRow("1", entity), null, listOf("ecs"))
    }

    fun testEntityTypesHaveDistinctIcons() {
        val icons = EntityType.entries.map { type ->
            val node = entityNode(""""${type.name}"""")
            node.update()
            val icon = icon(node)
            assertNotSame(type.name, PropertyIcons.ECS, icon)
            assertEquals(16, icon!!.iconWidth)
            assertEquals(16, icon.iconHeight)
            icon
        }
        assertEquals(EntityType.entries.size, icons.toSet().size)
    }

    fun testMissingAndUnknownEntityTypesKeepGenericIcon() {
        listOf(null, "null", """"FUTURE"""", "7").forEach {
            val node = entityNode(it)
            node.update()
            assertSame(PropertyIcons.ECS, icon(node))
        }
    }

    fun testTypeChangeInvalidatesRowEvenWhenLabelAndComponentCountStayTheSame() {
        val before = entityNode(""""OBJECT"""").value
        val same = entityNode(""""OBJECT"""").value
        val after = entityNode(""""CAMERA"""").value
        assertEquals(before, same)
        assertEquals(rowText(before), rowText(after))
        assertFalse("A refreshed type must replace the existing row", before == after)
    }

    fun testEveryKindOfNodeHasAnIcon() {
        listOf(
            SceneIcons.FILE, ScenesIcons.LIST, PropertyIcons.LIGHT, PropertyIcons.FOG, PropertyIcons.SKYBOX, PropertyIcons.ECS,
            ComponentIcons.GENERIC, ComponentIcons.TRANSFORM, ComponentIcons.PHYSICS, ComponentIcons.PARTICLES, AssetIcons.UNKNOWN,
            AssetIcons.forType("MODEL"), AssetIcons.forType("TERRAIN"), AssetIcons.forType("SKYBOX"), AssetIcons.forType("MATERIAL"),
        ).forEach { assertNotNull(it) }
    }

    fun testComponentNamesMapToKindsAndUnknownOnesAreGeneric() {
        assertSame(ComponentIcons.TRANSFORM, ComponentIcons.forComponent("PositionComponent"))
        assertSame(ComponentIcons.TRANSFORM, ComponentIcons.forComponent("Transform"))
        assertSame(ComponentIcons.RENDER, ComponentIcons.forComponent("RenderComponent"))
        assertSame(ComponentIcons.RIGID_BODY, ComponentIcons.forComponent("PhysicsComponent"))
        assertSame(ComponentIcons.COLLIDER, ComponentIcons.forComponent("ColliderComponent"))
        assertSame(ComponentIcons.PLANE, ComponentIcons.forComponent("PlaneComponent"))
        assertSame(ComponentIcons.PILOT, ComponentIcons.forComponent("PilotComponent"))
        assertSame(ComponentIcons.PARTICLES, ComponentIcons.forComponent("ParticlesComponent"))
        assertSame(ComponentIcons.NAME, ComponentIcons.forComponent("NameComponent"))
        assertSame(ComponentIcons.GENERIC, ComponentIcons.forComponent("SomethingNew"))
        assertEquals(4, setOf(ComponentIcons.TRANSFORM, ComponentIcons.PHYSICS, ComponentIcons.PARTICLES, ComponentIcons.GENERIC).size)
    }

    fun testEntityAndComponentRowsUseTheirIcons() {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        val abss = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { text(it).endsWith(".abss") }
        val scene = children(children(abss).first { text(it).startsWith("Scenes") }).single()
        val ecs = children(scene).first { text(it).startsWith("ecs") }
        val entity = children(ecs).first()
        text(entity)
        assertSame(EntityIcons.forType(EntityType.OBJECT), icon(entity))
        val components = children(entity).associateBy { text(it) }
        assertSame(ComponentIcons.TRANSFORM, icon(components.getValue("Position")))
        assertSame(ComponentIcons.NAME, icon(components.getValue("Name")))
        assertSame(ComponentIcons.RENDER, icon(components.getValue("Render")))
    }

    fun testCloudAssetsHaveTheirOwnIcon() {
        val preset = AssetIcons.forType("CLOUDS")
        assertSame(preset, AssetIcons.forType(net.nevinsky.abyssus.lib.core.assets.MetaType.CLOUDS))
        assertNotSame(AssetIcons.UNKNOWN, preset)
        assertNotSame(AssetIcons.forType("SKYBOX_PROCEDURAL"), preset)

        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject(
            "p/assets/clouds_storm/meta.json",
            """{"format":"abyssus","formatVersion":1,"version":1,"type":"CLOUDS","additional":{"low":{"type":"stratocumulus"},"mid":{"type":"altostratus"}}}""",
        )
        fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
        val abss = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        val assets = children(abss).single { label(it) == "assets" }
        val group = children(assets).single()
        assertEquals("Clouds  1", text(group))
        val row = children(group).single()
        assertTrue(text(row), text(row).contains("clouds_storm"))
        assertSame(preset, icon(row))
    }
}
