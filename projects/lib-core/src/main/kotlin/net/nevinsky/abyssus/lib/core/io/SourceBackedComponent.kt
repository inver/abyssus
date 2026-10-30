/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode

/** A component that writes itself back from the source object it was read from, instead of from its modeled values. */
interface SourceBackedComponent : Component {
    /** Remembers [node], the object this component was just bound from, and the values bound from it. */
    fun adoptSource(node: JsonNode)

    /** The native object for this component: the adopted source with the changed modeled values patched in. */
    fun sourceTree(): JsonNode
}
