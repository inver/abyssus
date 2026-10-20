"""Fit perspective cameras to the second-livery photographs (textures/img*.png).

Landmarks are hand-marked silhouette corners (wing and stabilizer tip corners are
straight-edge intersections, so 'virtual'), the spinner nose and the fin's aft top
corner. Which wing is the 740 mm one is not visible in every photo, so both
left/right assignments are fitted and the lower residual wins. A weak prior keeps
the focal length near that of the first-livery fits. The photographs are not
modified and not used as textures. (img.png and img_5.png are the first-livery photographs 8 and 7 with
their backgrounds removed; they were registered by fit_photo_overlays.py.)

Run: blender -b --python fit_livery2_photos.py
"""
import bpy, json, math, os
import numpy as np
from mathutils import Euler, Vector

ROOT=os.path.dirname(os.path.abspath(__file__))
W,H=1024,766
# World millimetres after the model's handedness reflection (+y is the engine side, 740 mm semispan).
# 'long'/'short' name the semispan; the left/right assignment is solved per photo.
def tips(long_sign,stab_sign):
    s=long_sign
    return {
     'long wing tip LE':[282,s*740,0],'long wing tip TE':[557,s*740,0],
     'short wing tip LE':[282,-s*710,0],'short wing tip TE':[557,-s*710,0],
     'stab +y tip LE':[921,stab_sign*345,5],'stab +y tip TE':[1036,stab_sign*345,5],
     'stab -y tip LE':[921,-stab_sign*345,5],'stab -y tip TE':[1036,-stab_sign*345,5]}
# Pixel landmarks per photo: (name, pixel). The first wing/stab named is the near one in the image.
photos={
 # img_4.png is registered at 1024x766; the file is the same photograph at twice the size.
 'img_4.png':{
  'near_is_long':True,
  'marks':{'near wing tip LE':(470,703),'near wing tip TE':(287,611),
           'far wing tip LE':(795,72),'far wing tip TE':(668,50),
           'near stab tip LE':(190,350),'near stab tip TE':(147,332),
           'far stab tip LE':(408,118),'far stab tip TE':(360,107),
           'spinner nose':(902,357),'fin aft top corner':(241,133)}},
 'img_1.png':{
  'marks':{'near wing tip LE':(930,418),'near wing tip TE':(845,527),
           'far wing tip LE':(310,76),'far wing tip TE':(226,137),
           'near stab tip LE':(434,553),'near stab tip TE':(368,604),
           'far stab tip LE':(158,322),'far stab tip TE':(112,357),
           'spinner nose':(703,127)}},
 'img_2.png':{
  'marks':{'far stab tip centre':(532,146),'near stab tip centre':(957,236),
           'spinner nose':(160,452),'fin aft top corner':(762,78)},
  # At the frame edge of a wide, close shot: reported, not fitted (lens distortion).
  'check':{'far wing tip LE':(52,190),'far wing tip TE':(97,152)}},
}
def world(name,near_long,far_is_long_side):
    pass

def project(p,pts):
    rotation=np.array(Euler(p[3:6],'XYZ').to_matrix())
    local=(pts-p[:3])@rotation
    depth=-local[:,2]
    f=math.exp(p[6])
    return np.column_stack((W/2+f*local[:,0]/depth,H/2-f*local[:,1]/depth)),depth

def residual(p,pts,px):
    uv,depth=project(p,pts)
    r=(uv-px).ravel()
    # Camera must sit above the aircraft: a flat target has a mirror solution below it.
    pen=np.where(depth<.05,1e4,0).sum()+(1e4 if p[2]<.15 else 0)
    return np.append(r,[(p[6]-math.log(1200))*40]) + pen

def lm(p,pts,px):
    damping=.01
    for _ in range(150):
        r=residual(p,pts,px)
        jac=np.empty((len(r),7))
        for k in range(7):
            q=p.copy();q[k]+=1e-4
            jac[:,k]=(residual(q,pts,px)-r)/1e-4
        normal=jac.T@jac
        delta=np.linalg.solve(normal+damping*np.diag(np.maximum(np.diag(normal),1)),-jac.T@r)
        q=p+delta
        if not 5<q[6]<10:
            damping*=3;continue
        if np.sum(residual(q,pts,px)**2)<np.sum(r**2):
            p=q;damping=max(1e-8,damping/2)
            if np.linalg.norm(delta)<1e-8:break
        else:damping=min(1e8,damping*3)
    return p

def fit(world_pts,pixels):
    pts=np.array(world_pts)/1000;px=np.array(pixels,float)
    best=None
    for radius in (1.0,1.6,2.4):
        for azimuth in range(0,360,30):
            for height in (.6,1.3):
                loc=np.array([.5+radius*math.cos(math.radians(azimuth)),radius*math.sin(math.radians(azimuth)),height])
                angles=(Vector((.5,0,0))-Vector(loc)).to_track_quat('-Z','Y').to_euler()
                p=lm(np.array([*loc,*angles,math.log(1200)]),pts,px)
                uv,depth=project(p,pts)
                if depth.min()<.05 or p[2]<.15:continue
                err=np.linalg.norm(uv-px,axis=1)
                rms=float(np.sqrt(np.mean(err**2)))
                if best is None or rms<best[0]:best=(rms,p,err)
    return best

def build(photo,near_long,near_stab_pos):
    """Return world points for the marked names under one left/right hypothesis."""
    ns=1 if near_long else -1   # sign of the near wing's y
    nst=near_stab_pos            # sign of the near stabilizer's y (same side as the near wing)
    table={'near wing tip LE':[282,ns*(740 if near_long else 710),0],
           'near wing tip TE':[557,ns*(740 if near_long else 710),0],
           'far wing tip LE':[282,-ns*(710 if near_long else 740),0],
           'far wing tip TE':[557,-ns*(710 if near_long else 740),0],
           'near stab tip LE':[921,ns*345,5],'near stab tip TE':[1036,ns*345,5],
           'far stab tip LE':[921,-ns*345,5],'far stab tip TE':[1036,-ns*345,5],
           'near stab tip centre':[978,ns*345,5],'far stab tip centre':[978,-ns*345,5],
           'spinner nose':[-48,2,10],'fin aft top corner':[1064,0,142]}
    return table

report={'method':'Multi-start Levenberg-Marquardt perspective camera fit, centre fixed at the image centre.',
        'limitations':'Hand-marked corners; wing/stab tip corners are virtual straight-edge intersections.',
        'photos':{}}
bpy.ops.wm.open_mainfile(filepath=os.path.join(ROOT,'Classic_F2B.blend'))
scene=bpy.context.scene;camera=scene.camera
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
scene.render.film_transparent=True
for name,spec in photos.items():
    results=[]
    hypotheses=[True] if 'near_is_long' in spec and spec['near_is_long'] else [True,False]
    for near_long in hypotheses:
        table=build(name,near_long,1)
        names=list(spec['marks']);rms,p,err=fit([table[n] for n in names],[spec['marks'][n] for n in names])
        results.append(dict(near_wing_is_long=near_long,rms=rms,p=p,err=err,names=names))
        print('FIT',name,'near wing long' if near_long else 'near wing short','rms %.1f'%rms,'f=%.0f'%math.exp(p[6]),flush=True)
    best=min(results,key=lambda r:r['rms'])
    p=best['p'];stem=name.split('.')[0]
    report['photos'][name]={'near_wing_is_long':best['near_wing_is_long'],
        'alternative_rms_px':[r['rms'] for r in results if r is not best],
        'landmark_rms_px':best['rms'],'camera_position_m':p[:3].tolist(),'camera_euler_rad':p[3:6].tolist(),
        'focal_px':math.exp(p[6]),'image_size':[W,H],
        'held_out_checks':[{'name':n,'photo_px':px,'predicted_px':project(p,np.array([build(name,best['near_wing_is_long'],1)[n]])/1000)[0][0].tolist()}
                           for n,px in spec.get('check',{}).items()],
        'landmarks':[{'name':n,'photo_px':spec['marks'][n],'error_px':float(e)} for n,e in zip(best['names'],best['err'])]}
    camera.location=p[:3];camera.rotation_euler=p[3:6];camera.data.type='PERSP'
    camera.data.sensor_fit='HORIZONTAL';camera.data.sensor_width=36
    camera.data.lens=math.exp(p[6])*36/W;camera.data.shift_x=camera.data.shift_y=0
    scene.render.engine='BLENDER_WORKBENCH';scene.display.shading.light='FLAT'
    scene.display.shading.color_type='SINGLE';scene.display.shading.single_color=(1,.18,.015)
    scene.display.shading.show_shadows=False;scene.display.shading.show_cavity=False
    scene.render.filepath=os.path.join(ROOT,'aligned-livery2-%s-mask.png'%stem)
    bpy.ops.render.render(write_still=True)
with open(os.path.join(ROOT,'photo-registration-livery2.json'),'w') as stream:
    json.dump(report,stream,indent=2)
