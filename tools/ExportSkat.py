"""Read SK02's saved Blender scene, without modifying it; derive exact block meshes.

Run with isolated Blender --background --factory-startup --python tools/ExportSkat.py.
All coordinates remain in Minecraft blocks. X,Z,Y swaps Blender's up axis;
the 0.5 X translation aligns native Create shafts with the authored rotor hubs.
"""
from pathlib import Path
import bpy, json, math, itertools, hashlib, shutil
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('D:/repos/Blender/creations/25_skat')
OUT = ROOT/'src/main/resources'
bpy.ops.wm.open_mainfile(filepath=str(SOURCE/'assembly/skat_SK02.blend'))
bpy.context.scene.frame_set(1)
design=json.loads((SOURCE/'source/skat_SK02.json').read_text(encoding='utf8'))
cells={}; rotors=[]; doors=[]; door_sides={'door_port':[],'door_starboard':[]};hatches=[]; source_count=0
textures={m['name']:'interstice:block/skat/'+m['name'] for m in json.loads((SOURCE/'source/texture_manifest_SK02.json').read_text())}
textures['particle']=textures['graphite']

def convert(v): return [float(v.x)+.5,float(v.z),float(v.y)]
def clean(a): return [round(float(v),6) for v in a]
def clip(poly,axis,limit,sign):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        da=(a[axis]-limit)*sign;db=(b[axis]-limit)*sign
        if da>=-1e-8:result.append(a)
        if (da>1e-8 and db< -1e-8) or (da< -1e-8 and db>1e-8):
            t=da/(da-db);result.append([x+(y-x)*t for x,y in zip(a,b)])
    return result
def split(poly,axes):
    result=[poly]
    for axis in axes:
        next_result=[]
        for p in result:
            lo=math.floor(min(v[axis] for v in p)+1e-6)
            hi=math.floor(max(v[axis] for v in p)-1e-6)
            for c in range(lo,max(lo,hi)+1):
                q=clip(clip(p,axis,c,1),axis,c+1,-1)
                if len(q)>=3:next_result.append(q)
        result=next_result
    return result
def quads(poly,material,origin=(0,0,0)):
    # Faces are split at UV repeat boundaries before atlas packing, preserving texel density.
    result=[]
    for p in split(poly,[3,4]):
        u=math.floor(sum(v[3] for v in p)/len(p));v=math.floor(sum(v[4] for v in p)/len(p))
        for i in range(1,len(p)-1):
            q=[p[0],p[i],p[i+1],p[i+1]]
            if (Vector(q[1][:3])-Vector(q[0][:3])).cross(Vector(q[2][:3])-Vector(q[0][:3])).length<1e-8:continue
            vertices=[clean([a[j]-origin[j] for j in range(3)]) for a in q]
            # Atlas/cell clipping can produce microscopic slivers. Quantization may
            # collapse one to a line; validate the serialized points, not just originals.
            if (Vector(vertices[1])-Vector(vertices[0])).cross(Vector(vertices[2])-Vector(vertices[0])).length<1e-9:continue
            result.append({'material':material,'vertices':vertices,
                           'uv':[clean([max(0,min(1,a[3]-u)),max(0,min(1,a[4]-v))]) for a in q]})
    return result
def cell(p):return cells.setdefault(tuple(p),{'pos':list(p),'boxes':[],'quads':[],'role':'part','cargo':-1})

for ob in bpy.data.objects:
    if ob.type!='MESH' or 'source_part' not in ob:continue
    source_count+=1
    spec=next(p for p in design['parts'] if p['name']==ob['source_part'])
    group=spec['group']; mesh=ob.data; uv=mesh.uv_layers.active.data;material=spec['material']
    dynamic=group in design['rotor_groups'] or group=='18_Stair_hatch_leaf' or spec['name'].startswith('Cabin_rear_door')
    target=rotors if group in design['rotor_groups'] else hatches if group=='18_Stair_hatch_leaf' else doors
    pivot=next(g['pivot'] for g in design['groups'] if g['name']==group)
    origin=convert(Vector(pivot)) if group in design['rotor_groups'] else [0,0,0]
    # Axis swap reverses handedness: reverse winding as well.
    for face in mesh.polygons:
        poly=[]
        for li in reversed(list(face.loop_indices)):
            vertex=ob.matrix_world@mesh.vertices[mesh.loops[li].vertex_index].co
            poly.append(convert(vertex)+[float(uv[li].uv.x),float(uv[li].uv.y)])
        if dynamic:
            # Both rotors share the same authored geometry, stored around their own shaft.
            if group!='Starboard_rotor':
                faces=quads(poly,material,origin);target.extend(faces)
                if target is doors:door_sides['door_port' if spec['center'][0]<0 else 'door_starboard'].extend(faces)
            continue
        for q in split(poly,[0,1,2]):
            anchor=[math.floor(sum(v[i] for v in q)/len(q)+1e-7) for i in range(3)]
            cell(anchor)['quads'].extend(quads(q,material,anchor))
            if material=='lamp':cell(anchor)['lamp']=True
    if not dynamic:
        # Conservative cuboid collision for each original part, clipped to its cells.
        # Sloping glazing uses its tight AABB; no whole-ship invisible bounding box.
        points=[convert(ob.matrix_world@Vector(v)) for v in ob.bound_box]
        lo=[min(p[i] for p in points) for i in range(3)];hi=[max(p[i] for p in points) for i in range(3)]
        # Ring ducts are hollow: use each side face's AABB instead of filling the disk.
        boxes=[(lo,hi)]
        if spec['shape']=='ring':
            boxes=[]
            for face in mesh.polygons:
                pp=[convert(ob.matrix_world@mesh.vertices[mesh.loops[i].vertex_index].co) for i in face.loop_indices]
                a=[min(p[i] for p in pp)-.015 for i in range(3)];b=[max(p[i] for p in pp)+.015 for i in range(3)];boxes.append((a,b))
        for a,b in boxes:
            for xyz in itertools.product(*(range(math.floor(a[i]),math.ceil(b[i]-1e-7)) for i in range(3))):
                bounds=[max(a[i],xyz[i])-xyz[i] for i in range(3)]+[min(b[i],xyz[i]+1)-xyz[i] for i in range(3)]
                if all(bounds[i+3]-bounds[i]>1e-6 for i in range(3)):cell(xyz)['boxes'].append(clean(bounds))

assert source_count==1615,source_count
for spec in design['parts']:
    if spec['group']!='18_Stair_hatch_leaf' and not spec['name'].startswith('Cabin_rear_door'):continue
    center=convert(Vector(spec['center']));size=[spec['size'][0],spec['size'][2],spec['size'][1]]
    a=[center[i]-size[i]/2 for i in range(3)];b=[center[i]+size[i]/2 for i in range(3)]
    for xyz in itertools.product(*(range(math.floor(a[i]),math.ceil(b[i]-1e-7)) for i in range(3))):
        c=cell(xyz);c['role']='opening';c['opening']='hatch' if spec['group']=='18_Stair_hatch_leaf' else 'doors'
        c.setdefault('closed_boxes',[]).append(clean([max(a[i],xyz[i])-xyz[i] for i in range(3)]+[min(b[i],xyz[i]+1)-xyz[i] for i in range(3)]))
for n,cargo in enumerate(design['cargo_modules']):
    c=convert(Vector(cargo['center']));anchor=tuple(math.floor(v) for v in c)
    assert cell(anchor)['cargo']==-1
    cell(anchor).update(role='cargo',cargo=n)
# Exact native propeller positions; static trim in these cells is retained by their state model.
for x in [-3,3]:cell((x,6,10))['role']='propeller';cell((x,6,9))['role']='engine'
# Create's native seat entities handle passengers inside moving Sable plots.
for x in [-2,2]:cell((x,4,-5))['role']='seat'
cell((0,4,-6))['role']='helm'
ordered=sorted(cells.values(),key=lambda c:tuple(c['pos']))
for i,c in enumerate(ordered):c['id']=i
assert len(ordered)<4096
def dump(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,separators=(',',':'),ensure_ascii=False)+'\n',encoding='utf8')
for c in ordered:
    dump(OUT/f"assets/interstice/models/block/skat/c{c['id']}.json",{'loader':'interstice:skat','textures':textures,'quads':c.pop('quads')})
variants={f"cell={c['id']}":{'model':f"interstice:block/skat/c{c['id']}"} for c in ordered}
for name in ['skat_part','skat_cargo','skat_helm','skat_seat','skat_opening']:
    dump(OUT/f'assets/interstice/blockstates/{name}.json',{'variants':variants})
# Kinetic blocks have the native FACING/REVERSED state too; multipart ignores it for fixed template meshes.
for name in ['skat_propeller','skat_engine']:
    dump(OUT/f'assets/interstice/blockstates/{name}.json',{'multipart':[{'when':{'port':str(c['pos'][0]<0).lower()},'apply':variants[f"cell={c['id']}"]} for c in ordered if c['role']==name[5:]]})
dump(OUT/'data/interstice/skat/sk02.json',{'revision':'SK02','cells':ordered,'cargo':[dict(id=c['id'],center=convert(Vector(c['center']))) for c in design['cargo_modules']],
    'dimensions':[11.11,8.597764,25.675],'source_meshes':source_count})
for name,faces in [('rotor',rotors),('doors',doors),('hatch',hatches)]:
    dump(OUT/f'assets/interstice/models/block/skat/{name}.json',{'loader':'interstice:skat','textures':textures,'quads':faces})
for name,faces in door_sides.items():dump(OUT/f'assets/interstice/models/block/skat/{name}.json',{'loader':'interstice:skat','textures':textures,'quads':faces})
for material in textures:
    src=SOURCE/f'source/textures_SK02/{material}.png'
    if src.exists():
        dest=OUT/f'assets/interstice/textures/block/skat/{material}.png';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest)
provenance={'source':'Blender/creations/25_skat','revision':'SK02','axis_mapping':'Minecraft (x,y,z) = Blender (x+.5,z,y)','source_meshes':source_count,'block_cells':len(ordered),
    'files':{str(p.relative_to(SOURCE)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [SOURCE/'source/skat_SK02.json',SOURCE/'assembly/skat_SK02.blend',SOURCE/'source/ASSET_PROVENANCE_SK02.json']}}
dump(OUT/'assets/interstice/provenance/skat_sk02.json',provenance)
shutil.copyfile(SOURCE/'source/ASSET_PROVENANCE_SK02.json',OUT/'assets/interstice/provenance/skat_textures.json')
print(json.dumps(provenance));print('Export complete; original scene was not saved.')
