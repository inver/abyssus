// The game's terrain: the core TerrainMesh layout (position, normal, texcoord0), lit by one directional light, with fog.
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform float u_terrainSize;
uniform vec3 u_cameraPosition;
uniform float u_fogDensity;
uniform float u_fogGradient;

varying vec2 v_uv;
varying vec2 v_splatUv;
varying vec3 v_normal;
varying float v_fog;

void main() {
    vec4 world = u_worldTrans * vec4(a_position, 1.0);
    v_uv = a_texCoord0;
    v_splatUv = a_position.xz / u_terrainSize;
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    float distance = length(world.xyz - u_cameraPosition);
    v_fog = u_fogDensity > 0.0 ? clamp(1.0 - exp(-pow(distance * u_fogDensity, u_fogGradient)), 0.0, 1.0) : 0.0;
    gl_Position = u_projViewTrans * world;
}
