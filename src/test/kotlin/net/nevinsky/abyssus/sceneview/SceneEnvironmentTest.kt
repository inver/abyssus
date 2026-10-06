package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Rgba

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which light replaces the ambient color, by the scene's skybox and the state of its HDR sky. */
class SceneEnvironmentTest {
    private val grey = Rgba(0.3f, 0.3f, 0.3f, 1f)

    /** Built environments by sky name; a sky missing from the map is another kind, still building or failed. */
    private fun ambient(skybox: String?, ambient: Rgba?, built: Map<String, String> = mapOf("sky_hdr" to "environment")) =
        SceneAmbient.of(skybox, ambient) { built[it] }

    @Test
    fun aBuiltHdrSkyReplacesTheAmbient() {
        assertEquals(SceneAmbient.Sky("environment"), ambient("sky_hdr", grey))
    }

    @Test
    fun aDisabledSkyboxKeepsTheAmbient() {
        // SceneContent gives no skybox name when skyboxEnabled is false
        assertEquals(SceneAmbient.Color(grey), ambient(null, grey))
    }

    @Test
    fun anLdrSkyKeepsTheAmbient() {
        assertEquals(SceneAmbient.Color(grey), ambient("skybox_default", grey))
        assertEquals(SceneAmbient.Color(grey), ambient("skybox_physical", grey))
    }

    @Test
    fun aBuildingSkyKeepsTheAmbient() {
        assertEquals(SceneAmbient.Color(grey), ambient("sky_hdr", grey, built = emptyMap()))
    }

    @Test
    fun aFailedSkyRestoresTheAmbient() {
        // a sky that worked, then failed after the asset was switched, is no longer built
        assertEquals(SceneAmbient.Sky("environment"), ambient("sky_hdr", grey))
        assertEquals(SceneAmbient.Color(grey), ambient("sky_broken", grey))
    }

    @Test
    fun skyLightsWithAmbientDisabled() {
        assertEquals(SceneAmbient.Sky("environment"), ambient("sky_hdr", null))
        assertEquals(SceneAmbient.Color(null), ambient(null, null))
    }
}
