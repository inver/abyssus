package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ScreenshotEnvironmentTest {
    private val manifestPath get() = Paths.get(System.getProperty("abyssus.ui.baselines")).resolve("environment.properties")

    private val manifest get() = ScreenshotEnvironment.parse(Files.readString(manifestPath))

    @Test
    fun theCommittedManifestNamesTheCanonicalProfile() {
        val v = manifest.values
        assertEquals("Linux", v["os"])
        assertEquals("amd64", v["arch"])
        assertEquals("UTC", v["timezone"])
        assertEquals("en", v["language"])
        assertEquals("1", v["uiScale"])
        assertEquals("1920x1080", v["screen"])
        assertTrue(v.keys.any { it.startsWith("pkg.fonts-") })
    }

    @Test
    fun anObservedEnvironmentEqualToTheManifestHasNoDifferences() {
        assertEquals(emptyList<String>(), manifest.differences(manifest.values))
    }

    @Test
    fun aChangedValueIsReportedByKey() {
        val observed = manifest.values + ("jbr" to "21.0.9+1")
        val differences = manifest.differences(observed)
        assertEquals(1, differences.size)
        assertTrue(differences.single(), differences.single().startsWith("jbr: expected"))
    }

    @Test
    fun aMissingValueIsAMismatch() {
        val observed = manifest.values - "pkg.fontconfig"
        assertTrue(manifest.differences(observed).any { it.startsWith("pkg.fontconfig") })
    }

    @Test
    fun aChangedManifestRejectsTheRealEnvironment() {
        val changed = ScreenshotEnvironment(manifest.values + ("theme" to "Light"))
        assertTrue(changed.differences(manifest.values).any { it.startsWith("theme:") })
    }

    @Test
    fun packageLinesParse() {
        assertEquals(mapOf("a" to "1.0", "b" to "2:3.4-5"), ScreenshotEnvironment.parseLines("a=1.0\n\nb=2:3.4-5\nnoise"))
    }
}
