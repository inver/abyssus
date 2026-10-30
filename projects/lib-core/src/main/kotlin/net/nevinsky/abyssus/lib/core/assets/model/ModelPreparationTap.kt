/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.lib.gdx.model.ModelData

/**
 * Lets a host observe the data [ModelLoader] has just prepared, without a second read: the editor copies it for ray
 * tracing. Called on the loader's prepare thread, never with GL. The loader keeps ownership of everything offered.
 */
fun interface ModelPreparationTap {
    /** Starts observing the preparation of the model [name]; null when nobody wants it. */
    fun begin(name: String): Capture?

    /** One preparation's interest. Offering must not dispose or mutate [data] or [images]. */
    fun interface Capture {
        fun offer(data: ModelData, images: Map<String, Pixmap>)
    }
}
