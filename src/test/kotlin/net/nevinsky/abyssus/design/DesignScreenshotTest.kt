/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.design

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.filetype.AssetIcons
import net.nevinsky.abyssus.filetype.ComponentIcons
import net.nevinsky.abyssus.filetype.EyeIcons
import net.nevinsky.abyssus.filetype.PropertyIcons
import net.nevinsky.abyssus.filetype.SceneIcons
import net.nevinsky.abyssus.filetype.ScenesIcons
import net.nevinsky.abyssus.filetype.AbyssusProjectIcons
import net.nevinsky.abyssus.projectView.AbyssusProjectViewPane
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Font
import java.awt.GridLayout
import javax.swing.JPanel
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import net.nevinsky.abyssus.assets.files.Asset
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.DtoEntry
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.SkyboxChoice
import net.nevinsky.abyssus.projectView.SkyboxChooserDialog
import net.nevinsky.abyssus.properties.AssetPropertiesPanel
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Screenshots of the plugin's components, written to `build/screenshots/`, and the measurable parts of the design canvas
 * ("Abyssus Panel Design": its icon set, row sizes and texts) checked against them. The design is HTML, so pixels are not
 * compared; the sheet `build/screenshots/icons.png` and the panel images are for looking at next to the canvas.
 */
class DesignScreenshotTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun render(c: JComponent, width: Int, height: Int = c.preferredSize.height): BufferedImage {
        c.setSize(width, height)
        layoutTree(c)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        c.paint(g)
        g.dispose()
        return image
    }

    private fun layoutTree(c: Component) {
        if (c is Container) {
            c.doLayout()
            c.components.forEach(::layoutTree)
        }
    }

    private fun save(name: String, image: BufferedImage) {
        assertTrue("$name is blank", (0 until image.width step 3).any { x -> (0 until image.height step 3).any { y -> image.getRGB(x, y) ushr 24 != 0 } })
        ImageIO.write(image, "png", File(out, "$name.png"))
    }

    /** Writes what differs from the design to `build/screenshots/<name>-diff.txt`; fails on it only with `-Dabyssus.designStrict=true`. */
    private fun report(name: String, differences: List<String>) {
        File(out, "$name-diff.txt").writeText(if (differences.isEmpty()) "matches the design\n" else differences.joinToString("\n", postfix = "\n"))
        if (differences.isNotEmpty()) println("design differences ($name):\n" + differences.joinToString("\n"))
        if (System.getProperty("abyssus.designStrict") == "true") assertTrue("differs from the design ($name):\n" + differences.joinToString("\n"), differences.isEmpty())
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.label() ?: (node as AbyssusAssetNode).virtualFile.name
    private fun DtoEntry.label() = name
    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }
    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun entity(id: String) = descend(children(descend(abss(), "scenes")).single(), "ecs", id)
    private fun asset(name: String) = children(descend(abss(), "assets")).single { (it as DtoEntryNode).label == name }

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile }!!.filter { it.extension in setOf("json", "png") }.forEach {
                myFixture.copyFileToProject("$dir/assets/${d.name}/${it.name}", "$dir/assets/${d.name}/${it.name}")
            }
        }
    }

    /** What the design's icon set says each icon looks like: its name there, the icon here, and its hue (`#rrggbb`). */
    private val designIcons: List<Triple<String, Icon, Int>> = listOf(
        Triple("Project", AbyssusProjectIcons.FILE, 0x3fb8c9),
        Triple("Scene", SceneIcons.FILE, 0x7bd88f),
        Triple("Ambient light", PropertyIcons.LIGHT, 0xe0c458),
        Triple("Fog", PropertyIcons.FOG, 0x9da0a8),
        Triple("Skybox", PropertyIcons.SKYBOX, 0x5aa7ff),
        Triple("ECS", PropertyIcons.ECS, 0xc792ea),
        Triple("Folder", ScenesIcons.LIST, 0x9da0a8),
        Triple("Model", AssetIcons.forType("MODEL"), 0xff9e6b),
        Triple("Terrain", AssetIcons.forType("TERRAIN"), 0xb5c46a),
        Triple("Transform", ComponentIcons.TRANSFORM, 0x9ec1ff),
        Triple("Material", AssetIcons.forType("MATERIAL"), 0xf2a07b),
        Triple("Physics", ComponentIcons.PHYSICS, 0x7bd88f),
        Triple("Particles", ComponentIcons.PARTICLES, 0x5aa7ff),
        Triple("Enabled", EyeIcons.ON, 0x3fb8c9),
        Triple("Disabled", EyeIcons.OFF, 0x8a8e96),
        Triple("Shader", AssetIcons.forType("SHADER"), 0xf47fb0),
    )

    private fun drawn(icon: Icon, size: Int = 32): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        icon.paintIcon(null, g, (size - icon.iconWidth) / 2, (size - icon.iconHeight) / 2)
        g.dispose()
        return image
    }

    /** The average colour of the icon's mostly opaque pixels. */
    private fun hue(icon: Icon): Color {
        val image = drawn(icon)
        var r = 0L; var gr = 0L; var b = 0L; var n = 0
        for (x in 0 until image.width) for (y in 0 until image.height) {
            val c = Color(image.getRGB(x, y), true)
            if (c.alpha > 160) { r += c.red; gr += c.green; b += c.blue; n++ }
        }
        assertTrue("an icon draws nothing", n > 0)
        return Color((r / n).toInt(), (gr / n).toInt(), (b / n).toInt())
    }

    private fun distance(a: Color, b: Int) =
        maxOf(Math.abs(a.red - (b shr 16 and 255)), Math.abs(a.green - (b shr 8 and 255)), Math.abs(a.blue - (b and 255)))

    fun testIconSheetAgainstTheDesignsIconSet() {
        val cell = 220
        val columns = 4
        val rows = (designIcons.size + columns - 1) / columns
        val sheet = BufferedImage(cell * columns, 64 * rows, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0x2b2d30)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = Font("SansSerif", Font.PLAIN, 12)
        val off = mutableListOf<String>()
        designIcons.forEachIndexed { i, (name, icon, want) ->
            val x = (i % columns) * cell
            val y = (i / columns) * 64
            // the icon here (left) and the design's colour (right swatch)
            g.drawImage(drawn(icon, 40), x + 12, y + 12, null)
            g.color = Color(want)
            g.fillRoundRect(x + 60, y + 24, 16, 16, 4, 4)
            g.color = Color(0xdfe1e5)
            g.drawString(name, x + 84, y + 36)
            val got = hue(icon)
            if (distance(got, want) > ICON_TOLERANCE) off += "$name is #%06x, the design has #%06x".format(got.rgb and 0xffffff, want)
        }
        g.dispose()
        save("icons", sheet)
        report("icons", off)
    }

    fun testAbyssusTree() {
        copyProject()
        val pane = AbyssusProjectViewPane(project)
        try {
            val component = pane.createComponent()
            pane.updateFromRoot(true)
            PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(pane.tree, 4), 30_000)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            save("tree", render(pane.tree, 560, maxOf(pane.tree.preferredSize.height, 200)))
            assertNotNull(component)
        } finally {
            Disposer.dispose(pane)
        }
    }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, { it.run() }, { it.run() })

    fun testPropertiesPanelStates() {
        copyProject()
        val p = panel()
        fun shot(name: String, node: Any?) {
            p.show(node)
            save("properties-$name", render(p, 460, 720))
        }
        shot("empty", null)
        shot("skybox", asset("skybox_default"))
        shot("model", children(descend(abss(), "assets")).first { (it as DtoEntryNode).value.value.let { v -> v is Asset<*> && v.meta.type.name == "MODEL" } })
        shot("terrain", children(descend(abss(), "assets")).first { (it as DtoEntryNode).value.value.let { v -> v is Asset<*> && v.meta.type.name == "TERRAIN" } })
        shot("entity", entity("0"))
        shot("entity-camera", entity("4"))
        shot("component", descend(entity("4"), "CameraComponent"))
        shot("component-unmodeled", descend(entity("0"), "PickableComponent"))
        shot("scene-not-an-asset", descend(abss(), "scenes"))
    }

    fun testSkyboxChooser() {
        val choices = listOf(
            SkyboxChoice("nebula", 6, listOf("png"), 1, false),
            SkyboxChoice("dusk", 6, listOf("png"), 0, true),
            SkyboxChoice("abyss-night", 6, listOf("png"), 0, true),
        )
        val d = SkyboxChooserDialog(project, choices, "nebula")
        Disposer.register(testRootDisposable, d.disposable)
        val rows = JPanel(GridLayout(0, 1))
        choices.indices.forEach { rows.add(d.renderedRow(it + 1, selected = it == 0)) }
        save("chooser-rows", render(rows, 480, rows.preferredSize.height))
        val differences = mutableListOf<String>()
        // the design's picker rows are 56px high, with the selection #2e436e
        choices.indices.forEach {
            val h = d.renderedRow(it + 1, false).preferredSize.height
            if (h < 56) differences += "chooser row ${it + 1} is ${h}px high, the design has 56px"
        }
        report("chooser", differences)
    }

    private companion object {
        const val ICON_TOLERANCE = 48
    }
}
