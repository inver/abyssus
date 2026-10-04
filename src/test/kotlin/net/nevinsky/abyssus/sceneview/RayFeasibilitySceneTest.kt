/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.raytracing.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

class RayFeasibilitySceneTest {
    @Test fun theMovingTriangleCastsAVisibleFloorShadowInTheActualPreviewScene() {
        assumeTrue(GlHarness.enabled && System.getProperty("os.name").startsWith("Mac"))
        val preview = RayFeasibilityPreview {}
        val result = MetalRayBackendFactory().probe()
        assertTrue("Metal required for the preview image regression: $result", result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { backend ->
            backend.openSession("preview-shadow", RayLimits()).use { session ->
                for (time in listOf(0.0, Math.PI / 2)) {
                    val request = preview.request(time, 1)
                    val exposed = render(session, RayRequest(request.key, request.width, request.height,
                        request.camera, request.meshes(), request.instances().dropLast(1)))
                    val shadowed = render(session, request)
                    val lit = exposed.colorValues()
                    val dark = shadowed.colorValues()
                    val litDepth = exposed.depthValues()
                    val darkDepth = shadowed.depthValues()
                    val floorShadow = litDepth.indices.count { pixel ->
                        // Unchanged primary depth excludes the triangle's camera silhouette.
                        abs(litDepth[pixel] - darkDepth[pixel]) < 0.000001f && litDepth[pixel] < 1f &&
                            lit[pixel * 4] > 0.4f && dark[pixel * 4] < lit[pixel * 4] * 0.5f
                    }
                    val image = java.awt.image.BufferedImage(request.width, request.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    for (y in 0 until request.height) for (x in 0 until request.width) {
                        val i = ((request.height - 1 - y) * request.width + x) * 4
                        fun channel(offset: Int) = (dark[i + offset].coerceIn(0f, 1f) * 255).toInt()
                        image.setRGB(x, y, (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
                    }
                    val output = java.io.File("build/screenshots/ray-preview-${if (time == 0.0) "center" else "moved"}.png")
                    output.parentFile.mkdirs()
                    javax.imageio.ImageIO.write(image, "png", output)
                    println("Preview floor shadow at time=$time: $floorShadow pixels")
                    assertTrue("Expected a visible shadow footprint at time=$time, found $floorShadow pixels", floorShadow > 100)
                }
            }
        }
    }

    private fun render(session: RaySession, request: RayRequest): RayFrame {
        session.submit(request)
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            session.poll()?.let { return it }
            Thread.sleep(1)
        }
        error("Metal preview frame timed out")
    }
}
