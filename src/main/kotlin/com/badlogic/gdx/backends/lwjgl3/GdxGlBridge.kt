package com.badlogic.gdx.backends.lwjgl3

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30

/** Lives in libGDX's package only because `Lwjgl3GL20/GL30` are package-private; they wrap LWJGL's GL calls and need no GLFW. */
object GdxGlBridge {
    fun gl20(): GL20 = Lwjgl3GL20()

    fun gl30(): GL30 = Lwjgl3GL30()
}
