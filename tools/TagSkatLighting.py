"""Annotate exported SK03 meshes from the author's existing lamp parts; no pixels/geometry change."""
from pathlib import Path
import json, math
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
source=json.loads(Path(globals().get('SKAT_DESIGN_PATH','D:/repos/Blender/creations/25_skat/source/skat_SK03.json')).read_text(encoding='utf8'))
lamps=[]
for p in source['parts']:
    if p['material'] not in ('lamp','cyan'):continue
    assert p['shape']=='cube' and not any(p.get('rotation',[0,0,0])),p['name']
    c=p['center'];s=p['size'];center=[c[0]+.5,c[2],c[1]];size=[s[0],s[2],s[1]]
    channel=1 if p['name'].startswith('Cabin_headlamp_face') else 2 if p['material']=='lamp' or 'boarding_entry_lamp' in p['name'] else 4
    a=center.copy();b=center.copy();axis=max(range(3),key=lambda i:size[i])
    if size[axis]>1:a[axis]-=size[axis]/2-.1;b[axis]+=size[axis]/2-.1
    lamps.append(dict(name=p['name'],material=p['material'],center=center,size=size,a=a,b=b,channel=channel))
template=RES/'data/interstice/skat/sk03.json';data=json.loads(template.read_text())
counts={}
for cell in data['cells']:
    path=RES/f"assets/interstice/models/block/skat/c{cell['id']}.json";model=json.loads(path.read_text());mask=0
    for q in model['quads']:
        if q['material'] not in ('lamp','cyan'):continue
        center=[cell['pos'][i]+sum(v[i] for v in q['vertices'])/4 for i in range(3)]
        matches=[p for p in lamps if p['material']==q['material'] and all(abs(center[i]-p['center'][i])<=p['size'][i]/2+.00001 for i in range(3))]
        assert matches,(cell['id'],center)
        channels={p['channel'] for p in matches};assert len(channels)==1
        q['light_channel']=channels.pop();mask|=q['light_channel']
    if mask:
        assert mask in (1,2,4),(cell['id'],mask)
        cell['light_mask']=mask
        if cell['role']=='part':cell['role']='lamp'
        assert cell['role'] in ('lamp','boarding'),(cell['id'],cell['role'])
        counts[mask]=counts.get(mask,0)+1
    path.write_text(json.dumps(model,separators=(',',':'))+'\n')
data['lights']=[{k:v for k,v in p.items() if k not in ('material','size')} for p in lamps]
template.write_text(json.dumps(data,separators=(',',':'))+'\n')
(RES/'assets/interstice/blockstates/skat_lamp.json').write_bytes((RES/'assets/interstice/blockstates/skat_part.json').read_bytes())
print(json.dumps({'lamp_parts':len(lamps),'light_cells_by_channel':counts,'cell_identity_preserved':True}))
