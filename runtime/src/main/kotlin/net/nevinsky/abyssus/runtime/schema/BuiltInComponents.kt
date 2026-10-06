package net.nevinsky.abyssus.runtime.schema

/** The short names of the components the runtime models itself; a game component cannot take one. */
val BUILT_IN_COMPONENTS: Set<String> = linkedSetOf(
    "NameComponent", "TypeComponent", "ParentComponent", "PositionComponent", "CameraComponent", "LightComponent",
    "Point2PointPositionComponent", "RenderComponent",
)

