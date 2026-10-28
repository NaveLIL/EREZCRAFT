from pathlib import Path
import json,copy,math
ROOT=Path(__file__).resolve().parents[1];a=json.loads((ROOT/'source/worktools_WT07.json').read_text(encoding='utf8'));d=copy.deepcopy(a)
def rot(p,r):
 x,y,z=p;u,v,w=map(math.radians,r);x,y=x*math.cos(w)-y*math.sin(w),x*math.sin(w)+y*math.cos(w);x,z=x*math.cos(v)+z*math.sin(v),-x*math.sin(v)+z*math.cos(v);y,z=y*math.cos(u)-z*math.sin(u),y*math.sin(u)+z*math.cos(u);return [x,y,z]
rows=[]
for name,row in d['models'].items():
 m=row['model'];old=a['models'][name]['model'];m['display']['firstperson_lefthand']=copy.deepcopy(m['display']['firstperson_righthand'])
 t=m['display']['thirdperson_righthand'];anchor=rot([0,-4.15,0],t['rotation']);target=[0,-2,2]
 t['translation']=[round(target[i]-anchor[i]*t['scale'][i],6) for i in range(3)]
 m['display']['thirdperson_lefthand']=copy.deepcopy(t)
 assert m['elements']==old['elements'] and m['textures']==old['textures']
 assert m['display']['firstperson_righthand']==old['display']['firstperson_righthand']
 for left in [False,True]:
  q=copy.deepcopy(t);r=q['rotation'];tr=q['translation'];s=q['scale']
  if left:r[1]*=-1;r[2]*=-1;tr[0]*=-1
  v=rot([0,-4.15,0],r);v=[tr[i]+v[i]*s[i] for i in range(3)];arm=[-(v[0]+(-1 if left else 1)),-(v[2]-10),-(v[1]+2)]
  assert math.dist(arm,[1 if left else -1,8,0])<1e-5,(name,left,arm)
 rows.append({'item':name,'thirdperson_display':t,'palm_anchor_arm_local':[-1,8,0],'firstperson_right_unchanged':True})
 p=ROOT/'exports/WT08/assets/interstice/models/item'/(name+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(m,indent=2)+'\n',encoding='utf8')
d['revision']='WT08';d['previous']={n:r['model'] for n,r in a['models'].items()};d['stage']='Owner accepted WT07 first-person right hand; fix mirrored left hand and true third-person palm grip';d['change']={'base':'WT07','geometry_unchanged':True,'firstperson_right_unchanged':True,'left_contexts_same_encoded_transform_as_right_for_Minecraft_mirroring':True,'thirdperson_grip_target_after_display':[0,-2,2],'palm_in_arm_frame_right':[-1,8,0],'palm_in_arm_frame_left':[1,8,0],'basis':'Vanilla ItemInHandLayer and PlayerModel, including SLIM translateToHand offset'}
(ROOT/'source/worktools_WT08.json').write_text(json.dumps(d,indent=2)+'\n',encoding='utf8');(ROOT/'reports/design_WT08_hands.json').write_text(json.dumps({'passed':True,'rows':rows},indent=2)+'\n',encoding='utf8');print(json.dumps(rows[:1]))
