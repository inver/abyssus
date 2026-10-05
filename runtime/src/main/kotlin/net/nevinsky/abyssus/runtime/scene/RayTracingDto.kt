package net.nevinsky.abyssus.runtime.scene

data class RayTracingDto(
    var enabled: Boolean = false,
    var targetSamplesPerPixel: Int = 256,
    var maxRaysPerFrame: Int = 2097152,
    var maxReflectionBounces: Int = 1,
    var maxRefractionBounces: Int = 0,
)