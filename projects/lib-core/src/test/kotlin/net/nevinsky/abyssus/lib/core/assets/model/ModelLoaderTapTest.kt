/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class ModelLoaderTapTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A copy of `Untitled`'s model asset, named `model`. */
    private fun loader(tap: ModelPreparationTap?): ModelLoader {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("model-tap").toFile().also(dirs::add)
        val source = File(testProject("Untitled"), "assets/model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
        assertTrue(source.copyRecursively(File(root, "assets/model")))
        val files = testFileLoader(root)
        return ModelLoader(testMetaLoader(root, fileLoader = files), AssimpModelLoader(), files, tap)
    }

    @Test
    fun aTapReceivesTheDataAndImagesOnce() {
        val begun = mutableListOf<String>()
        val offered = mutableListOf<Pair<ModelData, Map<String, Pixmap>>>()
        val loader = loader { name ->
            begun += name
            ModelPreparationTap.Capture { data, images -> offered += data to images }
        }
        val prepared = checkNotNull(loader.prepare("model")).staged
        assertEquals(listOf("model"), begun)
        assertEquals(1, offered.size)
        assertSame(prepared.data, offered.single().first)
        assertTrue(offered.single().second.values.none { it.isDisposed })
        loader.discardStaged(prepared)
    }

    @Test
    fun aTapThatDeclinesLeavesTheLoadUntouched() {
        val loader = loader { null }
        val prepared = checkNotNull(loader.prepare("model")).staged
        assertTrue(prepared.data.meshes.size > 0)
        loader.discardStaged(prepared)
    }

    @Test
    fun aCancellationFromOfferDisposesThePreparedModel() {
        var images: Map<String, Pixmap> = emptyMap()
        val loader = loader {
            ModelPreparationTap.Capture { _, offered ->
                images = offered
                throw CancellationException("cancelled")
            }
        }
        assertThrows(CancellationException::class.java) { loader.prepare("model") }
        assertTrue(images.isNotEmpty())
        assertTrue(images.values.all { it.isDisposed })
    }
}
