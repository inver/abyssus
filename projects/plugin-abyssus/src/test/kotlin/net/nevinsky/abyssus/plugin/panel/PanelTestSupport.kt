/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import java.awt.Component
import java.awt.Container

/** Shared helpers of the panel block tests. */
internal fun allComponents(c: Component): List<Component> =
    listOf(c) + ((c as? Container)?.components?.flatMap { allComponents(it) } ?: emptyList())

internal inline fun <reified T : Component> findNamed(root: Component, name: String): T =
    allComponents(root).filterIsInstance<T>().first { it.name == name }
