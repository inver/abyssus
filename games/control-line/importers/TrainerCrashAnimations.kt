/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("TrainerCrashAnimations")

package net.nevinsky.abyssus.games.controlline.tools

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The trainer's crash clips, as named in `model.glb`. */
const val CRASH_LITTLE = "crash_little"
const val CRASH_MEDIUM = "crash_medium"
const val CRASH_FULL = "crash_full"

/** Where the left and right wings break off the centre section (model x, metres): the wing roots at the fuselage. */
private const val WING_ROOT_X = 0.0615f

/** Where the tail cone breaks off the cabin (model z, metres). */
private const val TAIL_BREAK_Z = -0.30f

/** The ground under the parked trainer: where its main wheels touch (model y, metres). */
private const val GROUND_Y = -0.136f

/** Samples per second of the written clips (linear interpolation in between). */
private const val SAMPLE_RATE = 30

/**
 * Adds the crash clips to the imported trainer (`assets/model_trainer/model.glb`), which must have none yet. Run by
 * `./gradlew :games:control-line:importTrainer` after the import, or alone on a freshly imported model by
 * `./gradlew :games:control-line:animateTrainer`.
 *
 * Usage: `TrainerCrashAnimations <project folder>`
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: TrainerCrashAnimations <project folder>" }
    val file = File(args[0], "assets/model_trainer/model.glb")
    file.writeBytes(withCrashAnimations(file.readBytes()))
    println("model_trainer: added $CRASH_LITTLE, $CRASH_MEDIUM and $CRASH_FULL")
}

/**
 * The imported trainer [glb] with breakable parts and three crash clips. The import writes every part as its own
 * root node with its vertices in the model frame (nose +Z, up +Y, left wing +X, origin at the centre of gravity, the
 * main wheels touching y = -0.136). This cuts the wings at their roots and the tail cone behind the cabin, gives every
 * part that moves its own pivot (a hinge, a strut top, a break line) and hangs the parts under one `Trainer` node,
 * so the rest pose draws exactly as before. The clips start from that rest pose and end with the wreck at rest:
 *
 * - [CRASH_LITTLE]: the landing gear breaks. The nose leg folds back, the left leg splays out, the right one snaps
 *   off; the plane drops onto its belly and slides a little.
 * - [CRASH_MEDIUM]: the left wing and the tail break. A hard landing on the left wing tip folds that wing down and
 *   back at its root and snaps the tail cone, which drops onto the ground; the plane ground-loops to the left.
 * - [CRASH_FULL]: a nose-in. Gear, propeller, both wings and the tail come off and scatter; the fuselage ends on its
 *   belly, yawed and rolled.
 *
 * The output depends only on the input.
 */
fun withCrashAnimations(glb: ByteArray, mapper: ObjectMapper = ObjectMapper()): ByteArray {
    val model = Glb.read(glb, mapper)
    require(!model.json.has("animations")) { "model.glb already has animations: re-import it with importTrainer" }
    val parts = model.parts()

    fun part(name: String, pivot: Vec, vararg children: Part, prims: List<Prim> = parts.getValue(name)) =
        Part(name, prims, pivot, children.toList())

    val wings = parts.getValue("Wings").split(0, -WING_ROOT_X)
    val leftWings = wings.second.split(0, WING_ROOT_X)
    val fuselage = parts.getValue("Fuselage").split(2, TAIL_BREAK_Z)
    val fin = parts.getValue("VTail").split(2, TAIL_BREAK_Z)

    val root = Part(
        "Trainer", emptyList(), Vec(0f, 0f, 0f), listOf(
            Part("Fuselage", fuselage.second, Vec(0f, 0f, 0f), emptyList()),
            Part("VTail", fin.second, Vec(0f, 0f, 0f), emptyList()),
            part("Hub", Vec(0f, -0.025f, 0.2f)),
            part("Propeller", Vec(0f, -0.0235f, 0.199f)),
            Part(
                "Wings", leftWings.first, Vec(0f, 0f, 0f), listOf(
                    Part(
                        "LeftWing", leftWings.second, Vec(WING_ROOT_X, 0.04f, -0.03f), listOf(
                            part("LeftFlap", Vec(0.148f, 0.033f, -0.073f)),
                            part("LeftAileron", Vec(0.362f, 0.036f, -0.038f)),
                        ),
                    ),
                    Part(
                        "RightWing", wings.first, Vec(-WING_ROOT_X, 0.04f, -0.03f), listOf(
                            part("RightFlap", Vec(-0.148f, 0.033f, -0.072f)),
                            part("RightAileron", Vec(-0.362f, 0.036f, -0.036f)),
                        ),
                    ),
                ),
            ),
            Part(
                "Tail", fuselage.first + fin.first, Vec(0f, -0.071f, TAIL_BREAK_Z), listOf(
                    part("LeftHTail", Vec(0.008f, -0.04f, -0.42f), part("LeftElevator", Vec(0.09f, -0.04f, -0.414f))),
                    part("RightHTail", Vec(-0.008f, -0.04f, -0.42f), part("RightElevator", Vec(-0.09f, -0.04f, -0.414f))),
                    part("Rudder", Vec(0f, 0.04f, -0.459f)),
                ),
            ),
            part("NoseWheel", Vec(0f, -0.085f, 0.105f)),
            part("LeftWheel", Vec(0.055f, -0.082f, -0.045f)),
            part("RightWheel", Vec(-0.055f, -0.082f, -0.045f)),
        ),
    )
    val used = HashSet<String>().also { root.collectNames(it) }
    check(used.containsAll(parts.keys)) { "model.glb has parts the crash rig does not place: ${parts.keys - used}" }
    return model.write(root, listOf(crashLittle(), crashMedium(), crashFull()), mapper)
}

private fun crashLittle() = Clip(CRASH_LITTLE).apply {
    track("Trainer") {
        key(0.18f, move = v(0f, -0.036f, 0.02f), turn = v(4f, 0f, 3f), ease = Ease.IN, land = true)
        key(0.30f, move = v(0f, -0.028f, 0.04f), turn = v(2.5f, 0.5f, 2f), ease = Ease.OUT)
        key(0.42f, move = v(0f, -0.036f, 0.05f), turn = v(4f, 1f, 3f), ease = Ease.IN)
        key(1.2f, move = v(0f, -0.036f, 0.07f), turn = v(4f, 2f, 3f), ease = Ease.OUT)
    }
    track("NoseWheel") {
        key(0.14f, turn = v(80f, 0f, 0f), ease = Ease.IN)
        key(0.26f, turn = v(70f, 0f, 4f), ease = Ease.OUT)
        key(0.40f, turn = v(78f, 0f, 3f), ease = Ease.IN)
    }
    track("LeftWheel") {
        key(0.16f, turn = v(10f, 0f, 70f), ease = Ease.IN)
        key(0.28f, turn = v(8f, 0f, 62f), ease = Ease.OUT)
        key(0.40f, turn = v(12f, 0f, 72f), ease = Ease.IN)
    }
    track("RightWheel") {
        key(0.10f, turn = v(5f, 0f, -35f), ease = Ease.IN)
        key(0.30f, move = v(-0.06f, -0.01f, -0.06f), turn = v(-60f, -20f, -80f))
        key(0.65f, move = v(-0.12f, -0.035f, -0.17f), turn = v(-250f, -35f, -95f), ease = Ease.OUT, land = true)
        key(1.0f, move = v(-0.13f, -0.036f, -0.19f), turn = v(-270f, -40f, -90f), ease = Ease.OUT)
    }
}

private fun crashMedium() = Clip(CRASH_MEDIUM).apply {
    track("Trainer") {
        key(0.10f, move = v(0f, -0.012f, 0.03f), turn = v(0f, 2f, -9f), ease = Ease.IN, land = true)
        key(0.30f, move = v(0f, -0.008f, 0.06f), turn = v(-1f, 8f, -4f), ease = Ease.OUT)
        key(0.55f, move = v(0f, -0.004f, 0.08f), turn = v(0f, 14f, -2f))
        key(1.6f, move = v(0f, 0f, 0.09f), turn = v(0f, 22f, -1.5f), ease = Ease.OUT)
    }
    track("LeftWing") {
        key(0.10f, turn = v(0f, 0f, 4f), ease = Ease.IN)
        key(0.28f, turn = v(-4f, 26f, -20f), ease = Ease.IN)
        key(0.40f, turn = v(-3f, 22f, -17f), ease = Ease.OUT)
        key(0.55f, turn = v(-4f, 25f, -20f), ease = Ease.IN)
    }
    track("LeftAileron") {
        key(0.30f, turn = v(-35f, 0f, 0f), ease = Ease.IN)
        key(0.50f, turn = v(-20f, 0f, 0f), ease = Ease.OUT)
    }
    track("LeftFlap") {
        key(0.30f, turn = v(-25f, 0f, 0f), ease = Ease.IN)
        key(0.50f, turn = v(-15f, 0f, 0f), ease = Ease.OUT)
    }
    track("Tail") {
        key(0.18f, turn = v(-4f, 0f, 0f), ease = Ease.IN)
        key(0.36f, turn = v(-18f, -9f, 10f), ease = Ease.IN)
        key(0.48f, turn = v(-14f, -8f, 9f), ease = Ease.OUT)
        key(0.62f, turn = v(-18f, -10f, 11f), ease = Ease.IN)
    }
    track("LeftElevator") {
        key(0.40f, turn = v(-30f, 0f, 0f), ease = Ease.IN)
        key(0.60f, turn = v(-18f, 0f, 0f), ease = Ease.OUT)
    }
    track("RightElevator") {
        key(0.40f, turn = v(25f, 0f, 0f), ease = Ease.IN)
        key(0.60f, turn = v(12f, 0f, 0f), ease = Ease.OUT)
    }
    track("Rudder") {
        key(0.40f, turn = v(0f, 30f, 0f), ease = Ease.IN)
        key(0.65f, turn = v(0f, 18f, 0f), ease = Ease.OUT)
    }
}

private fun crashFull() = Clip(CRASH_FULL).apply {
    track("Trainer") {
        key(0.12f, move = v(0f, -0.03f, 0.05f), turn = v(22f, 3f, -6f), ease = Ease.IN)
        key(0.35f, move = v(0.01f, -0.02f, 0.12f), turn = v(30f, 15f, -14f), ease = Ease.OUT)
        key(0.70f, move = v(0.03f, -0.045f, 0.2f), turn = v(10f, 28f, -18f), ease = Ease.IN, land = true)
        key(0.85f, move = v(0.035f, -0.04f, 0.22f), turn = v(7f, 31f, -15f), ease = Ease.OUT)
        key(2.2f, move = v(0.04f, -0.045f, 0.25f), turn = v(8f, 35f, -16f), ease = Ease.OUT)
    }
    track("Propeller") {
        key(0.10f, turn = v(-40f, 0f, 25f), ease = Ease.IN)
        key(0.50f, move = v(0.10f, 0.02f, 0.18f), turn = v(-90f, 200f, 120f))
        key(0.90f, move = v(0.16f, -0.08f, 0.28f), turn = v(-90f, 320f, 180f), ease = Ease.IN, land = true)
        key(1.4f, move = v(0.17f, -0.08f, 0.31f), turn = v(-90f, 340f, 180f), ease = Ease.OUT)
    }
    track("NoseWheel") {
        key(0.10f, turn = v(70f, 0f, 0f), ease = Ease.IN)
        key(0.50f, move = v(-0.03f, 0.03f, -0.20f), turn = v(300f, 40f, 30f))
        key(1.0f, move = v(-0.06f, -0.05f, -0.40f), turn = v(450f, 60f, 90f), ease = Ease.IN, land = true)
        key(1.5f, move = v(-0.065f, -0.05f, -0.45f), turn = v(460f, 70f, 90f), ease = Ease.OUT)
    }
    track("LeftWheel") {
        key(0.12f, turn = v(10f, 0f, 60f), ease = Ease.IN)
        key(0.55f, move = v(0.18f, 0.04f, -0.10f), turn = v(-200f, 30f, 150f))
        key(1.1f, move = v(0.30f, -0.04f, -0.22f), turn = v(-360f, 40f, 270f), ease = Ease.IN, land = true)
        key(1.6f, move = v(0.33f, -0.04f, -0.26f), turn = v(-370f, 40f, 270f), ease = Ease.OUT)
    }
    track("RightWheel") {
        key(0.12f, turn = v(10f, 0f, -60f), ease = Ease.IN)
        key(0.60f, move = v(-0.15f, 0.05f, -0.05f), turn = v(-180f, -20f, -160f))
        key(1.2f, move = v(-0.26f, -0.04f, -0.12f), turn = v(-340f, -30f, -270f), ease = Ease.IN, land = true)
        key(1.7f, move = v(-0.29f, -0.04f, -0.14f), turn = v(-360f, -30f, -270f), ease = Ease.OUT)
    }
    track("LeftWing") {
        key(0.20f, turn = v(-5f, 10f, -10f), ease = Ease.IN)
        key(0.60f, move = v(0.12f, 0.10f, -0.06f), turn = v(-20f, 60f, 80f))
        key(1.1f, move = v(0.18f, -0.02f, -0.20f), turn = v(-10f, 75f, 175f), ease = Ease.IN, land = true)
        key(1.3f, move = v(0.19f, -0.01f, -0.22f), turn = v(-8f, 78f, 172f), ease = Ease.OUT)
        key(1.6f, move = v(0.19f, -0.02f, -0.22f), turn = v(-9f, 78f, 176f), ease = Ease.OUT)
    }
    track("LeftAileron") {
        key(0.6f, turn = v(60f, 0f, 0f), ease = Ease.IN)
        key(1.2f, turn = v(40f, 0f, 0f), ease = Ease.OUT)
    }
    track("LeftFlap") {
        key(0.6f, turn = v(-50f, 0f, 0f), ease = Ease.IN)
        key(1.2f, turn = v(-30f, 0f, 0f), ease = Ease.OUT)
    }
    track("RightWing") {
        key(0.30f, turn = v(-3f, -8f, 12f), ease = Ease.IN)
        key(0.55f, turn = v(-6f, -38f, 32f), ease = Ease.IN)
        key(0.70f, turn = v(-5f, -34f, 28f), ease = Ease.OUT)
        key(0.85f, turn = v(-6f, -36f, 30f), ease = Ease.IN)
    }
    track("RightAileron") {
        key(0.6f, turn = v(-45f, 0f, 0f), ease = Ease.IN)
        key(0.9f, turn = v(-30f, 0f, 0f), ease = Ease.OUT)
    }
    track("RightFlap") {
        key(0.6f, turn = v(-40f, 0f, 0f), ease = Ease.IN)
    }
    track("Tail") {
        key(0.20f, turn = v(10f, 0f, 0f), ease = Ease.IN)
        key(0.60f, move = v(-0.04f, 0.10f, -0.12f), turn = v(70f, -30f, 40f))
        key(1.1f, move = v(-0.08f, 0.01f, -0.22f), turn = v(10f, -60f, 85f), ease = Ease.IN, land = true)
        key(1.3f, move = v(-0.085f, 0.02f, -0.23f), turn = v(8f, -62f, 80f), ease = Ease.OUT)
        key(1.6f, move = v(-0.085f, 0.01f, -0.23f), turn = v(9f, -62f, 84f), ease = Ease.OUT)
    }
    track("LeftHTail") {
        key(0.6f, turn = v(0f, 0f, 25f), ease = Ease.IN)
        key(1.1f, turn = v(0f, 15f, -30f), ease = Ease.OUT)
    }
    track("LeftElevator") {
        key(0.6f, turn = v(-50f, 0f, 0f), ease = Ease.IN)
    }
    track("RightElevator") {
        key(0.6f, turn = v(35f, 0f, 0f), ease = Ease.IN)
    }
    track("Rudder") {
        key(0.6f, turn = v(0f, -40f, 0f), ease = Ease.IN)
        key(1.0f, turn = v(0f, -25f, 0f), ease = Ease.OUT)
    }
}

// ---- rig ----

private class Vec(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    fun isZero() = x == 0f && y == 0f && z == 0f
}

private fun v(x: Float, y: Float, z: Float) = Vec(x, y, z)

/** A part of the model: its triangles in the model frame, the point it turns about, and the parts it carries. */
private class Part(val name: String, val prims: List<Prim>, val pivot: Vec, val children: List<Part>) {
    fun collectNames(into: MutableSet<String>) {
        if (prims.isNotEmpty()) into.add(name)
        children.forEach { it.collectNames(into) }
    }
}

private enum class Ease { LINEAR, IN, OUT }

/** A pose relative to rest: [move] in the parent's frame, [turn] degrees about x (pitch), y (yaw) and z (roll). */
private class Key(val time: Float, val move: Vec, val turn: Vec, val ease: Ease)

/**
 * One part's motion. The part never goes below the ground: its height is raised where a key would sink it. From
 * [landsAt] on it rests on the ground, its height set so that its lowest point touches it.
 */
private class Track(val node: String) {
    val keys = mutableListOf(Key(0f, Vec(0f, 0f, 0f), Vec(0f, 0f, 0f), Ease.LINEAR))
    var landsAt: Float? = null
        private set

    /**
     * The pose at [time], reached from the previous key along [ease]. Keys come in time order. A [land] key puts the
     * part on the ground from then on.
     */
    fun key(time: Float, move: Vec = Vec(0f, 0f, 0f), turn: Vec = Vec(0f, 0f, 0f), ease: Ease = Ease.LINEAR, land: Boolean = false) {
        check(time > keys.last().time) { "$node: keys out of order at $time" }
        keys += Key(time, move, turn, ease)
        if (land && landsAt == null) landsAt = time
    }

    fun at(time: Float): Pair<Vec, Vec> {
        val next = keys.indexOfFirst { it.time >= time }
        if (next <= 0) return (if (next == 0) keys.first() else keys.last()).let { it.move to it.turn }
        val a = keys[next - 1]
        val b = keys[next]
        val t = (time - a.time) / (b.time - a.time)
        val e = when (b.ease) {
            Ease.LINEAR -> t
            Ease.IN -> t * t
            Ease.OUT -> 1 - (1 - t) * (1 - t)
        }
        fun mix(p: Vec, q: Vec) = Vec(p.x + (q.x - p.x) * e, p.y + (q.y - p.y) * e, p.z + (q.z - p.z) * e)
        return mix(a.move, b.move) to mix(a.turn, b.turn)
    }
}

private class Clip(val name: String) {
    val tracks = mutableListOf<Track>()
    val duration get() = tracks.maxOf { it.keys.last().time }

    fun track(node: String, block: Track.() -> Unit) {
        tracks += Track(node).apply(block)
    }
}

/**
 * Samples a clip's tracks over [root]'s parts, parents first, keeping each tracked part above the ground ([GROUND_Y])
 * and on it from its landing. A tracked part's body is its own triangles and those of the parts it carries that the
 * clip does not move.
 */
private class Settle(private val root: Part, private val clip: Clip) {
    private val tracks = clip.tracks.associateBy { it.node }
    private val bodies = HashMap<String, FloatArray>()

    init {
        fun collect(part: Part, offset: Vec, into: MutableList<Float>) {
            for (p in part.prims) for (i in p.positions.indices step 3) {
                into += p.positions[i] - part.pivot.x + offset.x
                into += p.positions[i + 1] - part.pivot.y + offset.y
                into += p.positions[i + 2] - part.pivot.z + offset.z
            }
            for (child in part.children) if (child.name !in tracks) collect(child, offset + (child.pivot - part.pivot), into)
        }
        fun visit(part: Part) {
            if (part.name in tracks) bodies[part.name] = ArrayList<Float>().also { collect(part, Vec(0f, 0f, 0f), it) }.toFloatArray()
            part.children.forEach(::visit)
        }
        visit(root)
        check(bodies.keys == tracks.keys) { "${clip.name}: no parts ${tracks.keys - bodies.keys}" }
    }

    /** Each tracked part's translations (three floats per time) and rotations (four), in its parent's frame. */
    fun sample(times: FloatArray): Map<String, Pair<FloatArray, FloatArray>> {
        val out = tracks.keys.associateWith { FloatArray(times.size * 3) to FloatArray(times.size * 4) }
        for ((i, time) in times.withIndex()) visit(root, Vec(0f, 0f, 0f), Quaternion(), Vector3(), time, i, out)
        return out
    }

    private fun visit(part: Part, parentPivot: Vec, parentTurn: Quaternion, parentAt: Vector3, time: Float, i: Int, out: Map<String, Pair<FloatArray, FloatArray>>) {
        val rest = part.pivot - parentPivot
        val local = Vector3(rest.x, rest.y, rest.z)
        val turn = Quaternion()
        val track = tracks[part.name]
        if (track != null) {
            val (move, angles) = track.at(time)
            local.add(move.x, move.y, move.z)
            turn.setEulerAngles(angles.y, angles.x, angles.z)
        }
        val worldTurn = Quaternion(parentTurn).mul(turn)
        val at = parentTurn.transform(Vector3(local)).add(parentAt)
        if (track != null) {
            val body = bodies.getValue(part.name)
            var lowest = Float.POSITIVE_INFINITY
            val v = Vector3()
            for (k in body.indices step 3) lowest = minOf(lowest, worldTurn.transform(v.set(body[k], body[k + 1], body[k + 2])).y + at.y)
            val landed = track.landsAt?.let { time >= it } ?: false
            val rise = if (landed || lowest < GROUND_Y) GROUND_Y - lowest else 0f
            if (body.isNotEmpty() && rise != 0f) {
                at.y += rise
                local.add(Quaternion(parentTurn).conjugate().transform(Vector3(0f, rise, 0f)))
            }
            val (translations, rotations) = out.getValue(part.name)
            translations[3 * i] = clean(local.x); translations[3 * i + 1] = clean(local.y); translations[3 * i + 2] = clean(local.z)
            rotations[4 * i] = clean(turn.x); rotations[4 * i + 1] = clean(turn.y)
            rotations[4 * i + 2] = clean(turn.z); rotations[4 * i + 3] = clean(turn.w)
        }
        for (child in part.children) visit(child, part.pivot, worldTurn, at, time, i, out)
    }

    /** Rounds away float noise, and -0 to 0. */
    private fun clean(f: Float): Float = if (abs(f) < 1e-7f) 0f else f
}

// ---- geometry ----

/** Triangles of one material: three floats per position and normal, two per UV, indexed. */
private class Prim(val material: Int, val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray)

/**
 * The triangles below and above `axis = at`, triangles across it cut along it (attributes interpolated). An empty
 * side is an empty list.
 */
private fun List<Prim>.split(axis: Int, at: Float): Pair<List<Prim>, List<Prim>> {
    val below = ArrayList<Prim>()
    val above = ArrayList<Prim>()
    for (prim in this) {
        val lo = Soup()
        val hi = Soup()
        for (t in prim.indices.indices step 3) {
            val corners = (0..2).map { prim.vertex(prim.indices[t + it]) }
            val side = corners.map { it[axis] - at }
            when {
                side.all { it <= 0f } -> lo.add(corners)
                side.all { it >= 0f } -> hi.add(corners)
                else -> {
                    lo.add(clip(corners, side, keepBelow = true))
                    hi.add(clip(corners, side, keepBelow = false))
                }
            }
        }
        lo.prim(prim.material)?.let(below::add)
        hi.prim(prim.material)?.let(above::add)
    }
    return below to above
}

/** One vertex: position (0..2), normal (3..5), UV (6..7). */
private fun Prim.vertex(i: Int) = floatArrayOf(
    positions[3 * i], positions[3 * i + 1], positions[3 * i + 2],
    normals[3 * i], normals[3 * i + 1], normals[3 * i + 2],
    uvs[2 * i], uvs[2 * i + 1],
)

/** The polygon of a triangle on one side of the plane (signed distances [side]), as a fan of triangles. */
private fun clip(corners: List<FloatArray>, side: List<Float>, keepBelow: Boolean): List<FloatArray> {
    val polygon = ArrayList<FloatArray>()
    for (i in 0..2) {
        val j = (i + 1) % 3
        val di = if (keepBelow) side[i] else -side[i]
        val dj = if (keepBelow) side[j] else -side[j]
        if (di <= 0f) polygon += corners[i]
        if ((di < 0f && dj > 0f) || (di > 0f && dj < 0f)) {
            val t = di / (di - dj)
            val v = FloatArray(8) { corners[i][it] + (corners[j][it] - corners[i][it]) * t }
            val n = sqrt(v[3] * v[3] + v[4] * v[4] + v[5] * v[5])
            if (n > 0f) for (k in 3..5) v[k] /= n
            polygon += v
        }
    }
    return (1 until polygon.size - 1).flatMap { listOf(polygon[0], polygon[it], polygon[it + 1]) }
}

/** Triangles as separate vertices. */
private class Soup {
    private val vertices = ArrayList<FloatArray>()

    fun add(triangles: List<FloatArray>) {
        vertices += triangles
    }

    fun prim(material: Int): Prim? = if (vertices.isEmpty()) null else Prim(
        material,
        FloatArray(vertices.size * 3) { vertices[it / 3][it % 3] },
        FloatArray(vertices.size * 3) { vertices[it / 3][3 + it % 3] },
        FloatArray(vertices.size * 2) { vertices[it / 2][6 + it % 2] },
        IntArray(vertices.size) { it },
    )
}

// ---- GLB ----

private class Glb(val json: ObjectNode, private val bin: ByteBuffer) {
    /** Each node's primitives, by node name: the importer's flat list of mesh nodes without transforms. */
    fun parts(): Map<String, List<Prim>> = json["nodes"].associate { node ->
        check(!node.has("translation") && !node.has("rotation") && !node.has("scale") && !node.has("matrix") && !node.has("children")) {
            "node ${node["name"]} is not a plain imported part"
        }
        val mesh = json["meshes"][node["mesh"].asInt()]
        node["name"].asText() to mesh["primitives"].map { p ->
            val attributes = p["attributes"]
            Prim(
                p["material"].asInt(), floats(attributes["POSITION"].asInt()), floats(attributes["NORMAL"].asInt()),
                floats(attributes["TEXCOORD_0"].asInt()), ints(p["indices"].asInt()),
            )
        }
    }

    private fun view(accessor: JsonNode, componentType: Int): ByteBuffer {
        check(accessor["componentType"].asInt() == componentType && !accessor.has("byteOffset"))
        val view = json["bufferViews"][accessor["bufferView"].asInt()]
        check(!view.has("byteStride"))
        return bin.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(view["byteOffset"].asInt())
            .limit(view["byteOffset"].asInt() + view["byteLength"].asInt()).slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun floats(accessor: Int): FloatArray {
        val buffer = view(json["accessors"][accessor], 5126).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun ints(accessor: Int): IntArray {
        val buffer = view(json["accessors"][accessor], 5125).asIntBuffer()
        return IntArray(buffer.remaining()).also { buffer.get(it) }
    }

    /** The model with [root]'s hierarchy and [clips]; materials, images and samplers are kept as they were. */
    fun write(root: Part, clips: List<Clip>, mapper: ObjectMapper): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = mapper.createObjectNode()
        doc.set<JsonNode>("asset", json["asset"])
        doc.put("scene", 0)
        doc.putArray("scenes").addObject().putArray("nodes").add(0)
        val nodes = doc.putArray("nodes")
        val meshes = doc.putArray("meshes")
        val accessors = doc.putArray("accessors")
        val views = doc.putArray("bufferViews")

        fun view(bytes: ByteArray, target: Int?): Int {
            while (out.size() % 4 != 0) out.write(0)
            val view = views.addObject().put("buffer", 0).put("byteOffset", out.size()).put("byteLength", bytes.size)
            target?.let { view.put("target", it) }
            out.write(bytes)
            return views.size() - 1
        }

        fun floats(values: FloatArray, components: Int, type: String, target: Int?, bounds: Boolean): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putFloat)
            val accessor = accessors.addObject().put("bufferView", view(buffer.array(), target)).put("componentType", 5126)
                .put("count", values.size / components).put("type", type)
            if (bounds) {
                val min = accessor.putArray("min")
                val max = accessor.putArray("max")
                for (c in 0 until components) {
                    val column = values.indices.filter { it % components == c }.map { values[it] }
                    min.add(column.min()); max.add(column.max())
                }
            }
            return accessors.size() - 1
        }

        fun indices(values: IntArray): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putInt)
            accessors.addObject().put("bufferView", view(buffer.array(), 34963)).put("componentType", 5125)
                .put("count", values.size).put("type", "SCALAR")
            return accessors.size() - 1
        }

        val indexOf = HashMap<String, Int>()
        fun add(part: Part, parentPivot: Vec): Int {
            val node = nodes.addObject().put("name", part.name)
            val index = nodes.size() - 1
            indexOf[part.name] = index
            val rest = part.pivot - parentPivot
            if (!rest.isZero()) node.putArray("translation").add(rest.x).add(rest.y).add(rest.z)
            if (part.prims.isNotEmpty()) {
                val mesh = meshes.addObject().put("name", part.name)
                val primitives = mesh.putArray("primitives")
                for (p in part.prims) {
                    val local = FloatArray(p.positions.size) {
                        p.positions[it] - when (it % 3) {
                            0 -> part.pivot.x
                            1 -> part.pivot.y
                            else -> part.pivot.z
                        }
                    }
                    val primitive = primitives.addObject()
                    val attributes = primitive.putObject("attributes")
                    attributes.put("POSITION", floats(local, 3, "VEC3", 34962, true))
                    attributes.put("NORMAL", floats(p.normals, 3, "VEC3", 34962, false))
                    attributes.put("TEXCOORD_0", floats(p.uvs, 2, "VEC2", 34962, false))
                    primitive.put("indices", indices(p.indices)).put("material", p.material).put("mode", 4)
                }
                node.put("mesh", meshes.size() - 1)
            }
            if (part.children.isNotEmpty()) {
                val children = part.children.map { add(it, part.pivot) }
                node.putArray("children").also { list -> children.forEach { list.add(it) } }
            }
            return index
        }
        add(root, Vec(0f, 0f, 0f))

        val animations = doc.putArray("animations")
        for (clip in clips) {
            val animation = animations.addObject().put("name", clip.name)
            val channels = animation.putArray("channels")
            val samplers = animation.putArray("samplers")
            val count = (clip.duration * SAMPLE_RATE).roundToInt() + 1
            val times = FloatArray(count) { minOf(it.toFloat() / SAMPLE_RATE, clip.duration) }
            val input = floats(times, 1, "SCALAR", null, true)
            val poses = Settle(root, clip).sample(times)
            for (track in clip.tracks) {
                val node = checkNotNull(indexOf[track.node]) { "${clip.name}: no node ${track.node}" }
                val (translations, rotations) = poses.getValue(track.node)
                for ((path, values) in listOf("translation" to floats(translations, 3, "VEC3", null, false), "rotation" to floats(rotations, 4, "VEC4", null, false))) {
                    samplers.addObject().put("input", input).put("output", values).put("interpolation", "LINEAR")
                    channels.addObject().put("sampler", samplers.size() - 1).putObject("target").put("node", node).put("path", path)
                }
            }
        }

        for (key in listOf("materials", "samplers", "images", "textures")) json[key]?.let { doc.set<JsonNode>(key, it) }
        while (out.size() % 4 != 0) out.write(0)
        doc.putArray("buffers").addObject().put("byteLength", out.size())

        var text = mapper.writeValueAsBytes(doc)
        if (text.size % 4 != 0) text += ByteArray(4 - text.size % 4) { ' '.code.toByte() }
        val data = out.toByteArray()
        val glb = ByteBuffer.allocate(12 + 8 + text.size + 8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity())
        glb.putInt(text.size).putInt(0x4E4F534A).put(text)
        glb.putInt(data.size).putInt(0x004E4942).put(data)
        return glb.array()
    }

    companion object {
        fun read(bytes: ByteArray, mapper: ObjectMapper): Glb {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            check(buffer.getInt(0) == 0x46546C67 && buffer.getInt(4) == 2) { "not a GLB 2.0 file" }
            val jsonLength = buffer.getInt(12)
            check(buffer.getInt(16) == 0x4E4F534A)
            val json = mapper.readTree(bytes, 20, jsonLength) as ObjectNode
            val binStart = 20 + jsonLength
            check(buffer.getInt(binStart + 4) == 0x004E4942)
            val bin = ByteBuffer.wrap(bytes, binStart + 8, buffer.getInt(binStart)).slice().order(ByteOrder.LITTLE_ENDIAN)
            return Glb(json, bin)
        }
    }
}
