/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <jni.h>
#include <simd/simd.h>
#include <vector>
#include <cstring>
#include <memory>

// C++ ownership avoids registering Objective-C classes when a plugin classloader is replaced.
struct AbyssusMetalSession {
    id<MTLDevice> device;
    id<MTLCommandQueue> queue;
    id<MTLComputePipelineState> pipeline;
    NSArray<id<MTLAccelerationStructure>> *geometry;
    NSArray<NSNumber *> *vertexOffsets;
    NSArray<NSNumber *> *indexOffsets;
    id<MTLBuffer> vertices, indices, color, depth;
    id<MTLCommandBuffer> inFlight;
};

static void fail(JNIEnv *env, NSString *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.UTF8String);
}

static std::unique_ptr<AbyssusMetalSession> makeSession(JNIEnv *env, jbyteArray shader) {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (!device || !device.supportsRaytracing) {
        fail(env, @"Metal ray tracing is unavailable on this device");
        return nullptr;
    }
    jsize length = env->GetArrayLength(shader);
    NSMutableData *bytes = [NSMutableData dataWithLength:length];
    env->GetByteArrayRegion(shader, 0, length, static_cast<jbyte *>(bytes.mutableBytes));
    if (env->ExceptionCheck()) return nullptr;
    // dispatch_data owns a copy; the JNI buffer cannot outlive this call.
    dispatch_data_t data = dispatch_data_create(bytes.bytes, length, nil, DISPATCH_DATA_DESTRUCTOR_DEFAULT);
    NSError *error = nil;
    id<MTLLibrary> library = [device newLibraryWithData:data error:&error];
    if (!library) { fail(env, error.localizedDescription ?: @"Cannot load Metal shader"); return nullptr; }
    id<MTLFunction> function = [library newFunctionWithName:@"raySlice"];
    id<MTLComputePipelineState> pipeline = function ? [device newComputePipelineStateWithFunction:function error:&error] : nil;
    if (!pipeline) { fail(env, error.localizedDescription ?: @"Missing Metal raySlice kernel"); return nullptr; }
    id<MTLCommandQueue> queue = [device newCommandQueue];
    id<MTLBuffer> buffer = [device newBufferWithLength:16 options:MTLResourceStorageModeShared];
    if (!queue || !buffer) { fail(env, @"Metal queue/readback allocation failed"); return nullptr; }
    auto session = std::make_unique<AbyssusMetalSession>();
    session->device = device;
    session->queue = queue;
    session->pipeline = pipeline;
    return session;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_probe(JNIEnv *env, jobject, jbyteArray shader) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        jlong values[5] = {0, 0, 0, 0, 0};
        if (device && device.supportsRaytracing) {
            if (!makeSession(env, shader)) return nullptr;
            values[0] = values[1] = values[2] = 1;
            values[3] = 4096; // This renderer's cap, below Metal's texture dimension bound.
            values[4] = static_cast<jlong>(MIN(device.recommendedMaxWorkingSetSize / 4, 512ULL * 1024 * 1024));
        }
        jlongArray result = env->NewLongArray(5);
        if (result) env->SetLongArrayRegion(result, 0, 5, values);
        return result;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_create(JNIEnv *env, jobject, jbyteArray shader) {
    @autoreleasepool {
        auto session = makeSession(env, shader);
        return reinterpret_cast<jlong>(session.release());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_destroy(JNIEnv *, jobject, jlong handle) {
    @autoreleasepool {
        if (handle) {
            std::unique_ptr<AbyssusMetalSession> session(reinterpret_cast<AbyssusMetalSession *>(handle));
            // Runs on the owner worker; resources live until submitted commands complete.
            [session->inFlight waitUntilCompleted];
        }
    }
}

static AbyssusMetalSession *sessionAt(jlong handle) {
    return reinterpret_cast<AbyssusMetalSession *>(handle);
}
extern "C" JNIEXPORT jlong JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_openSession(JNIEnv *env, jobject, jlong handle) {
    @autoreleasepool {
        AbyssusMetalSession *backend = sessionAt(handle);
        if (!backend) { fail(env,@"Metal backend is closed"); return 0; }
        auto session = std::make_unique<AbyssusMetalSession>();
        session->device = backend->device;
        session->queue = backend->queue;
        session->pipeline = backend->pipeline;
        return reinterpret_cast<jlong>(session.release());
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_deviceName(JNIEnv *env, jobject, jlong handle) {
    @autoreleasepool { return env->NewStringUTF(sessionAt(handle)->device.name.UTF8String); }
}

static std::vector<float> floats(JNIEnv *env, jfloatArray array) {
    std::vector<float> values(env->GetArrayLength(array));
    env->GetFloatArrayRegion(array,0,static_cast<jsize>(values.size()),values.data());
    return values;
}
static std::vector<jint> integers(JNIEnv *env, jintArray array) {
    std::vector<jint> values(env->GetArrayLength(array));
    env->GetIntArrayRegion(array,0,static_cast<jsize>(values.size()),values.data());
    return values;
}

extern "C" JNIEXPORT void JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_setGeometry(JNIEnv *env, jobject, jlong handle,
    jfloatArray vertexArray, jintArray indexArray, jintArray vertexCountArray, jintArray indexCountArray) {
    @autoreleasepool {
        AbyssusMetalSession *session = sessionAt(handle);
        auto vertices = floats(env,vertexArray);
        auto indices = integers(env,indexArray);
        auto vertexCounts = integers(env,vertexCountArray);
        auto indexCounts = integers(env,indexCountArray);
        if (env->ExceptionCheck()) return;
        if (session->inFlight || vertexCounts.empty() || vertexCounts.size() != indexCounts.size() || vertexCounts.size() > 128) {
            fail(env,@"Invalid Metal geometry update"); return;
        }
        id<MTLDevice> device = session->device;
        id<MTLBuffer> vertexBuffer = [device newBufferWithBytes:vertices.data() length:vertices.size()*sizeof(float) options:MTLResourceStorageModeShared];
        id<MTLBuffer> indexBuffer = [device newBufferWithBytes:indices.data() length:indices.size()*sizeof(jint) options:MTLResourceStorageModeShared];
        if (!vertexBuffer || !indexBuffer) { fail(env,@"Metal geometry allocation failed"); return; }
        NSMutableArray *built = [NSMutableArray array];
        NSMutableArray *vertexOffsets = [NSMutableArray array];
        NSMutableArray *indexOffsets = [NSMutableArray array];
        id<MTLCommandBuffer> command = [session->queue commandBuffer];
        id<MTLAccelerationStructureCommandEncoder> encoder = [command accelerationStructureCommandEncoder];
        NSUInteger vertexOffset = 0, indexOffset = 0;
        uint64_t budget = vertexBuffer.length + indexBuffer.length;
        for (size_t i = 0; i < vertexCounts.size(); i++) {
            if (vertexCounts[i] <= 0 || indexCounts[i] <= 0 || indexCounts[i] % 3 ||
                (vertexOffset+vertexCounts[i])*3 > vertices.size() || indexOffset+indexCounts[i] > indices.size()) {
                [encoder endEncoding]; fail(env,@"Invalid Metal mesh range"); return;
            }
            MTLAccelerationStructureTriangleGeometryDescriptor *triangles = [MTLAccelerationStructureTriangleGeometryDescriptor descriptor];
            triangles.vertexBuffer = vertexBuffer;
            triangles.vertexBufferOffset = vertexOffset*3*sizeof(float);
            triangles.vertexStride = 3*sizeof(float);
            triangles.vertexFormat = MTLAttributeFormatFloat3;
            triangles.indexBuffer = indexBuffer;
            triangles.indexBufferOffset = indexOffset*sizeof(jint);
            triangles.indexType = MTLIndexTypeUInt32;
            triangles.triangleCount = indexCounts[i]/3;
            triangles.opaque = YES;
            MTLPrimitiveAccelerationStructureDescriptor *descriptor = [MTLPrimitiveAccelerationStructureDescriptor descriptor];
            descriptor.geometryDescriptors = @[triangles];
            MTLAccelerationStructureSizes sizes = [device accelerationStructureSizesWithDescriptor:descriptor];
            budget += sizes.accelerationStructureSize + sizes.buildScratchBufferSize;
            if (budget > MIN(device.recommendedMaxWorkingSetSize/4,512ULL*1024*1024)) {
                [encoder endEncoding]; fail(env,@"Metal geometry exceeds the resource budget"); return;
            }
            id<MTLAccelerationStructure> geometry = [device newAccelerationStructureWithSize:sizes.accelerationStructureSize];
            id<MTLBuffer> scratch = [device newBufferWithLength:sizes.buildScratchBufferSize options:MTLResourceStorageModePrivate];
            if (!geometry || !scratch) { [encoder endEncoding]; fail(env,@"Metal acceleration structure allocation failed"); return; }
            [encoder buildAccelerationStructure:geometry descriptor:descriptor scratchBuffer:scratch scratchBufferOffset:0];
            [built addObject:geometry];
            [vertexOffsets addObject:@(vertexOffset)];
            [indexOffsets addObject:@(indexOffset)];
            vertexOffset += vertexCounts[i];
            indexOffset += indexCounts[i];
        }
        [encoder endEncoding];
        [command commit];
        [command waitUntilCompleted]; // Preparation on the worker only; submit/poll never wait.
        if (command.status == MTLCommandBufferStatusError) { fail(env,command.error.localizedDescription); return; }
        session->geometry = built;
        session->vertexOffsets = vertexOffsets;
        session->indexOffsets = indexOffsets;
        session->vertices = vertexBuffer;
        session->indices = indexBuffer;
    }
}

struct SliceInstance { simd_float4x4 transform; simd_float4 color; simd_uint4 offsets; };
static_assert(sizeof(SliceInstance) == 96,"Metal instance layout must agree with shader");

extern "C" JNIEXPORT void JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_submit(JNIEnv *env, jobject, jlong handle, jint width, jint height,
    jfloatArray cameraArray, jfloatArray instanceArray, jintArray meshArray) {
    @autoreleasepool {
        AbyssusMetalSession *session = sessionAt(handle);
        auto camera = floats(env,cameraArray);
        auto values = floats(env,instanceArray);
        auto meshes = integers(env,meshArray);
        if (env->ExceptionCheck()) return;
        if (session->inFlight || !session->geometry || camera.size() != 20 || meshes.empty() || meshes.size() > 128 ||
            values.size() != meshes.size()*20 || width <= 0 || height <= 0 || width > 4096 || height > 4096 ||
            static_cast<uint64_t>(width)*height > 4194304) {
            fail(env,@"Invalid Metal render request"); return;
        }
        std::vector<MTLAccelerationStructureInstanceDescriptor> descriptors(meshes.size());
        std::vector<SliceInstance> uniforms(meshes.size());
        for (size_t i = 0; i < meshes.size(); i++) {
            if (meshes[i] < 0 || static_cast<NSUInteger>(meshes[i]) >= session->geometry.count) { fail(env,@"Invalid Metal mesh id"); return; }
            const float *matrix = values.data()+i*20;
            for (int col = 0; col < 4; col++)
                std::memcpy(reinterpret_cast<float *>(&descriptors[i].transformationMatrix)+col*3,matrix+col*4,3*sizeof(float));
            descriptors[i].options = MTLAccelerationStructureInstanceOptionOpaque;
            descriptors[i].mask = 0xff;
            descriptors[i].accelerationStructureIndex = meshes[i];
            std::memcpy(&uniforms[i].transform,matrix,16*sizeof(float));
            std::memcpy(&uniforms[i].color,matrix+16,4*sizeof(float));
            uniforms[i].offsets = simd_make_uint4(session->vertexOffsets[meshes[i]].unsignedIntValue,session->indexOffsets[meshes[i]].unsignedIntValue,0,0);
        }
        id<MTLDevice> device = session->device;
        id<MTLBuffer> instances = [device newBufferWithBytes:descriptors.data() length:descriptors.size()*sizeof(descriptors[0]) options:MTLResourceStorageModeShared];
        id<MTLBuffer> shading = [device newBufferWithBytes:uniforms.data() length:uniforms.size()*sizeof(uniforms[0]) options:MTLResourceStorageModeShared];
        MTLInstanceAccelerationStructureDescriptor *descriptor = [MTLInstanceAccelerationStructureDescriptor descriptor];
        descriptor.instanceDescriptorBuffer = instances;
        descriptor.instanceCount = meshes.size();
        descriptor.instancedAccelerationStructures = session->geometry;
        MTLAccelerationStructureSizes sizes = [device accelerationStructureSizesWithDescriptor:descriptor];
        id<MTLAccelerationStructure> top = [device newAccelerationStructureWithSize:sizes.accelerationStructureSize];
        id<MTLBuffer> scratch = [device newBufferWithLength:sizes.buildScratchBufferSize options:MTLResourceStorageModePrivate];
        NSUInteger pixels = static_cast<NSUInteger>(width)*height;
        id<MTLBuffer> color = [device newBufferWithLength:pixels*4*sizeof(float) options:MTLResourceStorageModeShared];
        id<MTLBuffer> depth = [device newBufferWithLength:pixels*sizeof(float) options:MTLResourceStorageModeShared];
        if (!instances || !shading || !top || !scratch || !color || !depth) { fail(env,@"Metal frame allocation failed"); return; }
        id<MTLCommandBuffer> command = [session->queue commandBuffer];
        id<MTLAccelerationStructureCommandEncoder> build = [command accelerationStructureCommandEncoder];
        [build buildAccelerationStructure:top descriptor:descriptor scratchBuffer:scratch scratchBufferOffset:0];
        [build endEncoding];
        id<MTLComputeCommandEncoder> compute = [command computeCommandEncoder];
        [compute setComputePipelineState:session->pipeline];
        [compute setAccelerationStructure:top atBufferIndex:0];
        for (id<MTLAccelerationStructure> geometry in session->geometry) [compute useResource:geometry usage:MTLResourceUsageRead];
        [compute setBuffer:shading offset:0 atIndex:1];
        [compute setBuffer:session->vertices offset:0 atIndex:2];
        [compute setBuffer:session->indices offset:0 atIndex:3];
        [compute setBuffer:color offset:0 atIndex:4];
        [compute setBuffer:depth offset:0 atIndex:5];
        [compute setBytes:camera.data() length:camera.size()*sizeof(float) atIndex:6];
        simd_uint2 dimensions = simd_make_uint2(width,height);
        [compute setBytes:&dimensions length:sizeof(dimensions) atIndex:7];
        NSUInteger groupWidth = session->pipeline.threadExecutionWidth;
        [compute dispatchThreads:MTLSizeMake(width,height,1) threadsPerThreadgroup:MTLSizeMake(groupWidth,1,1)];
        [compute endEncoding];
        session->color = color;
        session->depth = depth;
        session->inFlight = command;
        [command commit];
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_nevinsky_abyssus_raytracing_MetalBridge_poll(JNIEnv *env, jobject, jlong handle, jfloatArray color, jfloatArray depth) {
    @autoreleasepool {
        AbyssusMetalSession *session = sessionAt(handle);
        id<MTLCommandBuffer> command = session->inFlight;
        if (!command || command.status < MTLCommandBufferStatusCompleted) return JNI_FALSE;
        if (command.status == MTLCommandBufferStatusError) {
            session->inFlight = nil;
            NSError *error = command.error;
            if ([error.domain isEqualToString:MTLCommandBufferErrorDomain] &&
                (error.code == MTLCommandBufferErrorTimeout || error.code == MTLCommandBufferErrorDeviceRemoved)) {
                env->ThrowNew(env->FindClass("net/nevinsky/abyssus/raytracing/RayDeviceLostException"),error.localizedDescription.UTF8String);
            } else fail(env,error.localizedDescription ?: @"Metal render failed");
            return JNI_FALSE;
        }
        if (static_cast<NSUInteger>(env->GetArrayLength(color))*sizeof(float) != session->color.length || static_cast<NSUInteger>(env->GetArrayLength(depth))*sizeof(float) != session->depth.length) {
            fail(env,@"Metal readback size mismatch"); return JNI_FALSE;
        }
        env->SetFloatArrayRegion(color,0,env->GetArrayLength(color),static_cast<const jfloat *>(session->color.contents));
        env->SetFloatArrayRegion(depth,0,env->GetArrayLength(depth),static_cast<const jfloat *>(session->depth.contents));
        session->inFlight = nil;
        session->color = nil;
        session->depth = nil;
        return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
    }
}
