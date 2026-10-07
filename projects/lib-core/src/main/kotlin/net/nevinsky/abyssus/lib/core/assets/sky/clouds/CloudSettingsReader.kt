/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger

/**
 * Reads a `CLOUDS` asset's `additional`: its `technique` and bands. A band that is not valid (a type of another level, `base` not below `top`, an altitude outside its level, a value that is not a number
 * or out of range) is skipped and logged, and the other bands are kept. No IO; runs in `prepare`.
 */
class CloudSettingsReader(private val log: Logger) {

    /** The `additional` object [node] of the cloud asset [assetName]; a missing or non-object one has no bands. */
    fun read(assetName: String, node: JsonNode?): CloudSettings {
        if (node == null || !node.isObject) return CloudSettings()
        val technique = node["technique"]?.let { value ->
            CloudTechnique.entries.firstOrNull { it.key == value.asText() }
                ?: CloudTechnique.SHELLS.also { log.warn("'$assetName': unknown cloud technique '${value.asText()}', using shells") }
        } ?: CloudTechnique.SHELLS
        return CloudSettings(technique, bands(assetName, node))
    }

    /** The valid bands `low`, `mid` and `high` of [node]; [owner] names the asset in log messages. */
    fun bands(owner: String, node: JsonNode): Map<CloudLevel, CloudBand> {
        val bands = LinkedHashMap<CloudLevel, CloudBand>()
        for (level in CloudLevel.entries) {
            val band = node[level.key] ?: continue
            when (val result = band(level, band)) {
                is Result.Valid -> bands[level] = result.band
                is Result.Invalid -> log.warn("'$owner': cloud band '${level.key}' skipped: ${result.reason}")
            }
        }
        return bands
    }

    private sealed interface Result {
        class Valid(val band: CloudBand) : Result
        class Invalid(val reason: String) : Result
    }

    private fun band(level: CloudLevel, node: JsonNode): Result {
        if (!node.isObject) return Result.Invalid("not an object")
        val typeName = node["type"]?.takeIf { it.isTextual }?.textValue() ?: return Result.Invalid("no type")
        val type = CloudType.entries.firstOrNull { it.key == typeName } ?: return Result.Invalid("unknown type '$typeName'")
        if (type.level != level) return Result.Invalid("type '$typeName' belongs to the ${type.level.key} band")

        fun number(field: String, default: Float): Float? {
            val value = node[field] ?: return default
            return value.takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() }
        }

        val base = number("base", type.base) ?: return Result.Invalid("base is not a number")
        val top = number("top", type.top) ?: return Result.Invalid("top is not a number")
        val coverage = number("coverage", type.coverage) ?: return Result.Invalid("coverage is not a number")
        val density = number("density", type.density) ?: return Result.Invalid("density is not a number")
        val wind = node["wind"]
        val windX: Float
        val windZ: Float
        if (wind == null) {
            windX = type.windX
            windZ = type.windZ
        } else {
            if (!wind.isArray || wind.size() != 2 || !wind.all { it.isNumber && it.floatValue().isFinite() }) {
                return Result.Invalid("wind is not two numbers")
            }
            windX = wind[0].floatValue()
            windZ = wind[1].floatValue()
        }
        if (base !in level.limits || top !in level.limits) {
            return Result.Invalid("altitudes $base..$top are outside ${level.limits.min}..${level.limits.max}")
        }
        if (base >= top) return Result.Invalid("base $base is not below top $top")
        if (coverage !in 0f..1f) return Result.Invalid("coverage $coverage is not between 0 and 1")
        if (density < 0f) return Result.Invalid("density $density is negative")
        return Result.Valid(CloudBand(level, type, base, top, coverage, density, windX, windZ))
    }
}
