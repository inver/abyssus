package net.nevinsky.abyssus.lib.core.dto

/** What an entity is, as the `type` of its `ecs` entry says. */
enum class EntityType {
    GROUP,
    LIGHT_DIRECTIONAL,
    LIGHT_POINT,
    LIGHT_SPOT,
    OBJECT,
    TERRAIN,
    CAMERA,
    HANDLE
}
