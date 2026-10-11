/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class RaySkyBakeCacheTest {
    private val cache = RaySkyBakeCache()
    private var bakes = 0
    private fun picture() = RaySkySnapshot(1, 1, floatArrayOf(0f, 0f, 0f, 1f), hdr = false)
    private fun sun(degrees: Double) = Vec3(sin(Math.toRadians(degrees)).toFloat(), cos(Math.toRadians(degrees)).toFloat(), 0f)

    private fun get(
        time: Double = 0.0, sun: Vec3 = sun(0.0), revision: Long = 1, clouds: Boolean = true, drifting: Boolean = true,
        name: String = "sky", project: File? = File("project"), bake: () -> RaySkySnapshot? = { picture().also { bakes++ } },
    ) = cache.get(name, project, sun, time, revision, clouds, drifting, bake)

    @Test fun `an unchanged sky is baked once and keeps its snapshot identity`() {
        val first = get()
        assertSame(first, get(time = 0.1))
        assertSame(first, get(time = 1.9))
        assertEquals(1, bakes)
    }

    @Test fun `the sun must move over a degree`() {
        val first = get()
        assertSame(first, get(sun = sun(0.9)))
        assertNotSame(first, get(sun = sun(1.5), time = 0.1))
        assertEquals(2, bakes)
    }

    @Test fun `drifting clouds are baked again every two seconds and stationary ones never`() {
        val first = get()
        assertNotSame(first, get(time = 2.0))
        assertEquals(2, bakes)
        val still = RaySkyBakeCache()
        val a = still.get("sky", null, sun(0.0), 0.0, 1, true, false) { picture() }
        assertSame(a, still.get("sky", null, sun(0.0), 600.0, 1, true, false) { picture() })
    }

    @Test fun `a reloaded removed or hidden cloud asset is baked again at once`() {
        val first = get()
        val reloaded = get(time = 0.1, revision = 2, drifting = false)
        assertNotSame(first, reloaded)
        val removed = get(time = 0.2, revision = 3, clouds = false, drifting = false)
        assertNotSame(reloaded, removed)
        assertEquals(3, bakes)
    }

    @Test fun `another sky or project is baked from scratch`() {
        val first = get()
        assertNotSame(first, get(name = "other"))
        assertNotSame(first, get(name = "sky", project = File("elsewhere")))
        assertEquals(3, bakes)
    }

    @Test fun `a failed bake is not retried until something changes and leaves no stale picture`() {
        assertNull(get(bake = { null }))
        assertNull(get(time = 1.0, bake = { bakes++; picture() }))
        assertEquals(0, bakes)
        val retried = get(time = 2.0, bake = { bakes++; picture() })
        assertEquals(1, bakes)
        assertSame(retried, get(time = 2.1))
        assertNull(get(time = 4.1, bake = { throw IllegalStateException("no GL") }))
    }

    @Test fun `clearing forgets the picture`() {
        val first = get()
        cache.clear()
        assertNotSame(first, get())
        assertEquals(2, bakes)
    }
}
