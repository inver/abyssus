/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import kotlin.math.abs
import kotlin.math.sqrt

class FakeRayBackendTest : RayBackendConformanceKit() {
    override val sceneMaterials = true
    override fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider = RayBackendProbe({
        RayCapabilities(devicePresent,devicePresent,devicePresent,4096,512L*1024*1024,maxInstances=1024,sceneOptics=true)
    }, { FakeRayBackend(it,health) })
}

/** Test-only CPU triangle renderer. No software fallback is packaged with the plugin. */
private class FakeRayBackend(override val capabilities: RayCapabilities, private val health: RayDeviceHealth) : RayBackend {
    override val info = RayBackendInfo("fake","in-memory")
    private var closed = false
    private val sessions = linkedMapOf<String,RaySession>()
    override fun openSession(viewId: String, limits: RayLimits): RaySession {
        check(!closed && viewId !in sessions)
        health.checkUsable()
        val driver = FakeRaySession { sessions.remove(viewId) }
        return RayQueuedSession(driver,health).also { sessions[viewId] = it }
    }
    override fun dispose() {
        if (closed) return
        closed = true
        sessions.values.toList().forEach { it.dispose() }
    }
}

private class FakeRaySession(private val onDisposed: () -> Unit) : RaySession {
    private val accumulator=RayFrameAccumulator()
    private var closed = false
    private var completed: RayFrame? = null
    private var meshes: List<RayMesh>? = null
    private var builds = 0L
    override val geometryBuilds: Long get() = builds
    override fun submit(request: RayRequest) {
        check(!closed && completed == null)
        completed = renderReferenceGeometry(request)
    }
    override fun submit(request: RaySceneRequest) {
        check(!closed && completed == null)
        request.scene.unsupportedReason()?.let { throw IllegalArgumentException(it) }
        builds += (dirtyMeshes(meshes, request.scene.meshes)?.size ?: request.scene.meshes.size).toLong()
        meshes = request.scene.meshes
        completed = accumulator.add(request,RaySceneReferenceRenderer().render(request))
    }
    override fun poll(): RayFrame? {
        check(!closed)
        return completed.also { completed = null }
    }
    override fun dispose() {
        if (closed) return
        closed = true
        completed = null
        onDisposed()
    }
}

private data class V(val x: Double, val y: Double, val z: Double) {
    operator fun plus(v: V) = V(x+v.x,y+v.y,z+v.z)
    operator fun minus(v: V) = V(x-v.x,y-v.y,z-v.z)
    operator fun times(n: Double) = V(x*n,y*n,z*n)
    fun dot(v: V) = x*v.x+y*v.y+z*v.z
    fun cross(v: V) = V(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
    fun unit() = this*(1/sqrt(dot(this)))
}
private data class Triangle(val a: V,val b: V,val c: V,val tint: V,val mirror: Boolean) {
    val normal = (b-a).cross(c-a).unit()
    fun distance(origin: V,direction: V): Double? {
        val e1 = b-a; val e2 = c-a
        val p = direction.cross(e2)
        val determinant = e1.dot(p)
        if (abs(determinant) < 1e-9) return null
        val t = origin-a
        val u = t.dot(p)/determinant
        val q = t.cross(e1)
        val v = direction.dot(q)/determinant
        if (u < 0 || v < 0 || u+v > 1) return null
        return e2.dot(q)/determinant
    }
}
private data class Hit(val triangle: Triangle,val distance: Double)

private fun renderReferenceGeometry(request: RayRequest): RayFrame {
    val meshes = request.meshes()
    val triangles = request.instances().flatMap { instance ->
        val values = instance.uniforms()
        val vertices = meshes[instance.mesh].vertices()
        val indices = meshes[instance.mesh].indices()
        fun vertex(index: Int): V {
            val x = vertices[index*3]; val y = vertices[index*3+1]; val z = vertices[index*3+2]
            return V((values[0]*x+values[4]*y+values[8]*z+values[12]).toDouble(),
                (values[1]*x+values[5]*y+values[9]*z+values[13]).toDouble(),
                (values[2]*x+values[6]*y+values[10]*z+values[14]).toDouble())
        }
        (indices.indices step 3).map { i -> Triangle(vertex(indices[i]),vertex(indices[i+1]),vertex(indices[i+2]),
            V(values[16].toDouble(),values[17].toDouble(),values[18].toDouble()),values[19] > 0.5f) }
    }
    fun nearest(origin: V,direction: V,min: Double,max: Double): Hit? = triangles.mapNotNull { t ->
        t.distance(origin,direction)?.takeIf { it >= min && it <= max }?.let { Hit(t,it) }
    }.minByOrNull { it.distance }
    val camera = request.camera.uniforms(request.width,request.height)
    fun vector(offset: Int) = V(camera[offset].toDouble(),camera[offset+1].toDouble(),camera[offset+2].toDouble())
    val origin = vector(0); val forward = vector(4); val right = vector(8); val up = vector(12); val light = vector(16)
    val background = V(0.05,0.1,0.2)
    fun direct(hit: Hit, point: V, normal: V): V {
        val blocked = nearest(point+normal*0.001,light,0.001,10000.0) != null
        val diffuse = if (blocked) 0.0 else maxOf(normal.dot(light),0.0)
        return hit.triangle.tint*(0.1+0.9*diffuse)
    }
    val color = FloatArray(request.width*request.height*4)
    val depth = FloatArray(request.width*request.height) { 1f }
    for (y in 0 until request.height) for (x in 0 until request.width) {
        val direction = (forward+right*((x+0.5)/request.width*2-1)*camera[15].toDouble()*camera[11].toDouble()+
            up*((y+0.5)/request.height*2-1)*camera[11].toDouble()).unit()
        val cosine = direction.dot(forward)
        val hit = nearest(origin,direction,camera[3]/cosine,camera[7]/cosine)
        var value = background
        val pixel = y*request.width+x
        if (hit != null) {
            val normal = hit.triangle.normal*if (hit.triangle.normal.dot(direction) > 0) -1.0 else 1.0
            val point = origin+direction*hit.distance
            val near = camera[3].toDouble(); val far = camera[7].toDouble()
            depth[pixel] = (far/(far-near)-far*near/((far-near)*hit.distance*cosine)).toFloat().coerceIn(0f,1f)
            value = direct(hit,point,normal)
            if (hit.triangle.mirror) {
                val reflected = direction-normal*(2*direction.dot(normal))
                val reflectedOrigin = point+normal*0.001
                val secondary = nearest(reflectedOrigin,reflected,0.001,10000.0)
                value = if (secondary == null) background else {
                    val n = secondary.triangle.normal*if (secondary.triangle.normal.dot(reflected) > 0) -1.0 else 1.0
                    direct(secondary,reflectedOrigin+reflected*secondary.distance,n)
                }
            }
        }
        color[pixel*4] = value.x.toFloat(); color[pixel*4+1] = value.y.toFloat(); color[pixel*4+2] = value.z.toFloat(); color[pixel*4+3] = 1f
    }
    return RayFrame(request.key,request.width,request.height,color,depth)
}
