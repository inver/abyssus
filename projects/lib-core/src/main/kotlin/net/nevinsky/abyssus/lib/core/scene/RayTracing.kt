package net.nevinsky.abyssus.lib.core.scene

data class RayTracing(
    // Explicit null is malformed saved data, retained for the editor's limit validator; omitted fields default.
    var targetSamplesPerPixel: Int? = 256,
    var maxRaysPerFrame: Int? = 2097152,
    var maxReflectionBounces: Int? = 1,
    var maxRefractionBounces: Int? = 0,
)
