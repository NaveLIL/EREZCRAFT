from pathlib import Path
import json,copy,shutil
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
p=ROOT/'source/armor_E05.json';d=json.loads(p.read_text(encoding='utf-8'))
if not any('waist_bridge' in part['name'] for part in d['parts']):
    additions=[]
    for part in d['parts']:
        if not (part['name'].endswith('_front_skirt') or part['name'].endswith('_rear_skirt') or part['name'].endswith('_side_gusset')):continue
        q=copy.deepcopy(part);q['name']+='_waist_bridge';q['rig_note']='Hidden waistband extension: top pinned at bodyY8.1, visible E04 skirt begins at9.4'
        sign=-1 if part['bone']=='right_leg' else 1;px=-1.9 if sign<0 else 1.9
        low=min(v[1] for v in q['vertices']);high=max(v[1] for v in q['vertices'])
        for v in q['vertices']:
            f=(v[1]-low)/(high-low)
            if part['name'].endswith('_side_gusset'):v[0]-=sign*1.8*f
            else:
                gx=v[0]+px;isOuter=abs(gx)>3;v[0]=sign*(4.35 if isOuter else .45)-px
            v[2]-=(1 if v[2]>0 else -1)*.4*f
            v[1]=low-1.3+1.3*f
        additions.append(q)
    d['parts']+=additions;d['waist_attachment']={'hidden_bridge_parts':len(additions),'pinned_body_y':8.1,'visible_skirt_top_y':9.4,'reason':'Avoid pinning the visible front edge inside posed thighs when crouching'}
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copy2(p,CAND/'src/main/resources/assets/interstice/armor/engineer.json')
print('Waist bridge source prepared')
