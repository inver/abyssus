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

package net.nevinsky.abyssus.projectView

import net.nevinsky.abyssus.AbyssusBundle

/**
 * What the skybox chooser shows, kept apart from Swing: the skyboxes matching [filter] after a "None" entry (`null`),
 * and the [selected] skybox (`null` for None). The selection starts on [current] when it is listed and stays put when
 * the filter hides it.
 */
class SkyboxPickerModel(val all: List<SkyboxChoice>, current: String?) {
    var filter: String = ""

    var selected: String? = current?.takeIf { name -> all.any { it.name == name } }

    /** The skyboxes whose folder name contains [filter], ignoring case. */
    val matches: List<SkyboxChoice>
        get() = all.filter { it.name.contains(filter.trim(), ignoreCase = true) }

    /** The list rows: `null` (None) first, then [matches]. */
    val entries: List<SkyboxChoice?>
        get() = listOf<SkyboxChoice?>(null) + matches

    val foundCount: Int get() = matches.size

    val noMatch: Boolean get() = matches.isEmpty()

    val foundText: String get() = AbyssusBundle.message("skyboxFound", foundCount)

    val footerText: String
        get() = selected?.let { AbyssusBundle.message("skyboxSelected", it) } ?: AbyssusBundle.message("skyboxSelectedNone")
}
