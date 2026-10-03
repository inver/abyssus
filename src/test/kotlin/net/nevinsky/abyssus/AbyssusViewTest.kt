/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.dto.ProjectDto
import net.nevinsky.abyssus.dto.SceneError
import net.nevinsky.abyssus.projectView.childrenOf
import net.nevinsky.abyssus.projectView.elementLabel
import net.nevinsky.abyssus.dto.ProjectReader
import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.projectView.foldToggles
import net.nevinsky.abyssus.filetype.AbyssusProjectFileType
import net.nevinsky.abyssus.filetype.AbyssusProjectIcons
import net.nevinsky.abyssus.filetype.SceneFileType
import net.nevinsky.abyssus.filetype.SceneIcons
import net.nevinsky.abyssus.language.GltfFileType
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.toggleEnabled

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class AbyssusViewTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/project"

    /** Copies only the project file and its scenes; the binary assets exceed the test VFS size limit. */
    private fun fixture(): VirtualFile {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        return myFixture.findFileInTempDir("Untitled")
    }

    /** The scene's current name: the fixture may have been renamed through the IDE's Rename Scene action. */
    private val fixtureSceneName: String by lazy {
        parseScene(java.io.File("$testDataPath/Untitled/scenes/Main Scene.scene").readText()).name!!
    }

    private fun add(path: String, text: String): VirtualFile = myFixture.addFileToProject(path, text).virtualFile

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        val p: PresentationData = node.presentation
        return p.presentableText ?: p.coloredText.joinToString("") { it.text }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun root() = AbyssusRootNode(project, ViewSettings.DEFAULT)

    private fun allAssets(): List<AbyssusAssetNode> = children(root()).map { it as AbyssusAssetNode }

    private fun asset(name: String) = allAssets().single { text(it).startsWith(name) }

    // 1. recognition

    fun testFileTypes() {
        assertEquals("scene", SceneFileType.INSTANCE.defaultExtension)
        assertEquals("abss", AbyssusProjectFileType.INSTANCE.defaultExtension)
        assertNotNull(SceneIcons.FILE)
        assertNotNull(AbyssusProjectIcons.FILE)
        assertNotSame(SceneIcons.FILE, AbyssusProjectIcons.FILE)
        assertEquals("gltf", GltfFileType.INSTANCE.defaultExtension)
        assertSame(SceneFileType.INSTANCE, com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName("a.scene"))
        assertSame(AbyssusProjectFileType.INSTANCE, com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName("a.abss"))
    }

    fun testPaneRegistered() {
        assertTrue(AbstractProjectViewPane.EP.getExtensions(project).any { it.id == "Abyssus" })
    }

    // 2. readers

    fun testSceneReader() {
        val dir = fixture()
        val file = dir.findFileByRelativePath("scenes/Main Scene.scene")!!
        val before = file.contentsToByteArray()
        val scene = service<SceneReader>().read(file).obj!!
        assertEquals(fixtureSceneName, scene.name)
        assertEquals(true, scene.fogEnabled)
        assertEquals(0.001f, scene.fog!!.density!!, 0f)
        assertEquals(0.3f, scene.ambientLight!!.intensity!!, 0f)
        assertEquals("skybox_physical", scene.skyboxName)
        assertEquals(listOf("id", "name", "ambientLightEnabled", "ambientLight", "fogEnabled", "fog", "skyboxEnabled", "skyboxName", "ecs"),
            childrenOf(scene).map { it.name })
        assertEquals(before.toList(), file.contentsToByteArray().toList())
    }

    fun testRowNamesAndOrderAreStable() {
        assertEquals(listOf("name", "scenes", "assets"), childrenOf(ProjectDto("n", emptyList(), emptyList())).map { it.name })
        assertEquals(listOf("type", "uuid"), childrenOf(testAsset("a", "u", "MODEL", listOf("r"), true)).map { it.name })
        assertEquals(listOf("error"), childrenOf(SceneError(add("e.scene", "x"), "boom")).map { it.name })
        val fog = parseScene("""{"fog":{"color":{"r":1,"g":2,"b":3,"a":4},"density":0.5}}""").fog!!
        assertEquals(listOf("color", "density", "gradient"), childrenOf(fog).map { it.name })
        assertEquals(listOf("r", "g", "b", "a"), childrenOf(fog.color).map { it.name })
    }

    fun testMistypedFieldFailsOnlyThatScene() {
        add("m/a.abss", """{"name":"m"}""")
        add("m/scenes/good.scene", """{"name":"Good"}""")
        add("m/scenes/bad.scene", """{"name":"Bad","fogEnabled":"nope"}""")
        val dto = project.service<ProjectReader>().read(myFixture.findFileInTempDir("m/a.abss")).obj!!
        assertEquals(2, dto.scenes.size)
        assertEquals("Good", (dto.scenes.single { it is SceneDto } as SceneDto).name)
        assertNotNull((dto.scenes.single { it is SceneError } as SceneError).error)
    }

    fun testEmptyScene() {
        assertTrue(!service<SceneReader>().read(add("empty.scene", "")).success)
        assertTrue(!service<SceneReader>().read(add("bad.scene", "{oops")).success)
        val scene = parseScene("{}")
        assertEquals(SceneDto(), scene)
        assertEquals(9, childrenOf(scene).size)
        assertTrue(childrenOf(scene).all { it.value == null })
    }

    fun testProjectReaderLoadsScenesFolder() {
        val file = fixture().findChild("Untitled.abss")!!
        val root = project.service<ProjectReader>().read(file).obj!!
        assertEquals(listOf("name", "scenes", "assets"), childrenOf(root).map { it.name })
        val scenes = (root as ProjectDto).scenes
        assertEquals(listOf("$fixtureSceneName (0)"), scenes.mapIndexed { i, it -> elementLabel("scenes", it, i) })
    }

    fun testProjectOrderAndMissingFolder() {
        add("p/a.abss", """{"name":"p"}""")
        add("p/scenes/b.scene", """{"name":"B"}""")
        add("p/scenes/a.scene", """{"name":"A"}""")
        add("p/scenes/c.scene", """{}""")
        add("p/scenes/d.scene", "broken")
        val items = (project.service<ProjectReader>().read(myFixture.findFileInTempDir("p/a.abss")).obj!!).scenes.mapIndexed { i, it -> elementLabel("scenes", it, i) }
        assertEquals(listOf("A", "B", "scenes[2]", "d.scene"), items)
        add("q/a.abss", """{"name":"q"}""")
        val none = project.service<ProjectReader>().read(myFixture.findFileInTempDir("q/a.abss")).obj!!
        assertTrue((none as ProjectDto).scenes.isEmpty())
        assertTrue(!project.service<ProjectReader>().read(add("bad.abss", "")).success)
    }

    // 3. discovery

    fun testDiscoveryIsExactSuffixCaseSensitive() {
        for (n in listOf("a.scene", "Forest.SCENE", "forest.scene.bak", "a.abss", "A.ABSS", "a.abss.bak", "scenes/s.scene", "x.txt")) add(n, "{}")
        val dir = myFixture.findFileInTempDir("")
        runWriteAction { dir.createChildDirectory(this, "excl").createChildData(this, "e.scene").setBinaryContent("{}".toByteArray()) }
        PsiTestUtil.addExcludedRoot(module, myFixture.findFileInTempDir("excl"))
        val names = allAssets().map { text(it) }
        // projects first, then standalone scenes; "scenes/s.scene" belongs to the sibling a.abss
        assertEquals(listOf("a.abss", "a.scene", "e.scene"), names)
        assertEquals(allAssets().size, allAssets().map { it.virtualFile }.toSet().size)
    }

    // 4. node rendering

    fun testNodeTree() {
        fixture()
        val scene = children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single()
        val top = children(scene).map { text(it) }
        assertEquals(listOf("ambientLight", "fog", "skybox: skybox_physical", "ecs  7 entities"), top)
        assertTrue(top.none { it.startsWith("id") || it.startsWith("name") })
        assertTrue("skybox: skybox_physical" in top)
        assertTrue(top.none { it.endsWith("Enabled: true") || it.endsWith("Enabled: false") })
        val fog = children(scene).first { text(it) == "fog" }
        assertEquals(true, (fog as DtoEntryNode).value.enabled)
        val sky = children(scene).first { text(it).startsWith("skybox: ") } as DtoEntryNode
        assertNotNull(sky.value.enabled) // skyboxEnabled's value may have been flipped in the fixture via the eye
        assertEquals(listOf("color", "density: 0.001", "gradient: 1.5"), children(fog).map { text(it) })
        val color = children(fog).first()
        assertEquals(listOf("r: 1.0", "g: 1.0", "b: 1.0", "a: 1.0"), children(color).map { text(it) })
        val ecs = children(scene).first { text(it).startsWith("ecs") }
        val entities = children(ecs)
        assertTrue(entities.none { text(it) == "entities" })
        assertTrue(entities.isNotEmpty())
        assertEquals("Model 0  5 components", text(entities.first()))
        assertTrue(children(entities.first()).any { text(it) == "Name" })
    }

    fun testProjectNodeExpandsScenesInline() {
        fixture()
        val project = asset("Untitled.abss")
        val props = children(project)
        assertEquals("name: Untitled", text(props[0]))
        val scenes = props.first { text(it).startsWith("Scenes") }
        val items = children(scenes)
        assertEquals(listOf("$fixtureSceneName (0)"), items.map { text(it) })
        assertTrue(children(items.single()).any { text(it) == "fog" })
    }

    fun testMalformedAssetKeepsOthers() {
        fixture()
        add("broken.scene", "{oops")
        val assets = allAssets()
        val broken = assets.single { text(it).startsWith("broken.scene") }
        assertTrue(text(broken).contains("Cannot read"))
        assertTrue(children(broken).isEmpty())
        assertTrue(children(asset("Untitled.abss")).isNotEmpty())
    }

    fun testProjectIsTopLevelAndScenesFolderIsHidden() {
        fixture()
        val top = children(root())
        assertEquals(listOf("Untitled.abss"), top.map { text(it) })
        children(top.single())
        assertEquals(listOf("Untitled.abss"), children(root()).map { text(it) })
    }

    fun testFoldToggles() {
        val props = childrenOf(parseScene("""{"fogEnabled":false,"fog":{},"skyboxEnabled":true,"skyboxName":"s","lonelyEnabled":true}"""))
        val folded = props.foldToggles()
        assertEquals(false, folded.first { it.name == "fog" }.enabled)
        assertEquals(true, folded.first { it.name == "skyboxName" }.enabled)
        assertTrue(folded.none { it.name == "fogEnabled" || it.name == "skyboxEnabled" })
        assertNull(folded.first { it.name == "name" }.enabled)
    }

    fun testClickingEyeFlipsEnabledInSceneFile() {
        val dir = fixture()
        val file = dir.findFileByRelativePath("scenes/Main Scene.scene")!!
        val original = String(file.contentsToByteArray())
        fun fog() = children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single()
            .let { children(it).first { n -> text(n) == "fog" } as DtoEntryNode }

        assertTrue(toggleEnabled(project, fog().value))
        val flipped = String(file.contentsToByteArray())
        assertTrue(Regex("\"fogEnabled\":\\s*false").containsMatchIn(flipped))
        assertEquals(false, fog().value.enabled)
        assertEquals(original.replace(Regex("\"fogEnabled\":(\\s*)true"), "\"fogEnabled\":$1false"), flipped)

        assertTrue(toggleEnabled(project, fog().value))
        assertEquals(original, String(file.contentsToByteArray()))
    }

    fun testToggleWithoutEnabledGateDoesNothing() {
        fixture()
        val name = children(asset("Untitled.abss")).first { text(it).startsWith("name") } as DtoEntryNode
        assertFalse(toggleEnabled(project, name.value))
    }

    fun testDisabledParameterAndItsChildrenAreGray() {
        val dir = fixture()
        val file = dir.findFileByRelativePath("scenes/Main Scene.scene")!!
        fun fog() = children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single()
            .let { children(it).first { n -> text(n) == "fog" } as DtoEntryNode }
        fun gray(n: AbstractTreeNode<*>): Boolean {
            n.update()
            return n.presentation.coloredText.all { it.attributes == com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES }
        }
        assertFalse(gray(fog()))
        assertTrue(toggleEnabled(project, fog().value))
        assertTrue(gray(fog()))
        assertTrue(children(fog()).all(::gray))
        assertTrue(children(children(fog()).first()).all(::gray))
        assertNotNull(file)
    }

    fun testEntryIdentityChangesWithToggleState() {
        fixture()
        val fog = children(children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single()).first { text(it) == "fog" } as DtoEntryNode
        val same = fog.value
        val flipped = net.nevinsky.abyssus.projectView.DtoEntry(same.path, same.name, same.value, !same.enabled!!, same.toggleName, same.source, same.parentKeys)
        assertFalse(same == flipped)
        assertEquals(same, net.nevinsky.abyssus.projectView.DtoEntry(same.path, same.name, same.value, same.enabled, same.toggleName, same.source, same.parentKeys))
    }

    fun testScenesListAndSceneEntriesHaveOwnIcons() {
        fixture()
        val scenes = children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }
        scenes.update()
        assertSame(net.nevinsky.abyssus.filetype.ScenesIcons.LIST, scenes.presentation.getIcon(false))
        val scene = children(scenes).single()
        scene.update()
        assertSame(SceneIcons.FILE, scene.presentation.getIcon(false))
        val fog = children(scene).first { text(it) == "fog" }
        fog.update()
        assertNotSame(SceneIcons.FILE, fog.presentation.getIcon(false))
    }

    fun testWellKnownPropertiesHaveOwnIcons() {
        fixture()
        val scene = children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single()
        fun icon(name: String) = children(scene).first { text(it).startsWith(name) }.let { it.update(); it.presentation.getIcon(false) }
        assertSame(net.nevinsky.abyssus.filetype.PropertyIcons.LIGHT, icon("ambientLight"))
        assertSame(net.nevinsky.abyssus.filetype.PropertyIcons.FOG, icon("fog"))
        assertSame(net.nevinsky.abyssus.filetype.PropertyIcons.SKYBOX, icon("skybox: "))
        assertSame(net.nevinsky.abyssus.filetype.PropertyIcons.ECS, icon("ecs"))
        assertEquals(4, setOf(icon("ambientLight"), icon("fog"), icon("skybox: "), icon("ecs")).size)
    }

    fun testSceneLabelShowsIdAndRenameEditsOnlyName() {
        val dir = fixture()
        val file = dir.findFileByRelativePath("scenes/Main Scene.scene")!!
        val original = String(file.contentsToByteArray())
        fun sceneNode() = children(children(asset("Untitled.abss")).first { text(it).startsWith("Scenes") }).single() as DtoEntryNode
        assertEquals("$fixtureSceneName (0)", text(sceneNode()))
        val entry = sceneNode().value
        assertEquals(file, net.nevinsky.abyssus.projectView.sceneFileOf(entry))
        assertEquals(fixtureSceneName, net.nevinsky.abyssus.projectView.sceneName(entry))
        assertTrue(net.nevinsky.abyssus.projectView.renameScene(project, file, "Forest"))
        assertEquals(original.replace(Regex("\"name\":(\\s*)\"$fixtureSceneName\""), "\"name\":$1\"Forest\""), String(file.contentsToByteArray()))
        assertEquals("Forest (0)", text(sceneNode()))
    }
}
