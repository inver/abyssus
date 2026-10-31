/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.twoQuadData
import net.nevinsky.abyssus.lib.core.assets.writeModelAsset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelMeshListerTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun lister(): Pair<File, ModelMeshLister> {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("lister").toFile().also(dirs::add)
        writeModelAsset(root, "duo", twoQuadData())
        val files = testFileLoader(root)
        return root to ModelMeshLister(files, testMetaLoader(root, fileLoader = files))
    }

    @Test
    fun listsEachMeshWithItsMaterials() {
        val (_, lister) = lister()
        assertEquals(
            listOf(ModelMeshInfo("mesh_0", listOf("skin")), ModelMeshInfo("mesh_1", listOf("glass"))),
            lister.list("duo"),
        )
    }

    @Test
    fun anAnswerIsKeptUntilTheAssetIsInvalidated() {
        val (root, lister) = lister()
        assertNull(lister.cached("duo"))
        val first = lister.list("duo")
        assertSame(first, lister.list("duo"))
        assertSame(first, lister.cached("duo"))
        File(root, "assets/duo").deleteRecursively()
        assertSame("still cached", first, lister.list("duo"))
        lister.invalidate("duo")
        assertNull(lister.cached("duo"))
        assertNull("the files are gone", lister.list("duo"))
    }

    @Test
    fun aMissingAssetHasNoMeshes() {
        assertNull(lister().second.list("nothing"))
    }
}
