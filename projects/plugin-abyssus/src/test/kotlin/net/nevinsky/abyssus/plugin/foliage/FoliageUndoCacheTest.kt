package net.nevinsky.abyssus.plugin.foliage

import net.nevinsky.abyssus.plugin.assetfiles.sha256Hex
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class FoliageUndoCacheTest {
    @Test fun `backup restores exact bytes and releases its file`() {
        val root = Files.createTempDirectory("foliage-undo-test")
        val source = root.resolve("source")
        val bytes = byteArrayOf(4, 3, 2, 1)
        Files.write(source, bytes)
        val cache = FoliageUndoCache()
        try {
            val handle = cache.preserve(source, sha256Hex(bytes))
            Files.write(source, byteArrayOf(0))
            assertArrayEquals(bytes, handle.read())
            assertTrue(Files.exists(handle.path))
            handle.close()
            assertFalse(Files.exists(handle.path))
        } finally { cache.dispose(); Files.deleteIfExists(source); Files.deleteIfExists(root) }
    }
    @Test fun `wrong input hash or altered backup is refused`() {
        val source = Files.createTempFile("foliage-source", ".data")
        Files.write(source, byteArrayOf(1))
        val cache = FoliageUndoCache()
        try {
            assertThrows(IllegalArgumentException::class.java) { cache.preserve(source, sha256Hex(byteArrayOf(2))) }
            val handle = cache.preserve(source, sha256Hex(byteArrayOf(1)))
            Files.write(handle.path, byteArrayOf(3))
            assertThrows(IllegalArgumentException::class.java) { handle.read() }
            cache.dispose()
            assertFalse(Files.exists(handle.path))
        } finally { cache.dispose(); Files.deleteIfExists(source) }
    }
}
