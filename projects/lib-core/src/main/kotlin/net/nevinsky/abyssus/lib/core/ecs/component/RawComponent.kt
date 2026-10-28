/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component

/**
 * A component entry of a scene that is kept as read: [name] as the file spelled it, its payload as JSON text [json]
 * unchanged, [reason], the warning to report, and whether the entry's entity lists it as disabled ([disabled]). It is never
 * added to an engine entity; the loader carries it so a scene can be written back without it being lost.
 */
class RawComponent(
    val name: String,
    val json: String,
    val reason: String,
    val disabled: Boolean = false,
) : Component

/**
 * The [RawComponent]s of one entity, in file order. An entity holds one component per class, so the raw entries of a
 * scene entry share this holder. Like them it is never added to an engine entity.
 */
class RawComponentsWrapper(val entries: MutableList<RawComponent> = ArrayList()) : Component
