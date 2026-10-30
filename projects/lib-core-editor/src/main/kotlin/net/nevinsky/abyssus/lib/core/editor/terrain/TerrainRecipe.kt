/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.displayMessage
import net.nevinsky.abyssus.lib.core.assets.terrain.MAX_TERRAIN_RESOLUTION

/** The Abyssus-only file beside `terrain.data` that keeps how the applied heights were made. */
const val TERRAIN_RECIPE_FILE = "abyssus-terrain.recipe.json"

private const val SHA_HEX_LENGTH = 64
private const val MAX_RESOLUTION = MAX_TERRAIN_RESOLUTION

/** The recipe layout this code reads and writes. */
const val RECIPE_SCHEMA_VERSION = 1

/**
 * How a terrain's heights were generated: the complete [settings], the [size] and [resolution] they were generated
 * for, the [generator] identifier and noise source revision, and the SHA-256 ([heightsSha256]) of the applied bytes.
 */
data class TerrainRecipe(
    val settings: TerrainGenerationSettings,
    val size: Int,
    val resolution: Int,
    val heightsSha256: String,
    val generator: String = OPENSIMPLEX2_FBM_V1,
    val sourceRevision: String = FAST_NOISE_LITE_REVISION,
    val schemaVersion: Int = RECIPE_SCHEMA_VERSION,
)

/** What the stored recipe says about the terrain next to it. */
sealed interface RecipeStatus {
    /** There is no recipe file: the terrain was not generated here. */
    data object Missing : RecipeStatus

    /** The file is not a recipe: [reason] is the parse problem. */
    data class Malformed(val reason: String) : RecipeStatus

    /** The recipe is from a newer layout or a generator this code does not have; [what] names it. */
    data class Unsupported(val what: String) : RecipeStatus

    /** The recipe describes other geometry or other heights than the terrain has now. */
    data class Mismatch(val reasons: Set<MismatchReason>, val recipe: TerrainRecipe) : RecipeStatus

    /** The recipe describes exactly this terrain; its settings can be restored. */
    data class Matching(val recipe: TerrainRecipe) : RecipeStatus
}

enum class MismatchReason { SIZE, RESOLUTION, HEIGHTS }

/** Reads and writes [TerrainRecipe] text and compares a recipe with the terrain it sits next to. */
class TerrainRecipeCodec(private val json: JsonProcessor) {
    fun encode(recipe: TerrainRecipe): String {
        val nodes = JsonNodeFactory.instance
        val s = recipe.settings
        val root = nodes.objectNode().apply {
            put("schemaVersion", recipe.schemaVersion)
            putObject("generator").apply {
                put("id", recipe.generator)
                put("sourceRevision", recipe.sourceRevision)
            }
            putObject("settings").apply {
                put("seed", s.seed)
                put("featureSize", s.featureSize)
                put("minHeight", s.minHeight)
                put("maxHeight", s.maxHeight)
                put("octaves", s.octaves)
                put("persistence", s.persistence)
                put("lacunarity", s.lacunarity)
            }
            put("size", recipe.size)
            put("resolution", recipe.resolution)
            put("heightsSha256", recipe.heightsSha256)
        }
        return json.pretty(root)
    }

    /** The recipe in [text], or why it is not usable ([RecipeStatus.Malformed] or [RecipeStatus.Unsupported]). */
    fun decode(text: String): Decoded = runCatchingKeepingCancellation { read(json.readObject(text)) }
        .getOrElse { Decoded.Failed(RecipeStatus.Malformed(it.displayMessage())) }

    sealed interface Decoded {
        data class Recipe(val recipe: TerrainRecipe) : Decoded
        data class Failed(val status: RecipeStatus) : Decoded
    }

    private fun read(root: JsonNode): Decoded {
        val version = int(root, "schemaVersion")
        if (version != RECIPE_SCHEMA_VERSION) return Decoded.Failed(RecipeStatus.Unsupported("schemaVersion $version"))
        val generator = root.get("generator")?.takeIf { it.isObject } ?: error("generator is missing")
        val id = string(generator, "id")
        if (id != OPENSIMPLEX2_FBM_V1) return Decoded.Failed(RecipeStatus.Unsupported("generator $id"))
        val s = root.get("settings")?.takeIf { it.isObject } ?: error("settings are missing")
        val settings = TerrainGenerationSettings(
            seed = int(s, "seed"),
            featureSize = float(s, "featureSize"),
            minHeight = float(s, "minHeight"),
            maxHeight = float(s, "maxHeight"),
            octaves = int(s, "octaves"),
            persistence = float(s, "persistence"),
            lacunarity = float(s, "lacunarity"),
        )
        check(settings.valid) { "settings are not valid: ${settings.errors()}" }
        val sha = string(root, "heightsSha256")
        check(sha.length == SHA_HEX_LENGTH && sha.all { it in "0123456789abcdef" }) { "heightsSha256 is not a SHA-256" }
        return Decoded.Recipe(
            TerrainRecipe(
                settings = settings,
                size = int(root, "size").also { check(it > 0) { "size must be positive" } },
                resolution = int(root, "resolution").also { check(it in MIN_TERRAIN_RESOLUTION..MAX_RESOLUTION) { "resolution is out of range" } },
                heightsSha256 = sha,
                generator = id,
                sourceRevision = string(generator, "sourceRevision"),
                schemaVersion = version,
            ),
        )
    }

    private fun int(node: JsonNode, key: String): Int =
        node.get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: error("$key is missing or not an integer")

    private fun float(node: JsonNode, key: String): Float =
        node.get(key)?.takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() } ?: error("$key is missing or not a number")

    private fun string(node: JsonNode, key: String): String =
        node.get(key)?.takeIf { it.isTextual }?.asText() ?: error("$key is missing or not text")

    /**
     * The status of [recipeText] (null when there is no file) for a terrain that is [size] world units wide, has
     * [resolution] heights per side and whose height file holds [heightBytes].
     */
    fun status(recipeText: String?, size: Int, resolution: Int, heightBytes: ByteArray): RecipeStatus {
        if (recipeText == null) return RecipeStatus.Missing
        val recipe = when (val decoded = decode(recipeText)) {
            is Decoded.Failed -> return decoded.status
            is Decoded.Recipe -> decoded.recipe
        }
        val reasons = buildSet {
            if (recipe.size != size) add(MismatchReason.SIZE)
            if (recipe.resolution != resolution) add(MismatchReason.RESOLUTION)
            if (recipe.heightsSha256 != sha256Hex(heightBytes)) add(MismatchReason.HEIGHTS)
        }
        return if (reasons.isEmpty()) RecipeStatus.Matching(recipe) else RecipeStatus.Mismatch(reasons, recipe)
    }

    /** The settings a draft starts from: the recipe's when it matches the terrain, else the defaults. */
    fun draftSettings(status: RecipeStatus): TerrainGenerationSettings =
        (status as? RecipeStatus.Matching)?.recipe?.settings ?: TerrainGenerationSettings()
}
