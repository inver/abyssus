/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.water

import net.nevinsky.abyssus.lib.core.editor.water.WaterEditError
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterIssue
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** The Water group of a scene's tree: the surfaces read from `abyssus.waterSurfaces`, invalid ones included. */
data class WaterGroup(val surfaces: List<WaterSurface>)

/** The internal name of the Water group row (its label is localized). */
const val WATER_ROW = "water"

fun waterPresetLabel(preset: WaterPreset): String =
    AbyssusBundle.message(if (preset == WaterPreset.LAKE) "waterPresetLake" else "waterPresetSea")

fun waterFieldLabel(field: WaterField): String = AbyssusBundle.message(
    when (field) {
        WaterField.X -> "waterFieldX"
        WaterField.LEVEL -> "waterFieldLevel"
        WaterField.Z -> "waterFieldZ"
        WaterField.WIDTH -> "waterFieldWidth"
        WaterField.LENGTH -> "waterFieldLength"
        WaterField.TINT_R -> "waterFieldTintR"
        WaterField.TINT_G -> "waterFieldTintG"
        WaterField.TINT_B -> "waterFieldTintB"
        WaterField.CLARITY -> "waterFieldClarity"
        WaterField.WAVE_AMPLITUDE -> "waterFieldWaveAmplitude"
        WaterField.WAVE_LENGTH -> "waterFieldWaveLength"
        WaterField.WAVE_SPEED -> "waterFieldWaveSpeed"
        WaterField.FOAM_AMOUNT -> "waterFieldFoamAmount"
        WaterField.FOAM_WIDTH -> "waterFieldFoamWidth"
    },
)

fun waterIssueText(issue: WaterIssue): String = AbyssusBundle.message(
    when (issue) {
        WaterIssue.NOT_FINITE -> "waterIssueNotFinite"
        WaterIssue.NOT_POSITIVE -> "waterIssueNotPositive"
        WaterIssue.NEGATIVE -> "waterIssueNegative"
        WaterIssue.OUT_OF_UNIT_RANGE -> "waterIssueUnitRange"
    },
)

/** The reason a refused edit is shown with. */
fun waterErrorText(error: WaterEditError): String = when (error) {
    WaterEditError.IncompatibleContainer -> AbyssusBundle.message("waterErrorIncompatible")
    WaterEditError.IdCollision -> AbyssusBundle.message("waterErrorIdCollision")
    WaterEditError.NotFound -> AbyssusBundle.message("waterErrorNotFound")
    WaterEditError.BlankName -> AbyssusBundle.message("waterErrorBlankName")
    is WaterEditError.InvalidValue -> waterIssueText(error.issue)
    is WaterEditError.InvalidSurface -> error.issues.entries.firstOrNull()
        ?.let { "${waterFieldLabel(it.key)}: ${waterIssueText(it.value)}" } ?: AbyssusBundle.message("waterErrorInvalidSurface")
}
