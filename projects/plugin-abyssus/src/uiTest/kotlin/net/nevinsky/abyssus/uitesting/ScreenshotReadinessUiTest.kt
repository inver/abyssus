package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.WaitForConditionTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

class ScreenshotReadinessUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun hdrAsset() = project.dir.resolve("assets").toFile().listFiles()!!.filter { it.isDirectory }
        .first { "SKYBOX_HDR" in Files.readString(it.toPath().resolve("meta.json")) }

    private fun openHdr(): PropertiesPanel {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expandAssets()
        frame.normalizeLayout(ide)
        tree.clickRow(hdrAsset().name)
        return frame.properties()
    }

    @Test
    fun capturesTheHdrPanelOnceItsPreviewIsReal() {
        val panel = openHdr()
        panel.awaitPreviewsLoaded()
        frame.movePointerAway()
        val pixels = shots.captureStable(panel.component)
        assertTrue("tool window is about the requested width, was ${pixels.width}", pixels.width in IdeFrame.PROPERTIES_WIDTH - 2..IdeFrame.PROPERTIES_WIDTH + 2)
        // normalizing again must give the same size and the same pixels
        frame.normalizeLayout(ide)
        val twice = shots.captureStable(frame.properties().component)
        assertEquals("fixed capture size", pixels.width to pixels.height, twice.width to twice.height)
        assertTrue("two stable captures agree", pixels.argb.contentEquals(twice.argb))
    }

    @Test
    fun anUnreadablePreviewIsNotAcceptedAsLoaded() {
        val exr = hdrAsset().toPath().resolve("sky.exr")
        Files.write(exr, "not an exr".toByteArray())
        ide.refresh(project.dir.toString())
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expandAssets()
        tree.clickRow("tree") // a different asset first, so the panel is rebuilt for the HDR one
        tree.clickRow(hdrAsset().name)
        val failure = runCatching { frame.properties().awaitPreviewsLoaded(Duration.ofSeconds(10)) }.exceptionOrNull()
        assertTrue("an unreadable HDR must not count as a loaded preview, got $failure", failure is WaitForConditionTimeoutException)
    }
}
