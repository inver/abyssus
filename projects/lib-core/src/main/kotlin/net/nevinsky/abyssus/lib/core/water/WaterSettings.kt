/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

/** The starting choice a surface was created from; it is a record of that choice, not a live link to its values. */
enum class WaterPreset { LAKE, SEA }

/** One scalar of a surface, with the JSON path (relative to the surface object) that stores it. */
enum class WaterField(val path: List<String>) {
    X(listOf("position", "x")),
    LEVEL(listOf("position", "y")),
    Z(listOf("position", "z")),
    WIDTH(listOf("width")),
    LENGTH(listOf("length")),
    TINT_R(listOf("tint", "r")),
    TINT_G(listOf("tint", "g")),
    TINT_B(listOf("tint", "b")),
    CLARITY(listOf("clarity")),
    WAVE_AMPLITUDE(listOf("waveAmplitude")),
    WAVE_LENGTH(listOf("waveLength")),
    WAVE_SPEED(listOf("waveSpeed")),
    FOAM_AMOUNT(listOf("foamAmount")),
    FOAM_WIDTH(listOf("foamWidth")),
}

/**
 * One finite horizontal water surface. [x], [level] and [z] are its centre (the level is the mean water height);
 * [width] runs along X and [length] along Z. Values are as saved: use [WaterValidation] before rendering them.
 */
data class WaterSettings(
    val id: String,
    val name: String,
    val preset: WaterPreset,
    val x: Double,
    val level: Double,
    val z: Double,
    val width: Double,
    val length: Double,
    val tintR: Double,
    val tintG: Double,
    val tintB: Double,
    /** The distance in world units over which the tint absorbs the view of a submerged bottom. */
    val clarity: Double,
    val waveAmplitude: Double,
    val waveLength: Double,
    val waveSpeed: Double,
    val foamAmount: Double,
    val foamWidth: Double,
    val enabled: Boolean = true,
) {
    fun value(field: WaterField): Double = when (field) {
        WaterField.X -> x
        WaterField.LEVEL -> level
        WaterField.Z -> z
        WaterField.WIDTH -> width
        WaterField.LENGTH -> length
        WaterField.TINT_R -> tintR
        WaterField.TINT_G -> tintG
        WaterField.TINT_B -> tintB
        WaterField.CLARITY -> clarity
        WaterField.WAVE_AMPLITUDE -> waveAmplitude
        WaterField.WAVE_LENGTH -> waveLength
        WaterField.WAVE_SPEED -> waveSpeed
        WaterField.FOAM_AMOUNT -> foamAmount
        WaterField.FOAM_WIDTH -> foamWidth
    }

    fun with(field: WaterField, value: Double): WaterSettings = when (field) {
        WaterField.X -> copy(x = value)
        WaterField.LEVEL -> copy(level = value)
        WaterField.Z -> copy(z = value)
        WaterField.WIDTH -> copy(width = value)
        WaterField.LENGTH -> copy(length = value)
        WaterField.TINT_R -> copy(tintR = value)
        WaterField.TINT_G -> copy(tintG = value)
        WaterField.TINT_B -> copy(tintB = value)
        WaterField.CLARITY -> copy(clarity = value)
        WaterField.WAVE_AMPLITUDE -> copy(waveAmplitude = value)
        WaterField.WAVE_LENGTH -> copy(waveLength = value)
        WaterField.WAVE_SPEED -> copy(waveSpeed = value)
        WaterField.FOAM_AMOUNT -> copy(foamAmount = value)
        WaterField.FOAM_WIDTH -> copy(foamWidth = value)
    }
}

/** The values a preset starts with. Written explicitly on creation, so retuning them never changes a saved surface. */
class WaterPresets {
    /** The preset named [name] exactly (`LAKE` or `SEA`), or null for anything else. */
    fun parse(name: String?): WaterPreset? = WaterPreset.entries.firstOrNull { it.name == name }

    fun defaults(preset: WaterPreset, id: String, name: String, x: Double = 0.0, level: Double = 0.0, z: Double = 0.0): WaterSettings =
        when (preset) {
            WaterPreset.LAKE -> WaterSettings(
                id, name, preset, x, level, z, width = 100.0, length = 100.0,
                tintR = 0.03, tintG = 0.22, tintB = 0.25, clarity = 8.0,
                waveAmplitude = 0.08, waveLength = 4.0, waveSpeed = 0.5, foamAmount = 0.5, foamWidth = 0.6,
            )
            WaterPreset.SEA -> WaterSettings(
                id, name, preset, x, level, z, width = 2000.0, length = 2000.0,
                tintR = 0.02, tintG = 0.12, tintB = 0.20, clarity = 15.0,
                waveAmplitude = 0.6, waveLength = 24.0, waveSpeed = 2.0, foamAmount = 0.8, foamWidth = 1.5,
            )
        }
}

/** Why a value is refused. */
enum class WaterIssue { NOT_FINITE, NOT_POSITIVE, NEGATIVE, OUT_OF_UNIT_RANGE }

/** The range rules of every water scalar; pure and shared by the file reader and the properties editors. */
class WaterValidation {
    fun check(field: WaterField, value: Double): WaterIssue? = when {
        !value.isFinite() -> WaterIssue.NOT_FINITE
        field in POSITIVE -> if (value > 0.0) null else WaterIssue.NOT_POSITIVE
        field in NON_NEGATIVE -> if (value >= 0.0) null else WaterIssue.NEGATIVE
        field in UNIT -> if (value in 0.0..1.0) null else WaterIssue.OUT_OF_UNIT_RANGE
        else -> null
    }

    /** Every field of [settings] that breaks its rule, in field order. */
    fun check(settings: WaterSettings): Map<WaterField, WaterIssue> {
        val issues = linkedMapOf<WaterField, WaterIssue>()
        for (field in WaterField.entries) check(field, settings.value(field))?.let { issues[field] = it }
        return issues
    }

    fun isValid(settings: WaterSettings): Boolean = check(settings).isEmpty()

    private val POSITIVE = setOf(
        WaterField.WIDTH, WaterField.LENGTH, WaterField.CLARITY, WaterField.WAVE_LENGTH, WaterField.FOAM_WIDTH,
    )
    private val NON_NEGATIVE = setOf(WaterField.WAVE_AMPLITUDE, WaterField.WAVE_SPEED)
    private val UNIT = setOf(WaterField.TINT_R, WaterField.TINT_G, WaterField.TINT_B, WaterField.FOAM_AMOUNT)
}
