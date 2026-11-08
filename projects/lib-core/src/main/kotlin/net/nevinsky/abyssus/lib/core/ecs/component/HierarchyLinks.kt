/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.hierarchy.LocalPose
import net.nevinsky.abyssus.lib.core.hierarchy.ParentLink

/** The stored pose of [position] (copies, never the component's own vectors). */
fun localPoseOf(position: PositionComponent): LocalPose =
    LocalPose(Vector3(position.localPosition), Quaternion(position.localRotation), Vector3(position.localScale))

/** The link [parent] makes, or null when it names no entity (`-1`). */
fun parentLinkOf(parent: ParentComponent): ParentLink? =
    parent.parentEntityId.takeIf { it >= 0 }?.let { ParentLink(it.toLong(), parent.node?.takeIf(String::isNotBlank)) }
