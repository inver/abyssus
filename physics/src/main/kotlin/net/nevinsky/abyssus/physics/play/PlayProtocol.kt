/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.play

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/** The play protocol version this build speaks; `play.json` and `hello` carry it. */
const val PLAY_PROTOCOL = 1

/** The largest frame accepted, so a corrupt length cannot exhaust memory. */
private const val MAX_FRAME = 64 * 1024 * 1024

/**
 * A frame of the play protocol between Abyssus Physics (the IDE) and a play host. Commands and events are UTF-8 JSON;
 * [Poses] and [Lines] are binary. Every frame on the wire is `int length`, `byte type`, then `length - 1` payload bytes,
 * big-endian.
 */
sealed interface PlayFrame {
    // host -> IDE

    /** The first frame a play host sends after connecting. */
    data class Hello(val protocol: Int, val token: String, val module: String) : PlayFrame

    /** The scene is loaded and the host waits for [Play]. */
    data object Ready : PlayFrame

    /** Entity poses after simulation step [frame], at [simTime] seconds. */
    data class Poses(val frame: Long, val simTime: Double, val poses: List<EntityPose>) : PlayFrame

    /** Extra debug segments from game systems. */
    data class Lines(val lines: List<DebugLine>) : PlayFrame

    /** Values a game shows while playing, such as a rope's tension. */
    data class Telemetry(val values: Map<String, String>) : PlayFrame

    data class Error(val message: String) : PlayFrame

    // IDE -> host

    /** The scene as the editor holds it, its project folder and the entity selected when Play was pressed (`-1`). */
    data class Load(val sceneText: String, val projectDir: String, val selection: Int) : PlayFrame

    data object Play : PlayFrame
    data object Pause : PlayFrame
    data object Step : PlayFrame
    data object Stop : PlayFrame

    /** Keyboard or mouse input while the Scene view has focus. */
    data class Input(val event: PlayInput) : PlayFrame

    /** The IDE is done; the host exits. */
    data object Bye : PlayFrame
}

/** One entity's pose: its scene id, position and rotation (a quaternion). */
data class EntityPose(val id: Int, val x: Float, val y: Float, val z: Float, val qx: Float, val qy: Float, val qz: Float, val qw: Float)

/** A segment from ([x1], [y1], [z1]) to ([x2], [y2], [z2]) in [color] (RGBA8888). */
data class DebugLine(val x1: Float, val y1: Float, val z1: Float, val x2: Float, val y2: Float, val z2: Float, val color: Int)

/** What happened: a key ([key] is its name, as `W`) or a mouse button ([button]) at view pixel ([x], [y]). */
data class PlayInput(val kind: Kind, val key: String = "", val button: Int = 0, val x: Int = 0, val y: Int = 0) {
    enum class Kind { KEY_DOWN, KEY_UP, BUTTON_DOWN, BUTTON_UP, MOUSE_MOVE }
}

/** A frame that cannot be read: an unknown type, a bad length or a malformed payload. */
class PlayProtocolException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Reads and writes [PlayFrame]s. Not thread-safe: use one instance per direction. */
class PlayProtocol(private val json: ObjectMapper = ObjectMapper()) {
    fun write(out: DataOutputStream, frame: PlayFrame) {
        val payload = ByteArrayOutputStream()
        val data = DataOutputStream(payload)
        val type = when (frame) {
            is PlayFrame.Hello -> 1.also { data.write(json(frame.protocol, frame.token, frame.module)) }
            PlayFrame.Ready -> 2
            is PlayFrame.Poses -> 3.also {
                data.writeLong(frame.frame)
                data.writeDouble(frame.simTime)
                data.writeInt(frame.poses.size)
                for (p in frame.poses) {
                    data.writeInt(p.id)
                    for (f in floatArrayOf(p.x, p.y, p.z, p.qx, p.qy, p.qz, p.qw)) data.writeFloat(f)
                }
            }
            is PlayFrame.Lines -> 4.also {
                data.writeInt(frame.lines.size)
                for (l in frame.lines) {
                    for (f in floatArrayOf(l.x1, l.y1, l.z1, l.x2, l.y2, l.z2)) data.writeFloat(f)
                    data.writeInt(l.color)
                }
            }
            is PlayFrame.Telemetry -> 5.also {
                data.write(json.writeValueAsBytes(json.createObjectNode().also { o -> frame.values.forEach { (k, v) -> o.put(k, v) } }))
            }
            is PlayFrame.Error -> 6.also { data.write(json.writeValueAsBytes(json.createObjectNode().put("message", frame.message))) }
            is PlayFrame.Load -> 20.also {
                data.write(json.writeValueAsBytes(json.createObjectNode()
                    .put("sceneText", frame.sceneText).put("projectDir", frame.projectDir).put("selection", frame.selection)))
            }
            PlayFrame.Play -> 21
            PlayFrame.Pause -> 22
            PlayFrame.Step -> 23
            PlayFrame.Stop -> 24
            is PlayFrame.Input -> 25.also {
                val e = frame.event
                data.write(json.writeValueAsBytes(json.createObjectNode().put("kind", e.kind.name).put("key", e.key)
                    .put("button", e.button).put("x", e.x).put("y", e.y)))
            }
            PlayFrame.Bye -> 26
        }
        val bytes = payload.toByteArray()
        out.writeInt(bytes.size + 1)
        out.writeByte(type)
        out.write(bytes)
        out.flush()
    }

    /** The next frame; throws [EOFException] when the stream ends between frames. */
    fun read(input: DataInputStream): PlayFrame {
        val length = input.readInt()
        if (length < 1 || length > MAX_FRAME) throw PlayProtocolException("bad frame length $length")
        val type = input.readUnsignedByte()
        val payload = ByteArray(length - 1)
        input.readFully(payload)
        return try {
            decode(type, payload)
        } catch (e: PlayProtocolException) {
            throw e
        } catch (e: Exception) {
            throw PlayProtocolException("malformed frame of type $type: ${e.message}", e)
        }
    }

    private fun decode(type: Int, payload: ByteArray): PlayFrame {
        val data = DataInputStream(payload.inputStream())
        return when (type) {
            1 -> obj(payload).let { PlayFrame.Hello(it.get("protocol").asInt(), it.get("token").asText(), it.get("module").asText()) }
            2 -> PlayFrame.Ready
            3 -> {
                val frame = data.readLong()
                val time = data.readDouble()
                val count = data.readInt()
                if (count < 0 || count.toLong() * 32 > payload.size) throw PlayProtocolException("bad pose count $count")
                PlayFrame.Poses(frame, time, List(count) {
                    EntityPose(data.readInt(), data.readFloat(), data.readFloat(), data.readFloat(),
                        data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat())
                })
            }
            4 -> {
                val count = data.readInt()
                if (count < 0 || count.toLong() * 28 > payload.size) throw PlayProtocolException("bad line count $count")
                PlayFrame.Lines(List(count) {
                    DebugLine(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readInt())
                })
            }
            5 -> PlayFrame.Telemetry(obj(payload).properties().associate { (k, v) -> k to v.asText() })
            6 -> PlayFrame.Error(obj(payload).get("message").asText())
            20 -> obj(payload).let { PlayFrame.Load(it.get("sceneText").asText(), it.get("projectDir").asText(), it.get("selection").asInt()) }
            21 -> PlayFrame.Play
            22 -> PlayFrame.Pause
            23 -> PlayFrame.Step
            24 -> PlayFrame.Stop
            25 -> obj(payload).let {
                PlayFrame.Input(PlayInput(PlayInput.Kind.valueOf(it.get("kind").asText()), it.get("key")?.asText().orEmpty(),
                    it.get("button")?.asInt() ?: 0, it.get("x")?.asInt() ?: 0, it.get("y")?.asInt() ?: 0))
            }
            26 -> PlayFrame.Bye
            else -> throw PlayProtocolException("unknown frame type $type")
        }
    }

    /**
     * The IDE's side of the handshake: reads the host's first frame and returns its [PlayFrame.Hello] when it carries
     * [token] and [PLAY_PROTOCOL]. Returns null after another token (the caller closes the socket without a reply) or
     * after another protocol, which first gets an [PlayFrame.Error] the host reports before exiting.
     */
    fun accept(input: DataInputStream, out: DataOutputStream, token: String): PlayFrame.Hello? {
        val hello = read(input) as? PlayFrame.Hello ?: return null
        if (hello.token != token) return null
        if (hello.protocol != PLAY_PROTOCOL) {
            write(out, PlayFrame.Error("play protocol ${hello.protocol} is not supported; Abyssus Physics speaks $PLAY_PROTOCOL"))
            return null
        }
        return hello
    }

    private fun json(protocol: Int, token: String, module: String): ByteArray =
        json.writeValueAsBytes(json.createObjectNode().put("protocol", protocol).put("token", token).put("module", module))

    private fun obj(payload: ByteArray): ObjectNode =
        json.readTree(payload) as? ObjectNode ?: throw PlayProtocolException("payload is not a JSON object")
}
