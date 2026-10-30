// A fullscreen triangle, no cube: the view direction is rebuilt per pixel from the inverse view-projection (camera
// translation removed), at the far plane (z = w) so everything else is drawn in front of the sky.
attribute vec2 a_position;
uniform mat4 u_invViewProj;
varying vec3 v_dir;
void main() {
    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);
    v_dir = farPoint.xyz / farPoint.w;
    gl_Position = vec4(a_position, 1.0, 1.0);
}
