/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaDocument
import net.nevinsky.abyssus.lib.core.editor.document.UiAssetAdditional
import net.nevinsky.abyssus.lib.core.editor.document.admitUiDocument

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.util.UUID

/** The `meta.json` text of a newly created UI asset: native markers, an editable `main` screen, nothing registered yet. */
class UiAssetTemplate(private val decoder: UiCollectionDecoder = UiCollectionDecoder()) {
    fun create(uuid: UUID, lastModified: Long): String {
        val meta = AssetMetaDocument(
            lastModified = lastModified, uuid = uuid.toString(), type = MetaType.UI,
            additional = UiAssetAdditional(),
        )
        check(admitUiDocument(meta, decoder) is UiAdmission.Admitted) { "the UI asset template is not valid UI" }
        return SceneJson().pretty(meta)
    }
}
