/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MetalLibraryLoadingTest {
    @Test fun constructionDoesNotLoadNativeCode() {
        var loads = 0
        MetalLibrary({ byteArrayOf(1) }, { loads++ })
        assertEquals(0, loads)
    }

    @Test fun loadingUsesExtractedBytesAndRunsOnlyOncePerInstance() {
        var loads = 0
        val loader = MetalLibrary({ byteArrayOf(1, 2, 3) }, { path ->
            loads++
            assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(java.nio.file.Path.of(path)))
        })
        loader.load()
        loader.load()
        assertEquals(1, loads)
    }

    @Test fun missingLibraryIsAnExplicitLoadFailure() {
        val loader = MetalLibrary({ null }, { fail("Must not call System.load without a packaged library") })
        assertThrows(IllegalStateException::class.java) { loader.load() }
    }

    @Test fun separateFactoriesLoadTheSameLibraryPath() {
        val paths = mutableListOf<String>()
        MetalLibrary({ byteArrayOf(1, 2, 3) }, { paths += it }).load()
        MetalLibrary({ byteArrayOf(1, 2, 3) }, { paths += it }).load()
        assertEquals(paths[0], paths[1])
    }
}
