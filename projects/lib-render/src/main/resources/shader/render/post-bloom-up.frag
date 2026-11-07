#version 150
// One level up the bloom chain (BloomPass.kt): this level's own downsampled light plus the 3 x 3 tent-filtered sum of
// the levels below it, times u_scale (1, and 1 / the number of levels on the last step: the bloom then holds the light
// of its source, spread out).
uniform sampler2D u_down;  // this level, from the down chain
uniform sampler2D u_up;    // the sum of the levels below, at half this size
uniform vec2 u_upTexel;    // one texel of u_up
uniform float u_scale;
in vec2 v_uv;
out vec4 fragColor;

vec3 tap(float x, float y, float w) {
    return texture(u_up, v_uv + vec2(x, y) * u_upTexel).rgb * w;
}

void main() {
    vec3 below = tap(-1.0, 1.0, 1.0) + tap(0.0, 1.0, 2.0) + tap(1.0, 1.0, 1.0)
        + tap(-1.0, 0.0, 2.0) + tap(0.0, 0.0, 4.0) + tap(1.0, 0.0, 2.0)
        + tap(-1.0, -1.0, 1.0) + tap(0.0, -1.0, 2.0) + tap(1.0, -1.0, 1.0);
    fragColor = vec4((texture(u_down, v_uv).rgb + below / 16.0) * u_scale, 1.0);
}
