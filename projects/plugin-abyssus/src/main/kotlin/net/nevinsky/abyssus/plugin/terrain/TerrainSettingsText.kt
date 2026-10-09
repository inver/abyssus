/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.lib.gdx.editor.terrain.SettingsError
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainGenerationSettings
import java.math.BigDecimal

/** The seven generation settings an editor shows, by the key a test or editor name uses. */
enum class TerrainSettingField(val key: String, val labelKey: String, val integer: Boolean) {
    SEED("seed", "terrainSeed", true),
    FEATURE_SIZE("featureSize", "terrainFeatureSize", false),
    MIN_HEIGHT("minHeight", "terrainMinHeight", false),
    MAX_HEIGHT("maxHeight", "terrainMaxHeight", false),
    OCTAVES("octaves", "terrainOctaves", true),
    PERSISTENCE("persistence", "terrainPersistence", false),
    LACUNARITY("lacunarity", "terrainLacunarity", false),
}

/** The outcome of reading one setting from the text typed into its editor. */
sealed interface SettingParse {
    data class Parsed(val settings: TerrainGenerationSettings) : SettingParse
    data class Failed(val message: String) : SettingParse
}

fun TerrainSettingField.label(): String = AbyssusBundle.message(labelKey)

/** [settings]'s value of this field as an editor shows it. */
fun TerrainSettingField.textOf(settings: TerrainGenerationSettings): String = when (this) {
    TerrainSettingField.SEED -> settings.seed.toString()
    TerrainSettingField.FEATURE_SIZE -> settings.featureSize.toString()
    TerrainSettingField.MIN_HEIGHT -> settings.minHeight.toString()
    TerrainSettingField.MAX_HEIGHT -> settings.maxHeight.toString()
    TerrainSettingField.OCTAVES -> settings.octaves.toString()
    TerrainSettingField.PERSISTENCE -> settings.persistence.toString()
    TerrainSettingField.LACUNARITY -> settings.lacunarity.toString()
}

/** [current] with this field set from [text], or why the text is not a value of the field's type. Range rules are the settings' own. */
fun TerrainSettingField.parse(current: TerrainGenerationSettings, text: String): SettingParse {
    val trimmed = text.trim()
    if (integer) {
        val n = trimmed.toIntOrNull()
            ?: return SettingParse.Failed(AbyssusBundle.message(if (trimmed.toBigDecimalOrNull() == null) "terrainSettingNotANumber" else "terrainSettingNotAnInteger", label()))
        return SettingParse.Parsed(if (this == TerrainSettingField.SEED) current.copy(seed = n) else current.copy(octaves = n))
    }
    val f = trimmed.toBigDecimalOrNull()?.let(BigDecimal::toFloat)?.takeIf { it.isFinite() }
        ?: return SettingParse.Failed(AbyssusBundle.message("terrainSettingNotANumber", label()))
    return SettingParse.Parsed(
        when (this) {
            TerrainSettingField.FEATURE_SIZE -> current.copy(featureSize = f)
            TerrainSettingField.MIN_HEIGHT -> current.copy(minHeight = f)
            TerrainSettingField.MAX_HEIGHT -> current.copy(maxHeight = f)
            TerrainSettingField.PERSISTENCE -> current.copy(persistence = f)
            else -> current.copy(lacunarity = f)
        },
    )
}

/** The localized reasons [settings] cannot generate, in field order; empty when it can. */
fun settingsProblems(settings: TerrainGenerationSettings): List<String> =
    settings.errors().map { AbyssusBundle.message("terrainSettingsError.${it.name}") }

internal fun SettingsError.message(): String = AbyssusBundle.message("terrainSettingsError.$name")
