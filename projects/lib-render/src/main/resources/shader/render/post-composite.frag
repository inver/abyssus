// The final pass of a host frame drawn in linear HDR (CompositePass.kt): bloom and ambient occlusion in linear light,
// then exposure and the shared tone map (output_transform.glsl, included above by CompositePass), vignette and grade,
// and the sRGB encode, once per pixel. It is blended over the host's display-referred background (ONE,
// ONE_MINUS_SRC_ALPHA): the frame's alpha is the coverage of its surfaces.
uniform sampler2D u_hdr;
uniform sampler2D u_bloom;
uniform sampler2D u_ao;
uniform int u_hasBloom;
uniform int u_hasAo;
uniform float u_bloomStrength;
uniform float u_aoStrength;
uniform float u_vignette;
uniform vec3 u_lift;
uniform vec3 u_gamma;
uniform vec3 u_gain;
uniform vec2 u_aspect; // the frame's width and height over the larger of them

in vec2 v_uv;
out vec4 fragColor;

// tone mapped colour (0..1) to display colour: vignette and grade, then the encode
vec3 display(vec3 linear) {
    vec3 c = outputToneMap(max(linear, 0.0) * u_exposure);
    if (u_vignette > 0.0) {
        vec2 d = (v_uv - 0.5) * u_aspect * 2.0;
        c *= 1.0 - u_vignette * smoothstep(0.35, 1.45, dot(d, d));
    }
    c = pow(max(c * u_gain + u_lift * (1.0 - c), 0.0), 1.0 / u_gamma);
    return outputLinearToSrgb(c);
}

void main() {
    vec4 hdr = texture(u_hdr, v_uv);
    float coverage = clamp(hdr.a, 0.0, 1.0);
    // the resolve averaged colour and coverage alike: undo it to get the surfaces' own light
    vec3 c = hdr.rgb / max(coverage, 1.0 / 255.0);
    if (u_hasAo == 1) {
        float luminance = dot(c, vec3(0.2126, 0.7152, 0.0722));
        float occlusion = mix(1.0, texture(u_ao, v_uv).r, u_aoStrength);
        // bright emissive surfaces are not occluded
        c *= mix(occlusion, 1.0, smoothstep(2.0, 4.0, luminance));
    }
    vec3 bloom = u_hasBloom == 1 ? texture(u_bloom, v_uv).rgb * u_bloomStrength : vec3(0.0);
    vec3 lit = display(c + bloom) * coverage;
    // where no surface covers the background, the glow alone, as display light over it (no vignette or grade: the
    // background is shown as it is)
    vec3 glow = u_hasBloom == 1 ? outputLinearToSrgb(outputToneMap(bloom * u_exposure)) * (1.0 - coverage) : vec3(0.0);
    fragColor = vec4(lit + glow, coverage);
}
