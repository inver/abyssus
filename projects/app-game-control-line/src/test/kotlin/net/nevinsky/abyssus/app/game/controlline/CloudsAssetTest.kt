package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudsLoader
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val LOG = org.slf4j.LoggerFactory.getLogger("clouds")

class CloudsAssetTest {
    private val files = FileLoader(bundledProject().toFile())
    private val metas = AssetMetaLoader(JsonProcessor(LOG), files, LOG)

    @Test
    fun theFieldHasACloudsAssetItsSkyNames() {
        val meta = checkNotNull(metas.loadBaseMeta("clouds_fair"))
        assertEquals(MetaType.CLOUDS, meta.type)
        val clouds = checkNotNull(CloudsLoader(metas).prepare("clouds_fair")).staged
        assertTrue("the clouds have a band to draw", clouds.meta.visible)
        assertNotNull(clouds.noise)

        val sky = checkNotNull(ProceduralSkyLoader(files, metas).prepare("skybox_physical")).staged
        assertEquals("the sky draws the clouds asset", "clouds_fair", sky.clouds)
    }
}
