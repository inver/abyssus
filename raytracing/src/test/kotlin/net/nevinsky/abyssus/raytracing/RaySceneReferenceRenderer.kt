/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import kotlin.math.abs
import kotlin.math.sqrt

private const val MAX_CUTOUT_LAYERS = 8
private const val MAX_BLEND_LAYERS = RAY_PRIMARY_BLEND_LAYERS

/** Test-only triangle traversal with the same semantics as the native kernels. */
internal class RaySceneReferenceRenderer(private val onIntersection: () -> Unit = {}) {
    private val reference=RayMaterialReference()
    private class Triangle(val a:RayVec3,val b:RayVec3,val c:RayVec3,val normals:List<RayVec3>?,val uv:FloatArray,val local:List<RayVec3>,val material:Int,val solid:Int) {
        fun hit(origin:RayVec3,direction:RayVec3,min:Float,max:Float):Hit? {
            val e1=b-a;val e2=c-a;val p=direction.cross(e2);val determinant=e1.dot(p)
            if(abs(determinant)<1e-8f) return null
            val t=origin-a;val u=t.dot(p)/determinant;val q=t.cross(e1);val v=direction.dot(q)/determinant
            val distance=e2.dot(q)/determinant
            return if(u<0 || v<0 || u+v>1 || distance<min || distance>max) null else Hit(this,distance,u,v)
        }
    }
    private class Hit(val triangle:Triangle,val distance:Float,val u:Float,val v:Float)
    private class Surface(val position:RayVec3,val normal:RayVec3,val material:RayMaterial,val evaluated:RayMaterial,val base:RayColor,
        val emissive:RayColor,val occlusion:Float,val geometric:RayVec3,val solid:Int)

    fun render(request:RaySceneRequest):RayFrame {
        request.scene.unsupportedReason()?.let { throw IllegalArgumentException(it) }
        request.requireWithinBudget()
        val scene=request.scene
        val solids=scene.instances.map { it.id.substringBefore('/') }.distinct()
        val optics=RayOptics()
        var queries=0L
        val queryLimit=RayWorkBudget().perCameraSample(scene,request.maxReflectionBounces,request.maxRefractionBounces)
        val triangles=scene.instances.flatMap { instance ->
            val mesh=scene.meshes[instance.mesh];val p=mesh.positions();val indices=mesh.indices();val normals=mesh.normals();val uv=mesh.uvs();val matrix=instance.transform()
            fun local(i:Int)=RayVec3(p[i*3],p[i*3+1],p[i*3+2])
            fun world(v:RayVec3)=RayVec3(matrix[0]*v.x+matrix[4]*v.y+matrix[8]*v.z+matrix[12],matrix[1]*v.x+matrix[5]*v.y+matrix[9]*v.z+matrix[13],matrix[2]*v.x+matrix[6]*v.y+matrix[10]*v.z+matrix[14])
            val x=RayVec3(matrix[0],matrix[1],matrix[2]);val y=RayVec3(matrix[4],matrix[5],matrix[6]);val z=RayVec3(matrix[8],matrix[9],matrix[10]);val sign=if(x.dot(y.cross(z))<0)-1f else 1f
            fun normal(i:Int):RayVec3 { val n=normals!!;return ((y.cross(z)*n[i*3]+z.cross(x)*n[i*3+1]+x.cross(y)*n[i*3+2])*sign).unit() }
            (indices.indices step 3).map { j ->
                val ids=listOf(indices[j],indices[j+1],indices[j+2]);val points=ids.map(::local)
                Triangle(world(points[0]),world(points[1]),world(points[2]),normals?.let { ids.map(::normal) },
                    ids.flatMap { i -> listOf(uv?.get(i*2) ?: 0f,uv?.get(i*2+1) ?: 0f) }.toFloatArray(),points,instance.material,solids.indexOf(instance.id.substringBefore('/')))
            }
        }
        // Blended triangles are visible to primary rays only; shadow and reflection rays never see them.
        val secondary=triangles.filter { scene.materials[it.material].alphaMode!=RayAlphaMode.BLEND }
        fun nearest(candidates:List<Triangle>,origin:RayVec3,direction:RayVec3,min:Float,max:Float): Hit? { onIntersection(); queries++;check(queries<=queryLimit) { "Ray query budget exceeded" }; return candidates.mapNotNull { it.hit(origin,direction,min,max) }.minByOrNull { it.distance } }
        fun surfaceOf(hit:Hit,position:RayVec3,direction:RayVec3):Surface {
            val triangle=hit.triangle;val weights=floatArrayOf(1-hit.u-hit.v,hit.u,hit.v)
            var normal=triangle.normals?.let { (it[0]*weights[0]+it[1]*weights[1]+it[2]*weights[2]).unit() } ?: (triangle.b-triangle.a).cross(triangle.c-triangle.a).unit()
            val material=scene.materials[triangle.material]
            if(material.doubleSided && normal.dot(direction)>0) normal=normal*-1f
            val u=(0..2).sumOf { (triangle.uv[it*2]*weights[it]).toDouble() }.toFloat();val v=(0..2).sumOf { (triangle.uv[it*2+1]*weights[it]).toDouble() }.toFloat()
            val local=triangle.local[0]*weights[0]+triangle.local[1]*weights[1]+triangle.local[2]*weights[2]
            val size=material.terrain?.size ?: 1f
            val base=reference.baseColor(material,scene.textures,u,v,local.x/size,local.z/size)
            fun texture(b:RayTextureBinding?)=b?.let { reference.sample(it,scene.textures,u,v) } ?: RayColor(1f,1f,1f)
            val mr=texture(material.metallicRoughnessTexture)
            val evaluated=material.copy(specular=material.specular*texture(material.specularTexture),metallic=material.metallic*mr.b,roughness=material.roughness*mr.g)
            return Surface(position,normal,material,evaluated,base,material.emissive*texture(material.emissiveTexture),texture(material.occlusionTexture).r,(triangle.b-triangle.a).cross(triangle.c-triangle.a).unit(),triangle.solid)
        }
        fun isCutout(s:Surface)=s.material.alphaMode==RayAlphaMode.MASK && s.base.a*s.material.opacity<s.material.alphaCutoff
        /** Nearest hit that is not an alpha-test hole; more than [MAX_CUTOUT_LAYERS] holes count as solid. */
        fun traceMasked(origin:RayVec3,direction:RayVec3,min:Float,max:Float):Pair<Hit,Surface>? {
            var start=0f;var last:Pair<Hit,Surface>?=null
            for(i in 0 until MAX_CUTOUT_LAYERS) {
                val from=origin+direction*start;val lo=if(i==0) min else .0005f;val hi=max-start
                if(hi<=lo) return null
                val hit=nearest(secondary,from,direction,lo,hi) ?: return null
                val surface=surfaceOf(hit,from+direction*hit.distance,direction)
                last=Pair(Hit(hit.triangle,start+hit.distance,hit.u,hit.v),surface)
                if(!isCutout(surface)) return last
                start+=hit.distance
            }
            return last
        }
        fun shade(s:Surface,view:RayVec3,environment:RayColor?):RayColor {
            val ambient=reference.ambientAt(scene.environment,s.normal)
            var value=reference.ambient(s.evaluated,s.base,ambient,environment ?: ambient,s.normal.dot(view))*s.occlusion+s.emissive
            for(light in scene.lights) {
                val delta=light.position-s.position
                val L=if(light.kind==RayLightKind.DIRECTIONAL) light.direction.unit()*-1f else delta.unit()
                val distance=if(light.kind==RayLightKind.DIRECTIONAL) 10000f else sqrt(delta.dot(delta))
                val attenuation=reference.attenuation(light,s.position)
                if(attenuation<=0f && light.kind!=RayLightKind.DIRECTIONAL) continue
                val blocked=light.castsShadow && s.normal.dot(L)>0f && traceMasked(s.position+s.normal*.001f,L,.001f,maxOf(distance-.002f,.001f))!=null
                if(!blocked) value+=reference.direct(s.evaluated,s.base,s.normal,view,L,light.color*attenuation)
            }
            return value
        }
        fun shadeSurface(initial:Surface,initialView:RayVec3,x:Int,y:Int,sample:Int):RayColor {
            var s=initial;var view=initialView
            var result=RayColor(0f,0f,0f);var throughput=RayColor(1f,1f,1f)
            val path=RayOpticalPath(request.maxReflectionBounces,request.maxRefractionBounces)
            val medium=RayOpticalMedium()
            for(event in 0..request.maxReflectionBounces+request.maxRefractionBounces) {
                if(s.material.kind!=RayMaterialKind.PBR) return result+throughput*shade(s,view,null)
                val transmission=if(request.maxRefractionBounces>0) s.material.transmission else 0f
                if(transmission==0f && path.reflections==request.maxReflectionBounces) return result+throughput*shade(s,view,null)
                val roughness=s.evaluated.roughness.coerceIn(.04f,1f)
                var direction=reference.reflectionDirection(s.normal,view,roughness,x+sample*1973+event*31847,y)
                val ambient=reference.ambientAt(scene.environment,s.normal)
                val zero=reference.ambient(s.evaluated,s.base,ambient,RayColor(0f,0f,0f),s.normal.dot(view))*s.occlusion
                val unit=reference.ambient(s.evaluated,s.base,ambient,RayColor(1f,1f,1f),s.normal.dot(view))*s.occlusion
                var weight=RayColor((unit.r-zero.r).coerceAtLeast(0f),(unit.g-zero.g).coerceAtLeast(0f),(unit.b-zero.b).coerceAtLeast(0f))
                var selected=RayOpticalEvent.REFLECTION
                var entering=false
                if(transmission>0f) {
                    entering=view.dot(s.geometric)>0f
                    val opposing=if(entering) s.geometric else s.geometric*-1f
                    val from=medium.incidentIor(s.solid,s.material.ior,entering)
                    val to=if(entering) s.material.ior else 1f
                    val refracted=optics.refract(view*-1f,opposing,from,to)
                    val F=optics.fresnel(view.dot(opposing),from,to)
                    val reflection=weight*(1-transmission)+RayColor(1f,1f,1f)*(transmission*F)
                    val transmitted=transmission*(1-F)
                    val maximum=maxOf(reflection.r,reflection.g,reflection.b)
                    val probability=if(maximum+transmitted>0f) maximum/(maximum+transmitted) else 1f
                    if(refracted!=null && optics.sample(x,y,sample,event)>=probability) {
                        selected=RayOpticalEvent.TRANSMISSION;direction=refracted
                        weight=RayColor(1f,1f,1f)*(transmitted/(1-probability))
                    } else weight=reflection*(1/probability.coerceAtLeast(1e-6f))
                }
                result+=throughput*shade(s,view,RayColor(0f,0f,0f))*(1-transmission)
                throughput=throughput*weight
                if(!path.consume(selected)) return result+throughput*reference.skyColor(scene.environment,scene.textures,direction)
                if(selected==RayOpticalEvent.TRANSMISSION) medium.cross(s.solid,s.material.ior,entering)
                val origin=optics.offset(s.position,s.geometric,direction)
                val next=traceMasked(origin,direction,.001f,10000f)
                if(next==null) {
                    require(!medium.inside) { "Unmatched dielectric boundary" }
                    return result+throughput*reference.skyColor(scene.environment,scene.textures,direction)
                }
                s=next.second;view=direction*-1f
            }
            error("Optical event bound exceeded")
        }
        val camera=request.camera.uniforms(request.width,request.height)
        fun vector(i:Int)=RayVec3(camera[i],camera[i+1],camera[i+2])
        val origin=vector(0);val forward=vector(4);val right=vector(8);val up=vector(12)
        val near=camera[3];val far=camera[7]
        val colors=FloatArray(request.width*request.height*4);val depths=FloatArray(request.width*request.height){1f}
        for(y in 0 until request.height) for(x in 0 until request.width) {
            var total=RayColor(0f,0f,0f)
            for(batchSample in 0 until request.samples) {
            queries=0L
            val direction=(forward+right*(((x+.5f)/request.width*2-1)*camera[15]*camera[11])+up*(((y+.5f)/request.height*2-1)*camera[11])).unit()
            val cosine=direction.dot(forward);val pixel=y*request.width+x
            var accumulated=RayColor(0f,0f,0f);var transmittance=1f;var start=0f;var finished=false
            for(layer in 0 until MAX_BLEND_LAYERS+MAX_CUTOUT_LAYERS) {
                if(finished) break
                val from=origin+direction*start;val lo=if(layer==0) near/cosine else .0005f;val hi=far/cosine-start
                if(hi<=lo) break
                val hit=nearest(triangles,from,direction,lo,hi) ?: break
                val travelled=start+hit.distance
                val surface=surfaceOf(hit,origin+direction*travelled,direction)
                if(isCutout(surface)) { start=travelled;continue }
                var color=shadeSurface(surface,direction*-1f,x,y,request.sampleOffset+batchSample)
                scene.fog?.let { fog -> color=mix(color,fog.color,reference.fogAmount(fog.density,travelled)) }
                if(surface.material.alphaMode==RayAlphaMode.BLEND) {
                    val alpha=(surface.base.a*surface.material.opacity).coerceIn(0f,1f)
                    accumulated+=color*(transmittance*alpha);transmittance*=1-alpha;start=travelled
                    if(transmittance<.003f) finished=true
                    continue
                }
                depths[pixel]=(far/(far-near)-far*near/((far-near)*travelled*cosine)).coerceIn(0f,1f)
                accumulated+=color*transmittance;transmittance=0f;finished=true
            }
            if(transmittance>0f) accumulated+=reference.skyDisplayColor(scene.environment,scene.textures,direction)*transmittance
            total+=accumulated
            }
            val pixel=y*request.width+x
            colors[pixel*4]=total.r/request.samples;colors[pixel*4+1]=total.g/request.samples;colors[pixel*4+2]=total.b/request.samples;colors[pixel*4+3]=1f
        }
        return RayFrame(request.key,request.width,request.height,colors,depths)
    }
    private fun mix(a:RayColor,b:RayColor,t:Float)=RayColor(a.r+(b.r-a.r)*t,a.g+(b.g-a.g)*t,a.b+(b.b-a.b)*t,1f)
}
