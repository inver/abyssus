package net.nevinsky.abyssus.assets.sky.procedural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import net.nevinsky.abyssus.assets.testProject
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSkyMeta
import java.io.File

class ProceduralSkyFixtureTest {
    @Test
    fun fixtureFolderIsComplete() {
        val files = AssetFiles(testProject("Untitled"), JsonProcessor())
        val asset = files.loadAsset(ProceduralSkyMeta::class.java, "skybox_physical")
        assertNotNull("meta.json of skybox_physical must bind", asset)
        assertEquals(MetaType.SKYBOX_PROCEDURAL, asset!!.meta.type)
        assertNotNull(files.loadFile("skybox_physical", asset.meta.additional.vertex))
        assertNotNull(files.loadFile("skybox_physical", asset.meta.additional.fragment))
    }
}
