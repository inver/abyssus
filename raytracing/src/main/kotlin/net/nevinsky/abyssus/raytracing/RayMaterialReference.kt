/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import kotlin.math.*

/** Scalar reference for the raster shader's linear material, sampler and light conventions. */
class RayMaterialReference {
    fun sample(texture: RayTexture,u: Float,v: Float): RayColor {
        fun index(x: Int,n: Int,wrap: RayWrap)=if(wrap==RayWrap.REPEAT) Math.floorMod(x,n) else x.coerceIn(0,n-1)
        fun pixel(x:Int,y:Int)=texture.pixel(index(x,texture.width,texture.wrapU),index(y,texture.height,texture.wrapV))
        if(texture.filter==RayFilter.NEAREST) return pixel(floor(u*texture.width).toInt(),floor(v*texture.height).toInt())
        val x=u*texture.width-.5f; val y=v*texture.height-.5f; val ix=floor(x).toInt(); val iy=floor(y).toInt()
        return mix(mix(pixel(ix,iy),pixel(ix+1,iy),x-ix),mix(pixel(ix,iy+1),pixel(ix+1,iy+1),x-ix),y-iy)
    }
    fun sample(binding:RayTextureBinding,textures:List<RayTexture>,u:Float,v:Float)=sample(textures[binding.texture],u*binding.scaleU+binding.offsetU,v*binding.scaleV+binding.offsetV)
    fun baseColor(material:RayMaterial,textures:List<RayTexture>,u:Float,v:Float,splatU:Float=u,splatV:Float=v):RayColor {
        val terrain=material.terrain
        if(material.kind!=RayMaterialKind.TERRAIN || terrain==null) return material.baseColor*(material.baseTexture?.let { sample(it,textures,u,v) } ?: RayColor(1f,1f,1f))
        var value=terrain.layers[0]?.let { sample(it,textures,u,v) } ?: RayColor(.8f,.8f,.8f)
        terrain.splat?.let { binding ->
            val s=sample(binding,textures,splatU,splatV); val weights=floatArrayOf(s.r,s.g,s.b,s.a)
            for(i in 1..4) terrain.layers[i]?.let { value=mix(value,sample(it,textures,u,v),weights[i-1]) }
        }
        return value
    }
    fun attenuation(light:RayLight,position:RayVec3):Float {
        if(light.kind==RayLightKind.DIRECTIONAL) return 1f
        if(light.range<=0) return 0f
        val delta=light.position-position; val d2=delta.dot(delta); val distance=sqrt(d2)
        var value=(1f-smooth(.75f*light.range,light.range,distance))/(1f+d2)
        if(light.kind==RayLightKind.SPOT) {
            val outer=cos(Math.toRadians(light.cutoffAngle.toDouble())).toFloat()
            val inner=cos(Math.toRadians((light.cutoffAngle*(1-light.exponent)).toDouble())).toFloat()
            val c=light.direction.unit().dot(delta.unit()*-1f)
            value*=if(inner<=outer) (if(c>=outer) 1f else 0f) else smooth(outer,inner,c)
        }
        return value
    }
    /** One light only; ambient and emission are deliberately separate from visibility. */
    fun direct(material:RayMaterial,base:RayColor,normal:RayVec3,view:RayVec3,light:RayVec3,radiance:RayColor):RayColor {
        val nl=normal.dot(light).coerceIn(0f,1f)
        if(nl<=0) return RayColor(0f,0f,0f)
        val half=(light+view).unit(); val nh=normal.dot(half).coerceIn(0f,1f)
        if(material.kind!=RayMaterialKind.PBR) return (base+material.specular*nh.pow(material.shininess))*radiance*nl
        val nv=max(normal.dot(view),.0001f); val vh=view.dot(half).coerceIn(0f,1f)
        val metal=material.metallic.coerceIn(0f,1f); val rough=material.roughness.coerceIn(.04f,1f)
        val alpha=rough*rough; val a2=alpha*alpha; val d=nh*nh*(a2-1)+1
        val distribution=a2/(PI.toFloat()*d*d)
        val gv=nl*sqrt(nv*nv*(1-a2)+a2); val gl=nv*sqrt(nl*nl*(1-a2)+a2)
        val visibility=.5f/max(gv+gl,1e-5f)
        val f0=mix(RayColor(.04f,.04f,.04f),base,metal)
        val fresnel=mix(f0,RayColor(1f,1f,1f),(1-vh).pow(5))
        val diffuse=RayColor(1-fresnel.r,1-fresnel.g,1-fresnel.b)*base*(1-metal)
        return (diffuse+fresnel*(distribution*visibility*PI.toFloat()))*radiance*nl
    }
    fun ambient(material:RayMaterial,base:RayColor,light:RayColor,ndotv:Float):RayColor=ambient(material,base,light,light,ndotv)
    /** PBR specular uses [specularLight] (ambient colour, or the traced reflection radiance); diffuse uses [diffuseLight]. */
    fun ambient(material:RayMaterial,base:RayColor,diffuseLight:RayColor,specularLight:RayColor,ndotv:Float):RayColor {
        if(material.kind!=RayMaterialKind.PBR) return base*diffuseLight
        val rough=material.roughness.coerceIn(.04f,1f); val metal=material.metallic.coerceIn(0f,1f)
        val rx=1-rough; val ry=.0425f-.0275f*rough; val rz=1.04f-.572f*rough; val rw=-.04f+.022f*rough
        val a004=min(rx*rx,2f.pow(-9.28f*max(ndotv,.0001f)))*rx+ry
        val f0=mix(RayColor(.04f,.04f,.04f),base,metal)
        val specular=f0*(-1.04f*a004+rz)+RayColor(1f,1f,1f)*(1.04f*a004+rw)
        return base*(1-metal)*diffuseLight+specular*specularLight
    }
    /** Equirectangular lookup matching the raster sky (centre column faces -Z, top row +Y); rotation is about +Y. */
    fun skyColor(environment:RayEnvironment,textures:List<RayTexture>,direction:RayVec3):RayColor {
        val index=environment.texture ?: return environment.background
        val angle=Math.toRadians(environment.rotation.toDouble()); val c=cos(angle).toFloat(); val s=sin(angle).toFloat()
        val d=direction.unit(); val r=RayVec3(c*d.x-s*d.z,d.y,s*d.x+c*d.z).unit()
        val u=.5f+atan2(r.x,-r.z)/(2f*PI.toFloat()); val v=acos(r.y.coerceIn(-1f,1f))/PI.toFloat()
        val texel=sample(textures[index],u,v)
        return RayColor(texel.r*environment.intensity,texel.g*environment.intensity,texel.b*environment.intensity,1f)
    }
    /** What a primary miss shows: an HDR sky is tone mapped like the raster sky (ACES fit, gamma 1/2.2); reflections use [skyColor]. */
    fun skyDisplayColor(environment:RayEnvironment,textures:List<RayTexture>,direction:RayVec3):RayColor {
        val linear=skyColor(environment,textures,direction)
        if(environment.texture==null || !environment.hdr) return linear
        fun map(c:Float):Float { val x=max(c,0f); return ((x*(2.51f*x+.03f))/(x*(2.43f*x+.59f)+.14f)).coerceIn(0f,1f).pow(1f/2.2f) }
        return RayColor(map(linear.r),map(linear.g),map(linear.b),1f)
    }
    /** Diffuse ambient for a surface normal: the flat colour or the six-colour ambient cube, as the raster shaders blend it. */
    fun ambientAt(environment:RayEnvironment,normal:RayVec3):RayColor {
        val cube=environment.ambientCube ?: return environment.ambient
        fun slot(component:Float,axis:Int)=cube[axis*2+if(component>=0f) 1 else 0]*(component*component)
        val x=slot(normal.x,0); val y=slot(normal.y,1); val z=slot(normal.z,2)
        return RayColor(x.r+y.r+z.r,x.g+y.g+z.g,x.b+y.b+z.b,1f)
    }
    /** The raster fog share: a quadratic ramp reaching 1 - 1/e of the fog colour at distance 1 / density. */
    fun fogAmount(density:Float,distance:Float):Float=min(distance*distance*(1f-exp(-1f))*density*density,1f)
    private fun pcg(v:UInt):UInt { val state=v*747796405u+2891336453u; val word=((state shr ((state shr 28)+4u).toInt()) xor state)*277803737u; return (word shr 22) xor word }
    /** Same hash as the Metal kernel, so both backends sample the same reflection directions per pixel. */
    fun random2(x:Int,y:Int):Pair<Float,Float> {
        val a=pcg(x.toUInt()+pcg(y.toUInt())); val b=pcg(a+1u)
        return Pair(a.toFloat()/4294967296f,b.toFloat()/4294967296f)
    }
    /** GGX-sampled reflection; falls back to the mirror direction when the sample points below the surface. */
    fun reflectionDirection(normal:RayVec3,view:RayVec3,roughness:Float,x:Int,y:Int):RayVec3 {
        fun reflect(i:RayVec3,n:RayVec3)=i-n*(2f*n.dot(i))
        val mirror=reflect(view*-1f,normal)
        val a=roughness*roughness; val a2=a*a
        val (xi0,xi1)=random2(x,y)
        val phi=2f*PI.toFloat()*xi0; val cosT=sqrt((1f-xi1)/(1f+(a2-1f)*xi1)); val sinT=sqrt(max(1f-cosT*cosT,0f))
        val up=if(abs(normal.y)<.999f) RayVec3(0f,1f,0f) else RayVec3(1f,0f,0f)
        val t=up.cross(normal).unit(); val b=normal.cross(t)
        val h=(t*(sinT*cos(phi))+b*(sinT*sin(phi))+normal*cosT).unit()
        val r=reflect(view*-1f,h)
        return if(r.dot(normal)>0f) r else mirror
    }
    private fun smooth(a:Float,b:Float,x:Float):Float { val t=((x-a)/(b-a)).coerceIn(0f,1f);return t*t*(3-2*t) }
    private fun mix(a:RayColor,b:RayColor,t:Float)=RayColor(a.r+(b.r-a.r)*t,a.g+(b.g-a.g)*t,a.b+(b.b-a.b)*t,a.a+(b.a-a.a)*t)
}
