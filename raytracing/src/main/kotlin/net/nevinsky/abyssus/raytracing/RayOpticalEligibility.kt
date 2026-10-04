/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

/** CPU boundary validation, across material parts, before any native upload. Medium overlap is checked per path. */
class RayOpticalEligibility {
    private data class Point(val x:Float,val y:Float,val z:Float)
    private data class Face(val a:Int,val b:Int,val c:Int)
    private data class Use(val from:Int,val to:Int,val face:Int)
    fun unsupportedReason(scene:RaySceneSnapshot):String? {
        for(parts in scene.instances.groupBy { it.id.substringBefore('/') }.values) {
            val materials=parts.map { scene.materials[it.material] }
            if(materials.none { it.transmission>0f }) continue
            if(materials.any { it.kind!=RayMaterialKind.PBR || it.alphaMode!=RayAlphaMode.OPAQUE })
                return "Transmission requires opaque PBR materials"
            if(materials.any { it.transmission<=0f } || materials.map { it.ior }.distinct().size!=1)
                return "A transmissive solid requires consistent optical boundary materials"
            val points=linkedMapOf<Point,Int>();val faces=mutableListOf<Face>()
            var volume=0.0
            for(part in parts) {
                val mesh=scene.meshes[part.mesh];val p=mesh.positions();val indices=mesh.indices();val m=part.transform()
                val x=RayVec3(m[0],m[1],m[2]);val y=RayVec3(m[4],m[5],m[6]);val z=RayVec3(m[8],m[9],m[10])
                val sign=if(x.dot(y.cross(z))<0f) -1 else 1
                fun point(i:Int):Point {
                    val a=p[i*3];val b=p[i*3+1];val c=p[i*3+2]
                    return Point(m[0]*a+m[4]*b+m[8]*c+m[12],m[1]*a+m[5]*b+m[9]*c+m[13],m[2]*a+m[6]*b+m[10]*c+m[14])
                }
                for(i in indices.indices step 3) {
                    val a=point(indices[i]);val b=point(indices[i+1]);val c=point(indices[i+2])
                    val ids=listOf(a,b,c).map { points.getOrPut(it) { points.size } }
                    if(ids.distinct().size!=3) return "Transmission requires a closed nondegenerate solid"
                    val av=RayVec3(a.x,a.y,a.z);val bv=RayVec3(b.x,b.y,b.z);val cv=RayVec3(c.x,c.y,c.z)
                    if((bv-av).cross(cv-av).dot((bv-av).cross(cv-av))<=1e-16f) return "Transmission requires a closed nondegenerate solid"
                    volume+=av.dot(bv.cross(cv)).toDouble()*sign
                    faces+=Face(ids[0],ids[1],ids[2])
                }
            }
            val edges=mutableMapOf<Pair<Int,Int>,MutableList<Use>>()
            for((i,f) in faces.withIndex()) for((a,b) in listOf(f.a to f.b,f.b to f.c,f.c to f.a))
                edges.getOrPut(minOf(a,b) to maxOf(a,b)) { mutableListOf() }.add(Use(a,b,i))
            if(edges.values.any { it.size!=2 || it[0].from!=it[1].to || it[0].to!=it[1].from })
                return "Transmission requires a closed manifold with consistently oriented faces"
            if(volume<=1e-12) return "Transmission requires an outward-facing closed solid"
            val neighbors=Array(faces.size) { mutableListOf<Int>() }
            for(edge in edges.values) { neighbors[edge[0].face]+=edge[1].face;neighbors[edge[1].face]+=edge[0].face }
            val seen=mutableSetOf<Int>();val pending=ArrayDeque<Int>();pending.add(0)
            while(pending.isNotEmpty()) { val i=pending.removeFirst();if(seen.add(i)) pending.addAll(neighbors[i]) }
            if(seen.size!=faces.size) return "Transmission supports one connected closed solid per instance"
        }
        return null
    }
}
