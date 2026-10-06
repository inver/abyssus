package net.nevinsky.abyssus.core.io

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram

class GeometryUtils {
    companion object {
        /** One triangle covering the whole viewport (2D positions, `a_position`); the caller disposes the mesh. GL thread only. */
        @JvmStatic
        fun createFullscreenTriangle(): Mesh =
            Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).also {
                it.setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
            }
    }
}