/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*

/**
 * Packaging check that the shipped scene shader understands the optics payload (version 3 encoding, 28-float camera):
 * a glass slab in front of an emissive red wall shows the wall only when the request lets rays cross both faces.
 */
internal fun assertPackagedSceneOptics(session: RaySession) {
    val slab = RayMesh("slab", floatArrayOf(-1f, -1f, -.2f, 1f, -1f, -.2f, 1f, 1f, -.2f, -1f, 1f, -.2f, -1f, -1f, .2f, 1f, -1f, .2f, 1f, 1f, .2f, -1f, 1f, .2f),
        intArrayOf(0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5))
    val wall = RayMesh("wall", floatArrayOf(-10f, -10f, -2f, 10f, -10f, -2f, 0f, 10f, -2f), intArrayOf(0, 1, 2))
    val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    val scene = RaySceneSnapshot(listOf(slab, wall), listOf(RayInstance("glass", 0, 0, identity), RayInstance("wall", 1, 1, identity)),
        listOf(RayMaterial(kind = RayMaterialKind.PBR, roughness = .04f, transmission = 1f, ior = 1.5f),
            RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 0f, 0f))),
        emptyList(), emptyList(), RayEnvironment(ambient = RayColor(0f, 0f, 0f), background = RayColor(0f, .2f, .4f)))
    val camera = RaySliceCamera(listOf(.3f, 0f, 3f), listOf(-.1f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
    fun render(revision: Long, refractions: Int): FloatArray {
        session.submit(RaySceneRequest(RayFrameKey(1, 1, revision, 1), 1, 1, camera, scene, samples = 8,
            maxReflectionBounces = 2, maxRefractionBounces = refractions))
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            session.poll()?.let { frame -> return frame.colorValues().also { assertTrue(it.all(Float::isFinite)) } }
            Thread.sleep(1)
        }
        fail("Packaged scene shader must render a frame"); error("unreachable")
    }
    val through = render(1, 2)
    assertTrue("glass with two crossings shows the red wall: ${through.toList()}", through[0] > .5f && through[0] > through[2])
    val opaque = render(2, 0)
    assertTrue("refraction limit 0 keeps the slab opaque: ${opaque.toList()}", opaque[0] < .2f)
}
