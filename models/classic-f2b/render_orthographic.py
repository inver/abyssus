import bpy, os
from mathutils import Vector
R=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(R,'Classic_F2B.blend'))
s=bpy.context.scene;s.render.engine='BLENDER_WORKBENCH'
s.display.shading.light='STUDIO';s.display.shading.color_type='SINGLE';s.display.shading.single_color=(.55,.61,.66)
s.display.shading.show_shadows=True;s.display.shading.show_cavity=True
s.render.film_transparent=False;s.display.shading.background_type='WORLD';s.world.color=(1,1,1)
s.render.resolution_x=1600;s.render.resolution_y=1000
c=s.camera;c.data.type='ORTHO'
for name,loc,target,scale in [('side',(.51,-4,0),(.51,0,0),1.4),('top',(.51,0,4),(.51,0,0),1.8)]:
    c.location=loc;c.rotation_euler=(Vector(target)-c.location).to_track_quat('-Z','Y').to_euler();c.data.ortho_scale=scale
    if name=='top':s.render.resolution_x=1400;s.render.resolution_y=1700;c.data.ortho_scale=1.85
    s.render.filepath=os.path.join(R,'orthographic-'+name+'.png');bpy.ops.render.render(write_still=True)
