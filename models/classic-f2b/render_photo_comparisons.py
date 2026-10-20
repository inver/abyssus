"""Render the saved asset from each local photo's approximate viewing direction.

Run after build_classic.py. Cameras are qualitative matches, not calibrated
photogrammetry; the source photos are never modified.
"""
import os
import bpy
from mathutils import Vector

ROOT=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(ROOT,'Classic_F2B.blend'))
scene=bpy.context.scene
scene.cycles.samples=48
scene.render.resolution_x=1200
scene.render.resolution_y=900
scene.render.film_transparent=False
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.32,.35,.39,1)
scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.7
camera=scene.camera
for name,location,target,scale in [
    ('primary',(-1.9,1.4,3.2),(.49,0,0),1.88),
    ('opposite',(-1.9,-1.4,3.2),(.49,0,0),1.88),
    ('cockpit',(.65,-.60,.72),(.33,0,.055),.37),
]:
    camera.location=location
    camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler()
    camera.data.ortho_scale=scale
    scene.render.filepath=os.path.join(ROOT,'comparison-'+name+'.png')
    bpy.ops.render.render(write_still=True)
