/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.core.assets.displayMessage

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import net.nevinsky.abyssus.lib.physics.play.PlayFrame
import net.nevinsky.abyssus.lib.physics.play.PlayInput
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.plugin.sceneview.SceneSimulation
import net.nevinsky.abyssus.plugin.sceneview.SceneSimulationProvider
import net.nevinsky.abyssus.plugin.sceneview.SimulationInput
import net.nevinsky.abyssus.plugin.sceneview.SimulationListener
import net.nevinsky.abyssus.plugin.sceneview.SimulationRequest

/** This plugin's id, to find its bundled `play-host/` folder. */
private const val PLUGIN_ID = "net.nevinsky.abyssus.lib.physics"

/** Play in the Scene view through a play process (see [PlayLaunch]); poses come back over the play protocol. */
class PhysicsSimulationProvider : SceneSimulationProvider {
    override fun start(request: SimulationRequest, listener: SimulationListener): SceneSimulation {
        val simulation = PhysicsSimulation(request.project, listener)
        val playHost = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.pluginPath?.resolve("play-host")?.toFile()
        ApplicationManager.getApplication().executeOnPooledThread {
            simulation.launch(PlayLaunch.choose(request.projectDir, playHost), request)
        }
        return simulation
    }
}

/**
 * One Play of one Scene view. It starts the play process off the EDT, loads the scene, then plays. The first failure
 * shows one notification with the process's last lines and tells the view; [stop] ends the process.
 */
internal class PhysicsSimulation(private val project: Project, private val listener: SimulationListener) : SceneSimulation {
    @Volatile
    private var client: PlayClient? = null

    @Volatile
    private var stopped = false

    fun launch(launch: PlayLaunch, request: SimulationRequest) {
        val plan = when (launch) {
            is PlayLaunch.Refused -> return failToStart(launch.message, "")
            is PlayLaunch.Plan -> launch
        }
        val started = try {
            PlayProcessLauncher().launch(plan, request.projectDir)
        } catch (e: PlayStartException) {
            return failToStart(e.message.orEmpty(), e.output)
        } catch (e: Exception) {
            return failToStart(e.displayMessage(), "")
        }
        if (stopped) {
            started.stop()
            return
        }
        client = started
        started.onReady = {
            started.send(PlayFrame.Play)
            listener.started()
        }
        started.onFailed = { message ->
            if (!stopped) {
                notify(AbyssusPhysicsBundle.message("playEnded"), message, started.tail.last())
                listener.failed(message)
            }
        }
        started.start()
        started.send(PlayFrame.Load(request.sceneText, request.projectDir.absolutePath, request.selection?.toIntOrNull() ?: -1))
    }

    private fun failToStart(message: String, output: String) {
        if (stopped) return
        notify(AbyssusPhysicsBundle.message("playCouldNotStart"), message, output)
        listener.failed(message)
    }

    private fun notify(title: String, message: String, output: String) {
        val text = if (output.isBlank()) StringUtil.escapeXmlEntities(message)
        else AbyssusPhysicsBundle.message("playEndedDetail", StringUtil.escapeXmlEntities(message), StringUtil.escapeXmlEntities(output))
        NotificationGroupManager.getInstance().getNotificationGroup("Abyssus Physics")
            .createNotification(title, text, NotificationType.ERROR).notify(project)
    }

    private fun send(frame: PlayFrame) {
        val c = client ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                c.send(frame)
            } catch (_: Exception) {
                // a dead connection is reported by the client's reader
            }
        }
    }

    override fun pause() = send(PlayFrame.Pause)
    override fun resume() = send(PlayFrame.Play)
    override fun step() = send(PlayFrame.Step)

    override fun stop() {
        stopped = true
        val c = client ?: return
        ApplicationManager.getApplication().executeOnPooledThread { c.stop() }
    }

    override fun input(event: SimulationInput) =
        send(PlayFrame.Input(PlayInput(PlayInput.Kind.valueOf(event.kind.name), event.key, event.button, event.x, event.y)))

    override fun poses(): Map<String, Pose>? = client?.poses()
}
