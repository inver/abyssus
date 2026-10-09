package net.nevinsky.abyssus.plugin.facts

import net.nevinsky.abyssus.lib.core.editor.ray.RayModeSnapshot

/** Read-only facts about the most recently opened view; no platform types or mutation methods. */
interface SceneFacts<out C> {
    fun content(scenePath: String): C?
    fun selection(scenePath: String): String?
    fun rayMode(scenePath: String): RayModeSnapshot?
}
