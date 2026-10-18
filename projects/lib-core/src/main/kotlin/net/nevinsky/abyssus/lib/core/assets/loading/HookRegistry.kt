/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Loader-wide hooks precede name hooks; both lists are snapshotted before invoking handlers. */
internal class HookRegistry<V>(private val bindThread: () -> Unit) {
    private val global = CopyOnWriteArrayList<(String, V) -> Unit>()
    private val named = ConcurrentHashMap<String, CopyOnWriteArrayList<(V) -> Unit>>()

    fun add(handler: (String, V) -> Unit): Subscription = add(global, handler)
    fun add(name: String, handler: (V) -> Unit): Subscription = addNamed(named, name, handler)

    fun fire(name: String, value: V): List<Throwable> =
        fire(global.toList(), named[name]?.toList().orEmpty(), name, value)

    private fun <H : Any> add(list: CopyOnWriteArrayList<H>, handler: H): Subscription {
        bindThread()
        list.add(handler)
        return Subscription {
            bindThread()
            list.remove(handler)
        }
    }

    private fun <H : Any> addNamed(
        lists: ConcurrentHashMap<String, CopyOnWriteArrayList<H>>,
        name: String,
        handler: H,
    ): Subscription {
        bindThread()
        val list = lists.computeIfAbsent(name) { CopyOnWriteArrayList() }
        list.add(handler)
        return Subscription {
            bindThread()
            list.remove(handler)
        }
    }

    private fun <V> fire(
        global: List<(String, V) -> Unit>,
        named: List<(V) -> Unit>,
        name: String,
        value: V,
    ): List<Throwable> {
        val errors = ArrayList<Throwable>()
        for (handler in global) {
            try {
                handler(name, value)
            } catch (e: Exception) {
                errors += e
            }
        }
        for (handler in named) {
            try {
                handler(value)
            } catch (e: Exception) {
                errors += e
            }
        }
        return errors
    }
}
