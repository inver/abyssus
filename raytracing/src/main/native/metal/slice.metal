/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
#include <metal_stdlib>
#include <metal_raytracing>
using namespace metal;
using namespace raytracing;

struct SliceInstance { float4x4 transform; float4 color; uint4 offsets; };
struct SliceCamera { float4 originNear; float4 forwardFar; float4 rightFov; float4 upAspect; float4 light; };

static float3 surfaceNormal(uint instanceId, uint primitiveId, float3 direction,
    device const SliceInstance *instances, device const packed_float3 *vertices, device const uint *indices) {
    SliceInstance instance = instances[instanceId];
    uint offset = instance.offsets.y + primitiveId*3;
    float3 a = (instance.transform * float4(float3(vertices[instance.offsets.x + indices[offset]]),1)).xyz;
    float3 b = (instance.transform * float4(float3(vertices[instance.offsets.x + indices[offset+1]]),1)).xyz;
    float3 c = (instance.transform * float4(float3(vertices[instance.offsets.x + indices[offset+2]]),1)).xyz;
    float3 normal = normalize(cross(b-a,c-a));
    return dot(normal,direction) > 0 ? -normal : normal;
}

static float3 directColor(uint instanceId, float3 position, float3 normal, float3 light,
    instance_acceleration_structure scene, device const SliceInstance *instances) {
    ray shadow;
    shadow.origin = position + normal*0.001f;
    shadow.direction = light;
    shadow.min_distance = 0.001f;
    shadow.max_distance = 10000;
    intersector<triangle_data, instancing> visibility;
    visibility.accept_any_intersection(true);
    auto blocked = visibility.intersect(shadow,scene,0xff);
    float diffuse = blocked.type == intersection_type::none ? max(dot(normal,light),0.0f) : 0;
    return instances[instanceId].color.xyz * (0.1f + 0.9f*diffuse);
}

kernel void raySlice(instance_acceleration_structure scene [[buffer(0)]],
    device const SliceInstance *instances [[buffer(1)]],
    device const packed_float3 *vertices [[buffer(2)]],
    device const uint *indices [[buffer(3)]],
    device float4 *colors [[buffer(4)]],
    device float *depths [[buffer(5)]],
    constant SliceCamera &camera [[buffer(6)]],
    constant uint2 &dimensions [[buffer(7)]],
    uint2 pixel [[thread_position_in_grid]]) {
    if (any(pixel >= dimensions)) return;
    uint index = pixel.y*dimensions.x + pixel.x;
    float2 ndc = (float2(pixel)+0.5f)/float2(dimensions)*2-1;
    ray primary;
    primary.origin = camera.originNear.xyz;
    primary.direction = normalize(camera.forwardFar.xyz +
        camera.rightFov.xyz*ndc.x*camera.upAspect.w*camera.rightFov.w + camera.upAspect.xyz*ndc.y*camera.rightFov.w);
    float cosine = dot(primary.direction,camera.forwardFar.xyz);
    primary.min_distance = camera.originNear.w/cosine;
    primary.max_distance = camera.forwardFar.w/cosine;
    intersector<triangle_data, instancing> query;
    auto hit = query.intersect(primary,scene,0xff);
    float3 color = float3(0.05f,0.1f,0.2f);
    float depth = 1;
    if (hit.type != intersection_type::none) {
        float3 position = primary.origin + primary.direction*hit.distance;
        float3 normal = surfaceNormal(hit.instance_id,hit.primitive_id,primary.direction,instances,vertices,indices);
        float z = hit.distance*cosine;
        float near = camera.originNear.w, far = camera.forwardFar.w;
        depth = clamp(far/(far-near)-far*near/((far-near)*z),0.0f,1.0f);
        color = directColor(hit.instance_id,position,normal,camera.light.xyz,scene,instances);
        if (instances[hit.instance_id].color.w > 0.5f) {
            ray reflection;
            reflection.origin = position + normal*0.001f;
            reflection.direction = reflect(primary.direction,normal);
            reflection.min_distance = 0.001f;
            reflection.max_distance = 10000;
            auto secondary = query.intersect(reflection,scene,0xff);
            color = float3(0.05f,0.1f,0.2f);
            if (secondary.type != intersection_type::none) {
                float3 point = reflection.origin + reflection.direction*secondary.distance;
                float3 n = surfaceNormal(secondary.instance_id,secondary.primitive_id,reflection.direction,instances,vertices,indices);
                color = directColor(secondary.instance_id,point,n,camera.light.xyz,scene,instances);
            }
        }
    }
    colors[index] = float4(color,1);
    depths[index] = depth;
}
