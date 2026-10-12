import bpy, sys, math, json
from mathutils import Matrix, Vector
out = sys.argv[sys.argv.index('--')+1]
CGX = 0.38
for o in list(bpy.data.objects):
    if o.type != 'MESH':
        bpy.data.objects.remove(o)
for o in list(bpy.data.collections['07 Studio'].objects): bpy.data.objects.remove(o)
# blender (x tail, y inboard, z up) -> game (x inboard, y up, z nose), a mirror so the inboard wing is on +X
M = Matrix(((0,1,0,0),(0,0,1,0),(-1,0,0,0),(0,0,0,1))) @ Matrix.Translation((-CGX,0,0))
for o in bpy.data.objects:
    mw = o.matrix_world.copy()
    o.parent = None
    o.data = o.data.copy()
    o.data.transform(M @ mw)
    o.matrix_world = Matrix.Identity(4)
    o.data.flip_normals()
# the game frame is already glTF's: export with +Y up off so nothing is rotated again
pts = {}
for o in bpy.data.objects:
    if o.name.startswith('Main tyre') or o.name == 'Tail wheel':
        pts[o.name] = [v.co.copy() for v in o.data.vertices]
main = [p for k,v in pts.items() if k.startswith('Main') for p in v]
tail = pts['Tail wheel']
def minh(ps, p): return min(q.y*math.cos(p) + q.z*math.sin(p) for q in ps)
lo, hi = 0.0, 0.3
for _ in range(60):
    mid=(lo+hi)/2
    if minh(main,mid) < minh(tail,mid): lo=mid  # nose-up p raises nose side; find equal
    else: hi=mid
p=(lo+hi)/2
print("PITCH", math.degrees(p), "CLEAR", -min(minh(main,p),minh(tail,p)))
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_yup=False, export_apply=True, use_selection=False)
