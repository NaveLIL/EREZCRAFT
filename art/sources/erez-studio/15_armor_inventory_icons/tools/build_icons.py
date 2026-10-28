"""Exact six-bone geometry to item quads; rotations stay exact, no painted PNGs."""
from pathlib import Path
import hashlib,json,math
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ai01')
sources=[ROOT.parent/'13_minecraft_armor/source/armor_design_A04.json',ROOT.parent/'14_kotomyaf_armor/source/kotomyaf_design_A04.json']
roles=['plate','edge','shadow','cloth','strap','buckle','dark']
face_indices=[[1,0,3,2],[4,5,6,7],[0,4,7,3],[5,1,2,6],[0,1,5,4],[7,6,2,3]]
records=[]
atlas=GAME/'src/main/resources/assets/minecraft/atlases/blocks.json';atlas.parent.mkdir(parents=True,exist_ok=True)
atlas.write_text(json.dumps({'sources':[
    {'type':'minecraft:single','resource':'minecraft:models/armor/iron_layer_1','sprite':'interstice:armor_icons/iron'},
    {'type':'minecraft:single','resource':'minecraft:models/armor/leather_layer_1','sprite':'interstice:armor_icons/leather'}]},indent=2)+'\n',encoding='utf-8')
def transformed(c,point,bones):
    v=list(point);pivot=c.get('rotation_pivot',[0,0,0]);v=[v[i]-pivot[i] for i in range(3)]
    for axis,degrees in enumerate(c.get('rotation',[0,0,0])):
        theta=math.radians(degrees);co,si=math.cos(theta),math.sin(theta);x,y,z=v
        v=([x,co*y-si*z,si*y+co*z] if axis==0 else [co*x+si*z,y,-si*x+co*z] if axis==1 else [co*x-si*y,si*x+co*y,z])
    return [v[i]+pivot[i]+bones[c['bone']][i] for i in range(3)]
for source in sources:
    design=json.loads(source.read_text(encoding='utf-8'));source_sha=hashlib.sha256(source.read_bytes()).hexdigest()
    for tier,part in design['sets'].items():
        for kind,slot in [('helmet','head'),('chestplate','chest'),('leggings','legs'),('boots','feet')]:
            cubes=[c for c in part['cubes'] if c['slot']==slot];faces=[]
            for c in cubes:
                x,y,z=c['from'];dx,dy,dz=c['size']
                points=[[x,y,z],[x+dx,y,z],[x+dx,y+dy,z],[x,y+dy,z],[x,y,z+dz],[x+dx,y,z+dz],[x+dx,y+dy,z+dz],[x,y+dy,z+dz]]
                points=[transformed(c,p,design['bones']) for p in points]
                # Y inversion changes handedness: reverse each face for outward normals.
                for indices in face_indices:faces.append({'role':roles.index(c['role']),'vertices':[[points[i][0],-points[i][1],points[i][2]] for i in reversed(indices)]})
            low=[min(p[i] for f in faces for p in f['vertices']) for i in range(3)];high=[max(p[i] for f in faces for p in f['vertices']) for i in range(3)]
            center=[(a+b)/2 for a,b in zip(low,high)]
            # Fit projected bounds at the declared GUI rotation, reserving a pixel border.
            rx,ry=math.radians(15),math.radians(155)
            projected=[]
            for f in faces:
                for p in f['vertices']:
                    x,y,z=[p[i]-center[i] for i in range(3)];y,z=math.cos(rx)*y-math.sin(rx)*z,math.sin(rx)*y+math.cos(rx)*z
                    projected.append((math.cos(ry)*x+math.sin(ry)*z,y))
            span=max(max(p[i] for p in projected)-min(p[i] for p in projected) for i in range(2))
            scale=14.5/span
            for f in faces:
                f['vertices']=[[round(.5+(p[i]-center[i])*scale/16,7) for i in range(3)] for p in f['vertices']]
            name=tier+'_'+kind
            model={'loader':'interstice:armor_icon','ambientocclusion':False,'gui_light':'front',
                'textures':{'armor':'interstice:armor_icons/iron','strap':'interstice:armor_icons/leather','particle':'interstice:armor_icons/iron'},
                'palette':[part['palette'][r] for r in roles],'quads':faces,
                'display':{'gui':{'rotation':[15,155,0],'translation':[0,0,0],'scale':[1,1,1]},
                    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.35,.35,.35]},
                    'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
                    'thirdperson_righthand':{'rotation':[75,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
                    'firstperson_righthand':{'rotation':[0,155,0],'translation':[0,0,0],'scale':[.65,.65,.65]}},
                'erez_source':{'geometry':source.name,'sha256':source_sha,'tier':tier,'slot':slot,'cubes':len(cubes),'all_bones_included':True}}
            payload=(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n').encode()
            relative='src/main/resources/assets/interstice/models/item/'+name+'.json';out=GAME/relative;out.write_bytes(payload)
            export=ROOT/'exports/models/item'/out.name;export.parent.mkdir(parents=True,exist_ok=True);export.write_bytes(payload)
            records.append({'item':'interstice:'+name,'source':str(source),'source_sha256':source_sha,'cubes':len(cubes),'quads':len(faces),'bones':sorted({c['bone'] for c in cubes}),'model_path':relative,'model_sha256':hashlib.sha256(payload).hexdigest(),'projected_gui_span_pixels':14.5})
assert len(records)==16
(ROOT/'source/icon_manifest.json').write_text(json.dumps({'revision':'ARMOR-ICONS-A01','units':'One item model unit = one block; source armor units divided by 16 and uniformly fitted for GUI','new_pngs':False,'exact_source_rotations':True,'icons':records},indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('Built',len(records),'exact item models')
