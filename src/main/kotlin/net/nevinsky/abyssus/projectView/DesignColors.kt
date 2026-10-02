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

import com.intellij.ui.JBColor

/** Colours of the design canvas that the IDE theme has no equivalent for: the selected-row highlight and the accent. */
object DesignColors {
    /** `#2e436e` on dark themes, a light blue of the same hue on light ones. */
    @JvmField
    val SELECTION = JBColor(0xD3E2FA, 0x2E436E)

    /** `#3fb8c9` on dark themes, darkened for light ones. */
    @JvmField
    val ACCENT = JBColor(0x1E8A99, 0x3FB8C9)
}
