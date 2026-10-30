package net.nevinsky.abyssus.plugin.sceneview.terrain

import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.shader.ShaderSources
import net.nevinsky.abyssus.lib.render.terrain.RENDER_SHADER_ROOT
import net.nevinsky.abyssus.lib.render.terrain.TerrainPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The terrain fragment gets the shadow and directional visibility functions from the shared GLSL that the model shaders use. */
class TerrainShaderSourceTest {
    private val functions = listOf("shadowCompare", "shadowBilinear", "shadowTileVisibility", "directionalVisibility")
    private val shaders = ShaderStorage().withResources(RENDER_SHADER_ROOT, TerrainPass::class.java)

    private fun definitions(source: String, function: String) = Regex("""(?m)^float $function\(""").findAll(source).count()

    @Test
    fun theAssembledTerrainFragmentContainsEachSharedFunctionOnce() {
        val assembled = shaders.fragment("terrain_editor.frag")
        for (function in functions) assertEquals(function, 1, definitions(assembled, function))
        assertFalse(assembled.contains("#pragma include"))
    }

    @Test
    fun theTerrainSourceKeepsNoCopyAndTheModelShadersGetTheSameFunctions() {
        for (function in functions) assertEquals(function, 0, definitions(shaders.read("terrain_editor.frag"), function))
        val shared = ShaderSources.readAssembled(ShaderSources.DEFAULT_FRAGMENT)
        val terrain = shaders.fragment("terrain_editor.frag")
        val block = { text: String -> text.substringAfter("// begin include ${ShaderSources.SHADOW_VISIBILITY}").substringBefore("// end include") }
        assertEquals(block(shared), block(terrain))
    }
}
