"""Read-only geometric audit of the saved blend; never saves the scene."""
import bpy, json, os, math, base64
from mathutils import Vector
R=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(R,'Classic_F2B.blend'))
def verts(o):return [tuple(q*1000 for q in o.matrix_world@v.co) for v in o.data.vertices]
def bounds(v):return [[min(p[k] for p in v),max(p[k] for p in v)] for k in range(3)]
objects={o.name:{'bounds_mm':bounds(verts(o)),'vertices_mm':verts(o)} for o in bpy.data.objects if o.type=='MESH'}
def rings(name,axis):
    groups={}
    for p in objects[name]['vertices_mm']:groups.setdefault(round(p[axis],3),[]).append(p)
    return [(k,v) for k,v in sorted(groups.items())]
body='Fuselage | moulded outer envelope'
wing='Wing inboard | 740 mm'
tail='Stabilizer left'
report={'source_blend':'Classic_F2B.blend','unit':'mm','objects':{n:{'bounds_mm':d['bounds_mm']} for n,d in objects.items()},'fuselage_sections':[], 'inboard_wing_sections':[], 'stabilizer_sections':[], 'curve_endpoints':{}}
for x,v in rings(body,0):
    if any(abs(x-q)<1 for q in [0,95,210,300,470,600,760,895,1000,1065]):report['fuselage_sections'].append({'x':x,'bounds':bounds(v)})
for name,key in [(wing,'inboard_wing_sections'),(tail,'stabilizer_sections')]:
    rr=rings(name,1)
    for i in [0,24,45,48]:
        y,v=rr[i];report[key].append({'y':y,'bounds':bounds(v)})
for o in bpy.data.objects:
    if o.type=='CURVE' and any(s in o.name for s in ['hinge seam','Canopy sill']):
        pp=[tuple(q*1000 for q in o.matrix_world@Vector(p.co[:3])) for sp in o.data.splines for p in sp.points]
        report['curve_endpoints'][o.name]={'bounds':bounds(pp),'first':pp[0],'last':pp[-1]}
with open(os.path.join(R,'blueprint-measurements.json'),'w') as f:json.dump(report,f,indent=2)
# Overlay registration uses wing root and stabilizer leading-edge stations.
# The raster is not uniformly dimensioned: do not interpret pixel residuals as tolerances.
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1020" viewBox="0 0 1200 1020">','<rect width="1200" height="1020" fill="white"/>']
encoded=base64.b64encode(open(os.path.join(R,'plan-reference.jpg'),'rb').read()).decode()
svg.append('<image width="1200" height="879" href="data:image/jpeg;base64,'+encoded+'"/>')
def poly(points,kind):
    if kind=='top':xy=[(288+(x-250)*346/645,422+y*396/740) for x,y in points]
    else:xy=[(285+(x-250)*341/645,163-z*135/240) for x,z in points]
    svg.append('<polygon points="'+' '.join('%.2f,%.2f'%p for p in xy)+'" fill="#ee7700" fill-opacity=".10" stroke="#e06000" stroke-width="1.5"/>')
for name in [body,'Wing inboard | 740 mm','Stabilizer left']:
    rr=rings(name,0 if name==body else 1)
    if name==body:
        # Boolean well intersections introduce x stations containing only interior
        # vertices. They are not the exterior outline of the fuselage.
        rr=[(x,v) for x,v in rr if len(v)>=32]
        poly([(x,min(p[1] for p in v)) for x,v in rr]+[(x,max(p[1] for p in v)) for x,v in reversed(rr)],'top')
        poly([(x,min(p[2] for p in v)) for x,v in rr]+[(x,max(p[2] for p in v)) for x,v in reversed(rr)],'side')
    else:poly([(min(p[0] for p in v),y) for y,v in rr]+[(max(p[0] for p in v),y) for y,v in reversed(rr)],'top')
vv=objects['Vertical fin and rudder']['vertices_mm'];poly([(x,z) for x,y,z in vv[:len(vv)//2]],'side')
rr=rings('Clear removable canopy',0);poly([(x,min(p[2] for p in v)) for x,v in rr]+[(x,max(p[2] for p in v)) for x,v in reversed(rr)],'side')
svg.extend(['<rect y="879" width="1200" height="141" fill="#f2f4f7"/>','<text x="30" y="914" font-family="sans-serif" font-size="22">Orange: saved Blender mesh outlines. Black: supplied blueprint.</text>','<text x="30" y="947" font-family="sans-serif" font-size="17">Registered to wing/stabilizer leading-edge stations; span calibrated to 740 mm.</text>','<text x="30" y="975" font-family="sans-serif" font-size="17">The drawing uses broken views and inconsistent raster scale. Overlay is qualitative.</text>','<text x="30" y="1003" font-family="sans-serif" font-size="17">Opposite wing/tail halves omitted because the blueprint uses broken views.</text>','</svg>'])
open(os.path.join(R,'blueprint-overlay.svg'),'w').write('\n'.join(svg))
print('BLUEPRINT_AUDIT_EXTRACTED',len(objects),'mesh objects')
