/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

import com.badlogic.ashley.core.EntitySystem
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.PHYSICS_COMPONENTS
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Poses are sent at most this often (ns): 60 times a second. */
private const val POSE_INTERVAL = 1_000_000_000L / 60

/** The loop runs at 120 Hz while playing (ns). */
private const val TICK = 1_000_000_000L / 120

/** A frame never advances physics by more than this (s), so a stalled loop does not jump. */
private const val MAX_FRAME_SECONDS = 0.25f

/**
 * The play process's loop: takes commands from the IDE over [input], runs the scene's physics and [module]'s systems
 * at 120 Hz while playing, and sends poses (at most 60 a second), telemetry and debug lines over [output].
 *
 * A reader thread queues the IDE's frames; everything else, the world included, runs on the thread that calls [run].
 * [run] returns when the IDE says `bye` or the connection ends.
 */
class PlayHost(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val module: PlayModule,
    private val log: Logger,
    private val natives: JoltNatives = JoltNatives(),
    private val protocol: PlayProtocol = PlayProtocol(),
    private val clock: () -> Long = System::nanoTime,
) {
    private val queue = LinkedBlockingQueue<Any>()
    private val closed = Any()

    private class Session(
        val engine: SceneEngine,
        val world: PhysicsWorld,
        val systems: List<EntitySystem>,
        val assets: AssetStorage,
    )

    private var session: Session? = null
    private var playing = false
    private var frame = 0L
    private var simTime = 0.0
    private var last = 0L
    private var lastSent = 0L

    /** Says hello, then serves the IDE until it says `bye` or disconnects. Returns the process exit code. */
    fun run(token: String): Int {
        send(PlayFrame.Hello(PLAY_PROTOCOL, token, module.javaClass.name))
        val reader = Thread({
            try {
                while (true) {
                    val f = protocol.read(input)
                    queue.put(f)
                    if (f == PlayFrame.Bye) break
                }
            } catch (_: IOException) {
                queue.put(closed)
            }
        }, "play-host-reader").apply { isDaemon = true; start() }
        try {
            while (true) {
                val next =
                    if (playing) queue.poll(maxOf(0L, last + TICK - clock()), TimeUnit.NANOSECONDS) else queue.take()
                when (next) {
                    null -> Unit
                    closed, PlayFrame.Bye -> return 0
                    is PlayFrame -> handle(next)
                }
                if (playing) tick()
            }
        } finally {
            endSession()
            reader.interrupt()
        }
    }

    /** Applies one command. A scene that cannot be loaded is reported with an `error` frame. */
    private fun handle(command: PlayFrame) {
        when (command) {
            is PlayFrame.Load -> {
                endSession()
                session = try {
                    load(command)
                } catch (e: Exception) {
                    log.warn("Could not load the scene: ${e.message}", e)
                    send(PlayFrame.Error("The scene could not be loaded: ${e.message}"))
                    null
                }
                if (session != null) {
                    send(PlayFrame.Ready)
                    sendPoses()
                }
            }

            PlayFrame.Play -> if (session != null && !playing) {
                playing = true
                last = clock()
            }

            PlayFrame.Pause -> playing = false
            PlayFrame.Step -> session?.let {
                if (!playing) {
                    it.world.step()
                    frame++
                    simTime += PHYSICS_STEP
                    sendPoses()
                }
            }

            PlayFrame.Stop -> endSession()
            is PlayFrame.Input -> module.input(command.event)
            else -> log.warn("Unexpected frame from the IDE: $command")
        }
    }

    private fun tick() {
        val s = session ?: return
        val now = clock()
        if (now - last < TICK) return
        val seconds = ((now - last) / 1e9f).coerceAtMost(MAX_FRAME_SECONDS)
        last = now
        for (system in s.systems) if (system.checkProcessing()) system.update(seconds)
        val steps = s.world.advance(seconds)
        frame += steps
        simTime += steps * PHYSICS_STEP.toDouble()
        if (now - lastSent >= POSE_INTERVAL) sendPoses()
    }

    private fun load(command: PlayFrame.Load): Session {
        val projectDir = Path.of(command.projectDir)
        val json = JsonProcessor(log)
        val files = FileLoader(projectDir.toFile())
        val registry = ComponentRegistry().also {
            it.registerAll(PHYSICS_COMPONENTS)
            it.registerAll(module.components())
        }
        // the scene loader only needs the storage to exist: nothing is drawn here, so no asset loader is registered
        val storage = AssetStorage(log)
        val loaded = try {
            RuntimeSceneLoader(SceneLoader(json, files), EcsLoader(json, storage, registry), log)
                .loadFromText(command.sceneText)
                ?: throw IllegalArgumentException("the scene text is not a supported Abyssus scene")
        } catch (e: Exception) {
            storage.dispose()
            throw e
        }
        val world = try {
            PhysicsWorld(loaded.engine, PhysicsAssets(projectDir.toFile(), log), log, natives)
        } catch (e: Exception) {
            storage.dispose()
            throw e
        }
        return try {
            val selection = if (command.selection == NO_ENTITY) null else loaded.engine.ids[command.selection]
            val systems = module.systems(world, loaded.engine, selection)
            for (system in systems) loaded.engine.addSystem(system)
            frame = 0
            simTime = 0.0
            Session(loaded.engine, world, systems, storage)
        } catch (e: Exception) {
            world.close()
            storage.dispose()
            throw e
        }
    }

    private fun endSession() {
        playing = false
        session?.let { s ->
            for (system in s.systems) s.engine.removeSystem(system)
            s.world.close()
            s.assets.dispose()
        }
        session = null
    }

    private fun sendPoses() {
        val s = session ?: return
        lastSent = clock()
        val poses = s.engine.ids.ids.sorted().mapNotNull { id ->
            val p = s.engine.ids[id]?.getComponent(PositionComponent::class.java) ?: return@mapNotNull null
            EntityPose(
                id,
                p.localPosition.x,
                p.localPosition.y,
                p.localPosition.z,
                p.localRotation.x,
                p.localRotation.y,
                p.localRotation.z,
                p.localRotation.w
            )
        }
        send(PlayFrame.Poses(frame, simTime, poses))
        module.lines(s.world).takeIf { it.isNotEmpty() }?.let { send(PlayFrame.Lines(it)) }
        module.telemetry(s.world).takeIf { it.isNotEmpty() }?.let { send(PlayFrame.Telemetry(it)) }
    }

    private fun send(frame: PlayFrame) = synchronized(output) { protocol.write(output, frame) }
}
