package net.nevinsky.abyssus.lib.core.format

/** What an entity is, as the `type` of its `ecs` entry says. */
enum class EntityType {
    GROUP,
    LIGHT_DIRECTIONAL,
    LIGHT_POINT,
    LIGHT_SPOT,
    OBJECT,
    TERRAIN,
    CAMERA,
    HANDLE,

    /** A named attach point: a child entity with only a name and a pose, drawn as a marker in the editor. */
    DUMMY,
}
