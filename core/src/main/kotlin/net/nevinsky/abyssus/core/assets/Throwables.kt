/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets

/** The message to show for this failure: its own, or the class name when it has none. */
fun Throwable.displayMessage(): String = message ?: javaClass.simpleName
