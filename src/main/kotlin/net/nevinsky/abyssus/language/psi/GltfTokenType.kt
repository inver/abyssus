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

package net.nevinsky.abyssus.language.psi

import com.intellij.psi.tree.IElementType
import net.nevinsky.abyssus.language.GltfLanguage
import org.jetbrains.annotations.NonNls

class GltfTokenType(@NonNls debugName: String) : IElementType(debugName, GltfLanguage) {
    override fun toString(): String {
        return "GltfTokenType." + super.toString()
    }
}