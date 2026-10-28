"""WT05: length-calibrated working tools; retain WT04 head width/depth and grip geometry."""
from pathlib import Path
import json,copy,hashlib,math,itertools
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'source/worktools_WT04.json';source=json.loads(BASE.read_text(encoding='utf8'));d=copy.deepcopy(source)
ref=json.loads((ROOT/'reports/vanilla_reference/run.json').read_text(encoding='utf8'))['proof']['native_metrics'];ref={r['item']:r for r in ref}
STH=.62;SFP=.496
GRIP={'grip','pommel','grip_binding_2','grip_binding_3.3','grip_binding_4.6'}
def vertices(model):return [p for e in model['elements'] for p in itertools.product(*zip(e['from'],e['to']))]
def diameter(model):
 v=vertices(model);return math.sqrt(max(sum((a[i]-b[i])**2 for i in range(3)) for j,a in enumerate(v) for b in v[:j]))
def extend(model,delta):
 n=copy.deepcopy(model)
 for e in n['elements']:
  if e['name']=='haft':e['to'][1]+=delta
  elif e['name'] not in GRIP:
   e['from'][1]+=delta;e['to'][1]+=delta
  e['from']=[round(v,5) for v in e['from']];e['to']=[round(v,5) for v in e['to']]
 return n
def rot(p,r):
 x,y,z=p;a,b,c=map(math.radians,r)
 x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
 x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
 y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
 return [x,y,z]
def frame(model,name,maximum):
 t=model['display'][name];v=[rot([p[i]-8 for i in range(3)],t.get('rotation',[0,0,0])) for p in vertices(model)]
 lo=[min(p[i] for p in v) for i in range(3)];hi=[max(p[i] for p in v) for i in range(3)]
 s=min(t['scale'][0],maximum/max(hi[i]-lo[i] for i in [0,1]))
 t['scale']=[round(s,6)]*3;t['translation']=[round(-(lo[i]+hi[i])*.5*s,5) if i<2 else 0 for i in range(3)]
rows=[];deltas={}
for kind in ['pickaxe','axe','shovel','hoe']:
 target=ref['iron_'+kind]['THIRD_PERSON_RIGHT_HAND']['opaque_geometry_diameter_model_units']
 base=source['models']['riftsteel_'+kind]['model'];lo,hi=0.,14.
 for _ in range(38):
  mid=(lo+hi)/2
  if diameter(extend(base,mid))*STH<target:lo=mid
  else:hi=mid
 delta=round((lo+hi)*.5*4)/4;deltas[kind]=delta
for name,row in d['models'].items():
 old=source['models'][name]['model'];n=extend(old,deltas[row['kind']]);row['model']=n
 for context in ['firstperson_righthand','firstperson_lefthand']:
  t=n['display'][context];prior=old['display'][context];r=list(prior['rotation'])
  if context.endswith('lefthand'):r[1]*=-1;r[2]*=-1
  anchor=rot([0,3.85-8,0],r)
  t['translation']=[round(prior['translation'][i]+(prior['scale'][0]-SFP)*anchor[i],5) for i in range(3)];t['scale']=[SFP]*3
 frame(n,'gui',13.4);frame(n,'fixed',14)
 n['display']['ground']['translation']=[0,2,0];n['display']['ground']['scale']=[round(.5*STH/.85,6)]*3
 # Exact width/depth and unchanged grip/pommel; head shape is translated, never resized.
 for a,b in zip(old['elements'],n['elements']):
  for axis in [0,2]:assert a['from'][axis]==b['from'][axis] and a['to'][axis]==b['to'][axis]
  if a['name'] in GRIP:assert a==b
  if a['name']!='haft':assert abs((a['to'][1]-a['from'][1])-(b['to'][1]-b['from'][1]))<1e-5
  assert all(-16<=x<=32 for x in b['from']+b['to'])
  if b['name']=='haft':
   w,h,z=[b['to'][i]-b['from'][i] for i in range(3)];dims={'north':(w,h),'south':(w,h),'east':(z,h),'west':(z,h),'up':(w,z),'down':(w,z)}
   for side,face in b['faces'].items():face['uv']=[1,1,round(1+min(14,dims[side][0]),4),round(1+min(14,dims[side][1]),4)]
 target=ref['iron_'+row['kind']]['THIRD_PERSON_RIGHT_HAND']['opaque_geometry_diameter_model_units'];actual=diameter(n)*STH
 assert .985<=actual/target<=1.015,(name,'size target')
 export=ROOT/'exports/WT05/assets/interstice/models/item'/(name+'.json');export.parent.mkdir(parents=True,exist_ok=True);export.write_text(json.dumps(n,indent=2)+'\n',encoding='utf8')
 rows.append({'item':name,'shaft_extension':deltas[row['kind']],'third_person_target':target,'third_person_predicted':actual,'percent_of_vanilla_predicted':actual/target*100,'width_depth_unchanged':True,'grip_geometry_unchanged':True,'first_person_scale':SFP,'first_person_grip_anchor_preserved':True})
d['revision']='WT05';d['previous']={n:row['model'] for n,row in source['models'].items()};d['stage']='Length and grip calibration from actual native vanilla tool reference';d['change']={'base':'WT04','base_sha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),'shaft_extensions':deltas,'third_person_scale':STH,'first_person_scale':SFP,'first_to_third_scale_ratio':.8,'head_width_depth_shape_unchanged':True,'grip_geometry_unchanged':True,'gui_and_fixed_framing_recomputed':True}
p=ROOT/'source/worktools_WT05.json';p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf8')
report={'passed':True,'revision':'WT05','source_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'rows':rows,'textures_changed':False,'gameplay_changed':False,'measurement_source':'reports/vanilla_reference/run.json'}
(ROOT/'reports/design_WT05_calibration.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print(json.dumps({'extensions':deltas,'predicted_ratios':[round(r['percent_of_vanilla_predicted'],2) for r in rows]}))
