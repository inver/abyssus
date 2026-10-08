/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

/**
 * Marks an Ashley component class as a game component stored in scene files under the short [name]
 * (`ecs.entities.<id>.components.<name>`). [label] is what the editor shows; empty means [name] without `Component`.
 * The class needs a no-argument constructor; its fields' values in a new instance are their defaults.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SceneComponent(val name: String, val label: String = "")

/**
 * An editable field of a [SceneComponent]: shown under [label] (the field name when empty) in [group]. [min] and
 * [max] bound a number, or each axis of a `Vector3`; with [minExclusive] the value must be greater than [min].
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Field(
    val label: String = "",
    val group: String = "",
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY,
    val minExclusive: Boolean = false,
)

/** On an `Int` [Field]: the id of another entity of the scene, `-1` for none. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class EntityRef

/** On a `String` [Field]: the name of an asset folder of the project whose meta type is [type] (`MODEL`, ...). */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class AssetRef(val type: String)
