/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

/** The shading payload: scalar floats, then raw RGBA8 texels in the same native buffer (the header's slot 29 is where they start). */
internal class MetalSceneData(val floats:FloatArray,val bytes:ByteArray)

/**
 * Version 2 ABI: offsets in the float section count floats, never bytes; texture table entries are
 * (offset, width, height, wrapU, wrapV, filter, isBytes), where a byte texture's offset counts RGBA8 texels from the start of
 * the byte section. All payloads are bounded before JNI.
 */
internal class MetalSceneEncoding(private val scene:RaySceneSnapshot) {
    fun encode():MetalSceneData {
        require(scene.materials.size<=128 && scene.textures.size<=128 && scene.lights.size<=12) { "Scene shading resource limit exceeded" }
        val attributes=scene.meshes.sumOf { it.vertexCount.toLong()*12 }
        val cubeFloats=if(scene.environment.ambientCube!=null) 18L else 0L
        val total=cubeFloats+32L+scene.materials.size*128L+scene.lights.size*16L+attributes+scene.textures.size*8L+scene.instances.size+
            scene.textures.filter { !it.isBytes }.sumOf { it.width.toLong()*it.height*4 }
        val byteTexels=scene.textures.filter { it.isBytes }.sumOf { it.width.toLong()*it.height }
        // offsets are stored as floats, which are exact only up to 2^24
        require(total<=MAX_SCENE_FLOATS) { "Scene shading payload exceeds ${MAX_SCENE_FLOATS*4/(1024*1024)} MiB" }
        require(byteTexels<=MAX_TEXTURE_TEXELS) { "Scene textures exceed $MAX_TEXTURE_TEXELS texels" }
        val bytes=ByteArray((byteTexels*4).toInt())
        var byteAt=0L
        val out=FloatArray(total.toInt())
        var at=32
        out[0]=at.toFloat()
        fun color(offset:Int,c:RayColor) { out[offset]=c.r;out[offset+1]=c.g;out[offset+2]=c.b;out[offset+3]=c.a }
        fun vector(offset:Int,v:RayVec3) { out[offset]=v.x;out[offset+1]=v.y;out[offset+2]=v.z }
        scene.materials.forEach { m ->
            val start=at
            color(start,m.baseColor);color(start+4,m.emissive);color(start+8,m.specular)
            out[start+12]=m.shininess;out[start+13]=m.metallic;out[start+14]=m.roughness;out[start+15]=m.opacity
            out[start+16]=m.kind.ordinal.toFloat();out[start+17]=m.alphaMode.ordinal.toFloat();out[start+18]=m.alphaCutoff;out[start+19]=if(m.doubleSided)1f else 0f
            out[start+20]=m.terrain?.size ?: 1f
            m.bindings().forEachIndexed { index,b ->
                val offset=start+32+index*8
                out[offset]=b?.texture?.toFloat() ?: -1f
                if(b!=null) { out[offset+1]=b.offsetU;out[offset+2]=b.offsetV;out[offset+3]=b.scaleU;out[offset+4]=b.scaleV }
            }
            at+=128
        }
        out[1]=at.toFloat();out[2]=scene.lights.size.toFloat()
        scene.lights.forEach { l ->
            color(at,l.color);vector(at+4,l.position);vector(at+8,l.direction.unit())
            out[at+3]=l.kind.ordinal.toFloat();out[at+7]=l.range;out[at+11]=l.cutoffAngle;out[at+12]=l.exponent;out[at+13]=if(l.castsShadow)1f else 0f
            at+=16
        }
        out[3]=at.toFloat()
        scene.meshes.forEach { m ->
            val normals=m.normals();val uv=m.uvs();val tangent=m.tangents()
            for(i in 0 until m.vertexCount) {
                if(normals!=null) normals.copyInto(out,at,i*3,i*3+3)
                if(uv!=null) uv.copyInto(out,at+3,i*2,i*2+2)
                if(tangent!=null) tangent.copyInto(out,at+5,i*4,i*4+4)
                out[at+9]=if(normals!=null)1f else 0f;out[at+10]=if(tangent!=null)1f else 0f;at+=12
            }
        }
        out[4]=at.toFloat();val textureTable=at;at+=scene.textures.size*8
        out[5]=at.toFloat()
        scene.instances.forEach { out[at++]=it.material.toFloat() }
        scene.textures.forEachIndexed { i,t ->
            val p=textureTable+i*8;out[p+1]=t.width.toFloat();out[p+2]=t.height.toFloat();out[p+3]=t.wrapU.ordinal.toFloat();out[p+4]=t.wrapV.ordinal.toFloat();out[p+5]=t.filter.ordinal.toFloat()
            val packed=t.rgba8()
            if(packed!=null) {
                out[p]=byteAt.toFloat();out[p+6]=1f
                packed.copyInto(bytes,(byteAt*4).toInt());byteAt+=t.width.toLong()*t.height
            } else {
                out[p]=at.toFloat();out[p+6]=0f
                val rgba=t.rgba();rgba.copyInto(out,at);at+=rgba.size
            }
        }
        color(8,scene.environment.ambient);color(12,scene.environment.background)
        scene.fog?.let { color(16,it.color);out[20]=it.density;out[21]=it.gradient;out[22]=1f }
        out[24]=scene.environment.texture?.toFloat() ?: -1f;out[25]=scene.environment.intensity;out[26]=scene.environment.rotation;out[27]=if(scene.environment.hdr)1f else 0f
        out[28]=-1f
        out[29]=total.toFloat() // the byte section starts right after the float section
        scene.environment.ambientCube?.let { cube ->
            out[28]=at.toFloat()
            cube.forEachIndexed { i,c -> out[at+i*3]=c.r;out[at+i*3+1]=c.g;out[at+i*3+2]=c.b }
            at+=18
        }
        return MetalSceneData(out,bytes)
    }
}

private const val MAX_SCENE_FLOATS = 16L*1024*1024
private const val MAX_TEXTURE_TEXELS = 16L*1024*1024
