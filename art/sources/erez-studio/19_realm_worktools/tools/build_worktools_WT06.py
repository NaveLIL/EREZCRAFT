"""WT06: visible held orientation and longer shovel blade; no width/depth growth."""
from pathlib import Path
import json,copy,math,itertools,hashlib
ROOT=Path(__file__).resolve().parents[1]
w4=json.loads((ROOT/'source/worktools_WT04.json').read_text(encoding='utf8'))
w5=json.loads((ROOT/'source/worktools_WT05.json').read_text(encoding='utf8'))
d=copy.deepcopy(w5)
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

rows=[]
for name,row in d['models'].items():
 old=w5['models'][name]['model'];n=copy.deepcopy(old)
 if row['kind']=='shovel':
  n=extend(w4['models'][name]['model'],3.5)
  for e in n['elements']:
   if e['name'] not in GRIP|{'haft','neck_band'}:
    for key in ['from','to']:e[key][1]=round(14.0+(e[key][1]-14.0)*1.8,5)
  n['display']=copy.deepcopy(old['display'])
 # Third-person shaft is turned out of the depth axis; hand anchor is preserved.
 for context in ['thirdperson_righthand','thirdperson_lefthand']:
  t=n['display'][context];r=list(t['rotation']);left=context.endswith('lefthand')
  if left:r[1]*=-1;r[2]*=-1
  a=rot([0,3.85-8,0],r);r[0]=75;r[1]=0
  b=rot([0,3.85-8,0],r)
  t['rotation'][0]=75;t['rotation'][1]=0
  t['translation']=[round(t['translation'][i]+t['scale'][i]*(a[i]-b[i])*(-1 if left and i==0 else 1),5) for i in range(3)]
 for context in ['firstperson_righthand','firstperson_lefthand']:
  t=n['display'][context];t['rotation'][1]=45 if context.endswith('lefthand') else -45
  t['translation'][0]=-.64;t['translation'][2]=-2.4
 frame(n,'gui',13.4);frame(n,'fixed',14)
 for a,b in zip(w4['models'][name]['model']['elements'],n['elements']):
  for axis in [0,2]:assert a['from'][axis]==b['from'][axis] and a['to'][axis]==b['to'][axis],(name,'width/depth')
  if a['name'] in GRIP:assert a==b
  assert all(-16<=v<=32 for v in b['from']+b['to'])
 row['model']=n
 out=ROOT/'exports/WT06/assets/interstice/models/item'/(name+'.json');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(n,indent=2)+'\n',encoding='utf8')
 rows.append({'item':name,'width_depth_unchanged':True,'shovel_blade_height_multiplier':1.8 if row['kind']=='shovel' else 1,'thirdperson_pitch':75,'thirdperson_yaw':0,'firstperson_yaw_absolute':45,'firstperson_translation_x':-.64,'firstperson_translation_z':-2.4})
d['revision']='WT06';d['previous']={n:r['model'] for n,r in w5['models'].items()};d['stage']='Native visual correction after WT05 numeric calibration';d['change']={'base':'WT05','shovel_blade_height':9.9,'shovel_shaft_extension_from_WT04':3.5,'all_width_depth_unchanged':True,'thirdperson_pitch':75}
p=ROOT/'source/worktools_WT06.json';p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf8')
(ROOT/'reports/design_WT06.json').write_text(json.dumps({'passed':True,'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'rows':rows},indent=2)+'\n',encoding='utf8')
print(json.dumps(rows))
