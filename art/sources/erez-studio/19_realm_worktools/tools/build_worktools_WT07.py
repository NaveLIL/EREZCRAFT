from pathlib import Path
import json,copy,hashlib
ROOT=Path(__file__).resolve().parents[1]
a=json.loads((ROOT/'source/worktools_WT06.json').read_text(encoding='utf8'));d=copy.deepcopy(a)
for name,row in d['models'].items():
 m=row['model'];old=a['models'][name]['model']
 for side in ['righthand','lefthand']:
  m['display']['firstperson_'+side]={'rotation':[0,-60 if side=='righthand' else 60,-20 if side=='righthand' else 20],'translation':[-2,0,0],'scale':[.68]*3}
 assert m['elements']==old['elements'] and m['textures']==old['textures']
 assert all(m['display'][c]==old['display'][c] for c in m['display'] if not c.startswith('firstperson_'))
 p=ROOT/'exports/WT07/assets/interstice/models/item'/(name+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(m,indent=2)+'\n',encoding='utf8')
d['revision']='WT07';d['previous']={n:r['model'] for n,r in a['models'].items()};d['stage']='First-person foreground tool view; WT06 rejected by owner';d['change']={'base':'WT06','geometry_unchanged':True,'all_width_depth_unchanged':True,'firstperson_scale':.68,'firstperson_translation':[-2,0,0],'firstperson_yaw_absolute':60,'calibration_source':'reports/WT07/calibration/run.json','chosen_calibration_candidate':5,'vanilla_reference':'reference-first-vanilla-shovel.png','note':'Do not fit the whole tool in first-person frame; compare the working blade in the foreground'}
p=ROOT/'source/worktools_WT07.json';p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf8');print(hashlib.sha256(p.read_bytes()).hexdigest())
