/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets

/** Splits the asset folder from the mesh id in an encoded [ModelKey]; an asset folder name cannot contain it. */
private const val SEPARATOR = '\u001F'

/**
 * What a placed model asks the asset storage for: the asset folder [name] and, to draw one mesh of it, that mesh's
 * [meshId]. Without a [meshId] it is the whole model. Callers build the storage name through [encode], never by
 * concatenating strings; [decodeModelKey] reads it back.
 */
data class ModelKey(val name: String, val meshId: String? = null) {
    init {
        require(SEPARATOR !in name) { "asset name '$name' contains the key separator" }
        require(meshId == null || meshId.isNotEmpty()) { "meshId is empty" }
    }

    /** The storage name: the bare [name] for the whole model, otherwise the name joined to the mesh id. */
    fun encode(): String = if (meshId == null) name else name + SEPARATOR + meshId
}

/**
 * The key [encoded] names. The first separator ends the asset folder, so a mesh id may hold any character
 * (`#`, `/`, spaces).
 */
fun decodeModelKey(encoded: String): ModelKey {
    val at = encoded.indexOf(SEPARATOR)
    return if (at < 0) ModelKey(encoded) else ModelKey(encoded.substring(0, at), encoded.substring(at + 1))
}

/** The asset folder behind a storage name, which may be an encoded variant of it. */
fun baseAssetName(encoded: String): String =
    encoded.indexOf(SEPARATOR).let { if (it < 0) encoded else encoded.substring(0, it) }

/** True when [encoded] names one mesh of an asset rather than the asset itself. */
fun isModelVariant(encoded: String): Boolean = encoded.indexOf(SEPARATOR) >= 0

/** This meta as seen for the variant [encoded]: the same content under the encoded name, so a loader can read the mesh. */
fun AssetMeta<Any>.forVariant(encoded: String): AssetMeta<Any> =
    AssetMeta(type, encoded, formatVersion, version, lastModified, additional, uuid)
