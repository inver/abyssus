/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AssetIcons
import net.nevinsky.abyssus.plugin.filetype.ComponentIcons
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
        assertSame(AssetIcons.forType("MODEL"), ComponentIcons.forComponent("RenderComponent"))
        assertSame(ComponentIcons.PHYSICS, ComponentIcons.forComponent("PhysicsComponent"))
        assertSame(ComponentIcons.PARTICLES, ComponentIcons.forComponent("ParticlesComponent"))
        assertSame(ComponentIcons.GENERIC, ComponentIcons.forComponent("NameComponent"))
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
        assertSame(PropertyIcons.ECS, icon(entity))
        val components = children(entity).associateBy { text(it) }
        assertSame(ComponentIcons.TRANSFORM, icon(components.getValue("Position")))
        assertSame(ComponentIcons.GENERIC, icon(components.getValue("Name")))
        assertSame(AssetIcons.forType("MODEL"), icon(components.getValue("Render")))
    }
}
