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

package net.nevinsky.abyssus.dto

import net.nevinsky.abyssus.filetype.SceneJson
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import net.nevinsky.abyssus.filetype.AssetIcons
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.sceneview.Asset
import net.nevinsky.abyssus.testAsset
import java.util.UUID

class ProjectAssetsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    /** The asset reader needs real UUIDs, so tests name assets by a short label and meet it as the UUID it hashes to. */
    private fun u(label: String): String = UUID.nameUUIDFromBytes(label.toByteArray()).toString()

    private fun asset(name: String, label: String, type: String, references: List<String> = emptyList()) =
        testAsset(name, label, type, references.map(::u))

    private fun add(path: String, text: String): VirtualFile = myFixture.addFileToProject(path, text).virtualFile

    private fun project(scenes: Map<String, String>, assets: Map<String, String>, root: String = "p"): VirtualFile {
        val abss = add("$root/P.abss", """{"name":"P"}""")
        scenes.forEach { (name, text) -> add("$root/scenes/$name.scene", text) }
        assets.forEach { (folder, meta) -> add("$root/assets/$folder/meta.json", meta) }
        return abss
    }

    private fun readAssets(abss: VirtualFile): List<Asset<Any>> =
        project.service<ProjectReader>().read(abss).obj!!.assets

    private fun ecs(vararg refs: String) =
        """{"ecs":{"entities":{${refs.mapIndexed { i, r -> "\"$i\":{\"components\":{\"RenderComponent\":{\"renderable\":$r}}}" }.joinToString(",")}}}}"""

    private fun modelRef(name: String, shader: String = "defaultShader") =
        """{"shaderKey":"$shader","asset":{"type":"MODEL","assetName":"$name"}}"""

    private fun meta(type: String, label: String, additional: String = "{}") =
        """{"version":1,"lastModified":1,"uuid":"${u(label)}","type":"$type","additional":$additional}"""

    // 2.1 listing

    fun testFixtureProjectListsNineAssetsInNameOrderWithTypes() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        java.io.File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach {
            myFixture.copyFileToProject("$dir/assets/${it.name}/meta.json", "$dir/assets/${it.name}/meta.json")
        }
        val assets = readAssets(myFixture.findFileInTempDir("$dir/Untitled.abss"))
        assertEquals(9, assets.size)
        assertEquals(assets.map { it.name }.sorted(), assets.map { it.name })
        fun type(a: Asset<Any>) = a.meta.type.name
        assertEquals("SKYBOX", type(assets.single { it.name == "skybox_default" }))
        assertEquals("SKYBOX_PROCEDURAL", type(assets.single { it.name == "skybox_physical" }))
        assertEquals("SKYBOX_HDR", type(assets.single { it.name == "skybox_hdr" }))
        assertEquals("TERRAIN", type(assets.single { it.name.startsWith("terrain_") }))
        assertEquals(4, assets.count { type(it) == "MODEL" && it.name.startsWith("model_") })
        // Main Scene names four of them directly; `tree` and one model are not reached; the skybox is named by the scene
        assertEquals(setOf("skybox_hdr", "skybox_physical", "tree", "model_828d51e4-8427-4769-bcb6-13f8f21f23e9"), assets.filter { it.unused }.map { it.name }.toSet())
    }

    fun testProjectWithoutAssetsFolderHasEmptyList() {
        val abss = add("none/P.abss", """{"name":"P"}""")
        assertEquals(emptyList<Asset<Any>>(), readAssets(abss))
    }

    fun testBrokenMetaStillListsTheAssetWithUnknownType() {
        val abss = project(emptyMap(), mapOf("good" to meta("MODEL", "u1"), "bad" to "{ nope", "empty" to ""), "broken")
        val assets = readAssets(abss).associateBy { it.name }
        assertEquals(setOf("bad", "empty", "good"), assets.keys)
        assertEquals("UNKNOWN", assets.getValue("bad").meta.type.name)
        assertEquals("MODEL", assets.getValue("good").meta.type.name)
    }

    fun testStrayFilesInAssetsAreNotAssets() {
        val abss = project(emptyMap(), mapOf("a" to meta("MODEL", "u1")), "stray")
        add("stray/assets/notes.txt", "x")
        assertEquals(listOf("a"), readAssets(abss).map { it.name })
    }

    // 2.2 direct usage

    fun testSceneNamesAssetsSkyboxesAndShadersDirectly() {
        val abss = project(
            mapOf("S" to """{"skyboxName":"sky","ecs":{"x":{"asset":{"assetName":"m1"},"shaderKey":"myShader"}}}"""),
            mapOf(
                "m1" to meta("MODEL", "u1"), "m2" to meta("MODEL", "u2"), "sky" to meta("SKYBOX", "u3"),
                "myShader" to meta("SHADER", "u4"), "otherShader" to meta("SHADER", "u5"),
            ),
            "direct",
        )
        val unused = readAssets(abss).filter { it.unused }.map { it.name }
        assertEquals(listOf("m2", "otherShader"), unused)
    }

    fun testBundledShaderKeyIsIgnored() {
        val abss = project(mapOf("S" to ecs(modelRef("m1", shader = "pbr"))), mapOf("m1" to meta("MODEL", "u1")), "bundled")
        val assets = readAssets(abss)
        assertEquals(listOf("m1"), assets.map { it.name })
        assertFalse(assets.single().unused)
    }

    fun testReferenceFromAnySceneCounts() {
        val abss = project(
            mapOf("A" to ecs(modelRef("m1")), "B" to ecs(modelRef("m2"))),
            mapOf("m1" to meta("MODEL", "u1"), "m2" to meta("MODEL", "u2"), "m3" to meta("MODEL", "u3")),
            "anyscene",
        )
        assertEquals(listOf("m3"), readAssets(abss).filter { it.unused }.map { it.name })
    }

    fun testMalformedSceneDoesNotDropOtherScenesReferences() {
        val abss = project(
            mapOf("A" to "{ nope", "B" to ecs(modelRef("m2"))),
            mapOf("m1" to meta("MODEL", "u1"), "m2" to meta("MODEL", "u2")),
            "badscene",
        )
        assertEquals(listOf("m1"), readAssets(abss).filter { it.unused }.map { it.name })
    }

    // 2.3 transitive usage

    fun testSplatTextureIsUsedThroughItsTerrain() {
        val abss = project(
            mapOf("S" to """{"ecs":{"e":{"asset":{"assetName":"terr"}}}}"""),
            mapOf(
                "terr" to meta("TERRAIN", "ut", """{"splatMap":"${u("us1")}","splatR":"${u("us2")}","splatG":null}"""),
                "splat1" to meta("TEXTURE", "us1"), "splat2" to meta("TEXTURE", "us2"), "lonely" to meta("TEXTURE", "ul"),
            ),
            "splat",
        )
        assertEquals(listOf("lonely"), readAssets(abss).filter { it.unused }.map { it.name })
    }

    fun testMaterialIsUsedThroughItsModelButNotThroughAnUnusedOne() {
        val abss = project(
            mapOf("S" to ecs(modelRef("used"))),
            mapOf(
                "used" to meta("MODEL", "u1", """{"materials":["${u("um1")}"]}"""),
                "dead" to meta("MODEL", "u2", """{"materials":["${u("um2")}"]}"""),
                "mat1" to meta("MATERIAL", "um1"), "mat2" to meta("MATERIAL", "um2"),
            ),
            "materials",
        )
        assertEquals(listOf("dead", "mat2"), readAssets(abss).filter { it.unused }.map { it.name })
    }

    fun testCyclesTerminateAndUnknownUuidsAreIgnored() {
        val assets = listOf(
            asset("a", "ua", "MODEL", listOf("ub", "nope")),
            asset("b", "ub", "MATERIAL", listOf("ua")),
            asset("c", "uc", "MODEL"),
        )
        assertEquals(setOf("a", "b"), project.service<ProjectReader>().usedAssets(assets, setOf("a", "missing")))
        assertEquals(emptySet<String>(), project.service<ProjectReader>().usedAssets(assets, emptySet()))
    }

    // 2.4 stamp

    fun testAddingAnAssetFolderChangesTheStamp() {
        val abss = project(emptyMap(), mapOf("a" to meta("MODEL", "u1")), "stamp")
        val before = project.service<ProjectReader>().stamp(abss)
        add("stamp/assets/b/meta.json", meta("MODEL", "u2"))
        assertTrue(before != project.service<ProjectReader>().stamp(abss))
    }

    fun testReadingLeavesFilesUnchanged() {
        val abss = project(mapOf("S" to ecs(modelRef("m1"))), mapOf("m1" to meta("MODEL", "u1")), "ro")
        val files = listOf(abss, abss.parent.findChild("scenes")!!.children.single(), abss.parent.findChild("assets")!!.findChild("m1")!!.findChild("meta.json")!!)
        val before = files.map { it.contentsToByteArray().toList() }
        project.service<ProjectReader>().read(abss)
        assertEquals(before, files.map { it.contentsToByteArray().toList() })
    }

    // 2.5 rendering

    fun testUnusedAssetsCarryAMarkerAndGrayText() {
        project(mapOf("S" to ecs(modelRef("used"))), mapOf("used" to meta("MODEL", "u1"), "dead" to meta("MODEL", "u2")), "render")
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        val projectNode = root.children.single { it is AbyssusAssetNode }
        val assetsNode = projectNode.children.map { it as AbstractTreeNode<*> }.single { text(it).startsWith("Assets") }
        val rows = assetsNode.children.map { it as AbstractTreeNode<*> }.associateBy { text(it).substringBefore("  ") }
        assertEquals(setOf("dead", "used"), rows.keys)
        // the badge is painted at the right edge of the row, not part of its text
        assertFalse(text(rows.getValue("dead")).contains("unused"))
        assertNotNull(net.nevinsky.abyssus.projectView.unusedBadgeFor((rows.getValue("dead") as net.nevinsky.abyssus.projectView.DtoEntryNode).value))
        assertNull(net.nevinsky.abyssus.projectView.unusedBadgeFor((rows.getValue("used") as net.nevinsky.abyssus.projectView.DtoEntryNode).value))
        fun gray(n: AbstractTreeNode<*>) = n.presentation.coloredText.all {
            it.attributes == SimpleTextAttributes.GRAYED_ATTRIBUTES || it.attributes == SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
        }
        assertTrue(gray(rows.getValue("dead")))
        val deadChildren = rows.getValue("dead").children.map { it as AbstractTreeNode<*> }
        assertTrue(deadChildren.isNotEmpty())
        assertTrue(deadChildren.all { it.update(); gray(it) })
        assertFalse(gray(rows.getValue("used")))
    }

    fun testEveryAssetTypeHasItsOwnIcon() {
        val types = listOf("MODEL", "TERRAIN", "SKYBOX", "SKYBOX_HDR", "TEXTURE", "PIXMAP_TEXTURE", "MATERIAL", "SHADER")
        val icons = types.map { AssetIcons.forType(it) } + AssetIcons.UNKNOWN
        assertEquals(types.size + 1, icons.toSet().size)
        assertSame(AssetIcons.UNKNOWN, AssetIcons.forType(null))
        assertSame(AssetIcons.UNKNOWN, AssetIcons.forType("BUNDLE"))
        icons.forEach { assertEquals(16, it.iconWidth) }
    }

    fun testAssetRowsUseTheirTypeIcon() {
        project(
            mapOf("S" to "{}"),
            mapOf("m" to meta("MODEL", "u1"), "t" to meta("TERRAIN", "u2"), "x" to "{ nope"),
            "icons",
        )
        val projectNode = AbyssusRootNode(project, ViewSettings.DEFAULT).children.single { it is AbyssusAssetNode }
        val assetsNode = projectNode.children.map { it as AbstractTreeNode<*> }.single { text(it).startsWith("Assets") }
        val icons = assetsNode.children.map { it as AbstractTreeNode<*> }.associate { text(it).substringBefore("  ") to it.presentation.getIcon(false) }
        assertSame(AssetIcons.forType("MODEL"), icons["m"])
        assertSame(AssetIcons.forType("TERRAIN"), icons["t"])
        assertSame(AssetIcons.UNKNOWN, icons["x"])
    }

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        return node.presentation.presentableText ?: node.presentation.coloredText.joinToString("") { it.text }
    }
}
