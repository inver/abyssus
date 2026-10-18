/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AmbiguousLoaderException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.MissingDependencyException

/** Resolves a requested dependency to one loader; LoadBook owns dependency readiness and cycles. */
internal class DependencyResolver<R>(
    private val metas: ((String) -> AssetMeta<Any>?)?,
    private val registrations: List<R>,
    private val absent: R,
    private val handles: (R, String, MetaType?) -> Boolean,
) {
    class Route<R>(val registration: R, val meta: AssetMeta<Any>?)

    fun route(dependent: String?, name: String): Route<R> {
        val meta = metas?.invoke(name)
        val matches = registrations.filter { handles(it, name, meta?.type) }
        return when (matches.size) {
            0 -> if (metas != null) {
                Route(absent, null)
            } else throw MissingDependencyException(dependent ?: name, name)

            1 -> Route(matches[0], meta)
            else -> throw AmbiguousLoaderException(name)
        }
    }
}
