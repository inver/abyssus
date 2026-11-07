#version 150
// Screen-space ambient occlusion (SsaoPass.kt), at half the frame's size: each pixel's view-space position comes from
// the frame's depth, its normal from the nearer of its neighbours (so silhouettes do not smear), and u_kernel's points in
// the hemisphere above it, turned per pixel, test whether the depth buffer has something in front of them. Writes the
// openness (1 open, 0 enclosed) and the pixel's view depth for the blur.
uniform sampler2D u_depth;
uniform mat4 u_projection;
uniform mat4 u_inverseProjection;
uniform vec2 u_depthTexel; // one texel of u_depth
uniform float u_radius; // metres
uniform vec3 u_kernel[12];
in vec2 v_uv;
out vec4 fragColor;

vec3 viewPosition(vec2 uv) {
    float depth = texture(u_depth, uv).r;
    vec4 view = u_inverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float depth = texture(u_depth, v_uv).r;
    if (depth >= 1.0) {
        fragColor = vec4(1.0, -1.0e6, 0.0, 1.0);
        return;
    }
    vec3 p = viewPosition(v_uv);
    vec3 left = viewPosition(v_uv - vec2(u_depthTexel.x, 0.0));
    vec3 right = viewPosition(v_uv + vec2(u_depthTexel.x, 0.0));
    vec3 down = viewPosition(v_uv - vec2(0.0, u_depthTexel.y));
    vec3 up = viewPosition(v_uv + vec2(0.0, u_depthTexel.y));
    vec3 dx = abs(right.z - p.z) < abs(p.z - left.z) ? right - p : p - left;
    vec3 dy = abs(up.z - p.z) < abs(p.z - down.z) ? up - p : p - down;
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, p) > 0.0) n = -n;

    float angle = hash(gl_FragCoord.xy) * 6.2831853;
    vec3 turn = vec3(cos(angle), sin(angle), 0.0);
    vec3 t = normalize(turn - n * dot(turn, n));
    mat3 frame = mat3(t, cross(n, t), n);

    float occlusion = 0.0;
    for (int i = 0; i < 12; i++) {
        vec3 s = p + frame * u_kernel[i] * u_radius;
        vec4 clip = u_projection * vec4(s, 1.0);
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) continue;
        float sceneZ = viewPosition(uv).z;
        // something in front of the sample, near enough to shade it
        float near = smoothstep(0.0, 1.0, u_radius / max(abs(p.z - sceneZ), 1.0e-4));
        // the bias grows with distance, as the depth's precision falls
        float bias = 0.05 * u_radius + 0.004 * -p.z;
        occlusion += (sceneZ >= s.z + bias ? 1.0 : 0.0) * near;
    }
    fragColor = vec4(1.0 - occlusion / 12.0, p.z, 0.0, 1.0);
}
