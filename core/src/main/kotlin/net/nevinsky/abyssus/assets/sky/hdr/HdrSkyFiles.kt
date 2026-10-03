/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
