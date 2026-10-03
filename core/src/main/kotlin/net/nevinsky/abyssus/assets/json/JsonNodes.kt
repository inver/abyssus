/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.json

import com.fasterxml.jackson.databind.JsonNode

/** The child [name] unless it is absent or an explicit JSON null. */
fun JsonNode.opt(name: String): JsonNode? = get(name)?.takeIf { !it.isNull && !it.isMissingNode }

/** The child [name] when it is a string. */
fun JsonNode.text(name: String): String? = opt(name)?.takeIf { it.isTextual }?.asText()

/** The child [name] when it is a finite number. */
fun JsonNode.float(name: String): Float? = opt(name)?.takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() }

/** The child [name] when it is an object. */
fun JsonNode.obj(name: String): JsonNode? = opt(name)?.takeIf { it.isObject }
