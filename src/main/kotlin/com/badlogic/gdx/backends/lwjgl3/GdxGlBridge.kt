/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package com.badlogic.gdx.backends.lwjgl3

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import org.lwjgl.opengl.GL30C

/** Lives in libGDX's package only because `Lwjgl3GL20/GL30` are package-private; they wrap LWJGL's GL calls and need no GLFW. */
object GdxGlBridge {
    fun gl20(): GL20 = CoreProfileGL20()

    fun gl30(): GL30 = CoreProfileGL30()
}

/**
 * libGDX's `glGenerateMipmap` goes through `EXTFramebufferObject`, which a core profile context does not have (the
 * JVM aborts); the core `glGenerateMipmap` does the same job.
 */
private class CoreProfileGL20 : Lwjgl3GL20() {
    override fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)
}

private class CoreProfileGL30 : Lwjgl3GL30() {
    override fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)
}
