// The display transform of a host that lights in linear space (OutputTransform.kt is the reference). Compiled only when
// the environment carries an OutputTransformAttribute (outputTransformFlag); a host without one gets no change at all.
#ifdef outputTransformFlag
uniform float u_exposure;

vec3 outputSrgbToLinear(vec3 c) {
	return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 outputLinearToSrgb(vec3 c) {
	c = clamp(c, 0.0, 1.0);
	return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

// Khronos PBR Neutral tone mapping
vec3 outputToneMap(vec3 color) {
	const float startCompression = 0.8 - 0.04;
	const float desaturation = 0.15;
	float x = min(color.r, min(color.g, color.b));
	float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
	color -= offset;
	float peak = max(color.r, max(color.g, color.b));
	if (peak < startCompression) return color;
	const float d = 1.0 - startCompression;
	float newPeak = 1.0 - d * d / (peak + d - startCompression);
	color *= newPeak / peak;
	float g = 1.0 - 1.0 / (desaturation * (peak - newPeak) + 1.0);
	return mix(color, newPeak * vec3(1.0), g);
}

vec3 outputTransform(vec3 linear) {
	return outputLinearToSrgb(outputToneMap(max(linear, 0.0) * u_exposure));
}

// a colour texture is authored as display colour
vec4 sampleColor(sampler2D s, vec2 uv) {
	vec4 c = texture2D(s, uv);
	return vec4(outputSrgbToLinear(c.rgb), c.a);
}
#else
#define sampleColor texture2D
#endif
