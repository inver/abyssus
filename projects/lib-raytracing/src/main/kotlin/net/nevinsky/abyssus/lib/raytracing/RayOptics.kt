/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import kotlin.math.sqrt

enum class RayOpticalEvent { REFLECTION, TRANSMISSION }
class RayOpticalPath(private val reflectionLimit: Int,private val refractionLimit: Int) {
    var reflections=0; private set
    var refractions=0; private set
    init { require(reflectionLimit in 0..16 && refractionLimit in 0..16) }
    fun consume(event: RayOpticalEvent): Boolean = when(event) {
        RayOpticalEvent.REFLECTION -> if(reflections<reflectionLimit) { reflections++;true } else false
        RayOpticalEvent.TRANSMISSION -> if(refractions<refractionLimit) { refractions++;true } else false
    }
}

/** Air or one solid; first backface exit infers a camera starting inside glass. */
class RayOpticalMedium {
    private var solid: Int?=null
    private var ior=1f
    val inside get()=solid!=null
    fun incidentIor(id: Int,materialIor: Float,entering: Boolean): Float {
        require(solid==null || solid==id && !entering) { "Nested or intersecting dielectric solids are unsupported" }
        if(solid==null && !entering) { solid=id;ior=materialIor }
        return if(solid!=null) ior else if(entering) 1f else materialIor
    }
    fun cross(id: Int,materialIor: Float,entering: Boolean) {
        incidentIor(id,materialIor,entering)
        solid=if(entering) id else null
        ior=if(entering) materialIor else 1f
    }
}

/** Geometric boundary math independent of traversal, graphics APIs and material textures. */
class RayOptics {
    fun refract(incident: RayVec3,opposingNormal: RayVec3,incidentIor: Float,targetIor: Float): RayVec3? {
        val eta=incidentIor/targetIor
        val cosine=(-incident.dot(opposingNormal)).coerceIn(0f,1f)
        val k=1f-eta*eta*(1f-cosine*cosine)
        if(k<0f) return null
        return (incident*eta+opposingNormal*(eta*cosine-sqrt(k))).unit()
    }
    fun fresnel(cosine: Float,incidentIor: Float,targetIor: Float): Float {
        if(incidentIor==targetIor) return 0f
        val c=cosine.coerceIn(0f,1f)
        val eta=incidentIor/targetIor
        val sin2=eta*eta*(1f-c*c)
        if(sin2>=1f) return 1f
        val transmitted=sqrt(1f-sin2)
        val rs=(incidentIor*c-targetIor*transmitted)/(incidentIor*c+targetIor*transmitted)
        val rp=(targetIor*c-incidentIor*transmitted)/(targetIor*c+incidentIor*transmitted)
        return ((rs*rs+rp*rp)*.5f).coerceIn(0f,1f)
    }
    fun offset(position: RayVec3,geometricNormal: RayVec3,direction: RayVec3): RayVec3 =
        position+geometricNormal*(if(direction.dot(geometricNormal)>=0f) .001f else -.001f)

    /** Integer hash shared with native kernels; exactly representable 24-bit fraction. */
    fun sample(x: Int,y: Int,sample: Int,event: Int): Float {
        var seed=x.toUInt()*1973u+y.toUInt()*9277u+sample.toUInt()*26699u+event.toUInt()*31847u+89173u
        seed=(seed xor (seed shr 16))*0x7feb352du
        seed=(seed xor (seed shr 15))*0x846ca68bu
        seed=seed xor (seed shr 16)
        return (seed and 0xffffffu).toFloat()/16777216f
    }
}

/** Native kernels flag a failed path in alpha; reject the entire frame before publication. */
internal fun requireNativeOpticalFrame(colors: FloatArray) {
    for(i in 3 until colors.size step 4) when {
        colors[i]==-1f -> throw RayQualityLimitException("Ray query budget exceeded")
        colors[i]<0f -> throw UnsupportedOperationException("Nested, intersecting or unmatched dielectric boundary")
    }
}
