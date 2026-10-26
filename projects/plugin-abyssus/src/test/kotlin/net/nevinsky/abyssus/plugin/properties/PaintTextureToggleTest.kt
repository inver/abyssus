/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.projectView.ComponentTarget
import net.nevinsky.abyssus.plugin.sceneview.PaintTextureRequests
import net.nevinsky.abyssus.plugin.sceneview.SculptRequests
import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.plugin.testPanelServices
import java.awt.Component
import java.awt.Container
import javax.swing.JToggleButton

class PaintTextureToggleTest : BasePlatformTestCase() {
    private fun scene(): VirtualFile {
        myFixture.addFileToProject("p/p.abss", """{"format":"abyssus","formatVersion":1,"name":"p"}""")
        val terrain = """{"renderable":{"kind":"asset","shaderKey":"terrain","asset":{"type":"TERRAIN","assetName":"hills"}}}"""
        return myFixture.addFileToProject("p/scenes/s.scene", SceneJson().pretty(SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
            "0":{"components":{"RenderComponent":$terrain}},
            "1":{"components":{"RenderComponent":$terrain}}}}}"""))).virtualFile
    }

    /** Built and then notified, as when the Properties panel shows it: the toggles follow the project's requests from then on. */
    private fun view(file: VirtualFile, entity: String = "0") = EntityDetailsView(
        project, readEntityState(ComponentTarget(file, entity, null), testPanelServices(project)) as PanelState.EntityDetails, testMetaFiles(),
    ).also { shown += it; it.addNotify() }

    private val shown = mutableListOf<Component>()

    override fun tearDown() {
        try { shown.forEach { it.removeNotify() } } finally { super.tearDown() }
    }

    private fun toggle(c: Component, name: String): JToggleButton = find(c, name) as JToggleButton

    private fun find(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

    fun testEachToggleEndsTheOther() {
        val file = scene()
        val v = view(file)
        val sculpt = project.service<SculptRequests>()
        val paint = project.service<PaintTextureRequests>()
        toggle(v, "sculpt-terrain").doClick()
        assertEquals("0", sculpt.current?.entityId)
        assertNull(paint.current)
        toggle(v, "paint-texture").doClick()
        assertEquals("0", paint.current?.entityId)
        assertNull("Paint Texture ends Sculpt Terrain", sculpt.current)
        assertTrue(toggle(v, "paint-texture").isSelected)
        assertFalse(toggle(v, "sculpt-terrain").isSelected)
        toggle(v, "sculpt-terrain").doClick()
        assertEquals("0", sculpt.current?.entityId)
        assertNull("Sculpt Terrain ends Paint Texture", paint.current)
        assertFalse(toggle(v, "paint-texture").isSelected)
        toggle(v, "sculpt-terrain").doClick()
        assertNull(sculpt.current)
    }

    fun testTheToggleFollowsTheRequestsAndAnotherEntityDoesNotShowItOn() {
        val file = scene()
        val v = view(file)
        val other = view(file, "1")
        val paint = project.service<PaintTextureRequests>()
        paint.start(file, "0")
        assertTrue(toggle(v, "paint-texture").isSelected)
        assertFalse(toggle(other, "paint-texture").isSelected)
        paint.start(file, "1")
        assertTrue(toggle(other, "paint-texture").isSelected)
        assertFalse("a toggle of another entity stays off", toggle(v, "paint-texture").isSelected)
        paint.stop(file)
        assertNull(paint.current)
    }
}
