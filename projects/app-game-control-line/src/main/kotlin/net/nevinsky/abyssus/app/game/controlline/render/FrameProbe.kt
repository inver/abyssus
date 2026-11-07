/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

/**
 * The named parts of a frame that the performance workload times (design decision D4). [FRAME], [FLIGHT], [FIELD] and
 * [UI] are CPU spans of `ControlLineGame.render` (FIELD holds the passes); the others are [FieldRenderer]'s passes,
 * which follow one another and never nest, so a GPU timer query can wrap each.
 */
enum class FrameSpan(val key: String, val pass: Boolean) {
    FRAME("frame", false),
    FLIGHT("flight", false),
    FIELD("field", false),
    UI("ui", false),
    SKY("sky", true),
    SHADOW("shadow", true),
    TERRAIN("terrain", true),
    MODELS("models", true),
    LINES("lines", true),
    POST("post", true),
}

/**
 * Where the game marks the start and end of each [FrameSpan]. The shipped game uses [NONE], which does nothing, so the
 * game carries no timing work; the performance workload passes one that records CPU time, GPU timer queries or
 * counters. Main thread only.
 */
interface FrameProbe {
    fun begin(span: FrameSpan) {}
    fun end(span: FrameSpan) {}

    /** The probe of the shipped game: nothing is measured. */
    object NONE : FrameProbe
}

/** Runs [block] between [FrameProbe.begin] and [FrameProbe.end] of [span]. */
inline fun <T> FrameProbe.span(span: FrameSpan, block: () -> T): T {
    begin(span)
    try {
        return block()
    } finally {
        end(span)
    }
}
