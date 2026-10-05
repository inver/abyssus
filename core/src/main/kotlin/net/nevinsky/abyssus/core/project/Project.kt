/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.project

import net.nevinsky.abyssus.core.scene.Scene

data class Project(
    val name: String?,
    val scenes: List<Scene>
)