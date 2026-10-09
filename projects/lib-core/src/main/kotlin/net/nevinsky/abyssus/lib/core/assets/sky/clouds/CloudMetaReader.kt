/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import org.slf4j.Logger

/** Reads native cloud settings; each invalid band is logged and skipped independently. No IO or GL. */
class CloudMetaReader(private val log: Logger) {
    fun read(name: String, additional: JsonNode?): CloudMeta {
        val technique = CloudTechnique.entries.firstOrNull { it.key == additional?.get("technique")?.textValue() }
            ?: CloudTechnique.SHELLS
        return CloudMeta(technique, band(name, additional, CloudLevel.LOW),
            band(name, additional, CloudLevel.MID), band(name, additional, CloudLevel.HIGH))
    }

    private fun band(name: String, additional: JsonNode?, level: CloudLevel): CloudBand? {
        val node = additional?.get(level.key)?.takeUnless { it.isNull } ?: return null
        try {
            require(node.isObject) { "expected an object" }
            val type = CloudType.entries.firstOrNull { it.key == node.get("type")?.textValue() }
            require(type != null && type.level == level) { "invalid type for this band" }
            val base = number(node, "base", type.base)
            val top = number(node, "top", type.top)
            val coverage = number(node, "coverage", type.coverage)
            val density = number(node, "density", type.density)
            require(base in level.limits && top in level.limits && base < top) { "invalid altitudes" }
            require(coverage in 0f..1f && density >= 0f) { "invalid coverage or density" }
            val wind = node.get("wind")?.takeUnless { it.isNull }
            require(wind == null || (wind.isArray && wind.size() == 2 && wind.all { it.isNumber && it.floatValue().isFinite() })) {
                "wind must contain two finite numbers"
            }
            return CloudBand(level, type, base, top, coverage, density,
                wind?.get(0)?.floatValue() ?: type.windX, wind?.get(1)?.floatValue() ?: type.windZ)
        } catch (e: IllegalArgumentException) {
            log.warn("Cloud asset '$name': skipping '${level.key}' band (${e.message})")
            return null
        }
    }

    private fun number(node: JsonNode, key: String, default: Float): Float {
        val value = node.get(key)?.takeUnless { it.isNull } ?: return default
        require(value.isNumber && value.floatValue().isFinite()) { "$key must be a finite number" }
        return value.floatValue()
    }
}
