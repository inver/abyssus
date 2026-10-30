/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.language.psi

import com.intellij.psi.tree.IElementType
import net.nevinsky.abyssus.plugin.language.GltfLanguage
import org.jetbrains.annotations.NonNls

class GltfElementType(@NonNls debugName: String) : IElementType(debugName, GltfLanguage)