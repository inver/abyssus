/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import java.io.File

/** What a view's assets hold in a test, with no storage behind them. */
class FakeFoliageAssets : FoliageAssets {
    var loading = false
    val wanted = HashSet<String>()
    private val built = HashMap<String, FoliageDrawable>()

    override val isLoading: Boolean get() = loading

    override fun update(projectDir: File?, names: Set<String>) {
        wanted.clear()
        wanted += names
    }

    override fun get(name: String): FoliageDrawable? = built[name]

    override fun abandon() = Unit

    override fun dispose() = Unit

    fun put(name: String, foliage: FoliageDrawable) {
        built[name] = foliage
    }
}

/** A model folder the storage built: a drawable only needs its identity, never GL. */
val unbuiltModel: Disposable = object : Disposable {
    override fun dispose() = Unit
}
