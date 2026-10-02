package net.nevinsky.abyssus.sceneview.skybox

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.sceneview.MetaType
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles
import java.io.File

class ProceduralSkyFixtureTest : BasePlatformTestCase() {
    fun testFixtureFolderIsComplete() {
        val files = ProjectAssetFiles(File("src/test/testData/project/Untitled"))
        val asset = files.loadAsset(ProceduralSkyMeta::class.java, "skybox_physical")
        assertNotNull("meta.json of skybox_physical must bind", asset)
        assertEquals(MetaType.SKYBOX_PROCEDURAL, asset!!.meta.type)
        assertNotNull(files.loadFile("skybox_physical", asset.meta.additional.vertex))
        assertNotNull(files.loadFile("skybox_physical", asset.meta.additional.fragment))
    }
}
