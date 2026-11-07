#version 150
// The ambient occlusion's 4 x 4 blur (SsaoPass.kt), weighted by how close each texel's view depth is to this one's, so
// it does not bleed across silhouettes.
uniform sampler2D u_ao; // R: openness, G: view depth
uniform vec2 u_texel;

// what a flat, open surface reads (the depth's own error at grazing views): read as fully open
const float OPEN = 0.92;
in vec2 v_uv;
out vec4 fragColor;

void main() {
    vec2 centre = texture(u_ao, v_uv).rg;
    float sum = 0.0;
    float weights = 0.0;
    for (int y = -2; y < 2; y++) {
        for (int x = -2; x < 2; x++) {
            vec2 s = texture(u_ao, v_uv + (vec2(x, y) + 0.5) * u_texel).rg;
            float w = exp(-abs(s.y - centre.y) * 4.0);
            sum += s.x * w;
            weights += w;
        }
    }
    float openness = weights > 0.0 ? sum / weights : centre.x;
    fragColor = vec4(clamp(openness / OPEN, 0.0, 1.0), 0.0, 0.0, 1.0);
}
