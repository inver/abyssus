/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.format

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.dto.ENTITY_DISABLED_FLAG

/** The key of the optional integer bit set on an entity entry of the `ecs` block. */
const val FLAGS_KEY = "flags"

/** The entity entry's `flags`, `0` when missing or not an integer. */
fun entityFlags(entity: JsonNode?): Int = entity?.get(FLAGS_KEY)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: 0

/** True unless the entity entry's `flags` has the disabled bit. */
fun isEntityEnabled(entity: JsonNode?): Boolean = entityFlags(entity) and ENTITY_DISABLED_FLAG == 0

/** The `type` of an entity entry as written, or null when it has none. */
fun entityTypeOf(entity: JsonNode?): String? = entity?.get("type")?.takeIf { it.isTextual }?.textValue()

/** The component names an entity entry lists in `disabledComponents`, as written. */
fun entityDisabledComponents(entity: JsonNode?): List<String> =
    entity?.get("disabledComponents")?.takeIf { it.isArray }?.mapNotNull { it.takeIf(JsonNode::isTextual)?.textValue() }.orEmpty()

/** True unless the entity entry lists the component [key] (short or fully qualified) as disabled. */
fun isComponentEnabled(entity: JsonNode?, key: String): Boolean {
    val short = key.substringAfterLast('.')
    return entityDisabledComponents(entity).none { it.substringAfterLast('.') == short }
}
