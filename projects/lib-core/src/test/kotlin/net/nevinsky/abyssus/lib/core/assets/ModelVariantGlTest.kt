/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.g3d.Model
import net.nevinsky.abyssus.lib.core.assets.loading.AssetState
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

/** One mesh of a model asset ("model variants") through a real storage and GL context. Opt-in: `-Dabyssus.glTests=true`. */
class ModelVariantGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun project(): File = Files.createTempDirectory("variants").toFile().also(dirs::add).also {
        writeModelAsset(it, "duo", twoQuadData())
    }

    private fun assets(dir: File, logged: MutableList<String>): AssetStorage {
        val log = warningsTo(logged)
        val files = FileLoader(dir)
        val metas = testMetaLoader(dir, log, files)
        val loaders = StandardAssetLoaders(files, metas, skyShaders(), log = log)
        return AssetStorage(log, Executor(Runnable::run), metas::loadBaseMeta).also { it.registerAll(loaders.byType) }
    }

    private fun settle(assets: AssetStorage, names: Set<String>) {
        repeat(400) {
            names.forEach(assets::request)
            assets.update()
            if (!assets.isLoading()) return
        }
        error("still loading")
    }

    private val meshIds = listOf("mesh_0", "mesh_1")
    private val partIds = listOf("part_0", "part_1")

    private fun Model.partNames() = meshParts.map { it.id }

    @Test
    fun aVariantBuildsFromTheBaseAssetsFilesWithOnlyItsMesh() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = assets(project(), logged)
            try {
                settle(assets, setOf("duo"))
                val whole = assets.getAs<Model>("duo")!!
                assertEquals(2, whole.meshes.size)
                assertEquals(partIds, whole.partNames())
                val keys = meshIds.map { ModelKey("duo", it).encode() }
                settle(assets, keys.toSet())
                for ((index, key) in keys.withIndex()) {
                    val variant = checkNotNull(assets.getAs<Model>(key)) { "${assets.state(key)} $logged" }
                    assertEquals(1, variant.meshes.size)
                    assertEquals(listOf(partIds[index]), variant.partNames())
                    assertEquals(1, variant.materials.size)
                }
                assertSame("the whole model is untouched", whole, assets.getAs<Model>("duo"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun anUnknownMeshFailsOnlyItsVariant() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = assets(project(), logged)
            try {
                settle(assets, setOf("duo"))
                val good = ModelKey("duo", meshIds[0]).encode()
                val bad = ModelKey("duo", "nope").encode()
                settle(assets, setOf(good, bad))
                assertNull(assets.getAs<Model>(bad))
                assertTrue(assets.state(bad) is AssetState.Failed)
                assertNotNull(assets.getAs<Model>(good))
                assertNotNull(assets.getAs<Model>("duo"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().contains("nope"))
    }

    @Test
    fun invalidatingTheAssetReplacesItsLoadedVariants() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = assets(project(), logged)
            try {
                settle(assets, setOf("duo"))
                val key = ModelKey("duo", meshIds[0]).encode()
                settle(assets, setOf(key))
                val before = assets.getAs<Model>(key)!!
                val wholeBefore = assets.getAs<Model>("duo")!!
                assets.invalidate(setOf("duo"))
                settle(assets, setOf("duo", key))
                assertNotSame(before, assets.getAs<Model>(key))
                assertNotSame(wholeBefore, assets.getAs<Model>("duo"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun retainKeepsTheVariantsNamedAndDisposesTheRest() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = assets(project(), logged)
            try {
                settle(assets, setOf("duo"))
                val first = ModelKey("duo", meshIds[0]).encode()
                val second = ModelKey("duo", meshIds[1]).encode()
                settle(assets, setOf(first, second))
                assets.retain(setOf(first))
                assertNotNull(assets.getAs<Model>(first))
                assertNull(assets.getAs<Model>(second))
                assertNull(assets.getAs<Model>("duo"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }
}
