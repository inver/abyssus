/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.format

import com.fasterxml.jackson.databind.JsonNode

/** The `type` of an entity entry as written, or null when it has none. */
fun entityTypeOf(entity: JsonNode?): String? = entity?.get("type")?.takeIf { it.isTextual }?.textValue()

/** The component names an entity entry lists in `disabledComponents`, as written. */
fun entityDisabledComponents(entity: JsonNode?): List<String> =
    entity?.get("disabledComponents")?.takeIf { it.isArray }?.mapNotNull { it.takeIf(JsonNode::isTextual)?.textValue() }
        .orEmpty()

/** True unless the entity entry lists the component [key] (short or fully qualified) as disabled. */
fun isComponentEnabled(entity: JsonNode?, key: String): Boolean {
    val short = key.substringAfterLast('.')
    return entityDisabledComponents(entity).none { it.substringAfterLast('.') == short }
}
