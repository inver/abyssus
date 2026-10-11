import bpy, bmesh, json, os
root=os.path.dirname(os.path.abspath(__file__))
bpy.ops.wm.open_mainfile(filepath=os.path.join(root,'Classic_F2B.blend'))
report={'blender_version':bpy.app.version_string,'mesh_objects':sum(o.type=='MESH' for o in bpy.data.objects),'packed_images':[i.name for i in bpy.data.images if i.packed_file], 'collections':[c.name for c in bpy.data.collections], 'airframe':{}}
for name in ['01 Fuselage','02 Wings','03 Stabilizer']:
    for o in bpy.data.collections[name].objects:
        if o.type!='MESH':continue
        bm=bmesh.new();bm.from_mesh(o.data)
        report['airframe'][o.name]={'non_manifold_edges':sum(not e.is_manifold for e in bm.edges),'volume_m3':bm.calc_volume(signed=True)}
        assert all(e.is_manifold for e in bm.edges),o.name
        assert bm.calc_volume(signed=True)>0,o.name
        bm.free()
assert report['packed_images']
with open(os.path.join(root,'blend-verification.json'),'w') as f:json.dump(report,f,indent=2)
print('VERIFIED_SAVED_BLEND',json.dumps(report))
