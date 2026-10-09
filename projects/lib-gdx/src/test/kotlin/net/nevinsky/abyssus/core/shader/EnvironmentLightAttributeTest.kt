package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentLightAttributeTest {
    /** A texture handle without GL: the attribute only carries it. */
    private class FakeCube(handle: Int) : GLTexture(GL20.GL_TEXTURE_CUBE_MAP, handle) {
        override fun getWidth() = 1
        override fun getHeight() = 1
        override fun getDepth() = 0
        override fun isManaged() = false
        override fun reload() = Unit
    }

    private val axisColors = FloatArray(18) { (it + 1) / 20f }
    private fun sky() = EnvironmentLightAttribute(FakeCube(7), FakeCube(8), 6, axisColors)

    @Test
    fun withoutTheAttributeThePbrDefinesAreUnchanged() {
        val mask = PBRFloatAttribute.Metallic or PBRFloatAttribute.Roughness
        val plain = Environment().apply { set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f)) }
        // exactly the defines the shader added before the attribute existed
        assertEquals("#define metallicFactorFlag\n#define roughnessFactorFlag\n", PbrShader.pbrDefines(mask, plain))
        assertEquals("#define metallicFactorFlag\n#define roughnessFactorFlag\n", PbrShader.pbrDefines(mask, null))
    }

    @Test
    fun theAttributeAddsTheFlag() {
        val lit = Environment().apply { set(sky()) }
        assertTrue(PbrShader.pbrDefines(PBRFloatAttribute.Metallic, lit).contains("#define environmentLightFlag\n"))
        assertTrue(EnvironmentLightAttribute.has(lit))
        assertFalse(EnvironmentLightAttribute.has(Environment()))
    }

    @Test
    fun theAttributeChangesTheEnvironmentMask() {
        // shaders are cached per attribute mask, so a lit environment gets its own program
        val plain = Environment()
        val lit = Environment().apply { set(sky()) }
        assertTrue(plain.getMask() != lit.getMask())
        assertEquals(1, java.lang.Long.bitCount(EnvironmentLightAttribute.Type))
    }

    @Test
    fun defaultShaderAmbientUsesTheSixColors() {
        val cubemap = AmbientCubemap()
        val both = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f))
            set(sky())
        }
        DefaultShader.ACubemapSetter.setAmbientBase(cubemap, both)
        assertArrayEquals(axisColors, cubemap.data, 0f)
    }

    @Test
    fun defaultShaderAmbientWithoutTheSkyIsTheAmbientColor() {
        val cubemap = AmbientCubemap()
        DefaultShader.ACubemapSetter.setAmbientBase(cubemap, Environment().apply { set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f)) })
        assertArrayEquals(FloatArray(18) { 0.3f }, cubemap.data, 1e-6f)
    }

    @Test
    fun copiesAndComparesByContent() {
        val a = sky()
        assertEquals(a, a.copy())
        assertEquals(0, a.compareTo(a.copy()))
    }
}
