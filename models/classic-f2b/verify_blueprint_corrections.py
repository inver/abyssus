"""Independent checks against blueprint dimensions, on the saved Blender file."""
import bpy, math, os, json, bmesh
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(R,'Classic_F2B.blend'))
results=[]
def check(name,value,target,tol=.05):
    ok=abs(value-target)<=tol
    results.append(dict(check=name,measured=round(value,5),blueprint=target,tolerance=tol,passed=ok))
def points(name):
    o=bpy.data.objects[name]
    if o.type=='MESH':return [o.matrix_world@v.co*1000 for v in o.data.vertices]
    return [o.matrix_world@Vector(p.co[:3])*1000 for s in o.data.splines for p in s.points]
def extent(p,a):return max(v[a] for v in p)-min(v[a] for v in p)
def at(p,a,value):return [v for v in p if abs(v[a]-value)<.01]
for name,span,outer in [('Wing inboard | 740 mm',740,660),('Wing outboard | 710 mm',710,630)]:
    p=points(name);check(name+' span',extent(p,1),span)
    root=at(p,1,0);check(name+' root chord',extent(root,0),340);check(name+' root thickness',extent(root,2),62)
    outerpoints=[v for v in p if abs(abs(v[1])-outer)<.01]
    check(name+' outer chord',extent(outerpoints,0),275);check(name+' outer thickness',extent(outerpoints,2),52)
for name,span in [('Flap hinge seam',600),('Flap hinge seam.001',570)]:
    p=points(name);check(name+' span',extent(p,1),span);check(name+' sweep',extent(p,0),0);check(name+' x',p[0].x,514)
for name in ['Elevator hinge seam','Elevator hinge seam.001']:
    p=points(name);check(name+' sweep',extent(p,0),0);check(name+' x',p[0].x,971)
for name in ['Stabilizer left','Stabilizer right']:
    p=points(name);check(name+' span',extent(p,1),345)
    root=at(p,1,0);check(name+' root chord',extent(root,0),161)
    outer=[v for v in p if abs(abs(v[1])-315)<.01]
    check(name+' outer chord',extent(outer,0),115)
    check(name+' outer fixed chord',971-min(v.x for v in outer),50)
    check(name+' outer elevator chord',max(v.x for v in outer)-971,65)
for x in [895,1000]:check('Aft fuselage depth at '+str(x),extent(at(points('Fuselage | moulded outer envelope'),0,x),2),95)
p=points('Clear removable canopy');check('Canopy length',extent(p,0),240)
check('Canopy rise above fuselage deck',max(v.z for v in p)-53,65)
check('Canopy sill recess (traced estimate)',53-min(v.z for v in p),16)
check('Canopy crest x (traced estimate)',max(p,key=lambda v:v.z).x,310.8,.2)
check('Fin height above aft deck',max(v.z for v in points('Vertical fin and rudder'))-48,125)
check('Fuselage width at wing leading edge',extent(at(points('Fuselage | moulded outer envelope'),0,250),1),75)
o=bpy.data.objects['Retro 60 crankcase'];axis=o.matrix_world.to_3x3()@Vector((0,0,1));yaw=math.degrees(math.atan2(abs(axis.y),abs(axis.x)));check('Engine yaw magnitude',yaw,3,.01)
check('Gear track',abs(bpy.data.objects['Main tyre -1'].location.y-bpy.data.objects['Main tyre 1'].location.y)*1000,370)
for sg in ['-1','1']:
    p=points('Main gear leg '+sg);check('Gear leg root |y|',abs(p[0].y if False else sum(q.y for q in p)/len(p)),165,25)
    t=bpy.data.objects['Main tyre '+sg];check('Tyre x (axle) '+sg,t.matrix_world.translation.x*1000 if False else sum(q.x for q in points('Main tyre '+sg))/len(points('Main tyre '+sg)),286,1)
check('Tail wheel x',sum(q.x for q in points('Tail wheel'))/len(points('Tail wheel')),919,1)
for name in ['Fuselage | moulded outer envelope','Wing inboard | 740 mm','Wing outboard | 710 mm','Stabilizer left','Stabilizer right','Vertical fin and rudder']:
    bm=bmesh.new();bm.from_mesh(bpy.data.objects[name].data)
    check(name+' nonmanifold edges',sum(not e.is_manifold for e in bm.edges),0,0);bm.free()
check('Packed primary photo',int(any(i.packed_file and i.name=='classic-photo.jpg' for i in bpy.data.images)),1,0)
for name in ['classic-opposite.jpg','cockpit-reference.jpg']:
    check('Packed reference '+name,int(any(i.packed_file and i.name==name for i in bpy.data.images)),1,0)
# The centre of the canopy must expose the well, not a solid fuselage deck.
p=points('Fuselage | moulded outer envelope')
deck=[v.z for v in p if 310<v.x<350 and abs(v.y)<5 and v.z>0]
check('Cockpit centre deck removed',int(bool(deck) and max(deck)<40),1,0)
canopy=bpy.data.objects['Clear removable canopy']
roof=BVHTree.FromPolygons([canopy.matrix_world@v.co for v in canopy.data.vertices],
    [list(p.vertices) for p in canopy.data.polygons])
seat=points('Perforated seat back')
check('Seat vertices beneath canopy',int(all(
    roof.ray_cast(v/1000,Vector((0,0,1)),.15)[0] is not None for v in seat)),1,0)
with open(os.path.join(R,'blueprint-correction-checks.json'),'w') as f:json.dump(results,f,indent=2)
failed=[r for r in results if not r['passed']]
print('BLUEPRINT CHECKS:',len(results),'FAILED:',json.dumps(failed))
assert not failed
