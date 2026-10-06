/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package com.badlogic.gdx.backends.lwjgl3

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import org.lwjgl.opengl.GL30C
import java.nio.IntBuffer

/** Lives in libGDX's package only because `Lwjgl3GL20/GL30` are package-private; they wrap LWJGL's GL calls and need no GLFW. */
object GdxGlBridge {
    fun gl20(): GL20 = CoreProfileGL20()

    fun gl30(): GL30 = CoreProfileGL30()
}

/**
 * libGDX's framebuffer and mipmap calls use EXTFramebufferObject. The canvas uses a core profile, so those
 * operations must go through their core equivalents; calling an absent EXT function aborts the JVM.
 */
private class CoreProfileGL20 : Lwjgl3GL20() {
    override fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)
    override fun glBindFramebuffer(target: Int, framebuffer: Int) = GL30C.glBindFramebuffer(target, framebuffer)
    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) = GL30C.glBindRenderbuffer(target, renderbuffer)
    override fun glCheckFramebufferStatus(target: Int) = GL30C.glCheckFramebufferStatus(target)
    override fun glGenFramebuffer() = GL30C.glGenFramebuffers()
    override fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) = GL30C.glGenFramebuffers(framebuffers)
    override fun glGenRenderbuffer() = GL30C.glGenRenderbuffers()
    override fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) = GL30C.glGenRenderbuffers(renderbuffers)
    override fun glDeleteFramebuffer(framebuffer: Int) = GL30C.glDeleteFramebuffers(framebuffer)
    override fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) = GL30C.glDeleteFramebuffers(framebuffers)
    override fun glDeleteRenderbuffer(renderbuffer: Int) = GL30C.glDeleteRenderbuffers(renderbuffer)
    override fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) = GL30C.glDeleteRenderbuffers(renderbuffers)
    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) =
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) =
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    override fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) =
        GL30C.glRenderbufferStorage(target, internalformat, width, height)
    override fun glGetFramebufferAttachmentParameteriv(target: Int, attachment: Int, pname: Int, params: IntBuffer) =
        GL30C.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params)
    override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) =
        GL30C.glGetRenderbufferParameteriv(target, pname, params)
    override fun glIsFramebuffer(framebuffer: Int) = GL30C.glIsFramebuffer(framebuffer)
    override fun glIsRenderbuffer(renderbuffer: Int) = GL30C.glIsRenderbuffer(renderbuffer)
}

private class CoreProfileGL30 : Lwjgl3GL30() {
    override fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)
    override fun glBindFramebuffer(target: Int, framebuffer: Int) = GL30C.glBindFramebuffer(target, framebuffer)
    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) = GL30C.glBindRenderbuffer(target, renderbuffer)
    override fun glCheckFramebufferStatus(target: Int) = GL30C.glCheckFramebufferStatus(target)
    override fun glGenFramebuffer() = GL30C.glGenFramebuffers()
    override fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) = GL30C.glGenFramebuffers(framebuffers)
    override fun glGenRenderbuffer() = GL30C.glGenRenderbuffers()
    override fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) = GL30C.glGenRenderbuffers(renderbuffers)
    override fun glDeleteFramebuffer(framebuffer: Int) = GL30C.glDeleteFramebuffers(framebuffer)
    override fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) = GL30C.glDeleteFramebuffers(framebuffers)
    override fun glDeleteRenderbuffer(renderbuffer: Int) = GL30C.glDeleteRenderbuffers(renderbuffer)
    override fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) = GL30C.glDeleteRenderbuffers(renderbuffers)
    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) =
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) =
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    override fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) =
        GL30C.glRenderbufferStorage(target, internalformat, width, height)
    override fun glGetFramebufferAttachmentParameteriv(target: Int, attachment: Int, pname: Int, params: IntBuffer) =
        GL30C.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params)
    override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) =
        GL30C.glGetRenderbufferParameteriv(target, pname, params)
    override fun glIsFramebuffer(framebuffer: Int) = GL30C.glIsFramebuffer(framebuffer)
    override fun glIsRenderbuffer(renderbuffer: Int) = GL30C.glIsRenderbuffer(renderbuffer)
}
