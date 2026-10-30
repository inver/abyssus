/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the launcher remembers between starts: the version it is [launching] (cleared once that start counts as clean),
 * the [unclean] starts in a row per version and the versions marked [bad].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StateData(
    val launching: String? = null,
    val unclean: Map<String, Int> = emptyMap(),
    val bad: Set<String> = emptySet(),
)

/** `state.json` in the update folder. A missing or unreadable file reads as the defaults. */
class UpdateState(private val file: Path) {
    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    fun load(): StateData = try {
        if (Files.isRegularFile(file)) mapper.readValue<StateData>(Files.readAllBytes(file)) else StateData()
    } catch (e: Exception) {
        StateData()
    }

    fun save(data: StateData) = writeAtomically(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data))

    /** Loads, applies [change] and saves; returns the saved data. */
    fun update(change: (StateData) -> StateData): StateData = change(load()).also { save(it) }
}
