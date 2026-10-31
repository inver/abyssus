/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("FieldFoliage")

package net.nevinsky.abyssus.app.game.controlline.tools

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array as GdxArray
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBakeState
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMaskFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageRuntimeLoad
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageScale
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_COPY_LIMIT
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterOutcome
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEdit
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriter
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import org.slf4j.helpers.NOPLogger
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.SplittableRandom
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Migrates the bundled Control Line field's 215 trees and bushes to two native foliage assets and adds generated grass.
 *
 *   FieldFoliage record   <project> <manifest>   records the manifest from the field's vegetation as it is now
 *   FieldFoliage generate <project> <manifest>   edits <project> in place: grass model, foliage assets, Field.scene
 *
 * The manifest (environment/foliage-manifest.json) is the immutable source of every run: the vegetation entities
 * disappear from the scene once migrated, so a rerun must not read them. Every run, from the original project or the
 * migrated one, writes byte-identical files. All outputs are staged and checked first; nothing is published when a
 * check fails, and a project whose vegetation or the rest of whose scene differs from the manifest is refused.
 *
 * GL-free. Run it on a copy first: `./gradlew :app-game-control-line:generateFieldFoliage -PfieldFoliageProject=<copy>`.
 */
fun main(args: Array<String>) {
    require(args.size == 3 && args[0] in setOf("record", "generate")) {
        "usage: FieldFoliage record|generate <project folder> <manifest file>"
    }
    val tool = FieldFoliageTool()
    val project = File(args[1])
    val manifest = File(args[2])
    when (args[0]) {
        "record" -> tool.record(project, manifest)
        else -> println(tool.generate(project, manifest))
    }
}

/** The id the generator stamps on everything it makes; changing the algorithm changes it. */
const val FIELD_FOLIAGE_GENERATOR = "field-foliage-v1"

/** Seeds every layer; fixed, so reruns are byte-equal. */
const val FIELD_FOLIAGE_SEED = 20261011L

/** The `lastModified` of every generated asset: fixed, so reruns are byte-equal. */
const val FIELD_FOLIAGE_TIME = 1791244800000L

private const val MANIFEST_FORMAT = "abyssus-field-foliage-manifest"
private const val GRASS_MODEL = "model_airfield_grass"

/** The first id of the entities added after the migration; the original scene ends at 717. */
private const val FIRST_POST_MIGRATION_ENTITY = 718
private const val GRASS_LAYER = 6

/** Nothing of a tree or bush crown may reach inside this radius around the pilot (the Racer line plus a margin). */
const val FIELD_CLEAR_RADIUS = 28f

/** Nothing of a grass clump may reach inside the paved pad. */
const val FIELD_GRASS_CLEAR_RADIUS = 25f

/** Grass stands only where the base ground still shows at least this much (sequential splat blend weight). */
const val FIELD_GRASS_BASE_WEIGHT = 0.6f

/** Each species' copies per terrain must land within this share of the recorded count. */
const val FIELD_COUNT_TOLERANCE = 0.25

private val SPECIES = listOf(
    "model_airfield_tree_broadleaf", "model_airfield_tree_young", "model_airfield_tree_acacia",
    "model_airfield_bush_tall", "model_airfield_bush_low",
)

/** One terrain of the field and the foliage asset it carries. */
data class FieldTerrain(
    val entity: Int, val asset: String, val foliage: String, val originX: Float, val originZ: Float, val size: Int,
    val maskResolution: Int, val sigma: Float,
)

/** One recorded tree or bush: where it stood (world), how it was turned and scaled, and the terrain it belongs to. */
data class FieldInstance(
    val id: Int, val model: String, val x: Float, val y: Float, val z: Float, val yaw: Float, val scale: Float, val terrain: Int,
)

/** The recorded source of a migration. */
class FieldManifest(
    val sceneSha256: String,
    val vegetationSha256: String,
    val restSha256: String,
    val pilotX: Float,
    val pilotZ: Float,
    val terrains: List<FieldTerrain>,
    val instances: List<FieldInstance>,
    val inputs: Map<String, String>,
) {
    fun countOf(model: String, terrain: Int) = instances.count { it.model == model && it.terrain == terrain }

    fun toJson(): ObjectNode {
        val n = JsonNodeFactory.instance
        val root = n.objectNode()
        root.put("format", MANIFEST_FORMAT)
        root.put("version", 1)
        root.putObject("generator").put("id", FIELD_FOLIAGE_GENERATOR).put("seed", FIELD_FOLIAGE_SEED).put("assetTime", FIELD_FOLIAGE_TIME)
        root.putObject("source")
            .put("scene", "scenes/Field.scene").put("sceneSha256", sceneSha256)
            .put("vegetationSha256", vegetationSha256).put("restSha256", restSha256)
        root.putObject("pilot").put("x", pilotX).put("z", pilotZ)
        root.set<ObjectNode>("inputs", n.objectNode().also { o -> inputs.toSortedMap().forEach { (k, v) -> o.put(k, v) } })
        val terrainsNode = root.putArray("terrains")
        for (t in terrains) terrainsNode.addObject()
            .put("entity", t.entity).put("asset", t.asset).put("foliage", t.foliage)
            .put("originX", t.originX).put("originZ", t.originZ).put("size", t.size)
            .put("maskResolution", t.maskResolution).put("sigma", t.sigma)
        val species = root.putArray("species")
        for (model in SPECIES) {
            val row = species.addObject().put("model", model).put("total", instances.count { it.model == model })
            val per = row.putObject("perTerrain")
            for (t in terrains) per.put(t.entity.toString(), countOf(model, t.entity))
        }
        val list = root.putArray("instances")
        for (i in instances.sortedBy { it.id }) list.addArray()
            .add(i.id).add(i.model.removePrefix("model_airfield_")).add(i.x).add(i.y).add(i.z).add(i.yaw).add(i.scale).add(i.terrain)
        return root
    }

    companion object {
        fun fromJson(root: JsonNode): FieldManifest {
            require(root["format"]?.asText() == MANIFEST_FORMAT) { "not a field foliage manifest" }
            val source = root["source"]
            return FieldManifest(
                source["sceneSha256"].asText(), source["vegetationSha256"].asText(), source["restSha256"].asText(),
                root["pilot"]["x"].floatValue(), root["pilot"]["z"].floatValue(),
                root["terrains"].map {
                    FieldTerrain(
                        it["entity"].asInt(), it["asset"].asText(), it["foliage"].asText(), it["originX"].floatValue(),
                        it["originZ"].floatValue(), it["size"].asInt(), it["maskResolution"].asInt(), it["sigma"].floatValue(),
                    )
                },
                root["instances"].map {
                    FieldInstance(
                        it[0].asInt(), "model_airfield_" + it[1].asText(), it[2].floatValue(), it[3].floatValue(),
                        it[4].floatValue(), it[5].floatValue(), it[6].floatValue(), it[7].asInt(),
                    )
                },
                root["inputs"].properties().associate { it.key to it.value.asText() },
            )
        }
    }
}

/** The project cannot be migrated as it is; nothing was written. */
class MigrationConflict(message: String) : RuntimeException(message)

/** A staged result failed a check (counts, clear areas, formats); nothing was written. */
class MigrationFailure(message: String) : RuntimeException(message)

/** What [FieldFoliageTool.generate] did. */
class FieldMigration(val removedEntities: Int, val written: List<String>, val sceneChanged: Boolean) {
    override fun toString() = "removed $removedEntities entities; wrote ${written.size} files; scene ${if (sceneChanged) "edited" else "unchanged"}"
}

class FieldFoliageTool {
    private val json = SceneJson()
    private val editing = HeadlessEditing()
    private val sha = HexSha()

    // ---- recording the manifest ----

    /** Records the manifest from [project]'s field as it is now. Refuses to replace a different manifest. */
    fun record(project: File, manifestFile: File): FieldManifest {
        val sceneText = File(project, SCENE).readText()
        val manifest = recordFrom(project, sceneText)
        val text = json.pretty(manifest.toJson())
        if (manifestFile.exists() && manifestFile.readText() != text) {
            throw MigrationConflict("${manifestFile.path} already records a different source; the manifest is immutable")
        }
        manifestFile.parentFile?.mkdirs()
        manifestFile.writeText(text)
        return manifest
    }

    private fun recordFrom(project: File, sceneText: String): FieldManifest {
        val entities = entitiesOf(sceneText)
        val specs = FIELD_TERRAINS.map { spec ->
            val position = entities.getValue(spec.entity.toString())["components"]["PositionComponent"]["localPosition"]
            val size = readTerrainSize(project, spec.asset)
            spec.copy(originX = position["x"].floatValue(), originZ = position["z"].floatValue(), size = size)
        }
        val site = specs.first()
        val instances = entities.entries.mapNotNull { (id, e) -> instanceOf(id.toInt(), e) }.sortedBy { it.id }
            .map { it.copy(terrain = terrainOf(it, site)) }
        val pilot = entities.values.firstOrNull { it["components"].has("PilotComponent") }
            ?.get("components")?.get("PositionComponent")?.get("localPosition")
        return FieldManifest(
            sha.of(sceneText.toByteArray()), vegetationHash(instances), restHash(entities, instances.map { it.id }.toSet()),
            pilot?.get("x")?.floatValue() ?: 0f, pilot?.get("z")?.floatValue() ?: 0f,
            specs, instances, inputHashes(project, specs),
        )
    }

    private fun readTerrainSize(project: File, asset: String): Int =
        json.parse(File(project, "assets/$asset/meta.json").readText())["additional"]["size"].asInt()

    private fun instanceOf(id: Int, entity: JsonNode): FieldInstance? {
        val render = entity["components"]["RenderComponent"] ?: return null
        val model = render["assetName"]?.asText() ?: return null
        if (render["type"]?.asText() != "MODEL" || model !in SPECIES) return null
        val position = entity["components"]["PositionComponent"]
        val at = position["localPosition"]
        val r = position["localRotation"]
        val yaw = if (r == null) 0f else Quaternion(
            r["x"].floatValue(), r["y"].floatValue(), r["z"].floatValue(), r["w"].floatValue(),
        ).getYawRad()
        val scale = position["localScale"]?.get("x")?.floatValue() ?: 1f
        return FieldInstance(id, model, at["x"].floatValue(), at["y"].floatValue(), at["z"].floatValue(), yaw, scale, -1)
    }

    /** The terrain entity a world point belongs to: the site's square, else the outer ground. */
    private fun terrainOf(i: FieldInstance, site: FieldTerrain): Int {
        val inside = i.x >= site.originX && i.x <= site.originX + site.size && i.z >= site.originZ && i.z <= site.originZ + site.size
        return if (inside) FIELD_TERRAINS[0].entity else FIELD_TERRAINS[1].entity
    }

    private fun vegetationHash(instances: List<FieldInstance>): String =
        sha.of(instances.sortedBy { it.id }.joinToString("\n") { "${it.id}|${it.model}|${it.x}|${it.y}|${it.z}|${it.yaw}|${it.scale}" }.toByteArray())

    /** SHA-256 of every entity but the vegetation, with the foliage components a migration adds left out. */
    private fun restHash(entities: Map<String, JsonNode>, vegetation: Set<Int>): String {
        val text = StringBuilder()
        for ((id, entity) in entities.entries.sortedBy { it.key.toInt() }) {
            if (id.toInt() in vegetation) continue
            // entities added after the migration (the Mig-3 and the display tables, add-control-line-scale-mode) are not part of the field
            if (id.toInt() >= FIRST_POST_MIGRATION_ENTITY) continue
            val copy = entity.deepCopy<JsonNode>()
            (copy["components"] as? ObjectNode)?.remove("FoliageComponent")
            // balance tuned after the migration (the wire's inertia, add-control-line-wire-physics): not part of the field
            ((copy["components"] as? ObjectNode)?.get("PlaneComponent") as? ObjectNode)?.remove("thrust")
            text.append(id).append(':').append(json.compact(copy)).append('\n')
        }
        return sha.of(text.toString().toByteArray())
    }

    private fun inputHashes(project: File, terrains: List<FieldTerrain>): Map<String, String> {
        val files = buildList {
            for (t in terrains) {
                add("assets/${t.asset}/meta.json")
                add("assets/${t.asset}/terrain.data")
            }
            add("assets/texture_airfield_site_splat/splat.png")
            SPECIES.forEach { add("assets/$it/model.glb") }
        }
        return files.associateWith { sha.of(File(project, it).readBytes()) }
    }

    // ---- migrating ----

    /**
     * Migrates [project] in place from [manifestFile]: stages and checks the grass model, the two foliage assets and the
     * scene edit, then publishes them. The project may be the original one or an already migrated one.
     */
    fun generate(project: File, manifestFile: File): FieldMigration {
        val manifest = FieldManifest.fromJson(json.parse(manifestFile.readText()))
        val sceneFile = File(project, SCENE)
        val sceneText = sceneFile.readText()
        val entities = entitiesOf(sceneText)
        checkSource(project, manifest, entities)

        val stage = Files.createTempDirectory("field-foliage").toFile()
        try {
            val staged = stageAll(project, manifest, stage)
            val edited = editScene(sceneText, manifest, entities)
            // everything passed: publish the assets, then the scene
            val written = ArrayList<String>()
            for (name in staged.sorted()) {
                val target = File(project, "assets/$name")
                target.deleteRecursively()
                File(stage, "assets/$name").copyRecursively(target)
                File(stage, "assets/$name").walkTopDown().filter { it.isFile }.sorted()
                    .forEach { written += it.relativeTo(File(stage, "assets")).path }
            }
            val changed = edited != sceneText
            if (changed) sceneFile.writeText(edited)
            val removed = manifest.instances.count { it.id.toString() in entities }
            return FieldMigration(removed, written, changed)
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Refuses a project whose vegetation, rest of scene or inputs differ from what the manifest recorded. */
    private fun checkSource(project: File, manifest: FieldManifest, entities: Map<String, JsonNode>) {
        val ids = manifest.instances.map { it.id }
        val present = ids.filter { it.toString() in entities }
        if (present.isNotEmpty()) {
            if (present.size != ids.size) {
                throw MigrationConflict("${present.size} of the ${ids.size} recorded trees and bushes are in the scene: it was edited since the manifest was recorded")
            }
            val now = entities.entries.mapNotNull { (id, e) -> instanceOf(id.toInt(), e) }.filter { it.id in ids }
                .map { it.copy(terrain = manifest.instances.first { m -> m.id == it.id }.terrain) }
            if (vegetationHash(now) != manifest.vegetationSha256) {
                throw MigrationConflict("the trees and bushes of the scene differ from the manifest: re-record it from the original scene")
            }
        }
        if (restHash(entities, ids.toSet()) != manifest.restSha256) {
            throw MigrationConflict("the rest of the scene differs from the manifest: re-record it from the original scene")
        }
        for ((path, hash) in manifest.inputs) {
            val file = File(project, path)
            if (!file.isFile || sha.of(file.readBytes()) != hash) throw MigrationConflict("$path differs from the manifest")
        }
    }

    /** The scene text with the vegetation entities removed and the foliage attached; refused edits fail the run. */
    private fun editScene(sceneText: String, manifest: FieldManifest, entities: Map<String, JsonNode>): String {
        var text = sceneText
        val ids = manifest.instances.map { it.id.toString() }.toSet()
        if (ids.any { it in entities }) {
            text = applied(editing.removeEntities(text, ids), text)
        }
        for (t in manifest.terrains) text = applied(editing.setFoliage(text, t.entity.toString(), t.foliage), text)
        editing.validate(text, DocumentKind.SCENE)?.let { throw MigrationFailure("the edited scene is not valid: ${it.message}") }
        return text
    }

    private fun applied(edit: HeadlessEdit, current: String): String = when (edit) {
        is HeadlessEdit.Edited -> edit.text
        HeadlessEdit.Unchanged -> current
        is HeadlessEdit.Refused -> throw MigrationConflict("the scene edit was refused: ${edit.refusal.message}")
    }

    // ---- staging ----

    /** Writes every output into [stage]/assets and checks it; returns the asset folders to publish. */
    private fun stageAll(project: File, manifest: FieldManifest, stage: File): List<String> {
        val assets = File(stage, "assets").apply { mkdirs() }
        val models = Crowns(project)
        // the terrains the foliage stands on, as the loaders will read them
        val staging = File(stage, "project")
        for (t in manifest.terrains) File(project, "assets/${t.asset}").copyRecursively(File(staging, "assets/${t.asset}"))
        // only the models' metas: the runtime read checks that a layer's model exists, and nothing else of it
        for (model in SPECIES) File(project, "assets/$model/meta.json").copyTo(File(staging, "assets/$model/meta.json"))
        val files = FileLoader(staging)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val terrains = TerrainLoader(files, metas)

        writeGrass(File(assets, GRASS_MODEL))
        File(assets, "$GRASS_MODEL/meta.json").copyTo(File(staging, "assets/$GRASS_MODEL/meta.json"))
        val grassRadius = models.radiusOfGlb(File(assets, "$GRASS_MODEL/model.glb"))
        val splat = Splat(File(project, "assets/texture_airfield_site_splat/splat.png"))
        val site = manifest.terrains.first()

        for (t in manifest.terrains) {
            val data = checkNotNull(terrains.prepare(t.asset)?.staged?.data) { "terrain ${t.asset} cannot be read" }
            val layers = ArrayList<StagedLayer>()
            for ((index, model) in SPECIES.withIndex()) {
                val points = manifest.instances.filter { it.model == model && it.terrain == t.entity }
                if (points.isEmpty()) continue
                layers += fitSpecies(manifest, t, data, index + 1, model, points, models.radius(model))
            }
            if (t === site) layers += fitGrass(manifest, t, data, splat, grassRadius)
            writeFoliage(File(assets, t.foliage), t, data, layers, manifest)
            validate(manifest, t, File(assets, t.foliage), staging, layers, models, grassRadius)
        }
        return listOf(GRASS_MODEL) + manifest.terrains.map { it.foliage }
    }

    private fun fitSpecies(
        manifest: FieldManifest, t: FieldTerrain, terrain: TerrainData, id: Int, model: String,
        points: List<FieldInstance>, crown: Float,
    ): StagedLayer {
        val scales = points.map { it.scale }
        val scale = FoliageScale(scales.min(), scales.max())
        val clear = FIELD_CLEAR_RADIUS + crown * scale.max
        val local = points.map { (it.x - t.originX) to (it.z - t.originZ) }
        val step = t.size / (t.maskResolution - 1f)
        for (margin in listOf(1.5f, 3f, 6f)) {
            val mask = speciesMask(manifest, t, local, clear + margin * step, margin * step)
            val layer = fit("${t.asset}/$id", terrain, t, id, model, FoliageLayerKind.OBJECT, 0f, scale, 0f, mask, points.size)
            if (layer != null && clearOfPilot(manifest, t, layer, clear - 0f) && (t.entity == FIELD_TERRAINS[0].entity || outsideSite(manifest, t, layer))) {
                return layer
            }
        }
        throw MigrationFailure("${t.foliage}: cannot fit ${points.size} ${model.removePrefix("model_airfield_")} copies within ${(FIELD_COUNT_TOLERANCE * 100).toInt()}% outside the clear area")
    }

    private fun fitGrass(manifest: FieldManifest, t: FieldTerrain, terrain: TerrainData, splat: Splat, footprint: Float): StagedLayer {
        val scale = FoliageScale(0.8f, 1.4f)
        val clear = FIELD_GRASS_CLEAR_RADIUS + footprint * scale.max
        val step = t.size / (t.maskResolution - 1f)
        for (margin in listOf(1.5f, 3f, 6f)) {
            val mask = grassMask(manifest, t, splat, clear + margin * step)
            val layer = fit(
                "${t.asset}/grass", terrain, t, GRASS_LAYER, GRASS_MODEL, FoliageLayerKind.DETAIL, 1f, scale, 60f, mask, GRASS_TARGET,
                fixedCells = GRASS_CELLS,
            )
            if (layer != null && clearOfPilot(manifest, t, layer, clear)) return layer
        }
        throw MigrationFailure("${t.foliage}: grass cannot keep ${FIELD_GRASS_CLEAR_RADIUS.toInt()} m clear of the pilot")
    }

    // ---- masks ----

    /** Soft blobs of [sigma] around the recorded positions, zero inside [clear] of the pilot and under the site. */
    private fun speciesMask(manifest: FieldManifest, t: FieldTerrain, points: List<Pair<Float, Float>>, clear: Float, margin: Float): ByteArray {
        val res = t.maskResolution
        val step = t.size / (res - 1f)
        val site = manifest.terrains.first()
        val mask = ByteArray(res * res)
        val reach = 3f * t.sigma
        for (j in 0 until res) for (i in 0 until res) {
            val lx = i * step
            val lz = j * step
            val wx = t.originX + lx
            val wz = t.originZ + lz
            if (hypot(wx - manifest.pilotX, wz - manifest.pilotZ) < clear) continue
            // the outer ground keeps clear of everything the site square covers, texel support included
            if (t !== site && wx > site.originX - margin - step && wx < site.originX + site.size + margin + step &&
                wz > site.originZ - margin - step && wz < site.originZ + site.size + margin + step
            ) continue
            var v = 0f
            for ((px, pz) in points) {
                val dx = lx - px
                val dz = lz - pz
                if (abs(dx) > reach || abs(dz) > reach) continue
                v = max(v, exp(-(dx * dx + dz * dz) / (2f * t.sigma * t.sigma)))
            }
            mask[j * res + i] = (255f * v).roundToInt().coerceIn(0, 255).toByte()
        }
        return mask
    }

    /** Grass only where the base ground shows: the sequential splat blend leaves at least [FIELD_GRASS_BASE_WEIGHT]. */
    private fun grassMask(manifest: FieldManifest, t: FieldTerrain, splat: Splat, clear: Float): ByteArray {
        val res = t.maskResolution
        val step = t.size / (res - 1f)
        val mask = ByteArray(res * res)
        for (j in 0 until res) for (i in 0 until res) {
            val lx = i * step
            val lz = j * step
            if (hypot(t.originX + lx - manifest.pilotX, t.originZ + lz - manifest.pilotZ) < clear) continue
            // the worst of the texel's neighbourhood, so a bilinear mask never leaks onto hard ground
            var weight = 1f
            for (dz in -1..1) for (dx in -1..1) {
                weight = min(weight, splat.baseWeight((lx + dx * step * 0.5f) / t.size, (lz + dz * step * 0.5f) / t.size))
            }
            val v = ((weight - FIELD_GRASS_BASE_WEIGHT) / 0.3f).coerceIn(0f, 1f)
            mask[j * res + i] = (255f * v).roundToInt().toByte()
        }
        return mask
    }

    // ---- fitting ----

    /**
     * Picks the cell count (hence density) and seed whose scatter of [mask] comes closest to [target] copies: a bounded
     * search with a fixed order and ties going to the smaller cell count and then the smaller seed, so equal inputs
     * pick equal values. Null when no trial lands within [FIELD_COUNT_TOLERANCE] of the target.
     */
    private fun fit(
        key: String, terrain: TerrainData, t: FieldTerrain, id: Int, model: String, kind: FoliageLayerKind, align: Float,
        scale: FoliageScale, drawDistance: Float, mask: ByteArray, target: Int, fixedCells: Int? = null,
    ): StagedLayer? {
        val coverage = mask.sumOf { it.toInt() and 0xFF } / 255.0 / mask.size
        if (coverage <= 0.0) return null
        val scatter = FoliageScatter(FoliageFingerprint())
        val base = FoliageLayerMeta(
            id = id, kind = kind.name, models = listOf(FoliageModelMeta(model, 1f)), scale = scale,
            alignToNormal = align, drawDistance = drawDistance,
        )

        fun trial(cells: Int, seed: Int): ScatterOutcome {
            val layer = base.copy(density = densityOf(cells, t.size), seed = seed)
            return scatter.scatter(terrain, FoliageMeta(terrain = t.asset, maskResolution = t.maskResolution, layers = listOf(layer)), mapOf(id to mask))
        }

        val seed0 = ((FIELD_FOLIAGE_SEED xor key.hashCode().toLong()) and 0x7fffffffL).toInt()
        var best: Triple<Int, Int, Int>? = null // cells, seed, count
        fun consider(cells: Int, seed: Int): Int {
            val count = trial(cells, seed).counts[id] ?: 0
            val current = best
            val better = current == null || abs(count - target) < abs(current.third - target) ||
                abs(count - target) == abs(current.third - target) && (cells < current.first || cells == current.first && seed < current.second)
            if (better) best = Triple(cells, seed, count)
            return count
        }

        if (fixedCells != null) {
            consider(fixedCells, seed0)
        } else {
            var cells = max(1, sqrt(target / coverage).roundToInt())
            repeat(6) { // the keep rate drifts with the jitter: aim again from the last count
                val count = consider(cells, seed0)
                if (count > 0) cells = max(1, (cells * sqrt(target.toDouble() / count)).roundToInt())
            }
            for (candidate in max(1, cells - 4)..cells + 4) for (offset in 0 until 6) consider(candidate, seed0 + offset)
        }
        val (cells, seed, count) = best ?: return null
        if (fixedCells == null && abs(count - target) > FIELD_COUNT_TOLERANCE * target) return null
        val copies = trial(cells, seed).bake.layers.single().chunks.flatten()
        return StagedLayer(base.copy(density = densityOf(cells, t.size), seed = seed), mask, count, copies)
    }

    /** A density whose cell count is exactly [cells] along the terrain (the scatter floors `size * sqrt(density)`). */
    private fun densityOf(cells: Int, size: Int): Float {
        val side = (cells + 0.5) / size
        return (side * side).toFloat()
    }

    /** Whether every copy of [layer] stands at least [radius] (plus its own scaled crown, already in the caller's radius) from the pilot. */
    private fun clearOfPilot(manifest: FieldManifest, t: FieldTerrain, layer: StagedLayer, radius: Float): Boolean =
        layer.copies.all { hypot(t.originX + it.x - manifest.pilotX, t.originZ + it.z - manifest.pilotZ) >= radius }

    /** Whether no copy of [layer] stands inside the site terrain's square. */
    private fun outsideSite(manifest: FieldManifest, t: FieldTerrain, layer: StagedLayer): Boolean {
        val site = manifest.terrains.first()
        return layer.copies.none {
            val wx = t.originX + it.x
            val wz = t.originZ + it.z
            wx >= site.originX && wx <= site.originX + site.size && wz >= site.originZ && wz <= site.originZ + site.size
        }
    }

    // ---- writing ----

    private class StagedLayer(val meta: FoliageLayerMeta, val mask: ByteArray, val fitted: Int, val copies: List<FoliageCopy>)

    /** The meta, masks, bake and provenance of one foliage asset. */
    private fun writeFoliage(dir: File, t: FieldTerrain, terrain: TerrainData, layers: List<StagedLayer>, manifest: FieldManifest) {
        dir.mkdirs()
        val meta = FoliageMeta(terrain = t.asset, maskResolution = t.maskResolution, layers = layers.map { it.meta })
        File(dir, "meta.json").writeText(json.pretty(metaNode(t.foliage, meta)))
        val maskFile = FoliageMaskFile(FileLoader(dir))
        for (layer in layers) maskFile.write(File(dir, "layer-${layer.meta.id}.mask"), layer.mask, t.maskResolution)
        // the bake the editor would make from exactly these files: read them back so floats match the loader's
        val staging = File(dir.parentFile.parentFile, "project")
        dir.copyRecursively(File(staging, "assets/${t.foliage}"), overwrite = true)
        val files = FileLoader(staging)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val prepared = checkNotNull(FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(t.foliage)?.staged) {
            "${t.foliage} cannot be read back"
        }
        val outcome = FoliageScatter(FoliageFingerprint()).scatter(prepared.terrain!!, prepared.meta, prepared.masks)
        if (outcome.refusal != null) throw MigrationFailure("${t.foliage} is over a foliage limit: ${outcome.refusal}")
        File(dir, "foliage.data").writeBytes(FoliageDataFile().write(outcome.bake))
        File(dir, "source.json").writeText(json.pretty(sourceNode(t, manifest, layers)))
        File(staging, "assets/${t.foliage}").deleteRecursively()
    }

    private fun metaNode(name: String, meta: FoliageMeta): ObjectNode {
        val n = JsonNodeFactory.instance
        val root = n.objectNode()
        root.put("format", "abyssus").put("formatVersion", 1).put("version", 1).put("lastModified", FIELD_FOLIAGE_TIME)
        root.put("uuid", uuidOf(name)).put("type", "FOLIAGE")
        val additional = root.putObject("additional")
        additional.put("terrain", meta.terrain).put("maskResolution", meta.maskResolution)
        val layers = additional.putArray("layers")
        for (layer in meta.layers) {
            val node = layers.addObject().put("id", layer.id).put("kind", layer.kind)
            val models = node.putArray("models")
            for (m in layer.models) models.addObject().put("asset", m.asset).put("weight", m.weight)
            node.put("density", layer.density)
            node.putObject("scale").put("min", layer.scale.min).put("max", layer.scale.max)
            node.put("alignToNormal", layer.alignToNormal)
            if (layer.layerIsDetail()) node.put("drawDistance", layer.drawDistance)
            node.put("seed", layer.seed)
        }
        return root
    }

    private fun FoliageLayerMeta.layerIsDetail() = kind == FoliageLayerKind.DETAIL.name

    private fun sourceNode(t: FieldTerrain, manifest: FieldManifest, layers: List<StagedLayer>): ObjectNode {
        val root = JsonNodeFactory.instance.objectNode()
        root.put("generator", "tools/FieldFoliage.kt").put("generatorId", FIELD_FOLIAGE_GENERATOR).put("seed", FIELD_FOLIAGE_SEED)
        root.put("license", "CC0-1.0").put("manifest", "environment/foliage-manifest.json")
        root.put("note", "Generated from the recorded placements of the field's trees and bushes; edit the layers in Abyssus and Re-bake.")
        root.put("terrainEntity", t.entity)
        val counts = root.putObject("fitted")
        for (layer in layers) counts.put(layer.meta.models.single().asset, layer.fitted)
        root.put("vegetationSha256", manifest.vegetationSha256)
        return root
    }

    private fun uuidOf(name: String) = UUID.nameUUIDFromBytes("abyssus/field-foliage/$name".toByteArray()).toString()

    // ---- the grass model ----

    /** A three-quad crossed clump with an alpha-tested blade texture: the folder's four files. */
    private fun writeGrass(dir: File) {
        File(dir, "textures").mkdirs()
        ImageIO.write(grassTexture(), "png", File(dir, "textures/grass.png"))
        File(dir, "model.glb").writeBytes(
            GltfWriter().write(grassModel(), mapOf("textures/grass.png" to "textures/grass.png"), FIELD_FOLIAGE_GENERATOR),
        )
        val meta = JsonNodeFactory.instance.objectNode()
        meta.put("format", "abyssus").put("formatVersion", 1).put("version", 1).put("lastModified", FIELD_FOLIAGE_TIME)
        meta.put("uuid", uuidOf(GRASS_MODEL)).put("type", "MODEL")
        meta.putObject("additional").put("file", "model.glb").put("format", "GLTF").put("binary", true).putArray("materials")
        File(dir, "meta.json").writeText(json.pretty(meta))
        val source = JsonNodeFactory.instance.objectNode()
        source.put("generator", "tools/FieldFoliage.kt").put("generatorId", FIELD_FOLIAGE_GENERATOR).put("license", "CC0-1.0")
        source.put("note", "Three crossed quads with a procedurally drawn blade texture; no outside material.")
        File(dir, "source.json").writeText(json.pretty(source))
    }

    private fun grassModel(): ModelData {
        val data = ModelData()
        val width = 0.9f
        val height = 0.65f
        val vertices = ArrayList<Float>()
        val indices = ArrayList<Int>()
        for (k in 0 until 3) {
            val angle = k * PI.toFloat() / 3f
            val dx = kotlin.math.cos(angle) * width / 2f
            val dz = kotlin.math.sin(angle) * width / 2f
            val base = k * 4
            // x y z, normal (up, so a clump is lit like the ground), u v
            for ((cx, cz, cy, u, v) in listOf(
                listOf(-dx, -dz, 0f, 0f, 1f), listOf(dx, dz, 0f, 1f, 1f), listOf(dx, dz, height, 1f, 0f), listOf(-dx, -dz, height, 0f, 0f),
            )) vertices += listOf(cx, cy, cz, 0f, 1f, 0f, u, v)
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }
        data.addMesh(ModelMesh().apply {
            id = "grass_mesh"
            attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
            this.vertices = vertices.toFloatArray()
            parts = arrayOf(ModelMeshPart().apply { id = "grass_part"; this.indices = indices.toIntArray(); primitiveType = GL20.GL_TRIANGLES })
        })
        data.materials.add(PbrModelMaterial().apply {
            id = "grass"; baseColor = Color.WHITE; metallic = 0f; roughness = 1f
            alphaMode = PbrModelMaterial.AlphaMode.MASK; alphaCutoff = 0.5f; doubleSided = true
            textures = GdxArray<ModelTexture>().apply { add(ModelTexture().apply { usage = ModelTexture.USAGE_DIFFUSE; fileName = "textures/grass.png" }) }
        })
        data.nodes.add(ModelNode().apply {
            id = "Grass"
            parts = arrayOf(ModelNodePart().apply { meshPartId = "grass_part"; materialId = "grass" })
        })
        return data
    }

    /** 128 by 256 blades: hard-edged (alpha tested), greens from dark at the root to light at the tip. */
    private fun grassTexture(): BufferedImage {
        val image = BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        val random = SplittableRandom(FIELD_FOLIAGE_SEED)
        repeat(11) {
            val x = 8 + random.nextInt(112)
            val top = 12 + random.nextInt(90)
            val lean = random.nextInt(-22, 23)
            val w = 5 + random.nextInt(5)
            val light = 0.55f + random.nextInt(40) / 100f
            for (y in 255 downTo top step 4) {
                val t = (255 - y) / (255f - top)
                val cx = x + (lean * t * t).toInt()
                val half = max(1, (w * (1f - 0.85f * t)).toInt())
                g.color = java.awt.Color(0.12f + 0.25f * t * light, 0.35f + 0.45f * t * light, 0.08f + 0.1f * t, 1f)
                g.fillRect(cx - half, y - 3, half * 2, 5)
            }
        }
        g.dispose()
        return image
    }

    // ---- validation ----

    /** Reads [dir] back through the runtime loaders and checks counts, formats and the clear areas. */
    private fun validate(
        manifest: FieldManifest, t: FieldTerrain, dir: File, staging: File, layers: List<StagedLayer>, crowns: Crowns, grassRadius: Float,
    ) {
        dir.copyRecursively(File(staging, "assets/${t.foliage}"), overwrite = true)
        val files = FileLoader(staging)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val read = FoliageRuntimeLoad(files, metas, TerrainLoader(files, metas)).read(t.foliage)
        val foliage = read.foliage ?: throw MigrationFailure("${t.foliage} cannot be read back: ${read.warnings}")
        if (read.warnings.isNotEmpty()) throw MigrationFailure("${t.foliage}: ${read.warnings}")
        if (foliage.state != FoliageBakeState.MATCHING) throw MigrationFailure("${t.foliage}: the bake does not match its inputs")
        editing.validate(File(dir, "meta.json").readText(), DocumentKind.ASSET)?.let { throw MigrationFailure("${t.foliage}/meta.json: ${it.message}") }
        val bakeFile = File(dir, "foliage.data")
        val bake = FoliageDataFile().read(bakeFile.readBytes()) ?: throw MigrationFailure("${t.foliage}/foliage.data is not a readable bake")
        if (bake.copyCount > FOLIAGE_COPY_LIMIT) throw MigrationFailure("${t.foliage} is over the copy limit")
        val site = manifest.terrains.first()
        for (layer in layers) {
            val baked = bake.layers.first { it.id == layer.meta.id }
            val model = layer.meta.models.single().asset
            val target = if (model == GRASS_MODEL) null else manifest.countOf(model, t.entity)
            if (target != null && abs(baked.copyCount - target) > FIELD_COUNT_TOLERANCE * target) {
                throw MigrationFailure("${t.foliage}: ${baked.copyCount} copies of ${model.removePrefix("model_airfield_")} against ${target} recorded")
            }
            val crown = if (model == GRASS_MODEL) grassRadius else crowns.radius(model)
            val clear = if (model == GRASS_MODEL) FIELD_GRASS_CLEAR_RADIUS else FIELD_CLEAR_RADIUS
            for (copy in baked.chunks.flatten()) {
                val wx = t.originX + copy.x
                val wz = t.originZ + copy.z
                if (hypot(wx - manifest.pilotX, wz - manifest.pilotZ) < clear + crown * copy.scale) {
                    throw MigrationFailure("${t.foliage}: a ${model.removePrefix("model_airfield_")} copy reaches inside ${clear.toInt()} m of the pilot")
                }
                if (t !== site && wx >= site.originX && wx <= site.originX + site.size && wz >= site.originZ && wz <= site.originZ + site.size) {
                    throw MigrationFailure("${t.foliage}: a copy stands under the site terrain")
                }
            }
        }
    }

    // ---- scene reading ----

    private fun entitiesOf(sceneText: String): Map<String, JsonNode> {
        val tree = SceneEntityTree(json.parse(sceneText))
        val entities = tree.entities() ?: throw MigrationConflict("the scene has no entities")
        return entities.properties().associate { it.key to it.value }
    }

    private companion object {
        const val SCENE = "scenes/Field.scene"

        /** The grass is a fixed 150 by 150 cell grid (about 0.25 copies per square metre); the mask decides where it stands. */
        const val GRASS_CELLS = 150
        const val GRASS_TARGET = 1
    }
}

/** The terrains of the field with the fixed settings of their foliage assets (origin and size are read from the scene). */
val FIELD_TERRAINS = listOf(
    FieldTerrain(0, "terrain_airfield_site", "foliage_airfield_site", 0f, 0f, 0, 128, 9f),
    FieldTerrain(300, "terrain_airfield_outer", "foliage_airfield_outer", 0f, 0f, 0, 128, 22f),
)

private class HexSha {
    fun of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** The ground-plane reach of model crowns, from the model files: the largest horizontal distance of any vertex. */
private class Crowns(private val project: File) {
    private val cache = HashMap<String, Float>()

    fun radius(model: String): Float = cache.getOrPut(model) { radiusOfGlb(File(project, "assets/$model/model.glb")) }

    fun radiusOfGlb(glb: File): Float {
        val data = AssimpModelLoader().loadData(FileHandle(glb))
        var reach = 0f
        fun visit(node: ModelNode, parent: Matrix4) {
            val world = Matrix4(parent).mul(
                Matrix4().set(node.translation ?: Vector3.Zero, node.rotation ?: Quaternion(), node.scale ?: Vector3(1f, 1f, 1f)),
            )
            for (part in node.parts.orEmpty()) {
                val mesh = data.meshes.firstOrNull { m -> m.parts.any { it.id == part.meshPartId } } ?: continue
                val stride = mesh.attributes.sumOf { it.numComponents }
                var at = 0
                var position = -1
                for (a in mesh.attributes) {
                    if (a.usage == VertexAttributes.Usage.Position) position = at
                    at += a.numComponents
                }
                if (position < 0 || stride == 0) continue
                val used = mesh.parts.first { it.id == part.meshPartId }.indices.distinct()
                val v = Vector3()
                for (i in used) {
                    val o = i * stride + position
                    if (o + 2 >= mesh.vertices.size) continue
                    v.set(mesh.vertices[o], mesh.vertices[o + 1], mesh.vertices[o + 2]).mul(world)
                    reach = max(reach, hypot(v.x, v.z))
                }
            }
            for (child in node.children.orEmpty()) child?.let { visit(it, world) }
        }
        for (node in data.nodes) visit(node, Matrix4())
        return reach
    }
}

/** The site's splat map: R compacted dirt (paths too), G asphalt, B ballast, A concrete, blended over the base in turn. */
private class Splat(file: File) {
    private val image: BufferedImage = ImageIO.read(file)
    private val width = image.width
    private val height = image.height

    /** How much of the base ground survives the sequential blend `mix(color, layer, channel)` at ([u], [v]) of the terrain. */
    fun baseWeight(u: Float, v: Float): Float {
        val r = channel(u, v, 16)
        val g = channel(u, v, 8)
        val b = channel(u, v, 0)
        val a = channel(u, v, 24)
        return (1f - r) * (1f - g) * (1f - b) * (1f - a)
    }

    /** One channel, bilinear like the GPU's clamped linear filter, from 0 to 1. */
    private fun channel(u: Float, v: Float, shift: Int): Float {
        val x = (u * width - 0.5f).coerceIn(0f, width - 1f)
        val y = (v * height - 0.5f).coerceIn(0f, height - 1f)
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = x - x0
        val fy = y - y0
        fun at(px: Int, py: Int) = ((image.getRGB(px, py) ushr shift) and 0xFF) / 255f
        return (at(x0, y0) * (1 - fx) + at(x1, y0) * fx) * (1 - fy) + (at(x0, y1) * (1 - fx) + at(x1, y1) * fx) * fy
    }
}
