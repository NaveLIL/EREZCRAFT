"""Small thickness adjustment to the accepted LS02, preserving its other geometry."""
from pathlib import Path
import json,copy,hashlib
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'source/lurker_LS02.bbmodel'
base=json.loads(source.read_text(encoding='utf8'));model=copy.deepcopy(base)
model['name']='EREZCRAFT Lurker LS03 - slightly thicker legs'
groups={g['uuid']:g for g in model['groups']};owner={};nodes={}
def walk(node):
    name=groups[node['uuid']]['name'];nodes[name]=node
    for c in node['children']:
        if isinstance(c,dict):walk(c)
        else:owner[c]=name
walk(model['outliner'][0])
elements={e['uuid']:e for e in model['elements']}
original=copy.deepcopy(elements)
changes=[];adjusted=set()
for e in model['elements']:
    name=owner[e['uuid']]
    if 'leg' not in name or '_bristle_' in e['name']:continue
    # Only the original principal leg cubes; all ornamental pieces remain thin.
    if e['name']!='cube':continue
    lengths=[e['to'][i]-e['from'][i] for i in range(3)]
    axis=max(range(3),key=lambda i:lengths[i]);cross=2 if axis==0 else 0
    factor=1.12 if min(lengths)<2.1 else 1.18
    for i in range(3):
        if i==axis:continue
        center=(e['from'][i]+e['to'][i])/2;half=lengths[i]*factor/2
        e['from'][i]=round(center-half,6);e['to'][i]=round(center+half,6)
    adjusted.add(e['uuid'])
    # Move existing bristles outward with the new surface, keeping their size and angle.
    for child in nodes[name]['children']:
        if not isinstance(child,str):continue
        hair=elements[child]
        if '_bristle_' not in hair['name']:continue
        sign=int(hair['name'].rsplit('_',1)[1]);delta=sign*lengths[cross]*(factor-1)/2
        for key in ['from','to','origin']:hair[key][cross]=round(hair[key][cross]+delta,6)
        adjusted.add(hair['uuid'])
    before=original[e['uuid']]
    assert e['from'][axis]==before['from'][axis] and e['to'][axis]==before['to'][axis]
    changes.append({'part':name,'factor':factor,'axis':axis,'length':lengths[axis],
                    'width_before':lengths[cross],'width_after':round(lengths[cross]*factor,6),
                    'longitudinal_endpoints_preserved':True})
assert len(changes)==36,len(changes)
assert model['groups']==base['groups'] and model['animations']==base['animations']
assert model['outliner']==base['outliner'] and model['textures']==base['textures']
for uid,e in elements.items():
    if uid not in adjusted:assert e==original[uid],e['name']
destination=ROOT/'source/lurker_LS03.bbmodel'
destination.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
report={'design':'LS03','baseline':'LS02','base_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'model_sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),
        'leg_lengths':changes,'main_leg_thickness_increase_percent':18,'tip_thickness_increase_percent':12,
        'leg_lengths_all_preserved':True,'groups_identical':True,'animations_identical':True,
        'texture_bytes_identical':True,'non_leg_geometry_identical_to_LS02':True,
        'bristle_dimensions_preserved':True,'prototype_cubes':len(model['elements']),
        'source':str(source),'game_verified':False}
(ROOT/'reports/design_LS03.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='leg_lengths'}))
