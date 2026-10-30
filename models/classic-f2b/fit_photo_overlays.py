"""Fit physical perspective cameras to hand-marked photo landmarks.

No image warping or model deformation is used by the camera fit. Coordinates
marked 'virtual' are intersections of straight planform-edge tangents, so they
are explicitly less certain than visible features. Only wing, tail and spinner
landmarks constrain the cameras, so the gear and fin can be checked against them
(photo_triangulation.py).
"""
import bpy, json, math, os
import numpy as np
from mathutils import Vector, Euler

ROOT=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(ROOT,'Classic_F2B.blend'))
scene=bpy.context.scene
scene.render.resolution_x=1200;scene.render.resolution_y=900
scene.render.resolution_percentage=100
scene.render.film_transparent=True
camera=scene.camera
# World coordinates are after the model's handedness reflection.
views={
 'primary':{
  'location':[-1.9,1.4,3.2],
  'landmarks':[
   ['wing root LE',[250,0,0],[765,548]],
   ['wing root TE',[590,0,0],[585,354]],
   ['near wing virtual LE',[282,740,0],[68,889]],
   ['near wing virtual TE',[557,740,0],[0,681]],
   ['far wing virtual LE',[282,-710,0],[1145,230]],
   ['far wing virtual TE',[557,-710,0],[977,134]],
   ['tail root LE',[895,0,5],[415,200]],
   ['tail root TE',[1056,0,5],[373,152]],
   ['near tail virtual LE',[921,345,5],[151,293]],
   ['near tail virtual TE',[1036,345,5],[128,246]],
   ['far tail virtual LE',[921,-345,5],[601,91]],
   ['far tail virtual TE',[1036,-345,5],[565,51]],
   ['spinner centre',[-25,1,10],[935,713]],

  ]},
 'opposite':{
  'location':[-1.9,-1.4,3.2],
  'landmarks':[
   ['wing root LE',[250,0,0],[425,446]],
   ['wing root TE',[590,0,0],[600,320]],
   ['near wing virtual LE',[282,-710,0],[1075,820]],
   ['near wing virtual TE',[557,-710,0],[1130,608]],
   ['far wing virtual LE',[282,740,0],[66,181]],
   ['far wing virtual TE',[557,740,0],[204,105]],
   ['tail root LE',[895,0,5],[771,183]],
   ['tail root TE',[1056,0,5],[818,143]],
   ['near tail virtual LE',[921,-345,5],[979,278]],
   ['near tail virtual TE',[1036,-345,5],[1012,250]],
   ['far tail virtual LE',[921,345,5],[570,80]],
   ['far tail virtual TE',[1036,345,5],[597,50]],
   ['spinner centre',[-25,1,10],[235,614]],

  ]},
}

def project(parameters,points):
    # Rotation is the camera's physical orientation; camera looks down local -Z.
    rotation=np.array(Euler(parameters[3:6],'XYZ').to_matrix())
    local=(points-parameters[:3])@rotation
    depth=-local[:,2]
    focal=math.exp(parameters[6])
    return np.column_stack((600+focal*local[:,0]/depth,
                            450-focal*local[:,1]/depth))

def fit(view):
    points=np.array([r[1] for r in view['landmarks']])/1000
    pixels=np.array([r[2] for r in view['landmarks']])
    location=Vector(view['location'])
    angles=(Vector((.49,0,0))-location).to_track_quat('-Z','Y').to_euler()
    p=np.array([*location,*angles,math.log(2500)])
    damping=.01
    for iteration in range(180):
        residual=(project(p,points)-pixels).ravel()
        jac=np.empty((len(residual),len(p)))
        for k in range(len(p)):
            step=1e-4
            q=p.copy();q[k]+=step
            jac[:,k]=(project(q,points).ravel()-project(p,points).ravel())/step
        normal=jac.T@jac
        delta=np.linalg.solve(normal+damping*np.diag(np.maximum(np.diag(normal),1)),
                              -jac.T@residual)
        q=p+delta
        if not 5<q[6]<10:
            damping*=3;continue
        if np.sum((project(q,points)-pixels)**2)<np.sum(residual**2):
            p=q;damping=max(1e-8,damping/2)
            if np.linalg.norm(delta)<1e-7:break
        else:damping=min(1e8,damping*3)
    prediction=project(p,points)
    return p,prediction,np.linalg.norm(prediction-pixels,axis=1)

report={'method':'Physical perspective camera; fixed image centre; hand-marked tangent intersections.',
        'limitations':'Landmark residuals include tracing uncertainty and shape error; they are not a photogrammetry accuracy certificate.',
        'views':{}}
for name,view in views.items():
    p,prediction,errors=fit(view)
    camera.location=p[:3];camera.rotation_euler=p[3:6]
    camera.data.type='PERSP';camera.data.sensor_fit='HORIZONTAL';camera.data.sensor_width=36
    camera.data.lens=math.exp(p[6])*36/1200
    camera.data.shift_x=0;camera.data.shift_y=0
    report['views'][name]={'camera_position_m':p[:3].tolist(),
        'camera_euler_rad':p[3:6].tolist(),'lens_mm':camera.data.lens,
        'landmark_rms_px':float(np.sqrt(np.mean(errors**2))),
        'landmarks':[{'name':r[0],'world_mm':r[1],'photo_px':r[2],
                      'model_px':list(map(float,pred)),'error_px':float(error)}
                     for r,pred,error in zip(view['landmarks'],prediction,errors)]}
    print('CAMERA_FIT',name,report['views'][name]['landmark_rms_px'],flush=True)
    scene.render.engine='BLENDER_WORKBENCH'
    scene.display.shading.light='FLAT';scene.display.shading.color_type='SINGLE'
    scene.display.shading.single_color=(1,.18,.015)
    scene.display.shading.show_shadows=False;scene.display.shading.show_cavity=False
    scene.render.filepath=os.path.join(ROOT,'aligned-'+name+'-mask.png')
    bpy.ops.render.render(write_still=True)
    scene.render.engine='CYCLES';scene.cycles.samples=24
    scene.render.filepath=os.path.join(ROOT,'aligned-'+name+'.png')
    bpy.ops.render.render(write_still=True)
with open(os.path.join(ROOT,'photo-registration.json'),'w') as stream:
    json.dump(report,stream,indent=2)
