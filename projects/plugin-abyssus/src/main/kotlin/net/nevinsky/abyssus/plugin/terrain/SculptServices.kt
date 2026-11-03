/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptPlanner
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptStateReader

/** The sculpting pieces of the terrain panel: reading a terrain's sculpt state and planning the files of an operation. */
class SculptServices(val reader: SculptStateReader, val planner: SculptPlanner)
