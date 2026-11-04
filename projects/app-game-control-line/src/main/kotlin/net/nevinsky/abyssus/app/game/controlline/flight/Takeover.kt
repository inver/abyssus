/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

/**
 * A span of an instructed flight in which the instructor held at least half the handle: from [start] to [end] flight
 * seconds.
 */
data class Takeover(val start: Float, val end: Float)
