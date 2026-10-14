/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

/** The separator of the status text: `Move · Local · Snap 0.5`. */
const val STATUS_SEPARATOR = " · "

/**
 * The tool line shown under the scene view: the [tool] name, the [space] name, and [snap] (such as `Snap 0.5`) while
 * snapping is on. Names are passed in already translated; this only joins them.
 */
fun toolStatus(tool: String, space: String, snap: String?): String =
    listOfNotNull(tool, space, snap).joinToString(STATUS_SEPARATOR)

/**
 * The stats strip text: the frame rate ([fps], `<n> fps`, only when the project shows it), the [objects] count, then the
 * [status] of [toolStatus]. [objectsText] is the translated `N objects`.
 */
fun statsLine(fps: Int?, objectsText: String, status: String): String =
    listOfNotNull(fps?.let { "$it fps" }, objectsText, status).joinToString(STATUS_SEPARATOR)
