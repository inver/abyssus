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
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.dto.ProjectReader
import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.dto.foldToggles
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
        val scene = SceneReader.readScene(file).getOrThrow()
        assertEquals("Main Scene", scene.name)
        assertEquals(true, scene.fogEnabled)
        assertEquals(0.001f, scene.fog!!.density!!, 0f)
        assertEquals(0.3f, scene.ambientLight!!.intensity!!, 0f)
        assertNull(scene.skyboxName)
        assertEquals(listOf("id", "name", "ambientLightEnabled", "ambientLight", "fogEnabled", "fog", "skyboxEnabled", "skyboxName", "ecs"),
            scene.properties().map { it.name })
        assertEquals(before.toList(), file.contentsToByteArray().toList())
    }

    fun testEmptyScene() {
        assertTrue(SceneReader.read(add("empty.scene", "")) is AssetReadResult.Failure)
        assertTrue(SceneReader.read(add("bad.scene", "{oops")) is AssetReadResult.Failure)
        val props = SceneReader.parse("{}").properties()
        assertEquals(9, props.size)
    }

    fun testProjectReaderLoadsScenesFolder() {
        val file = fixture().findChild("Untitled.abss")!!
        val root = (ProjectReader.read(file) as AssetReadResult.Success).root
        assertEquals(listOf("name", "scenes"), root.properties.map { it.name })
        val scenes = root.properties[1].value as DtoValue.Items
        assertEquals(listOf("Main Scene"), scenes.items.map { (it as DtoValue.Obj).label })
    }

    fun testProjectOrderAndMissingFolder() {
        add("p/a.abss", """{"name":"p"}""")
        add("p/scenes/b.scene", """{"name":"B"}""")
        add("p/scenes/a.scene", """{"name":"A"}""")
        add("p/scenes/c.scene", """{}""")
        add("p/scenes/d.scene", "broken")
        val items = ((ProjectReader.read(myFixture.findFileInTempDir("p/a.abss")) as AssetReadResult.Success)
            .root.properties[1].value as DtoValue.Items).items.map { (it as DtoValue.Obj).label }
        assertEquals(listOf("A", "B", "scenes[2]".let { "scenes[2]" }, "d.scene"), items)
        add("q/a.abss", """{"name":"q"}""")
        val none = (ProjectReader.read(myFixture.findFileInTempDir("q/a.abss")) as AssetReadResult.Success).root
        assertTrue((none.properties[1].value as DtoValue.Items).items.isEmpty())
        assertTrue(ProjectReader.read(add("bad.abss", "")) is AssetReadResult.Failure)
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
        val scene = children(children(asset("Untitled.abss")).first { text(it) == "scenes" }).single()
        val top = children(scene).map { text(it) }
        assertEquals("id: 0", top[0])
        assertTrue("skyboxName: null" in top)
        assertTrue(top.none { it.endsWith("Enabled: true") || it.endsWith("Enabled: false") })
        val fog = children(scene).first { text(it) == "fog" }
        assertEquals(true, (fog as DtoEntryNode).value.enabled)
        val sky = children(scene).first { text(it).startsWith("skyboxName") } as DtoEntryNode
        assertNotNull(sky.value.enabled) // skyboxEnabled's value may have been flipped in the fixture via the eye
        assertEquals(listOf("color", "density: 0.001", "gradient: 1.5"), children(fog).map { text(it) })
        val color = children(fog).first()
        assertEquals(listOf("r: 1.0", "g: 1.0", "b: 1.0", "a: 1.0"), children(color).map { text(it) })
        val ecs = children(scene).first { text(it) == "ecs" }
        val entities = children(ecs).first { text(it) == "entities" }
        assertTrue(children(entities).isNotEmpty())
        assertTrue(children(children(entities).first()).any { text(it) == "components" })
    }

    fun testProjectNodeExpandsScenesInline() {
        fixture()
        val project = asset("Untitled.abss")
        val props = children(project)
        assertEquals("name: Untitled", text(props[0]))
        val scenes = props.first { text(it) == "scenes" }
        val items = children(scenes)
        assertEquals(listOf("Main Scene"), items.map { text(it) })
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
        val props = SceneReader.parse("""{"fogEnabled":false,"fog":{},"skyboxEnabled":true,"skyboxName":"s","lonelyEnabled":true}""").properties()
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
        fun fog() = children(children(asset("Untitled.abss")).first { text(it) == "scenes" }).single()
            .let { children(it).first { n -> text(n) == "fog" } as DtoEntryNode }

        assertTrue(toggleEnabled(project, fog().value))
        val flipped = String(file.contentsToByteArray())
        assertTrue(flipped.contains("\"fogEnabled\":false"))
        assertEquals(false, fog().value.enabled)
        assertEquals(original.replace("\"fogEnabled\":true", "\"fogEnabled\":false"), flipped)

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
        fun fog() = children(children(asset("Untitled.abss")).first { text(it) == "scenes" }).single()
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
        val fog = children(children(children(asset("Untitled.abss")).first { text(it) == "scenes" }).single()).first { text(it) == "fog" } as DtoEntryNode
        val same = fog.value
        val flipped = net.nevinsky.abyssus.projectView.DtoEntry(same.path, same.name, same.value, !same.enabled!!, same.toggleName, same.source, same.parentKeys)
        assertFalse(same == flipped)
        assertEquals(same, net.nevinsky.abyssus.projectView.DtoEntry(same.path, same.name, same.value, same.enabled, same.toggleName, same.source, same.parentKeys))
    }
}
