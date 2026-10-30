package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import java.lang.reflect.Proxy
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HDR_BUILD_STEPS
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironmentBuild
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrImage
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.IRRADIANCE_SIZE
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.SPECULAR_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/** The GPU build of an HDR sky's environment, read back texel by texel. Opt-in: `-Dabyssus.glTests=true`. */
class HdrEnvironmentGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val shaders = skyShaders()

    private fun image(width: Int, height: Int, pixel: (Int, Int) -> FloatArray): HdrImage {
        val rgb = ShortArray(width * height * 3)
        for (y in 0 until height) for (x in 0 until width) {
            val p = pixel(x, y)
            for (c in 0..2) rgb[(y * width + x) * 3 + c] = java.lang.Float.floatToFloat16(p[c])
        }
        return HdrImage(width, height, rgb)
    }

    /** Runs [block] inside a fresh GL 3.2 core context and rethrows what it threw. */
    private fun inGl(block: () -> Unit) = TestGl.run(block)

    private fun build(image: HdrImage): Pair<HdrEnvironment, Int> {
        val build = HdrEnvironmentBuild(image, shaders)
        var steps = 1
        while (!build.step()) steps++
        return build.finish() to steps
    }

    /** The RGB texels of one face of [texture] at [level], [size] pixels square, row by row from the bottom. */
    private fun readFace(texture: GpuTexture, face: Int, level: Int, size: Int): FloatArray {
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(0x8CA6, ints) // GL_FRAMEBUFFER_BINDING
        val previous = ints.get(0)
        val fbo = gl.glGenFramebuffer()
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
        gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, texture.textureObjectHandle, level)
        val pixels = BufferUtils.newFloatBuffer(size * size * 4)
        gl.glReadPixels(0, 0, size, size, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
        gl.glDeleteFramebuffer(fbo)
        return FloatArray(size * size * 3) { pixels.get(it / 3 * 4 + it % 3) }
    }

    private fun centre(texels: FloatArray, size: Int): FloatArray {
        val i = ((size / 2) * size + size / 2) * 3
        return floatArrayOf(texels[i], texels[i + 1], texels[i + 2])
    }

    @Test
    fun cubeSourceBorrowsTextureAndTransfersLighting() = inGl {
        val source = uniformCube(64, 2f)
        val sourceHandle = source.textureObjectHandle
        val build = HdrEnvironmentBuild(CubeSource(source, 7), shaders)
        try {
            while (!build.step()) Unit
            val environment = build.finish()
            val specularHandle = environment.specular.textureObjectHandle
            val irradianceHandle = environment.irradiance.textureObjectHandle
            build.dispose()
            assertTrue(Gdx.gl.glIsTexture(sourceHandle))
            assertTrue(Gdx.gl.glIsTexture(specularHandle))
            assertTrue(Gdx.gl.glIsTexture(irradianceHandle))
            for (value in environment.ambient) assertEquals(2f, value, 0.02f)
            environment.dispose()
            environment.dispose()
            assertTrue(Gdx.gl.glIsTexture(sourceHandle))
            assertTrue(!Gdx.gl.glIsTexture(specularHandle))
            assertTrue(!Gdx.gl.glIsTexture(irradianceHandle))
        } finally { build.dispose(); source.dispose() }
    }

    @Test
    fun failedCubeBuildReleasesOwnedTexturesButKeepsBorrowedSource() = inGl {
        val source = uniformCube(64, 2f)
        val original = Gdx.gl
        val created = mutableListOf<Int>()
        val deleted = mutableListOf<Int>()
        Gdx.gl = Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, args ->
            if (method.name == "glDeleteTexture") deleted += args!![0] as Int
            method.invoke(original, *(args ?: emptyArray())).also {
                if (method.name == "glGenTexture") created += it as Int
            }
        } as GL20
        val broken = ShaderStorage().withResources("/shader/broken-environment", javaClass)
        val build = HdrEnvironmentBuild(CubeSource(source, 7), broken)
        try {
            build.step()
            var failure: Exception? = null
            try { build.step() } catch (e: Exception) { failure = e }
            assertTrue("expected prefilter shader failure: $failure", failure?.message?.contains("hdr_prefilter") == true)
            assertTrue("owned texture allocations", created.size >= 2)
            assertTrue("failure cleans up immediately", created.all { !original.glIsTexture(it) })
            build.dispose()
            assertEquals("each owned texture disposed once", created.sorted(), deleted.sorted())
            assertTrue(original.glIsTexture(source.textureObjectHandle))
        } finally { build.dispose(); Gdx.gl = original; source.dispose() }
    }

    @Test
    fun hdrBackgroundOwnershipIsSeparateFromLighting() = inGl {
        val build = HdrEnvironmentBuild(image(16, 8) { _, _ -> floatArrayOf(2f, 0f, 0f) }, shaders)
        try {
            while (!build.step()) Unit
            val result = build.finishHdr()
            val backgroundHandle = result.background.textureObjectHandle
            val irradianceHandle = result.lighting.irradiance.textureObjectHandle
            assertTrue(Gdx.gl.glIsTexture(backgroundHandle))
            result.lighting.dispose()
            assertTrue("lighting disposal leaves HDR background alive", Gdx.gl.glIsTexture(backgroundHandle))
            assertTrue(!Gdx.gl.glIsTexture(irradianceHandle))
            result.dispose()
            result.dispose()
            assertTrue(!Gdx.gl.glIsTexture(backgroundHandle))
        } finally { build.dispose() }
    }

    private fun uniformCube(size: Int, radiance: Float): GpuTexture {
        val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, size, size)
        texture.bind(0)
        val pixels = BufferUtils.newFloatBuffer(size * size * 3)
        repeat(pixels.capacity()) { pixels.put(radiance) }
        pixels.flip()
        for (face in 0..5) {
            Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, 0x881B,
                size, size, 0, GL20.GL_RGB, GL20.GL_FLOAT, pixels)
        }
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL30.GL_TEXTURE_MAX_LEVEL, 6)
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR)
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP)
        return texture
    }

    @Test
    fun aUniformSkyGivesUniformCubes() {
        var worst = 0f
        inGl {
            val (env, _) = build(image(64, 32) { _, _ -> floatArrayOf(2f, 2f, 2f) })
            try {
                for (face in 0 until 6) {
                    for (level in 0 until env.levels) {
                        val size = SPECULAR_SIZE shr level
                        readFace(env.specular, face, level, size).forEach { worst = maxOf(worst, abs(it - 2f) / 2f) }
                    }
                    readFace(env.irradiance, face, 0, IRRADIANCE_SIZE).forEach { worst = maxOf(worst, abs(it - 2f) / 2f) }
                }
                env.ambient.forEach { worst = maxOf(worst, abs(it - 2f) / 2f) }
            } finally {
                env.dispose()
            }
        }
        assertTrue("largest relative error $worst", worst <= 0.01f)
    }

    @Test
    fun orientationPutsTheCentreAtMinusZ() {
        var minusZ = floatArrayOf()
        var plusZ = floatArrayOf()
        inGl {
            // red in the middle half of the image (around u = 0.5), blue in the outer quarters (around the edges)
            val (env, _) = build(image(64, 32) { x, _ -> if (x in 16 until 48) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 0f, 1f) })
            try {
                val size = SPECULAR_SIZE
                minusZ = centre(readFace(env.specular, 5, 0, size), size)
                plusZ = centre(readFace(env.specular, 4, 0, size), size)
            } finally {
                env.dispose()
            }
        }
        assertTrue("-Z centre ${minusZ.toList()}", minusZ[0] > 0.9f && minusZ[2] < 0.1f)
        assertTrue("+Z centre ${plusZ.toList()}", plusZ[2] > 0.9f && plusZ[0] < 0.1f)
    }

    @Test
    fun buildsInNineSteps() {
        var steps = 0
        inGl {
            val (env, n) = build(image(16, 8) { _, _ -> floatArrayOf(1f, 1f, 1f) })
            steps = n
            env.dispose()
        }
        assertEquals(HDR_BUILD_STEPS, steps)
        assertEquals(9, steps)
    }

    @Test
    fun aMissingGl30FailsTheSky() {
        var error: Throwable? = null
        inGl {
            val gl30 = Gdx.gl30
            Gdx.gl30 = null
            val build =
                HdrEnvironmentBuild(shaders = shaders, image = image(16, 8) { _, _ -> floatArrayOf(1f, 1f, 1f) })
            try {
                error = runCatching { build.step() }.exceptionOrNull()
            } finally {
                Gdx.gl30 = gl30
                build.dispose()
            }
        }
        assertTrue("got $error", error is IllegalStateException && error!!.message!!.contains("OpenGL 3"))
    }
}
