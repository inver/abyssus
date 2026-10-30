/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.format

import com.fasterxml.jackson.databind.JsonNode

/** The key of the optional enabled flag on an entity entry and on a component entry of the `ecs` block. */
const val ENABLED_KEY = "enabled"

/** True unless [node] carries `enabled: false`; a missing or non-boolean flag means enabled. */
fun isEnabled(node: JsonNode?): Boolean = node?.get(ENABLED_KEY)?.let { !(it.isBoolean && !it.booleanValue()) } ?: true

/** True when [node] has no `enabled` key, or the key is a boolean. Anything else makes the document unsupported. */
fun isEnabledFlagValid(node: JsonNode?): Boolean = node?.get(ENABLED_KEY)?.isBoolean ?: true
