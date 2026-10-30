"""Confirm the preserved compact version still opens independently of SK02."""
from pathlib import Path
import bpy,json,hashlib,struct
ROOT=Path(__file__).resolve().parents[1]
source=json.loads((ROOT/'source/skat_SK01.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/skat_SK01.blend'))
meshes={ob['source_part']:ob for ob in bpy.data.objects if ob.type=='MESH' and 'source_part' in ob}
assert set(meshes)=={part['name'] for part in source['parts']}
assert sum(ob.get('design_tag')=='cargo_module' for ob in meshes.values())==24
for record in json.loads((ROOT/'source/texture_manifest.json').read_text()):
    image=next(im for im in bpy.data.images if im.name==Path(record['file']).name)
    assert image.packed_file
    assert hashlib.sha256(bytes(image.packed_file.data)).hexdigest()==hashlib.sha256((ROOT/record['file']).read_bytes()).hexdigest()
data=(ROOT/'exports/skat_SK01.glb').read_bytes();length,kind=struct.unpack_from('<I4s',data,12);gltf=json.loads(data[20:20+length])
assert kind==b'JSON' and len(gltf['meshes'])==len(meshes)
assert all('bufferView' in im and 'uri' not in im for im in gltf['images'])
for ob in list(bpy.data.objects):bpy.data.objects.remove(ob,do_unlink=True)
bpy.ops.import_scene.gltf(filepath=str(ROOT/'exports/skat_SK01.glb'))
assert len([ob for ob in bpy.data.objects if ob.type=='MESH'])==len(meshes)
report={'revision':'SK01','preserved_by_owner_request':True,'saved_scene_reopened':True,'source_meshes_matched':len(meshes),
    'packed_images_verified':True,'self_contained_glb_reimported':True,'cargo_modules':24,'physics_verified':False}
(ROOT/'reports/PRESERVED_SK01_CHECK.json').write_text(json.dumps(report,indent=2)+'\n')
print('PRESERVED_SK01_VERIFIED',json.dumps(report))
