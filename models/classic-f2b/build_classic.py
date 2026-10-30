"""Reproducible photo-referenced Classic reconstruction. Blender 5.2+, no add-ons.
Run: blender -b --python build_classic.py. Geometry inputs are millimetres.
"""
import bpy, math, os, json, bmesh
import numpy as np
from mathutils import Vector, Matrix
from math import sin, cos, pi, sqrt
ROOT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(os.path.join(ROOT,'stl'),exist_ok=True)
bpy.context.preferences.filepaths.save_version=0
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
for c in list(bpy.data.collections):
    if c.name != 'Collection': bpy.data.collections.remove(c)
base=bpy.data.collections.get('Collection'); base.name='01 Fuselage'
cols={base.name:base}
for name in ['02 Wings','03 Stabilizer','04 Landing gear','05 Engine and propeller','06 Cockpit','07 Studio']:
    c=bpy.data.collections.new(name); bpy.context.scene.collection.children.link(c); cols[name]=c
def put(o,c):
    for old in list(o.users_collection): old.objects.unlink(o)
    cols[c].objects.link(o)
    return o
def mat(name,color,metal=0,rough=.3):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
    p=m.node_tree.nodes.get('Principled BSDF'); p.inputs['Base Color'].default_value=(*color,1)
    p.inputs['Metallic'].default_value=metal; p.inputs['Roughness'].default_value=rough
    return m
white=mat('White moulded finish',(.88,.91,.94)); red=mat('Red lacquer',(.58,.008,.014))
black=mat('Rubber / dark hardware',(.012,.016,.022),0,.55)
silver=mat('Machined aluminium',(.52,.58,.64),.8,.25)
dark=mat('Cockpit grey',(.085,.115,.13),.1,.5)
glass=mat('Canopy acrylic',(.96,.98,1),0,.035)
gp=glass.node_tree.nodes.get('Principled BSDF'); gp.inputs.get('Transmission Weight', gp.inputs.get('Transmission')).default_value=1; gp.inputs['IOR'].default_value=1.46
seat_grey=mat('Cockpit seat grey',(.62,.65,.67),0,.4)
# Photos 9 and img_3: the cockpit tub and seat are light grey; only the glare shield and panel are dark.
tub=mat('Cockpit tub light grey',(.50,.54,.56),0,.5)
attitude=mat('Attitude indicator blue',(.06,.32,.55),0,.2)
def photo(name,file):
    m=mat(name,(1,1,1),0,.4); n=m.node_tree.nodes.new('ShaderNodeTexImage')
    n.image=bpy.data.images.load(os.path.join(ROOT,'textures',file)); n.image.pack(); n.interpolation='Linear'
    m.node_tree.links.new(n.outputs['Color'],m.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
    return m
paint=photo('SOURCE 07 | photographed upper-surface livery','img_5.png')   # photograph 7, background removed
# img*.png: the same model in a red/blue livery, used for geometry only (never as texture).
for reference in ['img.png','cockpit-reference.jpg','img_1.png','img_3.png','img_4.png','img_6.png']:   # img.png is photograph 8
    reference_image=bpy.data.images.load(os.path.join(ROOT,'textures',reference))
    reference_image.pack();reference_image.use_fake_user=True
def smooth_samples(x,xs,ys):
    """Shape-preserving cubic interpolation: no overshoot between traced stations."""
    slopes=[(ys[i+1]-ys[i])/(xs[i+1]-xs[i]) for i in range(len(xs)-1)]
    tangents=[slopes[0]]+[0 if slopes[i-1]*slopes[i]<=0 else
        2*slopes[i-1]*slopes[i]/(slopes[i-1]+slopes[i]) for i in range(1,len(xs)-1)]+[slopes[-1]]
    i=min(len(xs)-2,max(0,next((i for i in range(len(xs)-1) if x<=xs[i+1]),len(xs)-2)))
    h=xs[i+1]-xs[i];u=(x-xs[i])/h
    return ((2*u**3-3*u*u+1)*ys[i]+(u**3-2*u*u+u)*h*tangents[i]
        +(-2*u**3+3*u*u)*ys[i+1]+(u**3-u*u)*h*tangents[i+1])
def mesh(name,v,f,c,material=white,uv=None):
    me=bpy.data.meshes.new(name); me.from_pydata([tuple(a*.001 for a in p) for p in v],[],f); me.update()
    o=bpy.data.objects.new(name,me); cols[c].objects.link(o); me.materials.append(material)
    if uv:
        lay=me.uv_layers.new(name='Photo registration')
        for p in me.polygons:
            for li in p.loop_indices: lay.data[li].uv=uv[me.loops[li].vertex_index]
    bm=bmesh.new(); bm.from_mesh(me); bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces)); bm.to_mesh(me); bm.free()
    for p in me.polygons:p.use_smooth=True
    return o
def uvpx(x,y):return x/1200,1-y/900
def project(corners):
    # Projective, not affine: compensates for foreshortening in the photograph.
    A=[]; B=[]
    for (s,t),(x,y) in zip([(0,0),(1,0),(1,1),(0,1)],corners):
        A.extend([[s,t,1,0,0,0,-x*s,-x*t],[0,0,0,s,t,1,-y*s,-y*t]]); B.extend([x,y])
    h=np.append(np.linalg.solve(np.array(A),np.array(B)),1).reshape(3,3)
    def fn(s,t):
        q=h@np.array([s,t,1]);return uvpx(q[0]/q[2],q[1]/q[2])
    return fn
def foil(u,thick):
    return 5*thick*(.2969*sqrt(max(0,u))-.126*u-.3516*u*u+.2843*u**3-.1036*u**4)
def section(t,span,root,tip,le,sweep,thick):
    # Dimensioned outer station and rounded contour traced from image 15.
    is_tail=root<200
    outer=span-(30 if is_tail else 80)
    y=t*span
    if y<=outer:
        q=y/outer
        return le+sweep*q, root+(tip-root)*q, (18+(12-18)*q if is_tail else 62-10*q)
    q=(y-outer)/(span-outer)
    if is_tail:
        # 50/65 outer split occurs before the small rounded tip corners.
        inset=10*(1-sqrt(max(0,1-q*q)))
        return le+sweep+inset,tip-2*inset,12*(1-.55*q*q)
    # LE/TE bounds sampled from the plan's curved outer 80 mm.
    qs=[0,.5,.75,.9375,1]
    dx=[0,3,8,33,65]; te=[275,273,268,218,118]; hh=[52,51,48,25,8]
    return le+sweep+smooth_samples(q,qs,dx),smooth_samples(q,qs,te)-smooth_samples(q,qs,dx),smooth_samples(q,qs,hh)
def wing(name,sign,span,root,tip,le,sweep,thick,z,c,corners):
    v=[]; uv=[]; f=[]; N=96; mapper=project(corners)
    ts=sorted(set([j/64 for j in range(65)]+[(span-(30 if root<200 else 80))/span]))
    profile=[(i/N,1) for i in range(N+1)]+[(i/N,-1) for i in range(N-1,0,-1)]
    K=len(profile)
    peak=max(foil(i/10000,1) for i in range(10001))
    for t in ts:
        start,ch,h=section(t,span,root,tip,le,sweep,thick)
        for u,side in profile:
            x=start+ch*u
            zz=z+side*max(.3,foil(u,1)/peak*h/2)
            v.append((x,sign*span*t,zz));uv.append(mapper(.024+.952*u,.015+.965*t))
    for j in range(len(ts)-1):
        for i in range(K):f.append((j*K+i,j*K+(i+1)%K,(j+1)*K+(i+1)%K,(j+1)*K+i))
    f.extend([tuple(reversed(range(K))),tuple((len(ts)-1)*K+i for i in range(K))])
    o=mesh(name,v,f,c,paint,uv);o.data.materials.append(white)
    for p in o.data.polygons:
        if p.center.z < z*.001-.00001:p.material_index=1
    o['airfoil_note']='Symmetric analytic section; root/outer thickness constrained to blueprint, exact original ordinates unavailable.'
    o['outer_dimension_station_mm']=span-(30 if root<200 else 80)
    return o
wL=wing('Wing inboard | 740 mm',-1,740,340,275,250,32,62/340,0,'02 Wings',[(750,552),(584,360),(23,688),(66,851)])
wR=wing('Wing outboard | 710 mm',1,710,340,275,250,32,62/340,0,'02 Wings',[(765,548),(595,353),(983,146),(1112,226)])
tL=wing('Stabilizer left',-1,345,161,115,895,26,.11,5,'03 Stabilizer',[(414,205),(369,158),(134,246),(151,285)])
tR=wing('Stabilizer right',1,345,161,115,895,26,.11,5,'03 Stabilizer',[(413,195),(377,151),(555,55),(596,89)])
def loft(name,sections,c,material=white,uvfun=None):
    v=[]; uv=[]; f=[]; K=64
    for x,ry,rz,zc in sections:
        for i in range(K):
            a=2*pi*i/K; y=ry*cos(a); z=zc+rz*sin(a);v.append((x,y,z))
            uv.append(uvfun(x,a) if uvfun else (i/K,x/1100))
    for j in range(len(sections)-1):
        for i in range(K): f.append((j*K+i,j*K+(i+1)%K,(j+1)*K+(i+1)%K,(j+1)*K+i))
    f.extend([tuple(reversed(range(K))),tuple((len(sections)-1)*K+i for i in range(K))])
    return mesh(name,v,f,c,material,uv)
stations=[(0,27,29,10),(30,33,41,7),(95,37.5,55,-2),(210,37.5,59,-6),(250,37.5,59,-6),(300,36,59,-6),(470,29,58,-6),(600,23,55,-4),(760,17,51,-2),(895,12,47.5,.5),(1000,8,47.5,.5),(1045,4,47.5,.5),(1065,.7,43,1)]
sections=[]
for a,b in zip(stations,stations[1:]):
    for j in range(12):
        x=a[0]+(b[0]-a[0])*j/12
        sections.append((x,)+tuple(smooth_samples(x,[s[0] for s in stations],[s[k] for s in stations]) for k in range(1,4)))
sections.append(stations[-1])
def fuse_uv(x,a):
    # Narrow strips from the visible red spine, away from canopy/background.
    if x<215:
        t=x/215;cx=892-142*t;cy=658-137*t;w=15
    else:
        t=(x-470)/(1065-470);cx=621-235*t;cy=370-195*t;w=8
    return uvpx(cx+cos(a)*w,cy-cos(a)*w*.8)
body=loft('Fuselage | moulded outer envelope',sections,'01 Fuselage',paint,fuse_uv);body.data.materials.append(white);body.data.materials.append(red)
for p in body.data.polygons:
    if p.center.z<.019:p.material_index=1
    if .22<p.center.x<.47 and p.center.z>=.019:p.material_index=2
# Fine longitudinal white stripes, separate geometry for editability.
def curve(name,pts,r,material,c):
    cu=bpy.data.curves.new(name,'CURVE');cu.dimensions='3D';cu.resolution_u=2;cu.bevel_depth=r*.001;cu.bevel_resolution=3
    sp=cu.splines.new('POLY');sp.points.add(len(pts)-1)
    for p,co in zip(sp.points,pts):p.co=(*[x*.001 for x in co],1)
    o=bpy.data.objects.new(name,cu);cols[c].objects.link(o);cu.materials.append(material);return o
for sg in [-1,1]:
    for zz in [20,25]:
        pts=[]
        for x,ry,rz,zc in sections:
            if rz>zz-zc:pts.append((x,sg*ry*sqrt(1-((zz-zc)/rz)**2)*1.005,zz))
        curve('Fuselage pinstripe',pts,.8,white,'01 Fuselage')
# Fin outline traced from plan, rounded through bevel.
def bezier2(a,b,c,d,n=12):
    return [tuple((1-t)**3*a[k]+3*(1-t)**2*t*b[k]+3*(1-t)*t*t*c[k]+t**3*d[k] for k in range(2))
            for t in np.linspace(0,1,n,endpoint=False)]
# Photographed fin (triangulated from photos 7 and 8, photo_triangulation.py): a straight,
# strongly swept leading edge, a small rounded front crown 125 mm above the 48 mm aft deck
# (plan dimension), a top falling toward a tight aft corner at x~1064 and a near-vertical
# trailing edge. The plan's broader, forward-peaked "79" fin is a different build.
outline=(bezier2((865,48),(900,80),(945,125),(985,162))
    +bezier2((985,162),(993,170),(1000,173),(1010,173))
    +bezier2((1010,173),(1030,172),(1050,160),(1058,152))
    +bezier2((1058,152),(1063,147),(1064,142),(1064,132))
    +bezier2((1064,132),(1065,80),(1066,20),(1066,-32))
    +bezier2((1066,-32),(1067,-43),(1060,-47),(1049,-47))
    +[(990,-47)])
v=[(x,y,z) for y in [-3.5,3.5] for x,z in outline]; n=len(outline)
f=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
fin=mesh('Vertical fin and rudder',v,f,'01 Fuselage',red);fin.data.materials.append(white)
for p in fin.data.polygons:
    if p.center.z<.03:p.material_index=1
be=fin.modifiers.new('Rounded moulded edges','BEVEL');be.width=.003;be.segments=4
def ellipsoid(name,loc,scale,material,c):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48,ring_count=24,location=[x*.001 for x in loc]);o=bpy.context.object;o.name=name
    o.scale=[x*.001 for x in scale];bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.data.materials.append(material)
    for p in o.data.polygons:p.use_smooth=True
    return put(o,c)
def cyl(name,a,b,r,material,c,vertices=48):
    aa=Vector(a)*.001;bb=Vector(b)*.001;d=bb-aa
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices,radius=r*.001,depth=d.length,location=(aa+bb)/2)
    o=bpy.context.object;o.name=name;o.rotation_euler=d.to_track_quat('Z','Y').to_euler();o.data.materials.append(material)
    for p in o.data.polygons:p.use_smooth=True
    return put(o,c)
# Asymmetric canopy traced from the outer glazing silhouette, not the windscreen bow.
def canopy_height(t):
    return smooth_samples(t,[0,.12,.27,.42,.53,.67,.81,.92,1],
        [0,.37,.76,1,.97,.79,.51,.23,0])
def canopy_sill(t):
    return 53-16*sin(pi*t)
def canopy_point(t,a):
    sill=canopy_sill(t)
    return (210+240*t,32*sin(pi*t)**.65*cos(a),sill+(53+65*canopy_height(t)-sill)*sin(a))
# Canopy follows the 240 x 65 mm plan envelope.
v=[];f=[];CK=48
canopy_ts=sorted(set([j/64 for j in range(65)]+[.12,.27,.42,.53,.67,.78,.81,.92]));CS=len(canopy_ts)-1
for t in canopy_ts:
    sh=canopy_height(t)
    for i in range(CK+1):
        a=pi*i/CK;v.append(canopy_point(t,a))
for j in range(CS):
    for i in range(CK):a=j*(CK+1)+i;f.append((a,a+1,a+CK+2,a+CK+1))
canopy=mesh('Clear removable canopy',v,f,'06 Cockpit',glass)
sol=canopy.modifiers.new('Acrylic shell 0.8 mm','SOLIDIFY');sol.thickness=.0008
for t in [.35,.78]:
    curve('Canopy bow',[canopy_point(t,pi*i/64) for i in range(65)],1.1,white,'06 Cockpit')
# The photographed rim is a broad white band, about 2.5 mm proud of the glazing.
for sg in [-1,1]:curve('Canopy sill',[canopy_point(j/80,0 if sg==1 else pi) for j in range(81)],2.4,white,'06 Cockpit')
# Join the recessed glazing rim to the curved fuselage shoulders.
# The original flat sill floated above the elliptical body at its outer edges.
for sg in [-1,1]:
    rim=[];faces=[]
    for j in range(1,80):
        t=j/80;x,y,z=canopy_point(t,0 if sg==1 else pi)
        ry,rz,zc=[smooth_samples(x,[s[0] for s in stations],[s[k] for s in stations]) for k in range(1,4)]
        body_z=zc+rz*sqrt(max(0,1-(y/ry)**2))
        rim.extend([(x,y,z),(x,y,body_z-.5)])
    for j in range(78):faces.append((2*j,2*j+1,2*j+3,2*j+2))
    mesh('Canopy shoulder fairing '+str(sg),rim,faces,'06 Cockpit',red)
# Cut a real cockpit well: previously the solid fuselage hid the seat and controls.
cutter=ellipsoid('Cockpit well cutter',(330,0,98),(112,29,66),tub,'06 Cockpit')
body.data.materials.append(tub)
bpy.context.view_layer.objects.active=body
well=body.modifiers.new('Open cockpit well','BOOLEAN');well.operation='DIFFERENCE';well.object=cutter
bpy.ops.object.modifier_apply(modifier=well.name)
bpy.data.objects.remove(cutter,do_unlink=True)
ellipsoid('Cockpit inset', (330,0,35),(96,25,3),tub,'06 Cockpit')
ellipsoid('Seat cushion',(372,0,43),(23,22,5),seat_grey,'06 Cockpit')
# Thin, reclined bucket seat with the visible lightening holes from photo 9.
seat=mesh('Perforated seat back',[(384,-20,44),(384,20,44),(396,12,82),(396,-12,82)],
    [(0,1,2,3)],'06 Cockpit',seat_grey)
shell=seat.modifiers.new('Seat shell','SOLIDIFY');shell.thickness=.0015
bpy.context.view_layer.objects.active=seat;bpy.ops.object.modifier_apply(modifier=shell.name)
for z in [51,59,67,75]:
    x=384+(z-44)*12/38
    for y in [-8,8]:
        hole=cyl('Seat hole cutter',(x-8,y,z),(x+8,y,z),3.5,black,'06 Cockpit',32)
        bpy.context.view_layer.objects.active=seat
        cut=seat.modifiers.new('Seat lightening hole','BOOLEAN');cut.operation='DIFFERENCE';cut.object=hole
        bpy.ops.object.modifier_apply(modifier=cut.name);bpy.data.objects.remove(hole,do_unlink=True)
panel=ellipsoid('Instrument panel',(285,0,65),(3,24,16),black,'06 Cockpit')
# Photo layout: round gauge left, blue attitude indicator centre, round gauge right, one gauge below.
for y,z,r in [(-14,69,5),(0,72,6.5),(14,69,5),(0,59,4.5)]:
    cyl('Instrument silver bezel',(289,y,z),(291,y,z),r,silver,'06 Cockpit')
    cyl('Instrument dial',(291,y,z),(291.5,y,z),r*.8,attitude if y==0 and z==72 else black,'06 Cockpit')
    curve('Instrument needle',[(292,y,z),(292,y+r*.45,z+r*.35)],.3,white,'06 Cockpit')
cyl('Control stick',(350,0,52),(345,0,69),1.5,silver,'06 Cockpit');ellipsoid('Stick grip',(345,0,71),(2,2,4),black,'06 Cockpit')
# Horizontally mounted 10 cc engine: cylinder axis is lateral, not vertical.
cyl('Retro 60 crankcase',(14,-5,5),(125,-5,5),20,silver,'05 Engine and propeller')
cyl('Horizontal cylinder',(76,-19,5),(76,-76,5),17,silver,'05 Engine and propeller')
for j in range(12):cyl('Cooling fin %02d'%j,(76,-38-j*3.1,5),(76,-39.2-j*3.1,5),21,silver,'05 Engine and propeller')
cyl('Cylinder head',(76,-77,5),(76,-82,5),19,silver,'05 Engine and propeller')
cyl('Glow plug',(76,-82,5),(76,-88,5),4,silver,'05 Engine and propeller')
cyl('Exhaust silencer',(114,-53,-9),(238,-53,-9),15,silver,'05 Engine and propeller')
ellipsoid('Silencer rear dome',(238,-53,-9),(12,15,15),silver,'05 Engine and propeller')
curve('Exhaust outlet',[(240,-53,-9),(253,-54,-15),(258,-54,-27)],5,silver,'05 Engine and propeller')
loft('Polished spinner',[(-48,1,1,10),(-43,10,10,10),(-33,20,20,10),(-18,27,27,10),(-5,28,28,10)],'05 Engine and propeller',silver)
for sg in [-1,1]:
    # Elliptical blade section, twist reduces toward tip. 340 mm overall diameter.
    vs=[];fs=[];N=32;K=16
    for j in range(N+1):
        rr=18+152*j/N;ww=(10+10*sin(pi*j/N))*(1-.9*(j/N)**9);ang=math.atan(140/(2*pi*rr))
        for k in range(K):
            a=2*pi*k/K;ch=ww*cos(a);tt=1.6*sin(a)
            vs.append((-4+ch*sin(ang)+tt*cos(ang),sg*rr,10+sg*(ch*cos(ang)-tt*sin(ang))))
    for j in range(N):
        for k in range(K):fs.append((j*K+k,j*K+(k+1)%K,(j+1)*K+(k+1)%K,(j+1)*K+k))
    fs.extend([tuple(reversed(range(K))),tuple(N*K+k for k in range(K))])
    mesh('Propeller blade '+str(sg),vs,fs,'05 Engine and propeller',white)
def spat_sections(x0,length,ry,rz,zc):
    # Teardrop from the plan side view and both photographs: blunt nose, widest at 35%, tapering tail.
    out=[]
    for j in range(41):
        u=(1-cos(pi*j/40))/2
        if u<.35:f=sqrt(max(0,1-((u-.35)/.35)**2));lift=0
        else:q=(u-.35)/.65;f=1-.82*q**1.6;lift=9*q*q
        out.append((x0+length*u,max(.4,ry*f),max(.4,rz*f),zc+lift))
    return out
# Gear triangulated from photos 7 and 8 (photo_triangulation.py): legs leave the wing just behind
# its leading edge and splay to a 450 mm track; spats sit ~46 mm further forward than the plan's.
# The plan's 370 mm track belongs to the drawn build; the photographed, textured aircraft is wider.
for sg in [-1,1]:
    top=Vector((260,sg*168,-12));axle=Vector((240,sg*217,-150))
    cyl('Main gear leg '+str(sg),top,axle,4.0,silver,'04 Landing gear')
    d=(axle-top).normalized()
    cyl('Ball-lock socket | schematic '+str(sg),top,top+d*14,7,silver,'04 Landing gear')
    ellipsoid('Ball-lock detent | schematic '+str(sg),top+d*8,(3,3,3),silver,'04 Landing gear')
    loft('Wheel spat '+str(sg),spat_sections(170,160,19,24,-150),'04 Landing gear').location.y=sg*.225
    cyl('Main tyre '+str(sg),(240,sg*225-8,-152),(240,sg*225+8,-152),24,black,'04 Landing gear')
    cyl('Wheel hub '+str(sg),(240,sg*225-9,-152),(240,sg*225+9,-152),8,silver,'04 Landing gear')
curve('Tail wheel wire',[(880,0,-38),(905,0,-85),(919,0,-111)],2,silver,'04 Landing gear')
cyl('Tail wheel',(919,-5,-111),(919,5,-111),11,black,'04 Landing gear')
# Straight blueprint hinge stations; independently dimensioned flap lengths.
def surf_z(y,span,root,tip,le,sweep,x,zc=0):
    start,ch,h=section(abs(y)/span,span,root,tip,le,sweep,0)
    return zc+foil((x-start)/ch,1)/.50006*h/2
for sg,sp,flapspan in [(-1,740,600),(1,710,570)]:
    pts=[(514,sg*y,surf_z(y,sp,340,275,250,32,514)+.5) for y in np.linspace(0,flapspan,60)]
    curve('Flap hinge seam',pts,.55,dark,'02 Wings')
    start,ch,h=section(flapspan/sp,sp,340,275,250,32,0)
    curve('Flap end seam',[(x,sg*flapspan,surf_z(flapspan,sp,340,275,250,32,x)+.5) for x in np.linspace(514,start+ch,25)],.5,dark,'02 Wings')
    for y in [flapspan*.18,flapspan*.5,flapspan*.85]:
        z=surf_z(y,sp,340,275,250,32,514)
        cyl('Flap hinge',(512,sg*y,z+1),(517,sg*y,z+1),1.5,silver,'02 Wings')
for sg in [-1,1]:
    pts=[(971,sg*y,surf_z(y,345,161,115,895,26,971,5)+.4) for y in np.linspace(0,335,60)]
    curve('Elevator hinge seam',pts,.45,dark,'03 Stabilizer')
# Engine/propeller assembly yaws 3 degrees about the propeller centre.
pivot=Vector((-.004,0,.010))
transform=Matrix.Translation(pivot) @ Matrix.Rotation(math.radians(3),4,'Z') @ Matrix.Translation(-pivot)
for o in cols['05 Engine and propeller'].objects:o.matrix_world=transform @ o.matrix_world
for x in [390,430]:
    curve('Inboard control-line guide',[(x,-730,0),(x,-770,0)],.7,silver,'02 Wings')
# Match the photograph's handedness (engine and registered wing on visible side).
for o in bpy.data.objects:
    if o.type in {'MESH','CURVE'}:
        o.matrix_world = Matrix.Diagonal((1,-1,1,1)) @ o.matrix_world
        bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o
        bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
        if o.type=='MESH':
            bm=bmesh.new();bm.from_mesh(o.data);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(o.data);bm.free()
scene=bpy.context.scene;scene.unit_settings.system='METRIC';scene.unit_settings.length_unit='MILLIMETERS'
scene['Model']='Yuriy Yatsenko Classic, 1992, F2B — photo/plan reconstruction'
scene['Reference flying mass']='1600 ±20 g; metadata only, not mesh mass'
scene['Engine']='Discovery Retro 60, 10 cc, horizontal cylinder'
scene['Caution']='Display/reference geometry. Exact airfoil, internal structure, demountable joints and Petrov ball-lock mechanism are not documented by supplied views; hardware is schematic.'
scene['Sources']='http://discovery-aeromodels.com/en/yuriy-yatsenko-classic-f2b-model.html; images 7,8,9,15; other article images inspected for shape only.'
scene['Blueprint revision']='Photo triangulation revision: swept photographed fin with aft top corner (125 mm height kept); main gear moved forward to a photographed 450 mm track with teardrop spats. Other explicit plan dimensions retained.'
scene['Scale']='1:1. Blender coordinates in metres; STL coordinates in millimetres.'
# Export a selection of closed principal solids, retaining assembly coordinates.
for name,o in [('fuselage',body),('wing_inboard',wL),('wing_outboard',wR),('stabilizer_left',tL),('stabilizer_right',tR),('vertical_fin',fin)]:
    bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o
    bpy.ops.wm.stl_export(filepath=os.path.join(ROOT,'stl',name+'.stl'),export_selected_objects=True,global_scale=1000)
for o in list(cols['04 Landing gear'].objects):
    bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o
    bpy.ops.wm.stl_export(filepath=os.path.join(ROOT,'stl',o.name.replace(' | ','_').replace(' ','_')+'.stl'),export_selected_objects=True,global_scale=1000)
# Studio camera has nose bottom-right, tail top-left as in image 7.
def track(o,p):o.rotation_euler=(Vector(p)-o.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(-1.9,1.4,3.2));cam=put(bpy.context.object,'07 Studio');cam.name='Primary photo angle';track(cam,(.49,0,0));cam.data.type='ORTHO';cam.data.ortho_scale=1.88;scene.camera=cam
for name,loc,power,size in [('Large softbox',(-1,-1,3),180,3),('Rim softbox',(1,2,2),130,2)]:
    bpy.ops.object.light_add(type='AREA',location=loc);o=put(bpy.context.object,'07 Studio');o.name=name;o.data.energy=power;o.data.shape='DISK';o.data.size=size;track(o,(.5,0,0))
scene.world.color=(.32,.32,.32)
scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.view_settings.look='None';scene.view_settings.exposure=-.65;scene.view_settings.gamma=1
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=True
# A clean editable viewport, packed textures and explanatory text embedded in file.
note=bpy.data.texts.new('READ ME — accuracy and sources')
note.write('Classic F2B / Yuriy Yatsenko\n1:1 reference geometry, metres internally.\nWing span 1450 mm (740+710); tail span 690 mm; root chord 340 mm; tip chord 275 mm; canopy 240 x 65 mm; propeller 340 mm; gear track 450 mm as triangulated from photos 7 and 8 (plan: 370 mm).\nPhoto 7 is UV-mapped onto upper wing and tail surfaces and the red fuselage spine. Texture is packed. Photograph contains baked reflections; hidden lower surfaces are plain white reconstructions. Photo 8 confirms livery; photo 9 informs cockpit detail.\nSymmetric airfoil is an analytic approximation scaled to the plan thickness, not a verified original section. Engine, cockpit, ball-lock sockets and detents are external visual approximations. No functional release mechanism or flightworthy structure is implied. No mass distribution/material layup is defined; 1600 ±20 g is reference metadata.\nPrincipal STL solids use millimetres and preserve assembly coordinates; not pre-split for printer beds. Fin is separate from fuselage; landing gear subparts are separate. No press-fit tolerances have been designed.\nArticle: http://discovery-aeromodels.com/en/yuriy-yatsenko-classic-f2b-model.html\n')
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.shading.type='MATERIAL';area.spaces.active.clip_end=100
bpy.ops.object.select_all(action='DESELECT');body.select_set(True);bpy.context.view_layer.objects.active=body
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(ROOT,'Classic_F2B.blend'))
scene.render.filepath=os.path.join(ROOT,'rendered.png');bpy.ops.render.render(write_still=True)
# Wireframe preview uses the same actual geometry and camera.
scene.render.engine='BLENDER_WORKBENCH';scene.display.shading.light='STUDIO';scene.display.shading.color_type='MATERIAL';scene.display.shading.single_color=(.67,.75,.8);scene.display.shading.show_shadows=True;scene.display.shading.show_cavity=True
for o in bpy.data.objects:
    if o.type=='MESH':
        o.data.materials.clear();o.data.materials.append(white);o.data.materials.append(black)
        for p in o.data.polygons:p.material_index=0
        mod=o.modifiers.new('Topology overlay','WIREFRAME');mod.thickness=.00025;mod.use_replace=False;mod.material_offset=1
scene.render.filepath=os.path.join(ROOT,'wireframe.png');bpy.ops.render.render(write_still=True)
report={}
for o in [body,wL,wR,tL,tR,fin]:
    bm=bmesh.new();bm.from_mesh(o.data)
    world=[o.matrix_world@v.co for v in bm.verts]
    report[o.name]={'vertices':len(bm.verts),'faces':len(bm.faces),'non_manifold_edges':sum(not e.is_manifold for e in bm.edges),'bounds_mm':[round((max(v[k] for v in world)-min(v[k] for v in world))*1000,3) for k in range(3)]};bm.free()
with open(os.path.join(ROOT,'validation.json'),'w') as f:json.dump(report,f,indent=2)
print('CLASSIC BUILD COMPLETE',json.dumps(report))
