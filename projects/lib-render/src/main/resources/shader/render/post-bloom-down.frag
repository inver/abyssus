#version 150
// One level down the bloom chain (BloomPass.kt): a 13-tap filter of the larger level (Jimenez, "Next generation post
// processing in Call of Duty: Advanced Warfare", 2014). On the first level the five 2 x 2 groups are Karis-averaged
// (weighted by 1 / (1 + luminance)), so a single very bright pixel does not flicker as it moves.
uniform sampler2D u_source;
uniform vec2 u_texel; // one texel of u_source
uniform int u_karis;
in vec2 v_uv;
out vec4 fragColor;

float weight(vec3 c) {
    return 1.0 / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722)));
}

vec3 group(vec3 a, vec3 b, vec3 c, vec3 d) {
    if (u_karis == 1) {
        float wa = weight(a), wb = weight(b), wc = weight(c), wd = weight(d);
        return (a * wa + b * wb + c * wc + d * wd) / (wa + wb + wc + wd);
    }
    return (a + b + c + d) * 0.25;
}

vec3 tap(float x, float y) {
    return texture(u_source, v_uv + vec2(x, y) * u_texel).rgb;
}

void main() {
    vec3 a = tap(-2.0, 2.0), b = tap(0.0, 2.0), c = tap(2.0, 2.0);
    vec3 d = tap(-2.0, 0.0), e = tap(0.0, 0.0), f = tap(2.0, 0.0);
    vec3 g = tap(-2.0, -2.0), h = tap(0.0, -2.0), i = tap(2.0, -2.0);
    vec3 j = tap(-1.0, 1.0), k = tap(1.0, 1.0), l = tap(-1.0, -1.0), m = tap(1.0, -1.0);
    vec3 color = group(j, k, l, m) * 0.5
        + group(a, b, d, e) * 0.125 + group(b, c, e, f) * 0.125
        + group(d, e, g, h) * 0.125 + group(e, f, h, i) * 0.125;
    fragColor = vec4(max(color, 0.0), 1.0);
}
