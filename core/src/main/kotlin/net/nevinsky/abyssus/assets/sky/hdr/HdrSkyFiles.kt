/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.hdr

/** The image an `SKYBOX_HDR` folder uses, and a warning when the choice was ambiguous. */
data class HdrChoice(val file: String, val warning: String? = null)

/** Picks the `.hdr` of an `SKYBOX_HDR` folder without assuming which `additional` field names it. No IO. */
class HdrSkyFiles {
    fun isHdr(name: String) = name.endsWith(".hdr", ignoreCase = true)

    /**
     * From the file names in the folder ([files]) and the text values of its `meta.json` `additional` ([named]): a
     * named `.hdr` that exists, else the only `.hdr`, else the first by name with a warning; null when there is none.
     */
    fun choose(files: Collection<String>, named: Collection<String>): HdrChoice? {
        val hdrs = files.filter(::isHdr).sorted()
        named.firstOrNull { isHdr(it) && it in hdrs }?.let { return HdrChoice(it) }
        return when (hdrs.size) {
            0 -> null
            1 -> HdrChoice(hdrs.single())
            else -> HdrChoice(hdrs.first(), "several .hdr files (${hdrs.joinToString()}); using '${hdrs.first()}'")
        }
    }
}
