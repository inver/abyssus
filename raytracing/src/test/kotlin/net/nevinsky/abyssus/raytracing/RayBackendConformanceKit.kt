/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Feasibility/lifecycle stage. Full shading references are added with tasks 3.1–3.5. */
abstract class RayBackendConformanceKit {
    protected open val sceneMaterials: Boolean = false
    protected abstract fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider

    @Test
    fun probeWithoutDeviceIsUnavailable() {
        assertTrue(provider(false, RayDeviceHealth()).probe() is RayCapability.Unavailable)
    }

    @Test
    fun primaryVisibilityAndDepthMatchTheReference() = withBackend { backend, _ ->
        backend.openSession("primary", RayLimits()).use { session ->
            val frame = render(session, triangleRequest())
            assertReference(frame, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), floatArrayOf(0.950951f), 0.0002f)
        }
    }

    @Test
    fun instanceMotionChangesVisibility() = withBackend { backend, _ ->
        backend.openSession("motion", RayLimits()).use { session ->
            val hit = render(session, triangleRequest())
            val miss = render(session, triangleRequest(x = 10f))
            assertTrue(hit.depthValues()[0] < 1f)
            assertReference(miss, floatArrayOf(0.05f, 0.1f, 0.2f, 1f), floatArrayOf(1f), 0.0002f)
        }
    }

    @Test
    fun updatedGeometryMovesTheHitPosition() = withBackend { backend, _ ->
        backend.openSession("deformed", RayLimits()).use { session ->
            val before = render(session, triangleRequest())
            // same mesh index and instance, deformed vertices: the next frame must trace the new triangle, not a stale one
            val deformed = render(session, triangleRequest(z = -1f, revision = 2))
            val restored = render(session, triangleRequest(revision = 3))
            assertReference(before, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), floatArrayOf(0.950951f), 0.0002f)
            assertReference(deformed, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), floatArrayOf(0.9676343f), 0.0002f)
            assertReference(restored, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), floatArrayOf(0.950951f), 0.0002f)
        }
    }

    @Test
    fun directionalShadowDoesNotBlockThePrimaryCameraRay() = withBackend { backend, _ ->
        backend.openSession("shadow", RayLimits()).use { session ->
            val geometry = listOf(floor(), floor(0.5f))
            val exposed = render(session, request(geometry, listOf(instance(0), instance(1, x = 10f, y = 1f))))
            val shadow = render(session, request(geometry, listOf(instance(0), instance(1, y = 1f))))
            assertReference(exposed, floatArrayOf(1f, 1f, 1f, 1f), shadow.depthValues(), 0.0002f)
            assertReference(shadow, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), exposed.depthValues(), 0.0002f)
        }
    }

    @Test
    fun reflectionHitAndMissMatchTheReference() = withBackend { backend, _ ->
        backend.openSession("reflection", RayLimits()).use { session ->
            val geometry =
                listOf(floor(), RaySliceMesh(floatArrayOf(-1f, 0f, -2f, 1f, 0f, -2f, 0f, 2f, -2f), intArrayOf(0, 1, 2)))
            val mirror = instance(0, reflective = true)
            val hit = render(session, request(geometry, listOf(mirror, instance(1, color = listOf(1f, 0f, 0f)))))
            val miss =
                render(session, request(geometry, listOf(mirror, instance(1, x = 10f, color = listOf(1f, 0f, 0f)))))
            assertReference(hit, floatArrayOf(0.1f, 0f, 0f, 1f), miss.depthValues(), 0.0002f)
            assertReference(miss, floatArrayOf(0.05f, 0.1f, 0.2f, 1f), hit.depthValues(), 0.0002f)
        }
    }

    @Test
    fun aPendingRequestIsReplacedWithoutStarvingMotion() = withBackend { backend, _ ->
        backend.openSession("queue", RayLimits()).use { session ->
            session.submit(triangleRequest(revision = 1))
            session.submit(triangleRequest(revision = 2))
            session.submit(triangleRequest(revision = 3))
            assertEquals(1L, await(session).key.cameraRevision)
            assertEquals(3L, await(session).key.cameraRevision)
            assertNull(session.poll())
        }
    }

    @Test
    fun structurallyStaleFramesAreRejected() = withBackend { backend, _ ->
        backend.openSession("stale", RayLimits()).use { session ->
            session.submit(triangleRequest(generation = 1))
            session.submit(triangleRequest(generation = 2))
            assertEquals(2L, await(session).key.sceneGeneration)
        }
    }

    @Test
    fun staleResizeFramesAreRejected() = withBackend { backend, _ ->
        backend.openSession("resize", RayLimits()).use { session ->
            session.submit(triangleRequest(width = 1))
            session.submit(triangleRequest(width = 2))
            assertEquals(2, await(session).width)
        }
    }

    @Test
    fun twoSessionsAreIndependentOnOneDevice() = withBackend { backend, _ ->
        val first = backend.openSession("first", RayLimits())
        val second = backend.openSession("second", RayLimits())
        first.submit(triangleRequest())
        second.submit(triangleRequest(x = 10f))
        first.dispose()
        first.dispose()
        assertEquals(1f, await(second).depthValues()[0], 0f)
        second.dispose()
    }

    @Test
    fun disposeWithWorkInFlightRejectsLaterUse() = withBackend { backend, _ ->
        val session = backend.openSession("dispose", RayLimits())
        session.submit(triangleRequest())
        session.dispose()
        assertThrows(IllegalStateException::class.java) { session.poll() }
        assertThrows(IllegalStateException::class.java) { session.submit(triangleRequest()) }
    }

    @Test
    fun lossReportedAtPollFailsEverySessionOnTheDevice() = withBackend { backend, health ->
        backend.openSession("loss-first", RayLimits()).use { first ->
            backend.openSession("loss-second", RayLimits()).use { second ->
                first.submit(triangleRequest())
                second.submit(triangleRequest())
                health.reportLost("Injected device loss")
                assertThrows(RayDeviceLostException::class.java) { first.poll() }
                assertThrows(RayDeviceLostException::class.java) { second.poll() }
            }
        }
    }

    @Test fun defaultSceneMaterialMatchesRasterReference() {
        org.junit.Assume.assumeTrue(sceneMaterials)
        withBackend { backend,_ -> backend.openSession("default-material",RayLimits()).use { session ->
            val material=RayMaterial(baseColor=RayColor(.4f,.2f,.1f),specular=RayColor(.1f,.1f,.1f),emissive=RayColor(.3f,0f,0f))
            session.submit(materialRequest(material))
            assertReference(await(session),floatArrayOf(1.34f,.62f,.41f,1f),floatArrayOf(.950951f),.0003f)
        } }
    }
    @Test fun pbrSceneMaterialMatchesRasterReference() {
        org.junit.Assume.assumeTrue(sceneMaterials)
        withBackend { backend,_ -> backend.openSession("pbr-material",RayLimits()).use { session ->
            val material=RayMaterial(kind=RayMaterialKind.PBR,baseColor=RayColor(.8f,.4f,.2f),metallic=1f,roughness=1f)
            session.submit(materialRequest(material,environment=RayEnvironment(ambient=RayColor(0f,0f,0f),background=RayColor(0f,0f,0f))))
            assertReference(await(session),floatArrayOf(.4f,.2f,.1f,1f),floatArrayOf(.950951f),.0003f)
        } }
    }
    @Test fun terrainSplatSceneMatchesOrderedRasterMixes() {
        org.junit.Assume.assumeTrue(sceneMaterials)
        withBackend { backend,_ -> backend.openSession("terrain-material",RayLimits()).use { session ->
            val textures=listOf(RayTexture("splat",1,1,floatArrayOf(.5f,.5f,1f,1f)),RayTexture("red",1,1,floatArrayOf(1f,0f,0f,1f)),RayTexture("green",1,1,floatArrayOf(0f,1f,0f,1f)))
            val material=RayMaterial(kind=RayMaterialKind.TERRAIN,terrain=RayTerrainMaterial(RayTextureBinding(0),listOf(null,RayTextureBinding(1),RayTextureBinding(2),null,null),1f))
            session.submit(materialRequest(material,textures))
            assertReference(await(session),floatArrayOf(.945f,1.47f,.42f,1f),floatArrayOf(.950951f),.0003f)
        } }
    }
    @Test fun pointAndSpotSceneLightsKeepRangeAndConeAttenuation() {
        org.junit.Assume.assumeTrue(sceneMaterials)
        withBackend { backend,_ -> backend.openSession("local-lights",RayLimits()).use { session ->
            val lights=listOf(
                RayLight("point",RayLightKind.POINT,RayColor(1f,0f,0f),position=RayVec3(0f,0f,2f),range=4f),
                RayLight("spot",RayLightKind.SPOT,RayColor(0f,1f,0f),position=RayVec3(0f,0f,2f),direction=RayVec3(0f,0f,-1f),range=4f),
                RayLight("away",RayLightKind.SPOT,RayColor(0f,0f,1f),position=RayVec3(0f,0f,2f),direction=RayVec3(0f,0f,1f),range=4f))
            session.submit(materialRequest(RayMaterial(),lights=lights))
            assertReference(await(session),floatArrayOf(.3f,.3f,.1f,1f),floatArrayOf(.950951f),.0003f)
        } }
    }
    // ---- 3.2 per-light visibility rays and cutouts -------------------------------------------------------------

    @Test fun cutoutHolesStayOpenInShadows() = sceneCase("cutout") { session ->
        val request = shadowScene(occluder = RayMaterial(alphaMode = RayAlphaMode.MASK, baseTexture = RayTextureBinding(0)), textures = listOf(cutoutTexture()))
        val frame = renderScene(session, request)
        // columns 0 and 7 lie outside the occluder, 3 under its solid half, 4 under its hole
        assertPixels(frame, mapOf(0 to 1.1f, 3 to .1f, 4 to 1.1f, 7 to 1.1f), .001f)
        assertMatchesReference(frame, request, .001f)
    }

    @Test fun blendedSurfacesReceiveButDoNotCastShadows() = sceneCase("blended-shadow") { session ->
        val casting = renderScene(session, shadowScene(occluder = RayMaterial(alphaMode = RayAlphaMode.BLEND, opacity = .5f)))
        assertPixels(casting, mapOf(3 to 1.1f, 4 to 1.1f), .001f) // a blended occluder casts nothing
        val receiving = shadowScene(floor = RayMaterial(alphaMode = RayAlphaMode.BLEND, opacity = .5f), occluder = RayMaterial())
        val frame = renderScene(session, receiving)
        assertPixels(frame, mapOf(0 to .55f, 3 to .05f), .001f) // a blended floor is shadowed by an opaque occluder
        assertMatchesReference(frame, receiving, .001f)
    }

    @Test fun pointLightShadowCoversOnlyTheOccludedFloor() = sceneCase("point-shadow") { session ->
        val request = shadowScene(
            occluder = RayMaterial(), occluderHeight = 1.5f, occluderHalfWidth = 1.6f,
            lights = listOf(RayLight("point", RayLightKind.POINT, RayColor(50f, 50f, 50f), position = RayVec3(0f, 3f, 0f), range = 10f)),
        )
        val frame = renderScene(session, request)
        assertPixels(frame, mapOf(2 to .1f, 3 to .1f, 4 to .1f, 5 to .1f), .001f)
        assertTrue(frame.colorValues()[0] > .3f && frame.colorValues()[7 * 4] > .3f)
        assertMatchesReference(frame, request, .002f)
    }

    @Test fun spotConeLimitsLightAndShadowToItsCone() = sceneCase("spot-cone") { session ->
        val request = shadowScene(
            occluder = null,
            lights = listOf(RayLight("spot", RayLightKind.SPOT, RayColor(50f, 50f, 50f), direction = RayVec3(0f, -1f, 0f), position = RayVec3(0f, 3f, 0f), range = 10f, cutoffAngle = 40f)),
        )
        val frame = renderScene(session, request)
        assertPixels(frame, mapOf(0 to .1f, 2 to .1f, 5 to .1f, 7 to .1f), .001f)
        assertTrue(frame.colorValues()[3 * 4] > .3f && frame.colorValues()[4 * 4] > .3f)
        assertMatchesReference(frame, request, .002f)
    }

    @Test fun oneShadowKeepsAmbientEmissionAndTheOtherLight() = sceneCase("independent-lights") { session ->
        val request = shadowScene(
            floor = RayMaterial(emissive = RayColor(0f, 0f, .2f)), occluder = RayMaterial(),
            lights = listOf(
                RayLight("blocked", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), direction = RayVec3(0f, -1f, 0f)),
                RayLight("open", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), direction = RayVec3(0f, -1f, -1f)),
            ),
        )
        val frame = renderScene(session, request)
        val open = .70710677f
        assertArrayEquals(floatArrayOf(.1f + open, .1f + open, .3f + open), frame.colorValues().copyOfRange(3 * 4, 3 * 4 + 3), .002f)
        assertArrayEquals(floatArrayOf(1.1f + open, 1.1f + open, 1.3f + open), frame.colorValues().copyOfRange(0, 3), .002f)
        assertMatchesReference(frame, request, .002f)
    }

    @Test fun terrainReceivesModelShadowsAndRidgesShadowTerrain() = sceneCase("terrain-shadow") { session ->
        val terrain = RayMaterial(kind = RayMaterialKind.TERRAIN, terrain = RayTerrainMaterial(null, List(5) { null }, 1f))
        val model = shadowScene(floor = terrain, occluder = RayMaterial())
        val shadowed = renderScene(session, model)
        assertPixels(shadowed, mapOf(0 to .88f, 3 to .08f), .002f)
        assertMatchesReference(shadowed, model, .002f)
        val ridge = shadowScene(floor = terrain, occluder = null, ridge = terrain, lights = listOf(
            RayLight("low", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), direction = RayVec3(-1f, -1f, 0f).unit())))
        val frame = renderScene(session, ridge)
        // light travels toward +x: the ridge at x = 0 shadows the floor on its -x side
        assertPixels(frame, mapOf(3 to .08f, 4 to .08f + .8f * .70710677f, 6 to .08f + .8f * .70710677f, 1 to .08f + .8f * .70710677f), .002f)
        assertMatchesReference(frame, ridge, .002f)
    }

    // ---- 3.3 reflections ---------------------------------------------------------------------------------------

    @Test fun smoothReflectionsIncludeOffscreenModelsAndSkyMisses() = sceneCase("reflection-model") { session ->
        val request = reflectionScene(panel = RayMaterial(emissive = RayColor(1f, 0f, 0f), baseColor = RayColor(0f, 0f, 0f)))
        val frame = renderScene(session, request)
        val colors = frame.colorValues()
        val pixels = colors.size / 4
        assertTrue("an offscreen red panel must appear in the reflection", (0 until pixels).any { colors[it * 4] > .3f && colors[it * 4 + 1] < .02f && colors[it * 4 + 2] < .02f })
        assertTrue("rays that miss show the sky", (0 until pixels).any { colors[it * 4 + 2] > .2f && colors[it * 4] < .02f })
        assertMatchesReference(frame, request, .02f, outlierFraction = .03f, meanTolerance = .005f)
    }

    @Test fun reflectionsIncludeOffscreenTerrainAndRoughnessChangesTheResult() = sceneCase("reflection-terrain") { session ->
        val terrain = RayMaterial(kind = RayMaterialKind.TERRAIN, terrain = RayTerrainMaterial(null, List(5) { null }, 1f))
        val smooth = reflectionScene(panel = terrain, roughness = .04f, ambient = RayColor(.5f, .5f, .5f))
        val smoothFrame = renderScene(session, smooth)
        val colors = smoothFrame.colorValues()
        assertTrue("offscreen terrain appears in the reflection", (0 until colors.size / 4).any { colors[it * 4] > .05f && abs(colors[it * 4] - colors[it * 4 + 1]) < .01f && colors[it * 4 + 2] > .05f })
        assertMatchesReference(smoothFrame, smooth, .02f, outlierFraction = .03f, meanTolerance = .005f)
        val rough = reflectionScene(panel = terrain, roughness = 1f, ambient = RayColor(.5f, .5f, .5f))
        val roughFrame = renderScene(session, rough)
        assertFalse(smoothFrame.colorValues().contentEquals(roughFrame.colorValues()))
        assertMatchesReference(roughFrame, rough, .3f, outlierFraction = .1f, meanTolerance = .02f)
    }

    @Test fun reflectedPbrSurfacesAreShadedWithoutAFurtherBounce() = sceneCase("reflection-bounds") { session ->
        val panel = RayMaterial(kind = RayMaterialKind.PBR, baseColor = RayColor(.9f, .9f, .9f), metallic = 1f, roughness = .04f)
        val request = reflectionScene(panel = panel, ambient = RayColor(.2f, .2f, .2f))
        assertMatchesReference(renderScene(session, request), request, .02f, outlierFraction = .03f, meanTolerance = .005f)
    }

    @Test fun blendedGeometryIsNotReflected() = sceneCase("reflection-blended") { session ->
        val request = reflectionScene(panel = RayMaterial(emissive = RayColor(1f, 0f, 0f), baseColor = RayColor(0f, 0f, 0f), alphaMode = RayAlphaMode.BLEND, opacity = .9f))
        val colors = renderScene(session, request).colorValues()
        assertTrue("a blended panel never appears in a reflection", (0 until colors.size / 4).all { colors[it * 4] < .02f })
    }

    // ---- 3.4 sky, environment and fog --------------------------------------------------------------------------

    @Test fun skyMissesShowTheEnvironmentWithExposureAndOrientation() = sceneCase("sky") { session ->
        val textures = listOf(skyTexture())
        fun sky(intensity: Float, rotation: Float, hdr: Boolean = false) = skyScene(RayEnvironment(texture = 0, hdr = hdr, intensity = intensity, rotation = rotation), textures)
        val base = renderScene(session, sky(1f, 0f)).colorValues()
        assertMatchesReference(renderScene(session, sky(1f, 0f)), sky(1f, 0f), .001f)
        val bright = renderScene(session, sky(2f, 0f)).colorValues()
        assertArrayEquals(FloatArray(base.size) { if (it % 4 == 3) 1f else base[it] * 2f }, bright, .002f)
        val turned = sky(1f, 90f)
        val turnedFrame = renderScene(session, turned)
        assertFalse(base.contentEquals(turnedFrame.colorValues()))
        assertMatchesReference(turnedFrame, turned, .001f)
        assertTrue("a plain sky keeps texel values above one", bright.any { it > 1f })
    }

    @Test fun hdrSkyIsToneMappedWhenSeenDirectlyAndLinearWhenReflected() = sceneCase("hdr-sky") { session ->
        val textures = listOf(skyTexture())
        val hdr = skyScene(RayEnvironment(texture = 0, hdr = true, intensity = 2f), textures)
        val frame = renderScene(session, hdr)
        assertTrue("the visible HDR sky is display mapped into [0, 1]", frame.colorValues().all { it in 0f..1f })
        assertTrue(frame.colorValues().any { it in .05f..0.999f })
        assertMatchesReference(frame, hdr, .001f)
        // the same sky seen in a mirror stays linear radiance, so values above one survive
        val reflected = reflectionScene(panel = RayMaterial(emissive = RayColor(1f, 0f, 0f), baseColor = RayColor(0f, 0f, 0f)))
        val sky = RayEnvironment(texture = 0, hdr = true, intensity = 4f)
        val bright = sceneRequest(reflected.scene.meshes, reflected.scene.instances, reflected.scene.materials, textures, environment = sky, camera = reflected.camera, width = 16, height = 16)
        val mirror = renderScene(session, bright)
        assertTrue("reflected HDR radiance is not clamped", mirror.colorValues().any { it > 1f })
        assertMatchesReference(mirror, bright, .05f, outlierFraction = .03f, meanTolerance = .01f)
    }

    @Test fun hdrAmbientCubeLightsSurfacesByNormalLikeTheRasterModelShader() = sceneCase("ambient-cube") { session ->
        val cube = listOf(RayColor(.1f, 0f, 0f), RayColor(.2f, 0f, 0f), RayColor(0f, .3f, 0f), RayColor(0f, .4f, 0f), RayColor(0f, 0f, .5f), RayColor(0f, 0f, .6f))
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        fun wall(environment: RayEnvironment) = sceneRequest(listOf(quadXY(-3f, 3f, -3f, 3f, 0f)), listOf(instance(0, 0)), listOf(RayMaterial()), environment = environment, camera = camera)
        // the wall faces +Z, so only the Z slot contributes: a non-negative component takes the odd slot (+Z entry is slot 5)
        val frame = renderScene(session, wall(RayEnvironment(ambient = RayColor(.9f, .9f, .9f), ambientCube = cube)))
        assertArrayEquals(floatArrayOf(0f, 0f, .6f, 1f), frame.colorValues(), .002f)
        assertMatchesReference(frame, wall(RayEnvironment(ambient = RayColor(.9f, .9f, .9f), ambientCube = cube)), .002f)
        // without a cube the flat ambient colour applies, unchanged
        assertArrayEquals(floatArrayOf(.9f, .9f, .9f, 1f), renderScene(session, wall(RayEnvironment(ambient = RayColor(.9f, .9f, .9f)))).colorValues(), .002f)
    }

    @Test fun disabledOrMissingSkyShowsTheBackground() = sceneCase("no-sky") { session ->
        val frame = renderScene(session, skyScene(RayEnvironment(background = RayColor(.2f, .4f, .6f)), emptyList()))
        assertArrayEquals(floatArrayOf(.2f, .4f, .6f, 1f), frame.colorValues().copyOfRange(0, 4), .0005f)
        assertEquals(1f, frame.depthValues()[0], 0f)
    }

    @Test fun fogFollowsHitDistanceAndLeavesTheSkyUnfogged() = sceneCase("fog") { session ->
        fun wall(z: Float, density: Float): RaySceneRequest {
            val mesh = quadXY(-3f, 3f, -3f, 3f, z)
            return sceneRequest(listOf(mesh), listOf(instance(0, 0)), listOf(RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 0f, 0f))),
                environment = RayEnvironment(ambient = RayColor(0f, 0f, 0f), background = RayColor(0f, .5f, 0f)),
                fog = RayFog(RayColor(0f, 0f, 1f), density, 1f), camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        }
        fun amount(density: Float, distance: Float) = minOf(distance * distance * (1f - kotlin.math.exp(-1f)) * density * density, 1f)
        for ((z, density) in listOf(0f to .5f, -3f to .2f, 0f to 10f, 0f to 0f)) {
            val frame = renderScene(session, wall(z, density))
            val a = amount(density, 2f - z)
            assertArrayEquals(floatArrayOf(1f - a, 0f, a, 1f), frame.colorValues(), .002f)
            assertMatchesReference(frame, wall(z, density), .002f)
        }
        // a miss shows the unfogged background
        val miss = renderScene(session, skyScene(RayEnvironment(background = RayColor(0f, .5f, 0f)), emptyList(), fog = RayFog(RayColor(0f, 0f, 1f), 10f, 1f)))
        assertArrayEquals(floatArrayOf(0f, .5f, 0f, 1f), miss.colorValues().copyOfRange(0, 4), .001f)
    }

    // ---- 3.5 transparency --------------------------------------------------------------------------------------

    @Test fun overlappingBlendedLayersCompositeFrontToBackOverTheOpaqueSurface() = sceneCase("layers") { session ->
        fun glow(color: RayColor, alpha: Float) = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = color, opacity = alpha, alphaMode = if (alpha < 1f) RayAlphaMode.BLEND else RayAlphaMode.OPAQUE)
        val request = sceneRequest(
            listOf(quadXY(-3f, 3f, -3f, 3f, 1f), quadXY(-3f, 3f, -3f, 3f, 0f), quadXY(-3f, 3f, -3f, 3f, -3f)),
            listOf(instance(0, 0), instance(1, 1), instance(2, 2)),
            listOf(glow(RayColor(0f, 0f, 1f), .5f), glow(RayColor(0f, 1f, 0f), .5f), glow(RayColor(1f, 0f, 0f), 1f)),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f),
        )
        val frame = renderScene(session, request)
        assertArrayEquals(floatArrayOf(.25f, .25f, .5f, 1f), frame.colorValues(), .002f)
        assertEquals(.98098f, frame.depthValues()[0], .0003f) // blended layers do not write depth; the opaque wall does
        assertMatchesReference(frame, request, .002f)
    }

    @Test fun tooManyBlendedLayersAreAnExplicitFallback() = sceneCase("layer-limit") { session ->
        val glass = RayMaterial(opacity = .5f, alphaMode = RayAlphaMode.BLEND)
        val count = RAY_MAX_BLENDED_INSTANCES + 1
        val request = sceneRequest(List(count) { quadXY(-3f, 3f, -3f, 3f, -it.toFloat()) }, List(count) { instance(it, 0) }, listOf(glass),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        assertThrows(IllegalArgumentException::class.java) { session.submit(request) }
    }

    @Test fun tooManyLightsMaterialsOrTexturesAreAnExplicitFallback() = sceneCase("resource-limits") { session ->
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val lights = List(RAY_MAX_LIGHTS + 1) { RayLight("l$it", RayLightKind.DIRECTIONAL, RayColor(.1f, .1f, .1f)) }
        assertThrows(IllegalArgumentException::class.java) { session.submit(sceneRequest(listOf(quadXY(-1f, 1f, -1f, 1f, 0f)), listOf(instance(0, 0)), listOf(RayMaterial()), lights = lights, camera = camera)) }
        val materials = List(RAY_MAX_MATERIALS + 1) { RayMaterial() }
        assertThrows(IllegalArgumentException::class.java) { session.submit(sceneRequest(listOf(quadXY(-1f, 1f, -1f, 1f, 0f)), listOf(instance(0, 0)), materials, camera = camera)) }
        val textures = List(RAY_MAX_TEXTURES + 1) { RayTexture("t$it", 1, 1, floatArrayOf(1f, 1f, 1f, 1f)) }
        assertThrows(IllegalArgumentException::class.java) { session.submit(sceneRequest(listOf(quadXY(-1f, 1f, -1f, 1f, 0f)), listOf(instance(0, 0)), listOf(RayMaterial()), textures, camera = camera)) }
        // the limits themselves are representable
        val atLimit = renderScene(session, sceneRequest(listOf(quadXY(-1f, 1f, -1f, 1f, 0f)), listOf(instance(0, 0)), listOf(RayMaterial()),
            lights = lights.take(RAY_MAX_LIGHTS), camera = camera))
        assertEquals(1, atLimit.width)
    }

    @Test fun cutoutHolesAreSkippedUpToAFixedDepthAndThenCountAsSolid() = sceneCase("cutout-depth") { session ->
        fun stacked(layers: Int): RaySceneRequest {
            val request = shadowScene(occluder = null)
            val hole = RayMaterial(alphaMode = RayAlphaMode.MASK, baseColor = RayColor(1f, 1f, 1f, 0f))
            val scene = request.scene
            val meshes = scene.meshes + List(layers) { quadXZ("hole$it", -3f, 3f, -2f, 2f, 1f + it * .05f) }
            val instances = scene.instances + List(layers) { instance(1 + it, 1) }
            return sceneRequest(meshes, instances, scene.materials + hole, lights = scene.lights, camera = request.camera.let {
                RaySliceCamera(listOf(0f, .5f, 3f), listOf(0f, -.3f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f) }, width = 8, height = 1)
        }
        assertPixels(renderScene(session, stacked(RAY_CUTOUT_HOLE_DEPTH - 1)), mapOf(3 to 1.1f), .001f)
        assertPixels(renderScene(session, stacked(RAY_CUTOUT_HOLE_DEPTH)), mapOf(3 to .1f), .001f)
    }

    // ---- 2.5 geometry reuse, instances and budgets --------------------------------------------------------------

    @Test fun staticGeometryIsReusedWhileOnlyTransformsChange() = sceneCase("geometry-reuse") { session ->
        fun scene(x: Float, lift: Float = 0f) = sceneRequest(
            // a freshly captured but equal mesh must not rebuild anything
            listOf(quadXY(-3f, 3f, -3f, 3f, -2f + lift)), listOf(instance(0, 0, x = x)), listOf(RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 0f, 0f))),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        assertEquals(1f, renderScene(session, scene(0f)).colorValues()[0], .001f)
        assertEquals(1L, session.geometryBuilds)
        val moved = renderScene(session, scene(20f)) // the quad moved out of view: its transform changed, its geometry did not
        assertEquals(0f, moved.colorValues()[0], .001f)
        assertEquals(1L, session.geometryBuilds)
        assertEquals(1f, renderScene(session, scene(0f)).colorValues()[0], .001f)
        assertEquals(1L, session.geometryBuilds)
        renderScene(session, scene(0f, lift = 1f)) // different positions, same topology: the one structure is refit
        assertEquals(2L, session.geometryBuilds)
    }

    @Test fun aReSkinnedMeshRefitsOnlyItsOwnStructureAndMovesTheHit() = sceneCase("geometry-refit") { session ->
        val glow = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 1f, 1f))
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        fun scene(deformedZ: Float) = sceneRequest(
            // mesh 0 stays put out of view; mesh 1 keeps its triangles and only its positions move, like a skinned pose
            listOf(quadXY(-1f, 1f, -1f, 1f, -40f).let { RayMesh("static", it.positions(), it.indices(), it.normals(), it.uvs()) }, quadXY(-3f, 3f, -3f, 3f, deformedZ).let {
                RayMesh("deformed", it.positions(), it.indices(), it.normals(), it.uvs()) }),
            listOf(instance(0, 0, x = 30f), instance(1, 0)), listOf(glow), camera = camera)
        val before = renderScene(session, scene(0f))
        assertEquals(2L, session.geometryBuilds) // first frame: one structure per mesh
        val deformed = renderScene(session, scene(-3f))
        assertEquals("only the changed mesh is rebuilt", 3L, session.geometryBuilds)
        val back = renderScene(session, scene(0f))
        assertEquals(4L, session.geometryBuilds)
        // depth of a wall at distance 2, then 5, then 2 again: the hit follows the updated geometry
        fun depth(distance: Float) = 100f / 99.9f - 100f * .1f / (99.9f * distance)
        assertEquals(depth(2f), before.depthValues()[0], .0003f)
        assertEquals(depth(5f), deformed.depthValues()[0], .0003f)
        assertEquals(depth(2f), back.depthValues()[0], .0003f)
        assertEquals(1f, deformed.colorValues()[0], .001f)
    }

    @Test fun repeatedInstancesShareOneMeshAndRemovedInstancesDisappear() = sceneCase("repeated-instances") { session ->
        val material = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(0f, 1f, 0f))
        val camera = RaySliceCamera(listOf(0f, 0f, 6f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        // a 4x1 frame at distance 6 has pixel centres pitch = 2 * 6 * tan(30 deg) apart in x; each quad covers one centre
        val pitch = 12f * 0.57735026f
        fun row(columns: List<Int>) = sceneRequest(listOf(quadXY(-2f, 2f, -50f, 50f, 0f)),
            columns.map { instance(0, 0, x = (it - 1.5f) * pitch) }, listOf(material), camera = camera, width = 4, height = 1)
        val all = renderScene(session, row(listOf(0, 1, 2, 3)))
        assertEquals("one shared acceleration structure for every repeated instance", 1L, session.geometryBuilds)
        val covered = (0 until 4).count { all.colorValues()[it * 4 + 1] > .5f }
        assertEquals("repeated instances are all visible", 4, covered)
        val fewer = renderScene(session, row(listOf(0)))
        assertEquals(1L, session.geometryBuilds)
        assertEquals("removed instances no longer render", 1, (0 until 4).count { fewer.colorValues()[it * 4 + 1] > .5f })
    }

    @Test fun geometryOverTheMemoryBudgetIsRejectedAndTheSessionStaysUsable() = sceneCase("geometry-budget") { session ->
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val vertices = (RAY_MAX_GEOMETRY_BYTES / 12 + 1).toInt() // positions alone exceed the budget
        val positions = FloatArray(vertices * 3) { (it % 7) * .01f }
        val big = RayMesh("big", positions, intArrayOf(0, 1, 2))
        assertThrows(IllegalArgumentException::class.java) { session.submit(sceneRequest(listOf(big), listOf(instance(0, 0)), listOf(RayMaterial()), camera = camera)) }
        val ok = renderScene(session, sceneRequest(listOf(quadXY(-3f, 3f, -3f, 3f, 0f)), listOf(instance(0, 0)), listOf(RayMaterial()), camera = camera))
        assertEquals(1, ok.width)
    }

    // ---- real-scene scale: many instances, many blended layers, large and byte textures ---------------------------------

    @Test fun byteTexturesRenderLikeTheSameFloatTextures() = sceneCase("byte-textures") { session ->
        val texels = intArrayOf(255, 128, 0, 255, 10, 200, 90, 255) // two RGBA8 texels
        val bytes = ByteArray(8) { texels[it].toByte() }
        val floats = FloatArray(8) { texels[it] / 255f }
        fun scene(texture: RayTexture) = sceneRequest(listOf(quadXY(-12f, 12f, -3f, 3f, 0f)), listOf(instance(0, 0)),
            listOf(RayMaterial(baseColor = RayColor(1f, 1f, 1f), baseTexture = RayTextureBinding(0))), listOf(texture),
            environment = RayEnvironment(ambient = RayColor(1f, 1f, 1f), background = RayColor(0f, 0f, 0f)),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f), width = 8, height = 1)
        val byteTexture = RayTexture("t", 2, 1, bytes, RayWrap.CLAMP_TO_EDGE, RayWrap.CLAMP_TO_EDGE, RayFilter.NEAREST)
        val floatTexture = RayTexture("t", 2, 1, floats, RayWrap.CLAMP_TO_EDGE, RayWrap.CLAMP_TO_EDGE, RayFilter.NEAREST)
        assertTrue(byteTexture.isBytes); assertFalse(floatTexture.isBytes)
        val fromBytes = renderScene(session, scene(byteTexture))
        val fromFloats = renderScene(session, scene(floatTexture))
        assertArrayEquals(fromFloats.colorValues(), fromBytes.colorValues(), 1f / 255f)
        assertTrue("both halves of the texture are visible", fromBytes.colorValues().let { it[0] > .9f && it[7 * 4 + 1] > .5f })
        assertMatchesReference(fromBytes, scene(byteTexture), .003f)
    }

    @Test fun aLargeByteTextureIsAcceptedWithoutExpandingToFloats() = sceneCase("large-texture") { session ->
        val size = 2048
        val big = RayTexture("big", size, size, ByteArray(size * size * 4) { if (it % 4 == 3) -1 else 100 })
        val request = sceneRequest(listOf(quadXY(-3f, 3f, -3f, 3f, 0f)), listOf(instance(0, 0)),
            listOf(RayMaterial(baseTexture = RayTextureBinding(0))), listOf(big),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        // 4M texels as floats would be 67 MB; as bytes it is 16 MB and well inside the payload bounds
        assertEquals(100 / 255f * .1f, renderScene(session, request).colorValues()[0], .01f) // base colour x texel x the default 0.1 ambient
    }

    @Test fun scenesPastTheOldInstanceCapRenderWithTheBackendsOwnCapacity() = sceneCase("many-instances", wide = true) { session ->
        val material = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(0f, 1f, 0f))
        val pitch = 12f * 0.57735026f
        val inView = (0 until 4).map { instance(0, 0, x = (it - 1.5f) * pitch) }
        val count = 300 // well past the old 128
        val offscreen = List(count - inView.size) { instance(0, 0, x = 1000f + it) }
        val request = sceneRequest(listOf(quadXY(-2f, 2f, -50f, 50f, 0f)), inView + offscreen, listOf(material),
            camera = RaySliceCamera(listOf(0f, 0f, 6f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f), width = 4, height = 1)
        val frame = renderScene(session, request)
        assertEquals(count, request.scene.instances.size)
        assertEquals(4, (0 until 4).count { frame.colorValues()[it * 4 + 1] > .5f })
        assertEquals("one shared structure for all of them", 1L, session.geometryBuilds)
    }

    @Test fun manyDistinctMeshesPastTheOldCapAreBuiltAndHit() = sceneCase("many-meshes", wide = true) { session ->
        val material = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 0f, 0f))
        val count = 237 // the real Main Scene needs 237 mesh parts
        val meshes = List(count) { RayMesh("m$it", quadXY(-1f, 1f, -1f, 1f, 0f).positions(), quadXY(-1f, 1f, -1f, 1f, 0f).indices()) }
        val instances = List(count) { instance(it, 0, x = if (it == count - 1) 0f else 1000f + it) }
        val request = sceneRequest(meshes, instances, listOf(material),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        assertEquals(1f, renderScene(session, request).colorValues()[0], .001f)
        assertEquals(count.toLong(), session.geometryBuilds)
    }

    @Test fun aDozenBlendedLayersCompositeWithinTheLimit() = sceneCase("twelve-layers") { session ->
        // 12 blended instances is what the real Main Scene has; stacked in one ray they must composite, not fall back
        val glass = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(0f, 0f, 1f), opacity = .2f, alphaMode = RayAlphaMode.BLEND)
        val wall = RayMaterial(baseColor = RayColor(0f, 0f, 0f), emissive = RayColor(1f, 0f, 0f))
        val request = sceneRequest(List(12) { quadXY(-3f, 3f, -3f, 3f, 1f - it * .05f) } + quadXY(-3f, 3f, -3f, 3f, -3f),
            List(12) { instance(it, 0) } + instance(12, 1), listOf(glass, wall),
            camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f))
        assertNull(request.scene.unsupportedReason())
        val frame = renderScene(session, request)
        val transmittance = Math.pow(.8, 12.0).toFloat()
        assertEquals(transmittance, frame.colorValues()[0], .01f)           // the wall shows through twelve panes
        assertEquals(1f - transmittance, frame.colorValues()[2], .01f)      // the blue accumulates
        assertMatchesReference(frame, request, .003f)
    }

    // ---- scene helpers -----------------------------------------------------------------------------------------

    /** [wide] opens the session with every instance the backend can hold, as the plugin does, instead of the default limits. */
    private fun sceneCase(name: String, wide: Boolean = false, check: (RaySession) -> Unit) {
        org.junit.Assume.assumeTrue(sceneMaterials)
        withBackend { backend, _ ->
            backend.openSession(name, if (wide) RayLimits(maxInstances = backend.capabilities.maxInstances) else RayLimits()).use(check)
        }
    }

    private fun renderScene(session: RaySession, request: RaySceneRequest): RayFrame {
        session.submit(request); return await(session)
    }

    private fun assertPixels(frame: RayFrame, expected: Map<Int, Float>, tolerance: Float) {
        val colors = frame.colorValues()
        for ((column, value) in expected) for (channel in 0..2)
            assertEquals("column $column channel $channel", value, colors[column * 4 + channel], tolerance)
    }

    /** Compares against the shared reference renderer: a per-pixel tolerance, an allowed outlier share and a mean bound. */
    private fun assertMatchesReference(frame: RayFrame, request: RaySceneRequest, tolerance: Float, outlierFraction: Float = 0f, meanTolerance: Float = tolerance) {
        val expected = RaySceneReferenceRenderer().render(request)
        for ((actual, reference) in listOf(frame.colorValues() to expected.colorValues(), frame.depthValues() to expected.depthValues())) {
            val outliers = actual.indices.count { abs(actual[it] - reference[it]) > tolerance }
            assertTrue("$outliers of ${actual.size} values differ from the reference by more than $tolerance", outliers <= actual.size * outlierFraction)
            assertTrue(actual.indices.sumOf { abs(actual[it] - reference[it]).toDouble() } / actual.size <= meanTolerance)
        }
    }

    private fun sceneRequest(
        meshes: List<RayMesh>, instances: List<RayInstance>, materials: List<RayMaterial>, textures: List<RayTexture> = emptyList(),
        lights: List<RayLight> = emptyList(), environment: RayEnvironment = RayEnvironment(ambient = RayColor(.1f, .1f, .1f), background = RayColor(0f, 0f, 0f)),
        fog: RayFog? = null, camera: RaySliceCamera, width: Int = 1, height: Int = 1,
    ) = RaySceneRequest(RayFrameKey(1, 1, 1, 1), width, height, camera, RaySceneSnapshot(meshes, instances, materials, textures, lights, environment, fog))

    private fun instance(mesh: Int, material: Int, x: Float = 0f, y: Float = 0f, z: Float = 0f) =
        RayInstance("i$mesh-$material", mesh, material, floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, z, 1f))

    /** An upward-facing quad in the XZ plane; u runs along x over [x0, x1]. */
    private fun quadXZ(id: String, x0: Float, x1: Float, z0: Float, z1: Float, y: Float) = RayMesh(id,
        floatArrayOf(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1), intArrayOf(0, 2, 1, 0, 3, 2),
        normals = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f), uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

    /** A quad in an XY plane facing +z. */
    private fun quadXY(x0: Float, x1: Float, y0: Float, y1: Float, z: Float) = RayMesh("xy$z",
        floatArrayOf(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z), intArrayOf(0, 1, 2, 0, 2, 3),
        normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

    private fun cutoutTexture() = RayTexture("cutout", 2, 1, floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f), filter = RayFilter.NEAREST, wrapU = RayWrap.CLAMP_TO_EDGE, wrapV = RayWrap.CLAMP_TO_EDGE)

    private fun skyTexture() = RayTexture("sky", 4, 2, FloatArray(4 * 2 * 4) { i ->
        val texel = i / 4
        when (i % 4) { 0 -> .5f + texel; 1 -> 3f - texel * .25f; 2 -> (texel % 4) * 1.5f; else -> 1f }
    }, wrapU = RayWrap.REPEAT, wrapV = RayWrap.CLAMP_TO_EDGE)

    private fun skyScene(environment: RayEnvironment, textures: List<RayTexture>, fog: RayFog? = null) = sceneRequest(
        listOf(quadXY(-1f, 1f, -1f, 1f, 8f)), listOf(instance(0, 0)), listOf(RayMaterial()), textures, emptyList(), environment, fog,
        RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f), 4, 4,
    )

    /**
     * Floor at y = 0 and, above the camera's rays, an optional occluder at [occluderHeight], an optional ridge wall, and
     * the lights. An 8x1 frame looks down at the floor so each column is a distinct floor point (x of about
     * -7, -5, -3, -1, 1, 3, 5, 7) and no ray touches the occluder.
     */
    private fun shadowScene(
        floor: RayMaterial = RayMaterial(), occluder: RayMaterial? = RayMaterial(), occluderHeight: Float = 1f, occluderHalfWidth: Float = 3f,
        ridge: RayMaterial? = null,
        lights: List<RayLight> = listOf(RayLight("sun", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), direction = RayVec3(0f, -1f, 0f))),
        textures: List<RayTexture> = emptyList(),
    ): RaySceneRequest {
        val meshes = mutableListOf(quadXZ("floor", -20f, 20f, -20f, 20f, 0f))
        val instances = mutableListOf(instance(0, 0))
        val materials = mutableListOf(floor)
        if (occluder != null) {
            meshes += quadXZ("occluder", -occluderHalfWidth, occluderHalfWidth, -2f, 2f, occluderHeight)
            materials += occluder; instances += instance(1, materials.lastIndex)
        }
        if (ridge != null) {
            meshes += RayMesh("ridge", floatArrayOf(0f, 0f, -2f, 0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f, -2f), intArrayOf(0, 1, 2, 0, 2, 3),
                normals = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f), uvs = FloatArray(8))
            materials += ridge; instances += instance(meshes.lastIndex, materials.lastIndex)
        }
        return sceneRequest(meshes, instances, materials, textures, lights,
            camera = RaySliceCamera(listOf(0f, .5f, 3f), listOf(0f, -.3f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f), width = 8, height = 1)
    }

    /**
     * A PBR metal floor seen from above and behind a [panel] that stays outside the camera image (above the top ray at
     * its distance), so only reflections can show it.
     */
    private fun reflectionScene(panel: RayMaterial, roughness: Float = .04f, ambient: RayColor = RayColor(0f, 0f, 0f)): RaySceneRequest {
        val metal = RayMaterial(kind = RayMaterialKind.PBR, baseColor = RayColor(1f, 1f, 1f), metallic = 1f, roughness = roughness)
        val panelMesh = RayMesh("panel", floatArrayOf(-8f, 3f, -10f, 8f, 3f, -10f, 8f, 15f, -10f, -8f, 15f, -10f), intArrayOf(0, 1, 2, 0, 2, 3),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), uvs = FloatArray(8))
        return sceneRequest(listOf(quadXZ("floor", -30f, 30f, -30f, 30f, 0f), panelMesh), listOf(instance(0, 0), instance(1, 1)), listOf(metal, panel),
            environment = RayEnvironment(ambient = ambient, background = RayColor(0f, 0f, .5f)),
            camera = RaySliceCamera(listOf(0f, 2f, 4f), listOf(0f, -.5f, -1f), listOf(0f, 1f, 0f), 40f, .1f, 100f), width = 16, height = 16)
    }

    private fun materialRequest(material:RayMaterial,textures:List<RayTexture> = emptyList(),environment:RayEnvironment=RayEnvironment(),
        lights:List<RayLight> = listOf(RayLight("sun",RayLightKind.DIRECTIONAL,RayColor(2f,2f,2f),direction=RayVec3(0f,0f,-1f)))):RaySceneRequest {
        val mesh=RayMesh("triangle",floatArrayOf(-2f,-2f,0f,2f,-2f,0f,0f,2f,0f),intArrayOf(0,1,2),
            normals=floatArrayOf(0f,0f,1f,0f,0f,1f,0f,0f,1f),uvs=floatArrayOf(0f,0f,1f,0f,.5f,1f))
        val instance=RayInstance("triangle",0,0,floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f))
        return RaySceneRequest(RayFrameKey(1,1,1,1),1,1,RaySliceCamera(listOf(0f,0f,2f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,.1f,100f),
            RaySceneSnapshot(listOf(mesh),listOf(instance),listOf(material),textures,lights,environment))
    }

    protected fun withBackend(check: (RayBackend, RayDeviceHealth) -> Unit) {
        val health = RayDeviceHealth()
        val result = provider(true, health).probe()
        assertTrue("Backend probe must succeed: $result", result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { check(it, health) }
    }

    private fun triangleRequest(x: Float = 0f, z: Float = 0f, revision: Long = 1, generation: Long = 1, width: Int = 1) = RayRequest(
        RayFrameKey(generation, 1, revision, 1),
        width,
        1,
        RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f),
        listOf(RaySliceMesh(floatArrayOf(-2f, -2f, z, 2f, -2f, z, 0f, 2f, z), intArrayOf(0, 1, 2))),
        listOf(instance(0, x = x))
    )

    private fun request(meshes: List<RaySliceMesh>, instances: List<RaySliceInstance>) = RayRequest(
        RayFrameKey(1, 1, 1, 1),
        1,
        1,
        RaySliceCamera(listOf(0f, 2f, 4f), listOf(0f, -2f, -4f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f),
        meshes,
        instances
    )

    private fun floor(size: Float = 10f) = RaySliceMesh(
        floatArrayOf(-size, 0f, -size, size, 0f, -size, size, 0f, size, -size, 0f, size), intArrayOf(0, 2, 1, 0, 3, 2)
    )

    private fun instance(
        mesh: Int,
        x: Float = 0f,
        y: Float = 0f,
        color: List<Float> = listOf(1f, 1f, 1f),
        reflective: Boolean = false
    ) =
        RaySliceInstance(mesh, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, 0f, 1f), color, reflective)

    private fun render(session: RaySession, request: RayRequest): RayFrame {
        session.submit(request); return await(session)
    }

    private fun await(session: RaySession): RayFrame {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            session.poll()?.let { return it }
            Thread.sleep(1)
        }
        error("Backend frame completion timed out")
    }

    private fun assertReference(frame: RayFrame, color: FloatArray, depth: FloatArray, tolerance: Float) {
        val actual = frame.colorValues()
        assertArrayEquals(color, actual, tolerance)
        assertArrayEquals(depth, frame.depthValues(), tolerance)
        assertTrue(actual.indices.sumOf {
            kotlin.math.abs(actual[it] - color[it]).toDouble()
        } / actual.size <= tolerance)
    }
}
