/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

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

/** Stable wire discriminants, shared by both protocol directions. */
internal enum class FrameType(val id: Int) {
    HELLO(1), READY(2), POSES(3), LINES(4), TELEMETRY(5), ERROR(6),
    LOAD(20), PLAY(21), PAUSE(22), STEP(23), STOP(24), INPUT(25), BYE(26),
}

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
            is PlayFrame.Hello -> FrameType.HELLO.also { data.write(json(frame.protocol, frame.token, frame.module)) }
            PlayFrame.Ready -> FrameType.READY
            is PlayFrame.Poses -> FrameType.POSES.also {
                data.writeLong(frame.frame)
                data.writeDouble(frame.simTime)
                data.writeInt(frame.poses.size)
                for (p in frame.poses) {
                    data.writeInt(p.id)
                    for (f in floatArrayOf(p.x, p.y, p.z, p.qx, p.qy, p.qz, p.qw)) data.writeFloat(f)
                }
            }
            is PlayFrame.Lines -> FrameType.LINES.also {
                data.writeInt(frame.lines.size)
                for (l in frame.lines) {
                    for (f in floatArrayOf(l.x1, l.y1, l.z1, l.x2, l.y2, l.z2)) data.writeFloat(f)
                    data.writeInt(l.color)
                }
            }
            is PlayFrame.Telemetry -> FrameType.TELEMETRY.also {
                data.write(json.writeValueAsBytes(json.createObjectNode().also { o -> frame.values.forEach { (k, v) -> o.put(k, v) } }))
            }
            is PlayFrame.Error -> FrameType.ERROR.also { data.write(json.writeValueAsBytes(json.createObjectNode().put("message", frame.message))) }
            is PlayFrame.Load -> FrameType.LOAD.also {
                data.write(json.writeValueAsBytes(json.createObjectNode()
                    .put("sceneText", frame.sceneText).put("projectDir", frame.projectDir).put("selection", frame.selection)))
            }
            PlayFrame.Play -> FrameType.PLAY
            PlayFrame.Pause -> FrameType.PAUSE
            PlayFrame.Step -> FrameType.STEP
            PlayFrame.Stop -> FrameType.STOP
            is PlayFrame.Input -> FrameType.INPUT.also {
                val e = frame.event
                data.write(json.writeValueAsBytes(json.createObjectNode().put("kind", e.kind.name).put("key", e.key)
                    .put("button", e.button).put("x", e.x).put("y", e.y)))
            }
            PlayFrame.Bye -> FrameType.BYE
        }
        val bytes = payload.toByteArray()
        out.writeInt(bytes.size + 1)
        out.writeByte(type.id)
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
        val frameType = FrameType.entries.firstOrNull { it.id == type } ?: throw PlayProtocolException("unknown frame type $type")
        return when (frameType) {
            FrameType.HELLO -> obj(payload).let { PlayFrame.Hello(it.get("protocol").asInt(), it.get("token").asText(), it.get("module").asText()) }
            FrameType.READY -> PlayFrame.Ready
            FrameType.POSES -> {
                val frame = data.readLong()
                val time = data.readDouble()
                val count = data.readInt()
                if (count < 0 || count.toLong() * 32 > payload.size) throw PlayProtocolException("bad pose count $count")
                PlayFrame.Poses(frame, time, List(count) {
                    EntityPose(data.readInt(), data.readFloat(), data.readFloat(), data.readFloat(),
                        data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat())
                })
            }
            FrameType.LINES -> {
                val count = data.readInt()
                if (count < 0 || count.toLong() * 28 > payload.size) throw PlayProtocolException("bad line count $count")
                PlayFrame.Lines(List(count) {
                    DebugLine(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readInt())
                })
            }
            FrameType.TELEMETRY -> PlayFrame.Telemetry(obj(payload).properties().associate { (k, v) -> k to v.asText() })
            FrameType.ERROR -> PlayFrame.Error(obj(payload).get("message").asText())
            FrameType.LOAD -> obj(payload).let { PlayFrame.Load(it.get("sceneText").asText(), it.get("projectDir").asText(), it.get("selection").asInt()) }
            FrameType.PLAY -> PlayFrame.Play
            FrameType.PAUSE -> PlayFrame.Pause
            FrameType.STEP -> PlayFrame.Step
            FrameType.STOP -> PlayFrame.Stop
            FrameType.INPUT -> obj(payload).let {
                PlayFrame.Input(PlayInput(PlayInput.Kind.valueOf(it.get("kind").asText()), it.get("key")?.asText().orEmpty(),
                    it.get("button")?.asInt() ?: 0, it.get("x")?.asInt() ?: 0, it.get("y")?.asInt() ?: 0))
            }
            FrameType.BYE -> PlayFrame.Bye
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
