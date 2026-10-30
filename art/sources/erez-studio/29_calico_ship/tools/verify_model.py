from pathlib import Path
import bpy,bmesh,json,hashlib,math
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/calico_MC01.json').read_text(encoding='utf8'))
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/calico_MC01.blend'))
meshes=[ob for ob in bpy.data.objects if ob.type=='MESH' and 'source_part' in ob]
assert len(meshes)==len(data['parts'])==168
assert len({p['name'] for p in data['parts']})==len(data['parts'])
problems=[];triangles=0
for ob in meshes:
    bm=bmesh.new();bm.from_mesh(ob.data)
    if any(not e.is_manifold for e in bm.edges):problems.append(ob.name+': non-manifold source')
    if any(f.calc_area()<1e-9 for f in bm.faces):problems.append(ob.name+': zero-area face')
    if any(not all(math.isfinite(x) for x in v.co) for v in bm.verts):problems.append(ob.name+': non-finite vertex')
    triangles+=sum(len(f.verts)-2 for f in bm.faces);bm.free()
assert not problems,problems
source_bounds=[ob.matrix_world@Vector(c) for ob in meshes for c in ob.bound_box]
head=bpy.data.objects['cat / ivory head'];nose=bpy.data.objects['cat / pink triangular nose']
front=lambda ob:min((ob.matrix_world@Vector(c)).y for c in ob.bound_box)
projection=front(head)-front(nose);assert projection>.8
old=json.loads((ROOT/'reports/SKAT_PRESERVATION_BASELINE.json').read_text())
unchanged=all(Path(p).exists() and hashlib.sha256(Path(p).read_bytes()).hexdigest()==h for p,h in old.items())
assert unchanged,'Existing Skat source changed since baseline; inspect concurrent work before any claim'
# Read the exported GLB back in a clean scene, independently of the authoring file.
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports/calico_MC01.glb'))
imported=[o for o in bpy.data.objects if o.type=='MESH'];assert len(imported)==168
points=[o.matrix_world@Vector(c) for o in imported for c in o.bound_box]
# glTF may change axis convention on import; compare sorted overall extents.
extent=lambda ps:sorted(max(p[i] for p in ps)-min(p[i] for p in ps) for i in range(3))
err=max(abs(a-b) for a,b in zip(extent(source_bounds),extent(points)));assert err<.25,err
report={'revision':'MC01','source_parts':168,'closed_source_meshes':168,'finite_vertices':True,'zero_area_faces':0,
 'source_triangles_before_bevel':triangles,'raised_nose_projection_blocks':round(projection,4),
 'glb_round_trip_meshes':len(imported),'glb_bounds_max_difference_blocks':err,
 'existing_skat_files_checked':len(old),'existing_skat_files_unchanged':unchanged,
 'native_blender_art_verified':True,'minecraft_runtime_verified':False,'physics_verified':False}
(ROOT/'reports/GEOMETRY_CHECK.json').write_text(json.dumps(report,indent=2)+'\n')
print('CALICO_GEOMETRY_PASS',json.dumps(report))
