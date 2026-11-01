/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.document.UiAssetAdditional
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.util.UUID

/** The `meta.json` text of a newly created UI asset: an editable `main` screen, nothing registered yet. */
class UiAssetTemplate(
    private val json: JsonProcessor,
    private val parser: AssetMetaParser = AssetMetaParser(json),
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
) {
    fun create(uuid: UUID, lastModified: Long): String {
        val meta = AssetMeta(type = MetaType.UI, lastModified = lastModified, additional = UiAssetAdditional(), uuid = uuid)
        val text = parser.write(meta, pretty = true)
        check(decoder.decode(text, json) is UiAdmission.Admitted) { "the UI asset template is not valid UI" }
        return text
    }
}
