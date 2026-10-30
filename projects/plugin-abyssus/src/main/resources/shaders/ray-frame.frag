#version 150
uniform sampler2D u_color;
uniform sampler2D u_depth;
in vec2 v_uv;
out vec4 fragColor;
void main() {
    fragColor = texture(u_color, v_uv);
    gl_FragDepth = texture(u_depth, v_uv).r;
}
