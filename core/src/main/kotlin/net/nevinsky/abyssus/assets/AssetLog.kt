/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets

/** Where loading reports a problem with an asset: the IDE log in the plugin, a list in a test. */
fun interface AssetLog {
    fun warn(message: String, error: Throwable?)
}
