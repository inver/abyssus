#ifdef shadowAtlasFlag
uniform sampler2D u_shadowAtlas;
uniform float u_shadowEnabled;
uniform vec2 u_shadowTexel;
uniform float u_shadowBias[16];
uniform mat4 u_shadowMatrices[16];
uniform vec4 u_shadowTiles[16];
uniform vec3 u_shadowPositions[16];
uniform float u_shadowFars[16];
uniform float u_dirShadowTile[2];
uniform float u_pointShadowTiles[30];
uniform float u_spotShadowTile[5];
float readPackedShadow(vec2 uv) {
    vec4 c = texture2D(u_shadowAtlas, uv);
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}
// Compare at actual texel centers, compensating for the receiver plane's depth slope.
float shadowCompare(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    sampleUv = clamp(sampleUv, lo, hi);
    return step(depth + dot(gradient, sampleUv - centerUv), readPackedShadow(sampleUv));
}
float shadowBilinear(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    vec2 pixel = sampleUv / u_shadowTexel - 0.5;
    vec2 f = fract(pixel);
    vec2 p = (floor(pixel) + 0.5) * u_shadowTexel;
    float a = shadowCompare(p, centerUv, depth, gradient, lo, hi);
    float b = shadowCompare(p + vec2(u_shadowTexel.x, 0.0), centerUv, depth, gradient, lo, hi);
    float c = shadowCompare(p + vec2(0.0, u_shadowTexel.y), centerUv, depth, gradient, lo, hi);
    float d = shadowCompare(p + u_shadowTexel, centerUv, depth, gradient, lo, hi);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float shadowTileVisibility(float tileValue, vec3 worldPos, float radialDepth, float ndotl) {
    if (u_shadowEnabled < 0.5 || tileValue < 0.0) return 1.0;
    int tile = int(tileValue + 0.5);
    vec4 clip = u_shadowMatrices[tile] * vec4(worldPos, 1.0);
    vec3 ndc = clip.xyz / (abs(clip.w) < 1e-6 ? 1e-6 : clip.w);
    vec4 rect = u_shadowTiles[tile];
    vec2 uv = rect.xy + (ndc.xy * 0.5 + 0.5) * rect.zw;
    float compareDepth = radialDepth >= 0.0 ? radialDepth : ndc.z * 0.5 + 0.5;
    compareDepth -= u_shadowBias[tile] * (0.2 + 2.0 * (1.0 - clamp(ndotl, 0.0, 1.0)));
    vec2 dx = dFdx(uv), dy = dFdy(uv);
    float zx = dFdx(compareDepth), zy = dFdy(compareDepth);
    float determinant = dx.x * dy.y - dx.y * dy.x;
    vec2 gradient = vec2(0.0);
    if (abs(determinant) > 1e-12)
        gradient = vec2(dy.y * zx - dx.y * zy, dx.x * zy - dy.x * zx) / determinant;
    // Derivatives must be evaluated before a per-fragment coverage branch; otherwise neighboring
    // fragments outside the map leave undefined slopes and produce flickering border artifacts.
    if (abs(clip.w) < 1e-6 || any(greaterThan(abs(ndc.xy), vec2(1.0))) || ndc.z < -1.0 || ndc.z > 1.0) return 1.0;
    vec2 texel = u_shadowTexel;
    // Limit discontinuous derivatives at silhouettes/cube-face boundaries; retain a small numerical bias.
    gradient = clamp(gradient, vec2(-0.05) / texel, vec2(0.05) / texel);
    compareDepth -= max(0.000002, dot(abs(gradient), texel) * 0.02);
    vec2 lo = rect.xy + texel * 0.5;
    vec2 hi = rect.xy + rect.zw - texel * 0.5;
    float visible = 0.0;
    visible += shadowBilinear(uv + vec2(-texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(-texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    return visible * 0.25;
}
float pointShadowVisibility(int lightIndex, vec3 worldPos, float ndotl) {
    float firstTile = u_pointShadowTiles[lightIndex * 6];
    if (u_shadowEnabled < 0.5 || firstTile < 0.0) return 1.0;
    int firstIndex = int(firstTile + 0.5);
    vec3 ray = worldPos - u_shadowPositions[firstIndex];
    vec3 a = abs(ray);
    int face = a.x >= a.y && a.x >= a.z ? (ray.x >= 0.0 ? 0 : 1) :
               (a.y >= a.z ? (ray.y >= 0.0 ? 2 : 3) : (ray.z >= 0.0 ? 4 : 5));
    float tile = u_pointShadowTiles[lightIndex * 6 + face];
    int ti = int(tile + 0.5);
    return shadowTileVisibility(tile, worldPos, length(worldPos - u_shadowPositions[ti]) / max(u_shadowFars[ti], 1e-4), ndotl);
}
#endif

#ifdef directionalVisibilityFlag
uniform sampler2D u_dirVisMap;
uniform mat4 u_dirVisWorldToMap;
uniform vec2 u_dirVisParams; // x: width of the edge fade, in map coordinates; y: visibility outside the map
uniform int u_dirVisIndex;
// The map's visibility at a world position; fades to the outside value over the map's edge, whatever the texture clamps to.
float directionalVisibility(vec3 worldPos) {
    vec2 uv = (u_dirVisWorldToMap * vec4(worldPos, 1.0)).xy;
    vec2 edge = min(uv, 1.0 - uv);
    float inside = clamp(min(edge.x, edge.y) / max(u_dirVisParams.x, 1e-4), 0.0, 1.0);
    return mix(u_dirVisParams.y, texture2D(u_dirVisMap, clamp(uv, 0.0, 1.0)).r, inside);
}
#endif // directionalVisibilityFlag
