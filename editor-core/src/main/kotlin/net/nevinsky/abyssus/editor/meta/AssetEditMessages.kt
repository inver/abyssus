/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.meta

import net.nevinsky.abyssus.editor.EditorMessages

/** Why an asset property edit was refused, as the user reads it. */
fun EditError.message(messages: EditorMessages): String = messages.message("assetEditError.$name")
