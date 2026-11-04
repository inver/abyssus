/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.textValuesOf

/** Reads values out of the payload text a [RawComponent] keeps, without callers holding a JSON tree. */
class RawComponentValues(private val json: JsonProcessor) {
    /**
     * Every string held under a member called one of [fields], anywhere in the payload; empty when it is not an object.
     */
    fun textValues(component: RawComponent, vararg fields: String): Set<String> = runCatchingKeepingCancellation {
        json.readObject(component.json).textValuesOf(*fields)
    }.getOrDefault(emptySet())
}
