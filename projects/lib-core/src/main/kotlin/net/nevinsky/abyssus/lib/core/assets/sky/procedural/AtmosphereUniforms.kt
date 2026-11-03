package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3

/**
 * Sets the atmosphere uniforms the sky shaders share, for the sun toward [sun]: `u_sunDir`, the camera height and every
 * [AtmosphereParams] value. The program must be bound. One place for the list, so the sky drawn and the sky that lights
 * the scene cannot drift apart.
 */
fun AtmosphereParams.bindTo(program: ShaderProgram, sun: Vector3) {
    program.setUniformf("u_sunDir", sun.x, sun.y, sun.z)
    program.setUniformf("u_cameraHeight", SKY_CAMERA_HEIGHT)
    program.setUniformf("u_planetRadius", planetRadius)
    program.setUniformf("u_atmosphereRadius", atmosphereRadius)
    program.setUniformf("u_betaRayleigh", betaRayleigh[0], betaRayleigh[1], betaRayleigh[2])
    program.setUniformf("u_betaMie", betaMie)
    program.setUniformf("u_heightRayleigh", heightRayleigh)
    program.setUniformf("u_heightMie", heightMie)
    program.setUniformf("u_mieG", mieG)
    program.setUniformf("u_sunIntensity", sunIntensity)
}
