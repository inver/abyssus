/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

/** The starting choice a surface was created from; it is a record of that choice, not a live link to its values. */
enum class WaterPreset { LAKE, SEA }

/**
 * One scalar of a surface: the JSON [path] (relative to the surface object) that stores it, and how to read and replace
 * it on a [WaterSettings]. The one table behind the codec, the validation and the property editors.
 */
enum class WaterField(
    val path: List<String>,
    val get: (WaterSettings) -> Double,
    val set: (WaterSettings, Double) -> WaterSettings,
) {
    X(listOf("position", "x"), { it.x }, { s, v -> s.copy(x = v) }),
    LEVEL(listOf("position", "y"), { it.level }, { s, v -> s.copy(level = v) }),
    Z(listOf("position", "z"), { it.z }, { s, v -> s.copy(z = v) }),
    WIDTH(listOf("width"), { it.width }, { s, v -> s.copy(width = v) }),
    LENGTH(listOf("length"), { it.length }, { s, v -> s.copy(length = v) }),
    TINT_R(listOf("tint", "r"), { it.tintR }, { s, v -> s.copy(tintR = v) }),
    TINT_G(listOf("tint", "g"), { it.tintG }, { s, v -> s.copy(tintG = v) }),
    TINT_B(listOf("tint", "b"), { it.tintB }, { s, v -> s.copy(tintB = v) }),
    CLARITY(listOf("clarity"), { it.clarity }, { s, v -> s.copy(clarity = v) }),
    WAVE_AMPLITUDE(listOf("waveAmplitude"), { it.waveAmplitude }, { s, v -> s.copy(waveAmplitude = v) }),
    WAVE_LENGTH(listOf("waveLength"), { it.waveLength }, { s, v -> s.copy(waveLength = v) }),
    WAVE_SPEED(listOf("waveSpeed"), { it.waveSpeed }, { s, v -> s.copy(waveSpeed = v) }),
    FOAM_AMOUNT(listOf("foamAmount"), { it.foamAmount }, { s, v -> s.copy(foamAmount = v) }),
    FOAM_WIDTH(listOf("foamWidth"), { it.foamWidth }, { s, v -> s.copy(foamWidth = v) }),
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
    fun value(field: WaterField): Double = field.get(this)

    fun with(field: WaterField, value: Double): WaterSettings = field.set(this, value)
}

/** The values a preset starts with. Written explicitly on creation, so retuning them never changes a saved surface. */
class WaterPresets {
    /** The preset named [name] exactly (`LAKE` or `SEA`), or null for anything else. */
    fun parse(name: String?): WaterPreset? = WaterPreset.entries.firstOrNull { it.name == name }

    fun defaults(
        preset: WaterPreset,
        id: String,
        name: String,
        x: Double = 0.0,
        level: Double = 0.0,
        z: Double = 0.0
    ): WaterSettings =
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
enum class WaterIssue {
    NOT_FINITE,
    NOT_POSITIVE,
    NEGATIVE,
    OUT_OF_UNIT_RANGE
}

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
        for (field in WaterField.entries) {
            check(field, settings.value(field))?.let { issues[field] = it }
        }
        return issues
    }

    fun isValid(settings: WaterSettings): Boolean = check(settings).isEmpty()

    private val POSITIVE = setOf(
        WaterField.WIDTH, WaterField.LENGTH, WaterField.CLARITY, WaterField.WAVE_LENGTH, WaterField.FOAM_WIDTH,
    )
    private val NON_NEGATIVE = setOf(WaterField.WAVE_AMPLITUDE, WaterField.WAVE_SPEED)
    private val UNIT = setOf(WaterField.TINT_R, WaterField.TINT_G, WaterField.TINT_B, WaterField.FOAM_AMOUNT)
}
