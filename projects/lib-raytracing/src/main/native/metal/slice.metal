/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
#include <metal_stdlib>
#include <metal_raytracing>
using namespace metal;
using namespace raytracing;

struct SliceInstance { float4x4 transform; float4 color; uint4 offsets; };
struct SliceCamera { float4 originNear; float4 forwardFar; float4 rightFov; float4 upAspect; float4 light; float4 transport; float4 work; };

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

// Scene ABI v1: scalar floats avoid host SIMD padding. See MetalSceneEncoding.
static float3 s3(device const float *d,uint i) { return float3(d[i],d[i+1],d[i+2]); }
static float4 s4(device const float *d,uint i) { return float4(d[i],d[i+1],d[i+2],d[i+3]); }
static int texIndex(int x,int size,bool repeat) { return repeat ? ((x%size)+size)%size : clamp(x,0,size-1); }
static float4 texel(device const float *d,uint t,int x,int y) {
    int w=int(d[t+1]),h=int(d[t+2]);
    x=texIndex(x,w,d[t+3]<.5f); y=texIndex(y,h,d[t+4]<.5f);
    if(d[t+6]>.5f) {
        // RGBA8 texels live after the float section; header slot 29 is where they start
        device const uchar4 *texels=(device const uchar4 *)(d+uint(d[29]));
        return float4(texels[uint(d[t])+uint(y*w+x)])/255.0f;
    }
    return s4(d,uint(d[t])+uint(y*w+x)*4);
}
static float4 sampleTexture(device const float *d,uint index,float2 uv) {
    uint t=uint(d[4])+index*8;
    float2 size=float2(d[t+1],d[t+2]);
    if(d[t+5]>.5f) { int2 p=int2(floor(uv*size));return texel(d,t,p.x,p.y); }
    float2 p=uv*size-.5f,f=fract(p);int2 q=int2(floor(p));
    return mix(mix(texel(d,t,q.x,q.y),texel(d,t,q.x+1,q.y),f.x),mix(texel(d,t,q.x,q.y+1),texel(d,t,q.x+1,q.y+1),f.x),f.y);
}
static float4 binding(device const float *d,uint m,uint slot,float2 uv,float4 fallback) {
    uint b=m+32+slot*8;
    return d[b]<0 ? fallback : sampleTexture(d,uint(d[b]),uv*float2(d[b+3],d[b+4])+float2(d[b+1],d[b+2]));
}
struct Work { uint queries,limit;int error;uint reflectionLimit,refractionLimit; };
static bool queryAllowed(thread Work &work) { work.queries++;if(work.queries>work.limit) { work.error=-1;return false; }return true; }
struct Surface { float3 position,normal,geometric;uint solid;float2 uv,splat;uint material; };
static Surface sceneSurface(uint instanceId,uint primitiveId,float2 bary,float3 position,float3 direction,
    device const SliceInstance *instances,device const packed_float3 *vertices,device const uint *indices,device const float *d) {
    SliceInstance instance=instances[instanceId];
    uint offset=instance.offsets.y+primitiveId*3;
    uint ia=instance.offsets.x+indices[offset],ib=instance.offsets.x+indices[offset+1],ic=instance.offsets.x+indices[offset+2];
    uint aa=uint(d[3])+ia*12,ab=uint(d[3])+ib*12,ac=uint(d[3])+ic*12;
    float3 weight=float3(1-bary.x-bary.y,bary.x,bary.y);
    float3 local=float3(vertices[ia])*weight.x+float3(vertices[ib])*weight.y+float3(vertices[ic])*weight.z;
    float3 a=(instance.transform*float4(float3(vertices[ia]),1)).xyz;
    float3 b=(instance.transform*float4(float3(vertices[ib]),1)).xyz;
    float3 c=(instance.transform*float4(float3(vertices[ic]),1)).xyz;
    float3 n=normalize(cross(b-a,c-a));
    float detSign=dot(instance.transform[0].xyz,cross(instance.transform[1].xyz,instance.transform[2].xyz))<0?-1.0f:1.0f;
    float3 geometric=n*detSign;
    if(d[aa+9]>.5f) {
        float3 localNormal=s3(d,aa)*weight.x+s3(d,ab)*weight.y+s3(d,ac)*weight.z;
        float3x3 matrix=float3x3(instance.transform[0].xyz,instance.transform[1].xyz,instance.transform[2].xyz);
        // Cofactor matrix is inverse-transpose up to determinant; preserve mirrored transforms.
        float det=dot(matrix[0],cross(matrix[1],matrix[2]));
        n=normalize((cross(matrix[1],matrix[2])*localNormal.x+cross(matrix[2],matrix[0])*localNormal.y+cross(matrix[0],matrix[1])*localNormal.z)*(det<0?-1.0f:1.0f));
    }
    float2 ua=float2(d[aa+3],d[aa+4]),ub=float2(d[ab+3],d[ab+4]),uc=float2(d[ac+3],d[ac+4]);
    float2 uv=ua*weight.x+ub*weight.y+uc*weight.z;
    uint material=uint(d[0])+uint(d[uint(d[5])+instanceId])*128;
    if(d[material+32+3*8]>=0) {
        float3 tangent,bitangent;
        if(d[aa+10]>.5f) {
            float4 t=s4(d,aa+5)*weight.x+s4(d,ab+5)*weight.y+s4(d,ac+5)*weight.z;
            tangent=normalize((instance.transform*float4(t.xyz,0)).xyz);bitangent=normalize(cross(n,tangent))*t.w;
        } else {
            float2 du1=(ub-ua)*float2(1,-1),du2=(uc-ua)*float2(1,-1);
            float3 dp1=b-a,dp2=c-a,dp2perp=cross(dp2,n),dp1perp=cross(n,dp1);
            tangent=dp2perp*du1.x+dp1perp*du2.x;bitangent=dp2perp*du1.y+dp1perp*du2.y;
            float scale=rsqrt(max(max(dot(tangent,tangent),dot(bitangent,bitangent)),1e-12f));tangent*=scale;bitangent*=scale;
        }
        float3 map=binding(d,material,3,uv,float4(.5f,.5f,1,1)).xyz*2-1;
        n=normalize(tangent*map.x+bitangent*map.y+n*map.z);
    }
    if(d[material+19]>.5f && dot(n,direction)>0) n=-n;
    Surface surface={position,n,geometric,uint(d[uint(d[6])+instanceId]),uv,local.xz/max(d[material+20],.0001f),material};return surface;
}
static float4 sceneBase(Surface s,device const float *d) {
    uint m=s.material;
    if(d[m+16]<1.5f) return s4(d,m)*binding(d,m,0,s.uv,float4(1));
    float4 color=binding(d,m,7,s.uv,float4(.8f,.8f,.8f,1));
    if(d[m+32+6*8]>=0) {
        float4 splat=binding(d,m,6,s.splat,float4(0));
        for(uint i=0;i<4;i++) if(d[m+32+(8+i)*8]>=0) color=mix(color,binding(d,m,8+i,s.uv,float4(0)),splat[i]);
    }
    return color;
}
#define CTX thread Work &work,instance_acceleration_structure scene,device const SliceInstance *instances,device const packed_float3 *vertices,device const uint *indices,device const float *d
#define ARGS work,scene,instances,vertices,indices,d
// Instance masks: bit 1 = visible to shadow and reflection rays, bit 2 = visible to primary rays (blended surfaces: bit 2 only).
constant uint kSecondaryMask=1;
constant uint kMaxCutoutLayers=8;
constant uint kMaxBlendLayers=16;

static bool isCutout(Surface s,device const float *d) {
    uint m=s.material;
    if(d[m+17]<.5f || d[m+17]>1.5f) return false;
    return sceneBase(s,d).w*d[m+15]<d[m+18];
}
struct Found { bool valid; float distance; uint instance; uint primitive; float2 bary; };
// Nearest hit that is not an alpha-test hole. Rays pass through holes (a miss after holes is a miss); more than kMaxCutoutLayers holes count as solid.
static Found traceMasked(float3 origin,float3 direction,float minimum,float maximum,uint mask,CTX) {
    Found none={false,0,0,0,float2(0)};
    Found last=none;
    float start=0;
    for(uint i=0;i<kMaxCutoutLayers;i++) {
        ray r;r.origin=origin+direction*start;r.direction=direction;
        r.min_distance=i==0?minimum:.0005f;r.max_distance=maximum-start;
        if(r.max_distance<=r.min_distance) return none;
        if(!queryAllowed(work)) return none;
        intersector<triangle_data,instancing> query;
        auto hit=query.intersect(r,scene,mask);
        if(hit.type==intersection_type::none) return none;
        Surface s=sceneSurface(hit.instance_id,hit.primitive_id,hit.triangle_barycentric_coord,r.origin+direction*hit.distance,direction,instances,vertices,indices,d);
        last.valid=true;last.distance=start+hit.distance;last.instance=hit.instance_id;last.primitive=hit.primitive_id;last.bary=hit.triangle_barycentric_coord;
        if(!isCutout(s,d)) return last;
        start+=hit.distance;
    }
    return last;
}
// Diffuse ambient: the flat colour, or the sky's six axis colours (+X, -X, +Y, -Y, +Z, -Z) blended by the squared normal
// exactly as the raster shaders do (a non-negative component takes the odd slot).
static float3 ambientAt(device const float *d,float3 normal) {
    if(d[28]<0) return s3(d,8);
    uint c=uint(d[28]);float3 sq=normal*normal;
    return sq.x*s3(d,c+(normal.x>=0?3:0))+sq.y*s3(d,c+6+(normal.y>=0?3:0))+sq.z*s3(d,c+12+(normal.z>=0?3:0));
}
static float3 skyColor(device const float *d,float3 direction) {
    if(d[24]<0) return s3(d,12);
    float rotation=d[26]*M_PI_F/180,c=cos(rotation),s=sin(rotation);
    float3 r=normalize(float3(c*direction.x-s*direction.z,direction.y,s*direction.x+c*direction.z));
    // Equirectangular, same as the raster sky: centre column faces -Z, top row is +Y.
    float2 uv=float2(.5f+atan2(r.x,-r.z)/(2*M_PI_F),acos(clamp(r.y,-1.0f,1.0f))/M_PI_F);
    return sampleTexture(d,uint(d[24]),uv).xyz*d[25];
}
// What a camera ray that misses shows. The raster HDR sky is tone mapped for display (ACES fit, gamma 1/2.2) while
// reflections of it stay linear radiance, so only primary misses go through this.
static float3 skyDisplay(device const float *d,float3 direction) {
    float3 c=skyColor(d,direction);
    if(d[24]<0 || d[27]<.5f) return c;
    float3 mapped=clamp((c*(2.51f*c+.03f))/(c*(2.43f*c+.59f)+.14f),0.0f,1.0f);
    return pow(mapped,float3(1.0f/2.2f));
}
static uint pcg(uint v) { uint s=v*747796405u+2891336453u;uint w=((s>>((s>>28u)+4u))^s)*277803737u;return (w>>22u)^w; }
static float2 random2(uint2 p) { uint a=pcg(p.x+pcg(p.y));uint b=pcg(a+1u);return float2(a,b)*(1.0f/4294967296.0f); }
// GGX-sampled reflection direction; falls back to the mirror direction when the sample points below the surface.
static float3 reflectionDirection(float3 normal,float3 view,float roughness,uint2 pixel) {
    float3 mirror=reflect(-view,normal);
    float a=roughness*roughness,a2=a*a;
    float2 xi=random2(pixel);
    float phi=2*M_PI_F*xi.x,cosT=sqrt((1-xi.y)/(1+(a2-1)*xi.y)),sinT=sqrt(max(1-cosT*cosT,0.0f));
    float3 up=abs(normal.y)<.999f?float3(0,1,0):float3(1,0,0);
    float3 t=normalize(cross(up,normal)),b=cross(normal,t);
    float3 h=normalize(t*(sinT*cos(phi))+b*(sinT*sin(phi))+normal*cosT);
    float3 r=reflect(-view,h);
    return dot(r,normal)>0?r:mirror;
}
// Direct light, ambient and emission. [environment] replaces the ambient colour in the PBR specular term when set.
static float3 shade(Surface s,float3 view,bool hasEnvironment,float3 environment,CTX) {
    uint m=s.material;float4 base=sceneBase(s,d);
    float3 emission=s3(d,m+4)*binding(d,m,1,s.uv,float4(1)).xyz;
    float3 specular=s3(d,m+8)*binding(d,m,2,s.uv,float4(1)).xyz;
    float4 mr=binding(d,m,4,s.uv,float4(1));
    float metallic=clamp(d[m+13]*mr.b,0.0f,1.0f),roughness=clamp(d[m+14]*mr.g,.04f,1.0f);
    float alpha=roughness*roughness,a2=alpha*alpha;
    float3 f0=mix(float3(.04f),base.xyz,metallic),diffuse=base.xyz*(1-metallic);
    float nv=max(dot(s.normal,view),.0001f);
    bool pbr=d[m+16]>.5f && d[m+16]<1.5f;
    float3 ambientLight=ambientAt(d,s.normal);
    float3 ambient=ambientLight*base.xyz;
    if(pbr) {
        float4 r=roughness*float4(-1,-.0275f,-.572f,.022f)+float4(1,.0425f,1.04f,-.04f);
        float a004=min(r.x*r.x,exp2(-9.28f*nv))*r.x+r.y;
        float2 brdf=float2(-1.04f,1.04f)*a004+r.zw;
        float occlusion=binding(d,m,5,s.uv,float4(1)).r;
        ambient=(ambientLight*diffuse+(f0*brdf.x+brdf.y)*(hasEnvironment?environment:ambientLight))*occlusion;
    }
    float3 result=ambient+emission;
    for(uint i=0;i<uint(d[2]);i++) {
        uint l=uint(d[1])+i*16;float3 L;float distance=10000,attenuation=1;
        if(d[l+3]<.5f) L=-s3(d,l+8);
        else {
            float3 delta=s3(d,l+4)-s.position;float d2=dot(delta,delta);distance=sqrt(d2);L=delta*rsqrt(max(d2,1e-8f));
            float range=d[l+7];if(range<=0) continue;
            attenuation=(1-smoothstep(.75f*range,range,distance))/(1+d2);
            if(d[l+3]>1.5f) {
                float outer=cos(d[l+11]*M_PI_F/180),inner=cos(d[l+11]*(1-d[l+12])*M_PI_F/180),c=dot(s3(d,l+8),-L);
                attenuation*=inner<=outer ? step(outer,c) : smoothstep(outer,inner,c);
            }
        }
        float nl=clamp(dot(s.normal,L),0.0f,1.0f);if(nl<=0 || attenuation<=0) continue;
        if(d[l+13]>.5f && traceMasked(s.position+s.normal*.001f,L,.001f,max(distance-.002f,.001f),kSecondaryMask,ARGS).valid) continue;
        float3 H=normalize(L+view);float nh=clamp(dot(s.normal,H),0.0f,1.0f);
        float3 contribution=base.xyz+specular*pow(nh,d[m+12]);
        if(pbr) {
            float vh=clamp(dot(view,H),0.0f,1.0f);float3 F=f0+(1-f0)*pow(1-vh,5.0f);
            float denominator=nh*nh*(a2-1)+1,distribution=a2/(M_PI_F*denominator*denominator);
            float gv=nl*sqrt(nv*nv*(1-a2)+a2),gl=nv*sqrt(nl*nl*(1-a2)+a2);
            contribution=(1-F)*diffuse+F*(distribution*.5f/max(gv+gl,1e-5f)*M_PI_F);
        }
        result+=contribution*s3(d,l)*(nl*attenuation);
    }
    return result;
}
// Single sampled continuation keeps work linear in the two independent event limits.
float opticalSample(uint2 p, uint sampleIndex, uint event) {
    uint seed=p.x*1973u+p.y*9277u+sampleIndex*26699u+event*31847u+89173u;
    seed=(seed^(seed>>16u))*0x7feb352du; seed=(seed^(seed>>15u))*0x846ca68bu; seed=seed^(seed>>16u);
    return float(seed&0xffffffu)/16777216.0f;
}
float dielectricFresnel(float cosine, float from, float to) {
    if(from==to) return 0.0f;
    float c=clamp(cosine,0.0f,1.0f), eta=from/to, sin2=eta*eta*(1.0f-c*c);
    if(sin2>=1.0f) return 1.0f;
    float ct=sqrt(1.0f-sin2), rs=(from*c-to*ct)/(from*c+to*ct), rp=(to*c-from*ct)/(to*c+from*ct);
    return clamp((rs*rs+rp*rp)*0.5f,0.0f,1.0f);
}
float3 specularWeight(Surface s, float3 view,device const float *d) {
    uint m=s.material;
    float3 base=sceneBase(s,d).xyz;
    float4 mr=binding(d,m,4u,s.uv,float4(1.0f));
    float metallic=clamp(d[m+13u]*mr.b,0.0f,1.0f), roughness=clamp(d[m+14u]*mr.g,0.04f,1.0f);
    float4 r=roughness*float4(-1.0f,-0.0275f,-0.572f,0.022f)+float4(1.0f,0.0425f,1.04f,-0.04f);
    float nv=max(dot(s.normal,view),0.0001f), a004=min(r.x*r.x,exp2(-9.28f*nv))*r.x+r.y;
    float2 brdf=float2(-1.04f,1.04f)*a004+r.zw;
    return max((mix(float3(0.04f),base,metallic)*brdf.x+brdf.y)*binding(d,m,5u,s.uv,float4(1.0f)).r,float3(0.0f));
}
float3 shadeSurface(Surface s, float3 view, uint2 pixel, uint sampleIndex,CTX) {
    uint reflections=0u, refractions=0u, reflectionLimit=uint(work.reflectionLimit), refractionLimit=uint(work.refractionLimit);
    int medium=-1; float mediumIor=1.0f;
    float3 result=float3(0.0f), throughput=float3(1.0f);
    for(uint event=0u;event<=reflectionLimit+refractionLimit;event++) {
        uint m=s.material;
        if(!(d[m+16u]>0.5f && d[m+16u]<1.5f)) return result+throughput*shade(s,view,false,float3(0.0f),ARGS);
        float transmission=refractionLimit>0u?d[m+21u]:0.0f;
        if(transmission==0.0f && reflections==reflectionLimit) return result+throughput*shade(s,view,false,float3(0.0f),ARGS);
        float roughness=clamp(d[m+14u]*binding(d,m,4u,s.uv,float4(1.0f)).g,0.04f,1.0f);
        float3 direction=reflectionDirection(s.normal,view,roughness,pixel+uint2(sampleIndex*1973u+event*31847u,0u));
        float3 weight=specularWeight(s,view,d); bool transmitted=false, entering=false;
        if(transmission>0.0f) {
            entering=dot(view,s.geometric)>0.0f;
            if(medium>=0 && (medium!=int(s.solid) || entering)) { work.error=-2;return float3(0.0f); }
            if(medium<0 && !entering) { medium=int(s.solid);mediumIor=d[m+22u]; }
            float3 opposing=entering?s.geometric:-s.geometric;
            float from=medium>=0?mediumIor:1.0f, to=entering?d[m+22u]:1.0f;
            float3 refracted=refract(-view,opposing,from/to);
            float F=dielectricFresnel(dot(view,opposing),from,to);
            float3 reflectedWeight=weight*(1.0f-transmission)+float3(transmission*F);
            float transmittedWeight=transmission*(1.0f-F), maximum=max(max(reflectedWeight.r,reflectedWeight.g),reflectedWeight.b);
            float probability=maximum+transmittedWeight>0.0f?maximum/(maximum+transmittedWeight):1.0f;
            if(dot(refracted,refracted)>0.0f && opticalSample(pixel,sampleIndex,event)>=probability) {
                transmitted=true;direction=normalize(refracted);weight=float3(transmittedWeight/(1.0f-probability));
            } else weight=reflectedWeight/max(probability,0.000001f);
        }
        result+=throughput*shade(s,view,true,float3(0.0f),ARGS)*(1.0f-transmission);
        throughput*=weight;
        if(transmitted?refractions==refractionLimit:reflections==reflectionLimit) return result+throughput*skyColor(d,direction);
        if(transmitted) { refractions++;medium=entering?int(s.solid):-1;mediumIor=entering?d[m+22u]:1.0f; } else reflections++;
        float3 origin=s.position+s.geometric*(dot(direction,s.geometric)>=0.0f?0.001f:-0.001f);
        Found found=traceMasked(origin,direction,0.001f,10000.0f,kSecondaryMask,ARGS);
        if(!found.valid) { if(medium>=0) work.error=-2;return result+throughput*skyColor(d,direction); }
        s=sceneSurface(found.instance,found.primitive,found.bary,origin+direction*found.distance,direction,instances,vertices,indices,d);view=-direction;
    }
    work.error=-2;return float3(0.0f);
}

static float3 applyFog(device const float *d,float3 color,float distance) {
    if(d[22]<.5f) return color;
    float k=(1-exp(-1.0f))*d[20]*d[20];
    return mix(color,s3(d,16),min(distance*distance*k,1.0f));
}
kernel void rayScene(instance_acceleration_structure scene [[buffer(0)]],device const SliceInstance *instances [[buffer(1)]],
    device const packed_float3 *vertices [[buffer(2)]],device const uint *indices [[buffer(3)]],device float4 *colors [[buffer(4)]],
    device float *depths [[buffer(5)]],constant SliceCamera &camera [[buffer(6)]],constant uint2 &dimensions [[buffer(7)]],
    device const float *d [[buffer(8)]],uint2 pixel [[thread_position_in_grid]]) {
    if(any(pixel>=dimensions)) return;uint index=pixel.y*dimensions.x+pixel.x;
    float2 ndc=(float2(pixel)+.5f)/float2(dimensions)*2-1;
    float3 origin=camera.originNear.xyz;
    float3 direction=normalize(camera.forwardFar.xyz+camera.rightFov.xyz*ndc.x*camera.upAspect.w*camera.rightFov.w+camera.upAspect.xyz*ndc.y*camera.rightFov.w);
    float cosine=dot(direction,camera.forwardFar.xyz);
    float near=camera.originNear.w,far=camera.forwardFar.w;
    Work work={0,uint(camera.work.x),0,uint(camera.transport.x),uint(camera.transport.y)};
    float3 total=float3(0);float depth=1;
    for(uint batchSample=0;batchSample<uint(camera.transport.z);batchSample++) {
    work.queries=0;
    float3 accumulated=float3(0);float transmittance=1,start=0;bool finished=false;
    // Primary rays composite alpha-blended layers front to back; holes in alpha-tested surfaces are skipped.
    for(uint layer=0;layer<kMaxBlendLayers+kMaxCutoutLayers && !finished;layer++) {
        ray primary;primary.origin=origin+direction*start;primary.direction=direction;
        primary.min_distance=layer==0?near/cosine:.0005f;primary.max_distance=far/cosine-start;
        if(primary.max_distance<=primary.min_distance) break;
        if(!queryAllowed(work)) break;
        intersector<triangle_data,instancing> query;auto hit=query.intersect(primary,scene,0xff);
        if(hit.type==intersection_type::none) break;
        float travelled=start+hit.distance;
        Surface surface=sceneSurface(hit.instance_id,hit.primitive_id,hit.triangle_barycentric_coord,origin+direction*travelled,direction,instances,vertices,indices,d);
        if(isCutout(surface,d)) { start=travelled;continue; }
        float3 color=applyFog(d,shadeSurface(surface,-direction,pixel,uint(camera.transport.w)+batchSample,ARGS),travelled);
        if(d[surface.material+17]>1.5f) {
            float alpha=clamp(sceneBase(surface,d).w*d[surface.material+15],0.0f,1.0f);
            accumulated+=transmittance*alpha*color;transmittance*=1-alpha;start=travelled;
            if(transmittance<.003f) finished=true;
            continue;
        }
        float z=travelled*cosine;
        depth=clamp(far/(far-near)-far*near/((far-near)*z),0.0f,1.0f);
        accumulated+=transmittance*color;transmittance=0;finished=true;
    }
    if(transmittance>0) accumulated+=transmittance*skyDisplay(d,direction);
    total+=accumulated;
    }
    colors[index]=float4(total/camera.transport.z,work.error<0?float(work.error):1.0f);depths[index]=depth;
}
