"""Triangulate hand-marked features seen in both photographs.

Uses the cameras fitted by fit_photo_overlays.py (photo-registration.json) and
fit_livery2_photos.py (photo-registration-livery2.json); run those first. The spinner and canopy nose are checks with known model positions.
Features visible in only one photo are intersected with a known plane instead.
Both cameras look down from the front, so height is the least certain coordinate
(about +/-15 mm); x and y agree with the checks to about 5 mm.
"""
import json, math, os
import numpy as np
from mathutils import Euler

ROOT=os.path.dirname(os.path.abspath(__file__))
# Each camera: position (m), rotation, focal length (px) and image centre.
cameras={}
with open(os.path.join(ROOT,'photo-registration.json')) as stream:
    for name,view in json.load(stream)['views'].items():
        cameras[name]=(np.array(view['camera_position_m']),np.array(Euler(view['camera_euler_rad'],'XYZ').to_matrix()),
                       view['lens_mm']*1200/36,(600,450))
# Second-livery photographs (textures/img*.png); img_2 is too weakly registered to use.
with open(os.path.join(ROOT,'photo-registration-livery2.json')) as stream:
    for name,view in json.load(stream)['photos'].items():
        if name!='img_2.png':
            cameras[name]=(np.array(view['camera_position_m']),np.array(Euler(view['camera_euler_rad'],'XYZ').to_matrix()),
                           view['focal_px'],(view['image_size'][0]/2,view['image_size'][1]/2))

def projection(name):
    centre,rotation,focal,(cx,cy)=cameras[name]
    intrinsics=np.array([[focal,0,cx],[0,-focal,cy],[0,0,1]])
    world_to_camera=np.diag([1,1,-1])@rotation.T
    return intrinsics@np.hstack([world_to_camera,-world_to_camera@centre[:,None]])

matrices={name:projection(name) for name in cameras}

def triangulate(marks):
    rows=[]
    for name,(x,y) in marks.items():
        m=matrices[name];rows+=[x*m[2]-m[0],y*m[2]-m[1]]
    point=np.linalg.svd(np.array(rows))[2][-1]
    point=point[:3]/point[3]
    errors={}
    for name,(x,y) in marks.items():
        q=matrices[name]@np.append(point,1)
        errors[name]=math.hypot(q[0]/q[2]-x,q[1]/q[2]-y)
    return point*1000,errors

def on_plane(name,pixel,z_mm):
    centre,rotation,focal,(cx,cy)=cameras[name]
    direction=rotation@np.array([(pixel[0]-cx)/focal,-(pixel[1]-cy)/focal,-1])
    return (centre+(z_mm/1000-centre[2])/direction[2]*direction)*1000

# Pixel coordinates in the unmodified 1200x900 photographs.
paired={
 'check: spinner centre':({'primary':(935,713),'opposite':(235,614)},[-25,0,10]),
 'check: spinner nose (livery 2)':({'img.png':(902,357),'img_1.png':(703,127)},[-48,2,10]),
 'fin aft top corner (livery 2)':({'img.png':(241,133),'opposite':(818,50)},None),
 'spat +y nose (livery 2)':({'img.png':(690,497),'primary':(615,755)},None),
 'check: canopy nose':({'primary':(773,517),'opposite':(400,447)},[210,0,53]),
 'fin crown front':({'primary':(365,61),'opposite':(795,57)},None),
 'fin top aft corner':({'primary':(336,50),'opposite':(818,50)},None),
 'spat +y nose':({'primary':(615,755),'opposite':(262,485)},None),
 'spat +y tail':({'primary':(532,657),'opposite':(350,407)},None),
 'spat +y centre':({'primary':(576,708),'opposite':(294,451)},None),
 'spat -y nose':({'primary':(907,550),'opposite':(532,685)},None),
 'spat -y tail':({'primary':(825,470),'opposite':(625,585)},None),
 'spat -y centre':({'primary':(865,511),'opposite':(581,636)},None),
}
single=[
 ('gear leg +y wing exit','primary',(607,615),-27),
 ('gear leg -y wing exit','opposite',(550,542),-27),
 ('gear leg +y at z=-130','primary',(585,690),-130),
 ('gear leg -y at z=-130','opposite',(565,627),-130),
]
report={'method':'Linear two-view triangulation with the fitted photo cameras; single-view rays intersect a stated z plane.',
        'limitations':'Hand-marked silhouette extremes; camera landmark residuals are about 20 px. Height is least certain.',
        'paired':{},'single_view':{}}
for name,(marks,expected) in paired.items():
    point,errors=triangulate(marks)
    report['paired'][name]={'photo_px':marks,'world_mm':[round(v,1) for v in point],
        'reprojection_px':{k:round(v,1) for k,v in errors.items()},'model_mm':expected}
    print('%-24s'%name,np.round(point).astype(int),flush=True)
for name,view,pixel,z in single:
    point=on_plane(view,pixel,z)
    report['single_view'][name]={'photo':view,'photo_px':pixel,'plane_z_mm':z,'world_mm':[round(v,1) for v in point]}
    print('%-24s'%name,np.round(point).astype(int),flush=True)
with open(os.path.join(ROOT,'photo-triangulation.json'),'w') as stream:
    json.dump(report,stream,indent=2)
