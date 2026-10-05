package net.nevinsky.abyssus.core.assets.sky.cube

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.assets.sky.Sky

/** A six-face skybox on a cube mesh, drawn with [program] (`skybox.vert` / `skybox.frag`), which it owns. */
class SkyboxCube(prepared: PreparedSkybox, private val program: ShaderProgram) : Sky {
    private val viewProj = Matrix4()
    private val cubemap = prepared.faces.let { Cubemap(it[0], it[1], it[2], it[3], it[4], it[5]) }
    private val mesh = Mesh(true, 8, 36, VertexAttribute.Position()).also {
        it.setVertices(
            floatArrayOf(
                -1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f,
                -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f,
            )
        )
        it.setIndices(
            shortArrayOf(
                0, 1, 2, 2, 3, 0, 4, 6, 5, 6, 4, 7, 0, 3, 7, 7, 4, 0,
                1, 5, 6, 6, 2, 1, 3, 2, 6, 6, 7, 3, 0, 4, 5, 5, 1, 0,
            )
        )
    }

    init {
        prepared.dispose()
    }

    override fun draw(camera: Camera, sun: Vector3) {
        rotationOnlyViewProj(camera, viewProj)
        program.bind()
        program.setUniformMatrix("u_viewProj", viewProj)
        cubemap.bind(0)
        program.setUniformi("u_cubemap", 0)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        cubemap.dispose()
        program.dispose()
    }
}