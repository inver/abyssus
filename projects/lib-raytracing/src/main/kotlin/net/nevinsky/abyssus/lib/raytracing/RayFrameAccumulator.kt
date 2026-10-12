/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

/** Worker-owned sample-weighted history. Native kernels return the mean of each bounded batch. */
internal class RayFrameAccumulator {
    private data class History(val key:RayFrameKey,val width:Int,val height:Int,val epoch:Long,val revision:Long,
        val reflections:Int,val refractions:Int)
    private var history:History?=null
    private var samples=0
    private var colors:FloatArray?=null
    fun clear() { history=null;samples=0;colors=null }
    fun add(request:RaySceneRequest,frame:RayFrame):RayFrame {
        val next=History(request.key,request.width,request.height,request.accumulationEpoch,request.settingsRevision,
            request.maxReflectionBounces,request.maxRefractionBounces)
        val previous=colors
        val blend=request.sampleOffset>0 && history==next && samples==request.sampleOffset && previous!=null
        // the incoming frame is discarded, so its arrays are adopted; only a blend needs a fresh array because history is read from `previous`
        val values=if(blend) frame.colorValues() else frame.colorView()
        if(blend && previous!=null) {
            val total=samples+request.samples
            for(i in values.indices) values[i]=(previous[i]*samples+values[i]*request.samples)/total
            samples=total
        } else samples=request.sampleOffset+request.samples
        history=next;colors=values
        return RayFrame(frame.key,frame.width,frame.height,values,frame.depthView(),true)
    }
}
