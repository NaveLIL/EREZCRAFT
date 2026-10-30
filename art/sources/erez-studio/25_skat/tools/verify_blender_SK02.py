"""Re-open the saved native model and verify the usable design, then reimport GLB."""
from pathlib import Path
import bpy,json,math,hashlib,struct
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
source=json.loads((ROOT/'source/skat_SK02.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/skat_SK02.blend'))
scene=bpy.context.scene;scene.frame_set(1);bpy.context.view_layer.update()
meshes={ob['source_part']:ob for ob in bpy.data.objects if ob.type=='MESH' and 'source_part' in ob}
assert set(meshes)=={p['name'] for p in source['parts']}
assert len([ob for ob in meshes.values() if ob.get('design_tag')=='cargo_module'])==24
for ob in meshes.values():
    assert ob.data.uv_layers and ob.data.materials,ob.name
    assert not ob.hide_render and not ob.hide_viewport,ob.name
    assert all(not p.use_smooth for p in ob.data.polygons),ob.name
    assert all(math.isfinite(v) for row in ob.matrix_world for v in row),ob.name
specs=json.loads((ROOT/'source/texture_manifest_SK02.json').read_text())
packed=[]
for spec in specs:
    img=next(im for im in bpy.data.images if im.name==Path(spec['file']).name)
    assert img.packed_file,img.name
    assert hashlib.sha256(bytes(img.packed_file.data)).hexdigest()==hashlib.sha256((ROOT/spec['file']).read_bytes()).hexdigest()
    packed.append(img.name)
# Conservative AABB exclusion is enough for this axis-aligned interior reservation.
aisle=source['cargo_aisle'];lo=Vector([aisle[c][0] for c in 'xyz']);hi=Vector([aisle[c][1] for c in 'xyz']);blockers=[]
for name,ob in meshes.items():
    corners=[ob.matrix_world@Vector(v) for v in ob.bound_box]
    minimum=[min(p[i] for p in corners) for i in range(3)];maximum=[max(p[i] for p in corners) for i in range(3)]
    overlap=[min(maximum[i],hi[i])-max(minimum[i],lo[i]) for i in range(3)]
    if min(overlap)>.005:blockers.append({'part':name,'overlap':overlap})
assert not blockers,blockers
# Head space at each stair tread in the deliberately opened hatch art pose.
hatch=bpy.data.objects['18_Stair_hatch_leaf'];hatch.rotation_euler.x=math.radians(-85);bpy.context.view_layer.update()
stair_checks=[]
for step in range(6):
    tread=meshes[f'Hold_stair_{step}'];center=tread.matrix_world.translation
    lower=Vector((-.3,center.y-.12,center.z+.10));upper=Vector((.3,center.y+.12,center.z+1.88));hits=[]
    for name,ob in meshes.items():
        if name.startswith('Hold_stair_'):continue
        corners=[ob.matrix_world@Vector(v) for v in ob.bound_box]
        minimum=[min(p[i] for p in corners) for i in range(3)];maximum=[max(p[i] for p in corners) for i in range(3)]
        if min(min(maximum[i],upper[i])-max(minimum[i],lower[i]) for i in range(3))>.005:hits.append(name)
    assert not hits,(step,hits)
    stair_checks.append({'step':step,'headroom_blockers':hits})
hatch.rotation_euler.x=0;bpy.context.view_layer.update()
# Six-blade fan geometry and clear radial gap inside each duct; animation is only visual.
rotors={};motion={}
for label in ['Port','Starboard']:
    rotor=bpy.data.objects[label+'_rotor'];scene.frame_set(1);bpy.context.view_layer.update()
    blade=bpy.data.objects[label+'_blade_0'];point=Vector(blade.data.vertices[2].co)
    before=blade.matrix_world@point;scene.frame_set(13);bpy.context.view_layer.update();after=blade.matrix_world@point
    distance=(after-before).length;assert distance>2.0,(label,distance)
    motion[label]=distance
    assert len([o for o in meshes if o.startswith(label+'_blade_') and '_root_' not in o])==6
    origin=Vector(next(g['pivot'] for g in source['groups'] if g['name']==label+'_rotor'))
    scene.frame_set(1);bpy.context.view_layer.update()
    radius=max(math.hypot((ob.matrix_world@v.co-origin).x,(ob.matrix_world@v.co-origin).z)
        for name,ob in meshes.items() if name.startswith(label+'_blade_') for v in ob.data.vertices)
    # A circle smaller than the polygon's apothem stays clear at every rotor angle.
    apothem=(2.18-.24)*math.cos(math.pi/12);gap=apothem-radius;assert gap>.02,(label,gap)
    rotors[label]={'blades':6,'motion_at_frame13_blocks':distance,'minimum_swept_radial_gap_blocks':gap}
scene.frame_set(1);bpy.context.view_layer.update()
allpoints=[o.matrix_world@Vector(v) for o in meshes.values() for v in o.bound_box]
minimum=[min(p[i] for p in allpoints) for i in range(3)];maximum=[max(p[i] for p in allpoints) for i in range(3)]
native={'revision':'SK02','saved_blend_reopened':True,'source_parts_matched':len(meshes),'cargo_modules':24,'packed_images_verified':packed,
    'uvs_present':True,'flat_shaded_vehicle':True,'all_vehicle_parts_visible_on_open':True,
    'clear_aisle_blocks_xyz':[hi[i]-lo[i] for i in range(3)],'aisle_blockers':blockers,'stairs_open_hatch_pose':stair_checks,'rotors':rotors,
    'dimensions_blocks_xyz':[maximum[i]-minimum[i] for i in range(3)],'physics_verified':False,'runtime_inventory_verified':False}
(ROOT/'reports/SAVED_BLEND_SK02.json').write_text(json.dumps(native,indent=2)+'\n')
data=(ROOT/'exports/skat_SK02.glb').read_bytes();assert data[:4]==b'glTF'
length,kind=struct.unpack_from('<I4s',data,12);gltf=json.loads(data[20:20+length])
assert kind==b'JSON';assert all('bufferView' in im and 'uri' not in im for im in gltf['images'])
assert len(gltf['meshes'])==len(meshes)
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports/skat_SK02.glb'))
imported=[ob for ob in bpy.data.objects if ob.type=='MESH'];assert len(imported)==len(meshes)
assert len([ob for ob in imported if ob.name.startswith('Cargo_') and ob.name.endswith('_case')])==24
report={'revision':'SK02','self_contained_glb':True,'mesh_count':len(imported),'embedded_images':len(gltf['images']),
    'imported_in_blender':True,'static_export':True,'animated_rotors_in_native_blend_only':True,'studio_excluded':all(not ob.name.startswith(('STUDIO','SCALE')) for ob in bpy.data.objects)}
assert report['studio_excluded']
(ROOT/'reports/GLB_IMPORT_SK02.json').write_text(json.dumps(report,indent=2)+'\n')
print('SKAT_SK02_NATIVE_AND_GLB_VERIFIED',json.dumps({'native':native,'glb':report}))
