/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome

/** The foliage settings change as one text edit: the asset's `additional` block becomes the [FoliageMeta] it is given. */
class FoliageMetaEdits(private val editor: AssetMetaTextEditor) {
    fun edit(text: String, before: FoliageMeta, after: FoliageMeta): TextEditOutcome =
        if (before == after) TextEditOutcome.Unchanged
        else editor.edit<FoliageMeta>(text, MetaType.FOLIAGE) { after }
}
