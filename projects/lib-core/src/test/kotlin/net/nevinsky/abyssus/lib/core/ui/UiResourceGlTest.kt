package net.nevinsky.abyssus.lib.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.lib.core.assets.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** GL ownership of UI resources. Opt-in: `-Dabyssus.glTests=true` (opens a window). */
class UiResourceGlTest {
    private val dir: File = Files.createTempDirectory("ui-gl").toFile()

    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun png(name: String): File = File(dir, name).also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", it) }

    @Test fun buildsPreparedResourcesSkipsFailedOnesAndDisposesOnce() {
        val image = png("a.png")
        png("f_0.png")
        File(dir, "f.fnt").writeText("info face=\"T\" size=8 padding=0,0,0,0 spacing=1,1\ncommon lineHeight=8 base=6 scaleW=8 scaleH=8 pages=1\npage id=0 file=\"f_0.png\"\nchars count=1\nchar id=65 x=0 y=0 width=4 height=4 xoffset=0 yoffset=0 xadvance=4 page=0 chnl=15\n")
        val prepared = UiPreparedResources(mapOf(
            "img" to UiPreparedResource.Image("img", image, 8, 8),
            "font" to UiPreparedResource.Font("font", File(dir, "f.fnt"), listOf(File(dir, "f_0.png"))),
            "bad" to UiPreparedResource.Failed("bad", UiResourceProblem.MISSING),
        ))
        TestGl.run {
            Gdx.files = Lwjgl3Files()
            val built = UiBuiltResources.Builder().build(prepared)
            assertEquals(8, built.texture("img")!!.width)
            assertNotNull(built.font("font"))
            assertNull(built.texture("bad")); assertNull(built.font("bad"))
            built.dispose()
        }
    }

    @Test fun bundledFontIsSharedThroughLeasesAndDisposedWithTheLast() {
        TestGl.run {
            Gdx.files = Lwjgl3Files()
            val bundled = UiBundledFont()
            val first = bundled.acquire()
            val second = bundled.acquire()
            assertSame(first, second)
            assertEquals(2, bundled.activeLeases)
            bundled.release()
            assertEquals(1, bundled.activeLeases)
            bundled.release()
            assertEquals(0, bundled.activeLeases)
            assertThrows(IllegalStateException::class.java) { bundled.release() }
            assertNotSame(first, bundled.acquire().also { bundled.release() }) // rebuilt after the last release
        }
    }
}
