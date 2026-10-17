package net.nevinsky.abyssus.lib.core.assets.standard

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.PreparedFoliage
import net.nevinsky.abyssus.lib.core.assets.foliage.RuntimeFoliageLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudsLoader
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardAssetLoadersTest {
    private val dir = testProject("Untitled")
    private val files = testFileLoader(dir)
    private val metas = testMetaLoader(dir)

    private fun loaders() = StandardAssetLoaders(files, metas, ShaderStorage().withAssets(files))

    @Test
    fun everyDrawableKindHasItsLoader() {
        val byType = loaders().byType
        val expected = mapOf(
            MetaType.MODEL to ModelLoader::class,
            MetaType.TERRAIN to TerrainLoader::class,
            MetaType.FOLIAGE to RuntimeFoliageLoader::class,
            MetaType.TEXTURE to TextureLoader::class,
            MetaType.PIXMAP_TEXTURE to TextureLoader::class,
            MetaType.SKYBOX to SkyboxLoader::class,
            MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader::class,
            MetaType.CLOUDS to CloudsLoader::class,
            MetaType.SKYBOX_HDR to HdrSkyLoader::class,
        )
        assertEquals(expected.keys, byType.keys)
        expected.forEach { (type, loader) -> assertEquals(type.name, loader, byType.getValue(type)::class) }
    }

    @Test
    fun kindsThatAreNotDrawnHaveNoLoader() {
        val missing = MetaType.entries.toSet() - loaders().byType.keys
        assertEquals(setOf(MetaType.UNKNOWN, MetaType.MATERIAL, MetaType.SHADER, MetaType.UI), missing)
    }

    @Test
    fun loadersTheHostAlsoNeedsAreTheSameInstancesAsInTheTable() {
        val loaders = loaders()
        assertSame(loaders.terrain, loaders.byType[MetaType.TERRAIN])
        assertSame(loaders.texture, loaders.byType[MetaType.TEXTURE])
        assertSame(loaders.texture, loaders.byType[MetaType.PIXMAP_TEXTURE])
        assertSame(loaders.skybox, loaders.byType[MetaType.SKYBOX])
        assertSame(loaders.hdrSky, loaders.byType[MetaType.SKYBOX_HDR])
    }

    @Test
    fun aHostCanReplaceTheFoliageLoader() {
        var seen: TerrainLoader? = null
        val custom = RuntimeFoliageLoaderStub()
        val loaders = StandardAssetLoaders(files, metas, ShaderStorage(), foliage = { seen = it; custom })
        assertSame(custom, loaders.byType[MetaType.FOLIAGE])
        assertTrue(seen === loaders.terrain)
    }

    private class RuntimeFoliageLoaderStub : AssetLoader<Unit, PreparedFoliage, FoliageDrawable> {
        override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedFoliage>? = null
        override fun prepare(name: String): Prepared<Unit, PreparedFoliage>? = null
        override fun build(staged: PreparedFoliage, assets: BuiltAssets): FoliageDrawable = error("not built in this test")
        override fun discard(model: Unit) {}
    }
}
