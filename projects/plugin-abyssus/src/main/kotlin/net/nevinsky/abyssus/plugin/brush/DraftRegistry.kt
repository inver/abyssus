/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.brush

import java.util.concurrent.ConcurrentHashMap

/** The drafts of a project's terrain folders, by folder name: a stroke in progress, seen by every view of the project. */
abstract class DraftRegistry<D : Any> {
    private val drafts = ConcurrentHashMap<String, D>()

    /** The draft of the folder [name], or null while its stored files are in use. */
    fun of(name: String): D? = drafts[name]

    /** Ends the draft of [name]: the views reload from the folder's files. */
    fun discard(name: String) {
        drafts.remove(name)
    }

    /** Ends every draft: the selection changed or the project closed. */
    fun discardAll() {
        drafts.clear()
    }

    /** Starts a draft of [name] made by [create], replacing any earlier one. */
    protected fun replace(name: String, create: () -> D): D = create().also { drafts[name] = it }

    /** The draft of [name], made by [create] when the folder has none yet. */
    protected fun getOrCreate(name: String, create: () -> D): D = drafts.computeIfAbsent(name) { create() }
}
