/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.meta

import java.math.BigDecimal
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.parseUuidOrNull
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_MAP
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** How an editable `additional` field is shown and checked. */
enum class AssetFieldKind {
    /** An integer above zero (terrain `size`). */
    POSITIVE_INT,

    /** A finite number above zero. */
    POSITIVE_FLOAT,

    /** A finite number of zero or more. */
    NON_NEGATIVE_FLOAT,

    /** A finite number strictly between -1 and 1. */
    UNIT_OPEN_FLOAT,

    /** Three finite numbers of zero or more. */
    NON_NEGATIVE_FLOAT3,

    /** The `uuid` of another asset, or none. */
    ASSET_REFERENCE,

    /** A file name inside the asset's own folder, or none. */
    LOCAL_FILE,
}

/** A value of an editable field. [Invalid] keeps the text of a stored value that does not fit the field's kind. */
sealed interface AssetFieldValue {
    data class Int(val value: kotlin.Int) : AssetFieldValue

    data class Real(val value: Float) : AssetFieldValue

    data class Reals(val values: List<Float>) : AssetFieldValue

    data class Text(val value: String) : AssetFieldValue

    data object None : AssetFieldValue

    data class Invalid(val text: String) : AssetFieldValue
}

/** An editable key of the `additional` block, and the value a missing key stands for ([default], else none). */
class AssetField(val key: String, val kind: AssetFieldKind, val default: AssetFieldValue? = null) {
    /** The nullable kinds accept [AssetFieldValue.None] as "clear". */
    val nullable: Boolean
        get() = kind == AssetFieldKind.ASSET_REFERENCE || kind == AssetFieldKind.LOCAL_FILE
}

/** Why an edit was refused; the plugin shows a localized message for each. */
enum class EditError {
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_FIELD,
    NOT_AN_OBJECT,
    NOT_A_NUMBER,
    NOT_AN_INTEGER,
    NOT_POSITIVE,
    NEGATIVE,
    NOT_FINITE,
    MIE_G_RANGE,
    WRONG_COUNT,
    ATMOSPHERE_ORDER,
    NOT_A_UUID,
    BAD_FILE_NAME,
    EMPTY,
}

sealed interface ParseOutcome {
    data class Parsed(val value: AssetFieldValue) : ParseOutcome

    data class Failed(val error: EditError) : ParseOutcome
}

/** The verdict of [AssetMetaEditor.editText] and the new text of a change. */
class AssetTextEdit(val outcome: EditOutcome, val text: String?)

sealed interface EditOutcome {
    /** The tree was changed. */
    data object Changed : EditOutcome

    /** The value already is the effective value: nothing was changed, and no default key was written. */
    data object NoChange : EditOutcome

    data class Rejected(val error: EditError) : EditOutcome

    /** The stored value differs from the one the editor was filled with; [actual] is what is stored now. */
    data class Conflict(val actual: AssetFieldValue) : EditOutcome
}

/** The editable fields of each asset type. Everything else (identity, bookkeeping, file keys) stays read-only. */
class AssetFieldDescriptions {
    private val defaults = AtmosphereParams()

    private val terrain =
        listOf(
            AssetField("size", AssetFieldKind.POSITIVE_INT),
            AssetField("uv", AssetFieldKind.POSITIVE_FLOAT),
        ) + SPLAT_FIELDS.map { AssetField(it, AssetFieldKind.ASSET_REFERENCE) }

    private val cube =
        listOf("top", "bottom", "left", "right", "front", "back").map { AssetField(it, AssetFieldKind.LOCAL_FILE) }

    private val procedural =
        listOf(
            AssetField("planetRadius", AssetFieldKind.POSITIVE_FLOAT, AssetFieldValue.Real(defaults.planetRadius)),
            AssetField(
                "atmosphereRadius",
                AssetFieldKind.POSITIVE_FLOAT,
                AssetFieldValue.Real(defaults.atmosphereRadius),
            ),
            AssetField(
                "betaRayleigh",
                AssetFieldKind.NON_NEGATIVE_FLOAT3,
                AssetFieldValue.Reals(defaults.betaRayleigh),
            ),
            AssetField("betaMie", AssetFieldKind.NON_NEGATIVE_FLOAT, AssetFieldValue.Real(defaults.betaMie)),
            AssetField("heightRayleigh", AssetFieldKind.POSITIVE_FLOAT, AssetFieldValue.Real(defaults.heightRayleigh)),
            AssetField("heightMie", AssetFieldKind.POSITIVE_FLOAT, AssetFieldValue.Real(defaults.heightMie)),
            AssetField("mieG", AssetFieldKind.UNIT_OPEN_FLOAT, AssetFieldValue.Real(defaults.mieG)),
            AssetField("sunIntensity", AssetFieldKind.NON_NEGATIVE_FLOAT, AssetFieldValue.Real(defaults.sunIntensity)),
        )

    /** The editable fields of [type] in display order; empty for a type without editors. */
    fun fields(type: MetaType): List<AssetField> =
        when (type) {
            MetaType.TERRAIN -> terrain
            MetaType.SKYBOX -> cube
            MetaType.SKYBOX_PROCEDURAL -> procedural
            else -> emptyList()
        }

    fun field(type: MetaType, key: String): AssetField? = fields(type).firstOrNull { it.key == key }
}

/**
 * Reads and edits the editable `additional` fields of an asset's typed meta. An edit changes exactly one field of the
 * terrain, cube-sky or procedural-sky block and nothing else of the meta (`version`, `uuid`, `type`, `lastModified`
 * stay); keys the typed block does not model are not kept when the meta is written back.
 */
class AssetMetaEditor(
    private val descriptions: AssetFieldDescriptions,
    private val json: JsonProcessor,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    private val parser = AssetMetaParser(json)

    /**
     * The effective value of [field] in the typed [additional] block: the stored value, the default of an omitted key,
     * or none.
     */
    fun current(additional: Any?, field: AssetField): AssetFieldValue =
        when (additional) {
            is TerrainMeta ->
                when (field.key) {
                    "size" -> additional.size.takeIf { it > 0 }?.let { AssetFieldValue.Int(it) } ?: AssetFieldValue.None
                    "uv" -> additional.uv.takeIf { it != 0f }?.let { AssetFieldValue.Real(it) } ?: AssetFieldValue.None
                    else -> additional.splat(field.key)?.let { AssetFieldValue.Text(it) } ?: AssetFieldValue.None
                }

            is SkyboxMeta -> face(additional, field.key)?.let { AssetFieldValue.Text(it) } ?: AssetFieldValue.None
            is ProceduralSkyMeta -> procedural(additional, field.key) ?: field.default ?: AssetFieldValue.None
            else -> field.default ?: AssetFieldValue.None
        }

    private fun face(sky: SkyboxMeta, key: String): String? =
        when (key) {
            "top" -> sky.top
            "bottom" -> sky.bottom
            "left" -> sky.left
            "right" -> sky.right
            "front" -> sky.front
            "back" -> sky.back
            else -> null
        }

    private fun procedural(sky: ProceduralSkyMeta, key: String): AssetFieldValue? =
        when (key) {
            "planetRadius" -> sky.planetRadius?.let { AssetFieldValue.Real(it) }
            "atmosphereRadius" -> sky.atmosphereRadius?.let { AssetFieldValue.Real(it) }
            "betaRayleigh" -> sky.betaRayleigh?.let { AssetFieldValue.Reals(it) }
            "betaMie" -> sky.betaMie?.let { AssetFieldValue.Real(it) }
            "heightRayleigh" -> sky.heightRayleigh?.let { AssetFieldValue.Real(it) }
            "heightMie" -> sky.heightMie?.let { AssetFieldValue.Real(it) }
            "mieG" -> sky.mieG?.let { AssetFieldValue.Real(it) }
            "sunIntensity" -> sky.sunIntensity?.let { AssetFieldValue.Real(it) }
            else -> null
        }

    /** Turns the text typed into an editor into a value of [field]'s kind, or says why it cannot be one. */
    fun parse(field: AssetField, text: String): ParseOutcome {
        val trimmed = text.trim()
        return when (field.kind) {
            AssetFieldKind.POSITIVE_INT -> {
                val n =
                    trimmed.toIntOrNull()
                        ?: return failed(
                            if (trimmed.toBigDecimalOrNull() == null) {
                                EditError.NOT_A_NUMBER
                            } else {
                                EditError.NOT_AN_INTEGER
                            }
                        )
                parsed(AssetFieldValue.Int(n))
            }

            AssetFieldKind.POSITIVE_FLOAT,
            AssetFieldKind.NON_NEGATIVE_FLOAT,
            AssetFieldKind.UNIT_OPEN_FLOAT ->
                real(trimmed)?.let { parsed(AssetFieldValue.Real(it)) } ?: failed(EditError.NOT_A_NUMBER)

            AssetFieldKind.NON_NEGATIVE_FLOAT3 -> {
                val parts = trimmed.split(',', ' ', ';').filter { it.isNotBlank() }
                if (parts.size != 3) {
                    return failed(EditError.WRONG_COUNT)
                }
                val values = parts.map { real(it) ?: return failed(EditError.NOT_A_NUMBER) }
                parsed(AssetFieldValue.Reals(values))
            }

            AssetFieldKind.ASSET_REFERENCE,
            AssetFieldKind.LOCAL_FILE ->
                parsed(
                    if (trimmed.isEmpty()) {
                        AssetFieldValue.None
                    } else {
                        AssetFieldValue.Text(trimmed)
                    }
                )
        }
    }

    private fun real(text: String): Float? =
        text.toBigDecimalOrNull()?.let(BigDecimal::toFloat)?.takeIf { it.isFinite() }

    private fun parsed(value: AssetFieldValue): ParseOutcome = ParseOutcome.Parsed(value)

    private fun failed(error: EditError): ParseOutcome = ParseOutcome.Failed(error)

    /** Why [value] is not acceptable for [field] given the rest of [additional], or null when it is. */
    fun validate(additional: Any?, field: AssetField, value: AssetFieldValue): EditError? {
        val own = validateOwn(field, value)
        if (own != null) {
            return own
        }
        if (field.key == "planetRadius" || field.key == "atmosphereRadius") {
            val planet =
                if (field.key == "planetRadius") {
                    value
                } else {
                    current(additional, descriptions.field(MetaType.SKYBOX_PROCEDURAL, "planetRadius")!!)
                }
            val atmosphere =
                if (field.key == "atmosphereRadius") {
                    value
                } else {
                    current(additional, descriptions.field(MetaType.SKYBOX_PROCEDURAL, "atmosphereRadius")!!)
                }
            if (
                planet is AssetFieldValue.Real && atmosphere is AssetFieldValue.Real && atmosphere.value <= planet.value
            ) {
                return EditError.ATMOSPHERE_ORDER
            }
        }
        return null
    }

    private fun validateOwn(field: AssetField, value: AssetFieldValue): EditError? =
        when (field.kind) {
            AssetFieldKind.POSITIVE_INT ->
                when {
                    value !is AssetFieldValue.Int -> EditError.NOT_AN_INTEGER
                    value.value <= 0 -> EditError.NOT_POSITIVE
                    else -> null
                }

            AssetFieldKind.POSITIVE_FLOAT ->
                real(value) {
                    if (it <= 0f) {
                        EditError.NOT_POSITIVE
                    } else {
                        null
                    }
                }
            AssetFieldKind.NON_NEGATIVE_FLOAT ->
                real(value) {
                    if (it < 0f) {
                        EditError.NEGATIVE
                    } else {
                        null
                    }
                }
            AssetFieldKind.UNIT_OPEN_FLOAT ->
                real(value) {
                    if (it <= -1f || it >= 1f) {
                        EditError.MIE_G_RANGE
                    } else {
                        null
                    }
                }
            AssetFieldKind.NON_NEGATIVE_FLOAT3 ->
                when {
                    value !is AssetFieldValue.Reals -> EditError.WRONG_COUNT
                    value.values.size != 3 -> EditError.WRONG_COUNT
                    value.values.any { !it.isFinite() } -> EditError.NOT_FINITE
                    value.values.any { it < 0f } -> EditError.NEGATIVE
                    else -> null
                }

            AssetFieldKind.ASSET_REFERENCE ->
                when (value) {
                    AssetFieldValue.None -> null
                    is AssetFieldValue.Text ->
                        if (parseUuidOrNull(value.value) != null) {
                            null
                        } else {
                            EditError.NOT_A_UUID
                        }
                    else -> EditError.NOT_A_UUID
                }

            AssetFieldKind.LOCAL_FILE ->
                when (value) {
                    AssetFieldValue.None -> null
                    is AssetFieldValue.Text ->
                        if (value.value.isBlank()) {
                            EditError.EMPTY
                        } else if (isLocalName(value.value)) {
                            null
                        } else {
                            EditError.BAD_FILE_NAME
                        }
                    else -> EditError.BAD_FILE_NAME
                }
        }

    private fun real(value: AssetFieldValue, check: (Float) -> EditError?): EditError? =
        when {
            value !is AssetFieldValue.Real -> EditError.NOT_A_NUMBER
            !value.value.isFinite() -> EditError.NOT_FINITE
            else -> check(value.value)
        }

    private fun isLocalName(name: String): Boolean =
        !name.startsWith("/") &&
            !name.startsWith("\\") &&
            !name.contains(':') &&
            name.split('/', '\\').none { it == ".." || it == "." || it.isEmpty() }

    /**
     * Sets [key] of the typed [meta] to [value] if the stored value still is [expected], else reports a conflict. The
     * outcome says what happened; the edited meta comes back only for [EditOutcome.Changed]. Nothing changes for a
     * refused edit, for a value equal to the effective value, or for a default that is already in effect.
     */
    fun edit(meta: AssetMeta<Any>, key: String, expected: AssetFieldValue, value: AssetFieldValue): TypedAssetEdit {
        val field =
            descriptions.field(meta.type, key)
                ?: return TypedAssetEdit(EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD))
        val additional = meta.additional
        val actual = current(additional, field)
        if (actual != expected) {
            return TypedAssetEdit(EditOutcome.Conflict(actual))
        }
        validate(additional, field, value)?.let {
            return TypedAssetEdit(EditOutcome.Rejected(it))
        }
        if (actual == value) {
            return TypedAssetEdit(EditOutcome.NoChange)
        }
        val edited =
            with(additional, key, value) ?: return TypedAssetEdit(EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD))
        return TypedAssetEdit(EditOutcome.Changed, parser.withAdditional(meta, edited))
    }

    /**
     * [edit] on the `meta.json` text: the verdict and, when it changed, the pretty-printed new text. Null when [text]
     * is not a JSON object; a document that is not native is [EditError.UNSUPPORTED_FORMAT].
     */
    fun editText(text: String, key: String, expected: AssetFieldValue, value: AssetFieldValue): AssetTextEdit? {
        val root =
            net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation { json.readObject(text) }.getOrNull()
                ?: return null
        if (format.validate(root, DocumentKind.ASSET) != null) {
            return AssetTextEdit(EditOutcome.Rejected(EditError.UNSUPPORTED_FORMAT), null)
        }
        val meta =
            net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation { parser.read("", text) }.getOrNull()
                ?: return null
        val edit = edit(meta, key, expected, value)
        return AssetTextEdit(edit.outcome, edit.meta?.let { parser.write(it, pretty = true) })
    }

    private fun with(additional: Any, key: String, value: AssetFieldValue): Any? =
        when (additional) {
            is TerrainMeta ->
                when (key) {
                    "size" -> additional.copy(size = (value as AssetFieldValue.Int).value)
                    "uv" -> additional.copy(uv = (value as AssetFieldValue.Real).value)
                    SPLAT_MAP -> additional.copy(splatMap = text(value))
                    "splatBase" -> additional.copy(splatBase = text(value))
                    "splatR" -> additional.copy(splatR = text(value))
                    "splatG" -> additional.copy(splatG = text(value))
                    "splatB" -> additional.copy(splatB = text(value))
                    "splatA" -> additional.copy(splatA = text(value))
                    else -> null
                }

            is SkyboxMeta ->
                SkyboxMeta(
                    top =
                        if (key == "top") {
                            text(value)
                        } else {
                            additional.top
                        },
                    bottom =
                        if (key == "bottom") {
                            text(value)
                        } else {
                            additional.bottom
                        },
                    left =
                        if (key == "left") {
                            text(value)
                        } else {
                            additional.left
                        },
                    right =
                        if (key == "right") {
                            text(value)
                        } else {
                            additional.right
                        },
                    front =
                        if (key == "front") {
                            text(value)
                        } else {
                            additional.front
                        },
                    back =
                        if (key == "back") {
                            text(value)
                        } else {
                            additional.back
                        },
                    shader = additional.shader,
                )

            is ProceduralSkyMeta ->
                ProceduralSkyMeta(
                    vertex = additional.vertex,
                    fragment = additional.fragment,
                    planetRadius =
                        if (key == "planetRadius") {
                            real(value)
                        } else {
                            additional.planetRadius
                        },
                    atmosphereRadius =
                        if (key == "atmosphereRadius") {
                            real(value)
                        } else {
                            additional.atmosphereRadius
                        },
                    betaRayleigh =
                        if (key == "betaRayleigh") {
                            (value as AssetFieldValue.Reals).values
                        } else {
                            additional.betaRayleigh
                        },
                    betaMie =
                        if (key == "betaMie") {
                            real(value)
                        } else {
                            additional.betaMie
                        },
                    heightRayleigh =
                        if (key == "heightRayleigh") {
                            real(value)
                        } else {
                            additional.heightRayleigh
                        },
                    heightMie =
                        if (key == "heightMie") {
                            real(value)
                        } else {
                            additional.heightMie
                        },
                    mieG =
                        if (key == "mieG") {
                            real(value)
                        } else {
                            additional.mieG
                        },
                    sunIntensity =
                        if (key == "sunIntensity") {
                            real(value)
                        } else {
                            additional.sunIntensity
                        },
                    clouds = additional.clouds,
                    lightsScene = additional.lightsScene,
                )

            else -> null
        }

    private fun text(value: AssetFieldValue): String? = (value as? AssetFieldValue.Text)?.value

    private fun real(value: AssetFieldValue): Float? = (value as? AssetFieldValue.Real)?.value
}

/** What [AssetMetaEditor.edit] made of a typed meta: the verdict and, for a change, the edited meta. */
class TypedAssetEdit(val outcome: EditOutcome, val meta: AssetMeta<Any>? = null)
