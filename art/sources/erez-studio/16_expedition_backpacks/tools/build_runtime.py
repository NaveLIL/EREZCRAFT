"""Same source meshes -> worn quads and native inventory quads, including exact material UVs."""
from pathlib import Path
import json,math,hashlib
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT/.verification/bp01');RES=GAME/'src/main/resources/assets/interstice'
source=ROOT/'source/backpacks_B02.json';data=json.loads(source.read_text(encoding='utf-8'))
runtime={'revision':data['revision'],'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'reference_back':2.22,'reference_front':-2.56,'magen_sternum_offset':3.6,
 'textures':{'canvas':'minecraft:textures/block/white_wool.png','metal':'minecraft:textures/models/armor/iron_layer_1.png','leather':'minecraft:textures/models/armor/leather_layer_1.png'},'sets':{}}
roles=['fabric','edge','leather','metal','dark','accent','light'];manifest=[]
def quads(part):
    out=[]
    for face in part['faces']:
        points=[part['vertices'][i] for i in face];a=[points[1][i]-points[0][i] for i in range(3)];b=[points[2][i]-points[0][i] for i in range(3)]
        normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];length=math.sqrt(sum(x*x for x in normal));normal=[x/length for x in normal]
        axis=max(range(3),key=lambda i:abs(normal[i]));axes=[i for i in range(3) if i!=axis];low=[min(p[i] for p in points) for i in axes];high=[max(p[i] for p in points) for i in axes]
        uv=[[(p[i]-low[j])/max(1e-9,high[j]-low[j]) for j,i in enumerate(axes)] for p in points]
        if part['texture'] in ['metal','leather']:uv=[[(8+u*8)/64,v*8/32] for u,v in uv]
        # Convex even-sided caps tile into true quads, without duplicate triangle vertices.
        indices=[list(range(4))] if len(face)==4 else [[0,i,i+1,i+2] for i in range(1,len(face)-2,2)]
        for face_ in indices:out.append({'vertices':[points[i] for i in face_],'uv':[uv[i] for i in face_],'normal':normal,'role':roles.index(part['role']),'material':part['texture'],'anchor':part['anchor'],'name':part['name']})
    return out
for tier,pack in data['proposed'].items():
    faces=[f for p in pack['parts'] for f in quads(p)]
    runtime['sets'][tier]={'palette':[pack['palette'][r] for r in roles],'quads':faces}
    # Only the rear pack belongs in a handheld/item miniature; front straps are worn harness parts.
    visible=[f for f in faces if f['anchor']=='back']
    low=[min(p[i] for f in visible for p in f['vertices']) for i in range(3)];high=[max(p[i] for f in visible for p in f['vertices']) for i in range(3)];center=[(a+b)/2 for a,b in zip(low,high)];scale=13.5/max(high[i]-low[i] for i in range(3))
    item=[]
    for face in visible:
        row={'role':face['role'],'material':face['material'],'uv':list(reversed(face['uv'])),'vertices':[[round(.5+(p[i]-center[i])*scale/16*(1 if i!=1 else -1),7) for i in range(3)] for p in reversed(face['vertices'])]};item.append(row)
    model={'loader':'interstice:armor_icon','gui_light':'front','ambientocclusion':False,'textures':{'canvas':'minecraft:block/white_wool','metal':'interstice:armor_icons/iron','leather':'interstice:armor_icons/leather','particle':'minecraft:block/white_wool'},'palette':[pack['palette'][r] for r in roles],'quads':item,
           'display':{'gui':{'rotation':[15,-25,0],'scale':[1,1,1]},'ground':{'translation':[0,2,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,0,0],'scale':[.8,.8,.8]},'thirdperson_righthand':{'rotation':[75,0,0],'translation':[0,2,0],'scale':[.45,.45,.45]},'firstperson_righthand':{'rotation':[0,-25,0],'scale':[.65,.65,.65]}},'erez_source':{'geometry':source.name,'sha256':runtime['source_sha256'],'tier':tier,'quads':len(item)}}
    p=RES/'models/item'/(tier+'.json');p.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8');manifest.append({'item':tier,'worn_quads':len(faces),'item_quads':len(item)})
p=RES/'equipment/backpacks.json';p.parent.mkdir(exist_ok=True);p.write_text(json.dumps(runtime,separators=(',',':'))+'\n',encoding='utf-8')
(ROOT/'reports/runtime_models.json').write_text(json.dumps({'source_sha256':runtime['source_sha256'],'models':manifest},indent=2),encoding='utf-8')
print(manifest)
