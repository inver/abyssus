/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20

/** Checks the currently bound framebuffer in the current GL context. */
fun checkFramebufferComplete(label: String) {
    val status = Gdx.gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)
    check(status == GL20.GL_FRAMEBUFFER_COMPLETE) { "$label is incomplete (status 0x${status.toString(16)})" }
}
