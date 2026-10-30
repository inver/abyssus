/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import java.util.Collections
import kotlin.math.sqrt

internal fun <T> rayList(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())

data class RayVec3(val x: Float, val y: Float, val z: Float) {
    init { require(x.isFinite() && y.isFinite() && z.isFinite()) }
    operator fun plus(v: RayVec3) = RayVec3(x+v.x,y+v.y,z+v.z)
    operator fun minus(v: RayVec3) = RayVec3(x-v.x,y-v.y,z-v.z)
    operator fun times(s: Float) = RayVec3(x*s,y*s,z*s)
    fun dot(v: RayVec3) = x*v.x+y*v.y+z*v.z
    fun cross(v: RayVec3) = RayVec3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
    fun unit(): RayVec3 { val length=sqrt(dot(this)); return if(length>1e-8f) this*(1f/length) else RayVec3(0f,0f,0f) }
}
data class RayColor(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    init { require(listOf(r,g,b,a).all { it.isFinite() }) }
    operator fun times(s: Float) = RayColor(r*s,g*s,b*s,a)
    operator fun times(c: RayColor) = RayColor(r*c.r,g*c.g,b*c.b,a*c.a)
    operator fun plus(c: RayColor) = RayColor(r+c.r,g+c.g,b+c.b,a)
}
enum class RayWrap { REPEAT, CLAMP_TO_EDGE }
enum class RayFilter { LINEAR, NEAREST }
/**
 * RGBA in raster upload row order, with no implicit V flip or color-space conversion. Built from floats (linear, possibly
 * HDR) or from 8-bit RGBA bytes, which take a quarter of the memory in the JVM and in the native payload and are what
 * ordinary image textures should use.
 */
class RayTexture private constructor(val id: String, val width: Int, val height: Int, private val floats: FloatArray?, private val bytes: ByteArray?,
    val wrapU: RayWrap, val wrapV: RayWrap, val filter: RayFilter) {
    constructor(id: String, width: Int, height: Int, rgba: FloatArray, wrapU: RayWrap = RayWrap.REPEAT, wrapV: RayWrap = RayWrap.REPEAT,
        filter: RayFilter = RayFilter.LINEAR) : this(id, width, height, rgba.copyOf(), null, wrapU, wrapV, filter)
    constructor(id: String, width: Int, height: Int, rgba8: ByteArray, wrapU: RayWrap = RayWrap.REPEAT, wrapV: RayWrap = RayWrap.REPEAT,
        filter: RayFilter = RayFilter.LINEAR) : this(id, width, height, null, rgba8.copyOf(), wrapU, wrapV, filter)
    init {
        require(width>0 && height>0)
        val size=width.toLong()*height*4
        require(if(floats!=null) floats.size.toLong()==size && floats.all { it.isFinite() } else bytes!!.size.toLong()==size)
    }
    /** True for a texture built from bytes, which the native payload also stores as bytes. */
    val isBytes get()=bytes!=null
    /** Linear floats; a byte texture is converted (a copy of full size, so prefer [rgba8] for those). */
    fun rgba(): FloatArray = floats?.copyOf() ?: FloatArray(bytes!!.size) { (bytes[it].toInt() and 255)/255f }
    /** The 8-bit RGBA bytes of a byte texture, else null. */
    fun rgba8(): ByteArray? = bytes?.copyOf()
    /** Identity, or equal id, size, sampler and pixels: a recaptured but unchanged texture needs no re-upload. */
    fun sameContentAs(o:RayTexture)=this===o || (id==o.id && width==o.width && height==o.height && wrapU==o.wrapU && wrapV==o.wrapV && filter==o.filter &&
        floats.contentEquals(o.floats) && bytes.contentEquals(o.bytes))
    /** Texels of a byte texture; float textures live in the float section and are bounded there. */
    val byteTexels get()=if(bytes!=null) width.toLong()*height else 0L
    /** A byte texture at half the width and height (2x2 box average), or null for a float texture or a single texel. */
    fun halved(): RayTexture? {
        val src=bytes ?: return null
        if(width==1 && height==1) return null
        val w=maxOf(1,width/2);val h=maxOf(1,height/2)
        val out=ByteArray(w*h*4)
        for(y in 0 until h) for(x in 0 until w) for(c in 0 until 4) {
            var sum=0;var n=0
            for(dy in 0..1) for(dx in 0..1) {
                val sx=minOf(x*2+dx,width-1);val sy=minOf(y*2+dy,height-1)
                sum+=src[(sy*width+sx)*4+c].toInt() and 255;n++
            }
            out[(y*w+x)*4+c]=((sum+n/2)/n).toByte()
        }
        return RayTexture(id,w,h,out,wrapU,wrapV,filter)
    }
    internal fun pixel(x: Int,y: Int): RayColor {
        val p=(y*width+x)*4
        bytes?.let { return RayColor((it[p].toInt() and 255)/255f,(it[p+1].toInt() and 255)/255f,(it[p+2].toInt() and 255)/255f,(it[p+3].toInt() and 255)/255f) }
        val f=floats!!
        return RayColor(f[p],f[p+1],f[p+2],f[p+3])
    }
}
data class RayTextureBinding(val texture: Int, val offsetU: Float=0f, val offsetV: Float=0f, val scaleU: Float=1f, val scaleV: Float=1f) {
    init { require(texture>=0 && listOf(offsetU,offsetV,scaleU,scaleV).all { it.isFinite() }) }
}
enum class RayMaterialKind { DEFAULT, PBR, TERRAIN }
enum class RayAlphaMode { OPAQUE, MASK, BLEND }
class RayTerrainMaterial(val splat: RayTextureBinding?, layers: List<RayTextureBinding?>, val size: Float) {
    val layers=rayList(layers)
    init { require(layers.size==5 && size.isFinite() && size>0) }
    // by value: a recaptured but unchanged terrain material must not read as a material change (it would restart ray accumulation every frame)
    override fun equals(other:Any?)=other is RayTerrainMaterial && splat==other.splat && layers==other.layers && size==other.size
    override fun hashCode()=(splat.hashCode()*31+layers.hashCode())*31+size.hashCode()
}
data class RayMaterial(
    val kind: RayMaterialKind=RayMaterialKind.DEFAULT,
    val baseColor: RayColor=RayColor(1f,1f,1f), val emissive: RayColor=RayColor(0f,0f,0f),
    val specular: RayColor=RayColor(0f,0f,0f), val shininess: Float=20f,
    val metallic: Float=0f, val roughness: Float=1f, val opacity: Float=1f,
    val alphaMode: RayAlphaMode=RayAlphaMode.OPAQUE, val alphaCutoff: Float=.5f, val doubleSided: Boolean=false,
    val baseTexture: RayTextureBinding?=null, val emissiveTexture: RayTextureBinding?=null,
    val specularTexture: RayTextureBinding?=null, val normalTexture: RayTextureBinding?=null,
    val metallicRoughnessTexture: RayTextureBinding?=null, val occlusionTexture: RayTextureBinding?=null,
    val terrain: RayTerrainMaterial?=null,
    val transmission: Float=0f, val ior: Float=1.5f,
) {
    init { require(transmission.isFinite() && transmission in 0f..1f && ior.isFinite() && ior in 1f..3f); require(listOf(shininess,metallic,roughness,opacity,alphaCutoff).all { it.isFinite() }); require(shininess>=0 && opacity in 0f..1f && alphaCutoff in 0f..1f) }
    internal fun bindings()=listOf(baseTexture,emissiveTexture,specularTexture,normalTexture,metallicRoughnessTexture,occlusionTexture)+listOf(terrain?.splat)+(terrain?.layers ?: List(5){null})
}
/** One triangle part. Local positions/normals/UV and optional tangent xyzw, preserving 32-bit indices. */
class RayMesh(val id: String, positions: FloatArray, indices: IntArray, normals: FloatArray?=null, uvs: FloatArray?=null, tangents: FloatArray?=null) {
    private val p=positions.copyOf(); private val i=indices.copyOf(); private val n=normals?.copyOf(); private val uv=uvs?.copyOf(); private val t=tangents?.copyOf()
    val vertexCount get()=p.size/3
    init {
        require(p.isNotEmpty() && p.size%3==0 && p.all { it.isFinite() })
        require(i.isNotEmpty() && i.size%3==0 && i.all { it in 0 until vertexCount })
        require(n==null || n.size==p.size && n.all { it.isFinite() })
        require(uv==null || uv.size==vertexCount*2 && uv.all { it.isFinite() })
        require(t==null || t.size==vertexCount*4 && t.all { it.isFinite() })
    }
    val indexCount get()=i.size
    /** Same acceleration-structure input (id, positions, indices). */
    fun sameGeometryAs(o:RayMesh)=this===o || (id==o.id && p.contentEquals(o.p) && i.contentEquals(o.i))
    /** Same vertex count and triangles: positions may differ and the structure can be refit without changing the index data. */
    fun sameTopologyAs(o:RayMesh)=this===o || (p.size==o.p.size && i.contentEquals(o.i))
    /** Same geometry and the same shading attributes. */
    fun sameContentAs(o:RayMesh)=this===o || (sameGeometryAs(o) && n.contentEquals(o.n) && uv.contentEquals(o.uv) && t.contentEquals(o.t))
    fun positions()=p.copyOf(); fun indices()=i.copyOf(); fun normals()=n?.copyOf(); fun uvs()=uv?.copyOf(); fun tangents()=t?.copyOf()
}
class RayInstance(val id: String, val mesh: Int, val material: Int, transform: FloatArray) {
    private val matrix=transform.copyOf()
    init { require(mesh>=0 && material>=0 && matrix.size==16 && matrix.all { it.isFinite() }); require(matrix[3]==0f && matrix[7]==0f && matrix[11]==0f && matrix[15]==1f) }
    fun transform()=matrix.copyOf()
}
enum class RayLightKind { DIRECTIONAL, POINT, SPOT }
/** Color includes raster binder intensity scaling. Direction points away from the light; cutoff is HALF angle. */
data class RayLight(val id: String, val kind: RayLightKind, val color: RayColor,
    val direction: RayVec3=RayVec3(0f,-1f,0f), val position: RayVec3=RayVec3(0f,0f,0f),
    val range: Float=0f, val cutoffAngle: Float=30f, val exponent: Float=0f, val castsShadow: Boolean=true) {
    init { require(range.isFinite() && range>=0 && cutoffAngle in 0f..180f && exponent in 0f..1f) }
}
/**
 * Equirectangular texture index; values remain linear HDR. Rotation is in degrees about +Y. [ambientCube], when set,
 * replaces the flat [ambient] colour for diffuse lighting with six axis colours in the raster order (+X, -X, +Y, -Y,
 * +Z, -Z): a surface takes the sum over axes of its squared normal component times the colour in the odd slot when that
 * component is non-negative and the even slot otherwise, exactly as the raster model shaders do.
 */
data class RayEnvironment(val ambient: RayColor=RayColor(.1f,.1f,.1f), val background: RayColor=RayColor(.05f,.1f,.2f),
    val texture: Int?=null, val hdr: Boolean=false, val intensity: Float=1f, val rotation: Float=0f, val ambientCube: List<RayColor>?=null) {
    init { require(texture==null || texture>=0); require(intensity.isFinite() && intensity>=0 && rotation.isFinite()); require(ambientCube==null || ambientCube.size==6) }
}
/**
 * How much of one directional light, the sun, passes the clouds at each world position: an immutable CPU copy of the
 * cloud shadow map the raster view uses. [visibility] holds `size*size` values from 0 to 1 in the map's red channel,
 * row-major, texel (u, v) at `v*size+u`, bilinearly filtered with clamped edges like the raster sampler. [worldToMap]
 * is eight floats, two rows of a 4-component dot product with `(x, y, z, 1)` giving the map coordinates `(u, v)`
 * (0 to 1 covers the map). Over the outer [edgeFade] share of the map, and beyond it, the factor fades to [outside],
 * the uniform dimming of the clouds elsewhere. It multiplies the direct contribution of the light with id [lightId]
 * only, on top of that light's own shadow ray. [revision] identifies the content: a new map has a new revision.
 */
class RaySunVisibility(val lightId: String, val size: Int, visibility: FloatArray, worldToMap: FloatArray,
    val edgeFade: Float, val outside: Float, val revision: Long=0) {
    private val texels=visibility.copyOf()
    private val matrix=worldToMap.copyOf()
    init {
        require(size in 1..RAY_MAX_SUN_VISIBILITY_SIZE && texels.size==size*size && texels.all { it.isFinite() && it in 0f..1f }) { "Invalid cloud visibility map" }
        require(matrix.size==8 && matrix.all { it.isFinite() })
        require(edgeFade>0f && edgeFade<=0.5f && outside in 0f..1f)
    }
    fun visibility():FloatArray=texels.copyOf()
    fun worldToMap():FloatArray=matrix.copyOf()
    /** The map as the single-channel RGBA texture the shading payload carries (red holds the visibility). */
    internal fun texture():RayTexture=RayTexture("sunVisibility",size,size,FloatArray(size*size*4) { if(it%4==0) texels[it/4] else if(it%4==3) 1f else 0f },
        RayWrap.CLAMP_TO_EDGE,RayWrap.CLAMP_TO_EDGE,RayFilter.LINEAR)
    override fun equals(other:Any?)=other is RaySunVisibility && lightId==other.lightId && size==other.size && revision==other.revision &&
        edgeFade==other.edgeFade && outside==other.outside && matrix.contentEquals(other.matrix)
    override fun hashCode()=(lightId.hashCode()*31+size)*31+revision.hashCode()
    companion object {
        /** A light dimmed by [scale] everywhere: a one-texel map placed far from the scene, so only the outside factor applies. */
        fun uniform(lightId:String,scale:Float,revision:Long=0)=
            RaySunVisibility(lightId,1,floatArrayOf(1f),floatArrayOf(0f,0f,0f,-8f,0f,0f,0f,-8f),0.1f,scale,revision)
    }
}
/** The widest cloud visibility map a scene may carry, per side. */
const val RAY_MAX_SUN_VISIBILITY_SIZE = 1024
data class RayFog(val color: RayColor,val density: Float,val gradient: Float) {
    init { require(density.isFinite() && density>=0 && gradient.isFinite() && gradient>=0) }
}
/** A primary ray composites at most 16 blended layers (the nearest win), so scenes with more blended instances use whole-view raster fallback. */
const val RAY_MAX_BLENDED_INSTANCES = 32
/** Shadow and reflection rays pass through this many alpha-test holes in a row; the next hole counts as solid. */
const val RAY_CUTOUT_HOLE_DEPTH = 8
/** Retained triangle input (positions and 32-bit indices) per session; the native bridge enforces the same bound. */
const val RAY_MAX_GEOMETRY_BYTES = 32L*1024*1024
const val RAY_MAX_MATERIALS = 128
const val RAY_MAX_TEXTURES = 128
const val RAY_MAX_LIGHTS = 12

/** The most 8-bit texels a scene's textures may hold together; the native shading payload carries 4 bytes for each. */
const val RAY_MAX_TEXTURE_TEXELS = 16L*1024*1024
/**
 * [textures] with the largest 8-bit ones halved ([halve], 2x2 box average by default) until their texels fit [budget],
 * keeping every index, id and sampler; the same list when it already fits, null when even single texels cannot fit.
 * Float textures are left alone.
 */
fun fitTextureTexels(textures:List<RayTexture>,budget:Long=RAY_MAX_TEXTURE_TEXELS,halve:(RayTexture)->RayTexture?={ it.halved() }):List<RayTexture>? {
    if(textures.sumOf { it.byteTexels }<=budget) return textures
    val fitted=textures.toMutableList()
    while(fitted.sumOf { it.byteTexels }>budget) {
        val largest=fitted.indices.filter { fitted[it].byteTexels>1 }.maxByOrNull { fitted[it].byteTexels } ?: return null
        fitted[largest]=halve(fitted[largest]) ?: return null
    }
    return fitted
}

class RaySceneSnapshot(meshes: List<RayMesh>, instances: List<RayInstance>, materials: List<RayMaterial>, textures: List<RayTexture>, lights: List<RayLight>,
    val environment: RayEnvironment=RayEnvironment(), val fog: RayFog?=null, val sunVisibility: RaySunVisibility?=null) {
    val meshes=rayList(meshes); val instances=rayList(instances); val materials=rayList(materials); val textures=rayList(textures); val lights=rayList(lights)
    /** Why every backend rejects this scene (the view then falls back to raster), or null when it is representable. */
    fun unsupportedReason():String? = when {
        materials.size>RAY_MAX_MATERIALS -> "Scene has more than $RAY_MAX_MATERIALS materials"
        textures.size+(if(sunVisibility!=null) 1 else 0)>RAY_MAX_TEXTURES -> "Scene has more than $RAY_MAX_TEXTURES textures"
        lights.size>RAY_MAX_LIGHTS -> "Scene has more than $RAY_MAX_LIGHTS lights"
        textures.sumOf { it.byteTexels }>RAY_MAX_TEXTURE_TEXELS -> "Scene textures exceed $RAY_MAX_TEXTURE_TEXELS texels"
        geometryBytes>RAY_MAX_GEOMETRY_BYTES -> "Scene geometry exceeds the $RAY_MAX_GEOMETRY_BYTES byte budget"
        blendedInstances>RAY_MAX_BLENDED_INSTANCES -> "Ray transparency exceeds the supported layer limit"
        else -> RayOpticalEligibility().unsupportedReason(this)
    }
    val geometryBytes get()=this.meshes.sumOf { (it.vertexCount*3L+it.indexCount)*4L }
    val blendedInstances get()=this.instances.count { this.materials[it.material].alphaMode==RayAlphaMode.BLEND }
    init {
        require(instances.all { it.mesh in meshes.indices && it.material in materials.indices })
        require(materials.flatMap { it.bindings() }.filterNotNull().all { it.texture in textures.indices })
        require(environment.texture==null || environment.texture in textures.indices)
    }
}
/**
 * Which meshes of [next] must have their structure rebuilt, or null when every structure must be built (no previous
 * scene, a different mesh count, or a changed mesh whose vertex count or indices differ). An empty list means every
 * existing structure is still valid and only transforms may differ.
 */
fun dirtyMeshes(previous:List<RayMesh>?,next:List<RayMesh>):List<Int>? {
    if(previous==null || previous.size!=next.size) return null
    val dirty=next.indices.filter { !previous[it].sameGeometryAs(next[it]) }
    return if(dirty.all { previous[it].sameTopologyAs(next[it]) }) dirty else null
}

/** True when [next] needs the same acceleration structures as [previous]: only transforms may differ. */
fun sameGeometry(previous:List<RayMesh>?,next:List<RayMesh>)=previous!=null && previous.size==next.size && previous.indices.all { previous[it].sameGeometryAs(next[it]) }

class RaySceneRequest(override val key: RayFrameKey, override val width: Int, override val height: Int, override val camera: RaySliceCamera, val scene: RaySceneSnapshot,
    val samples: Int=1, val sampleOffset: Int=0, val accumulationEpoch: Long=0,
    val maxReflectionBounces: Int=1, val maxRefractionBounces: Int=0, val maxRaysPerFrame: Long=33554432,
    val settingsRevision: Long=0) : RayRenderRequest {
    init {
        require(width>0 && height>0 && samples in 1..8 && sampleOffset>=0)
        require(maxReflectionBounces in 0..16 && maxRefractionBounces in 0..16 && maxRaysPerFrame in 1..MAX_RAYS_PER_FRAME)
    }
    override fun withRenderPlan(width: Int,height: Int,samples: Int,sampleOffset: Int,accumulationEpoch: Long) =
        RaySceneRequest(key,width,height,camera,scene,samples,sampleOffset,accumulationEpoch,maxReflectionBounces,maxRefractionBounces,maxRaysPerFrame,settingsRevision)
    fun requireWithinBudget() {
        val cost=RayWorkBudget().perCameraSample(scene,maxReflectionBounces,maxRefractionBounces)
        val work=RayWorkBudget().frameQueries(width,height,samples,cost)
        if(work>maxRaysPerFrame) throw RayQualityLimitException("Submitted frame exceeds the saved ray budget")
    }
    fun requireOptics(capabilities: RayCapabilities) {
        if(!capabilities.sceneOptics && (maxReflectionBounces!=1 || maxRefractionBounces!=0 || scene.materials.any { it.transmission>0f }))
            throw UnsupportedOperationException("Backend does not support scene optics")
    }
    /** Common placement ABI; blended instances take primary rays only in both native backends. */
    internal fun instances(): List<RaySliceInstance> = scene.instances.map {
        RaySliceInstance(it.mesh, it.transform().toList(), listOf(1f, 1f, 1f),
            primaryOnly = scene.materials[it.material].alphaMode == RayAlphaMode.BLEND)
    }
    /** 28 float camera ABI: lens/light, transport (R,T,samples,offset), per-sample and per-frame query budgets. */
    internal fun nativeCamera(): FloatArray = camera.uniforms(width,height).copyOf(28).also {
        it[20]=maxReflectionBounces.toFloat();it[21]=maxRefractionBounces.toFloat();it[22]=samples.toFloat();it[23]=sampleOffset.toFloat()
        it[24]=RayWorkBudget().perCameraSample(scene,maxReflectionBounces,maxRefractionBounces).toFloat()
        it[25]=maxRaysPerFrame.toFloat()
    }
}
