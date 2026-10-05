package net.nevinsky.abyssus.core.scene

data class RayTracing(
    var targetSamplesPerPixel: Int = 256,
    var maxRaysPerFrame: Int = 2097152,
    var maxReflectionBounces: Int = 1,
    var maxRefractionBounces: Int = 0,
)