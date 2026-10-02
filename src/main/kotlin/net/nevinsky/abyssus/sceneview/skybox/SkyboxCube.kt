package net.nevinsky.abyssus.sceneview.skybox

import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

class SkyboxCube(prepared: PreparedSkybox) : Disposable {
    private val cubemap = prepared.faces.let { Cubemap(it[0], it[1], it[2], it[3], it[4], it[5]) }
    private val mesh = Mesh(true, 8, 36, VertexAttribute.Position()).also {
        it.setVertices(
            floatArrayOf(
                -1f,
                -1f,
                -1f,
                1f,
                -1f,
                -1f,
                1f,
                1f,
                -1f,
                -1f,
                1f,
                -1f,
                -1f,
                -1f,
                1f,
                1f,
                -1f,
                1f,
                1f,
                1f,
                1f,
                -1f,
                1f,
                1f
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

    fun draw(program: ShaderProgram) {
        cubemap.bind(0)
        program.setUniformi("u_cubemap", 0)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        cubemap.dispose()
    }
}