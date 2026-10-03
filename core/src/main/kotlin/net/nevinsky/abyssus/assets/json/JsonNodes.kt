/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
