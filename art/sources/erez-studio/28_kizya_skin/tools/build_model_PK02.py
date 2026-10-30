"""Map newly painted character artwork to native player geometry; no block textures."""
from pathlib import Path
import json,math,copy
ROOT=Path(__file__).resolve().parents[1]
old=json.loads((ROOT/'archive/PK01_block_texture_trial/avatar_PK01.json').read_text(encoding='utf8'))
def rect(a,b,c,d):return {'sx':a,'sy':b,'ex':c,'ey':d,'rot':'0','autoUV':False}
def six(front,back,right,left,top,bottom):return dict(north=front,south=back,west=right,east=left,up=top,down=bottom)
base={
 'head':six(rect(24,16,40,30),rect(58,16,72,30),rect(9,16,23,30),rect(41,16,54,30),rect(25,2,39,15),rect(25,28,39,30)),
 'body':six(rect(24,31,40,54),rect(58,31,73,54),rect(24,31,28,54),rect(36,31,40,54),rect(26,31,38,34),rect(25,51,39,54)),
 'right_arm':six(rect(6,32,14,54),rect(6,32,14,54),rect(6,32,14,54),rect(6,32,14,54),rect(8,32,14,36),rect(8,50,14,54)),
 'left_arm':six(rect(6,32,14,54),rect(6,32,14,54),rect(6,32,14,54),rect(6,32,14,54),rect(8,32,14,36),rect(8,50,14,54)),
 'right_leg':six(rect(24,55,32,78),rect(58,55,65,78),rect(9,55,16,78),rect(9,55,16,78),rect(25,55,31,59),rect(25,75,31,78)),
 'left_leg':six(rect(32,55,40,78),rect(66,55,73,78),rect(9,55,16,78),rect(9,55,16,78),rect(33,55,39,59),rect(33,75,39,78))}
hair=rect(59,17,71,29);white=rect(7,37,14,44);pink=rect(29,44,35,49)
parts=[]
for original in old['parts']:
    p=copy.deepcopy(original);name=p['name'];role=p['role'];bone=p['bone']
    if name.startswith('hair_side_cap_'):p['offset'][2]=-4.35;p['size'][2]=8.7
    if name.startswith('cheek_lock_'):p['offset'][2]=-4.6
    if name.startswith('crown_flyaway_'):p['offset'][1]=-8.53;p['size'][1]=.1
    if any(s in name for s in ['_eye_','_cheek','subtle_mouth','white_neckline','white_collar','pinafore_bib','pinafore_front','pinafore_back','pinafore_side','strap_','button_','_sleeve_fold','_cuff_hem','_shorts_hem','_sock_stripe','_sock_back_stripe','_sock_bow','_shoe_white_tab']):continue
    if name.endswith('_sock'):continue
    if role.startswith('hair'):
        p['uv']={}
        for side in base['head']:
            width=p['size'][0] if side in ('north','south','up','down') else p['size'][2]
            height=p['size'][2] if side in ('up','down') else p['size'][1]
            w=min(11,max(1,round(width)));h=min(11,max(1,round(height)))
            u=59+(p['id']%max(1,12-w));v=17+(p['id']%max(1,12-h))
            p['uv'][side]=rect(u,v,u+w,v+h)
        if name=='hair_crown':p['uv']['up']=rect(25,2,39,15)
    elif name=='face_base' or name=='blouse_body' or name.endswith('_leg_base') or name.endswith('_sleeve'):
        p['uv']=copy.deepcopy(base[bone])
    elif '_pinafore_shorts' in name:
        p['uv']={s:dict(pink) for s in base['head']};p['uv']['north']=rect(24 if bone=='right_leg' else 32,55,32 if bone=='right_leg' else 40,61)
    elif '_shoe' in name or '_sole' in name:
        x1,x2=(24,32) if bone=='right_leg' else (32,40)
        p['uv']={s:rect(x1,72,x2,78) for s in base['head']}
    else:p['uv']={s:dict(white) for s in base['head']}
    if any(p['rotation']):
        p['pivot']=[p['offset'][i]+p['size'][i]/2 for i in range(3)];p['offset']=[-v/2 for v in p['size']]
    parts.append(p)
# Export at 256px: about four painted texels per model pixel on the face.
for face_map in list(base.values())+[p['uv'] for p in parts]:
    for r in face_map.values():
        for key in ['sx','sy','ex','ey']:r[key]*=2
vec=lambda v:dict(zip('xyz',v));elements=[]
for bone in old['pivots']:
    root={'id':bone,'name':bone,'show':False,'pos':vec([0,0,0]),'rotation':vec([0,0,0]),'children':[]}
    for p in [p for p in parts if p['bone']==bone]:
        root['children'].append({'name':p['name'],'storeID':p['id'],'show':True,'texture':True,'textureSize':1,'offset':vec(p['offset']),'pos':vec(p.get('pivot',[0,0,0])),'rotation':vec(p['rotation']),'size':vec(p['size']),'rscale':vec([1,1,1]),'scale':vec([1,1,1]),'u':0,'v':0,'color':'ffffff','mirror':False,'mcScale':0,'glow':False,'recolor':False,'hidden':False,'faceUV':p['uv']})
    elements.append(root)
animations={}
for pose in sorted({x for p in parts for x in p['hide_poses']}):
    components=[{'storeID':p['id'],'pos':vec([0,0,0]),'rotation':vec([0,0,0]),'color':'ffffff','show':False,'scale':vec([1,1,1])} for p in parts if pose in p['hide_poses']]
    animations['v_'+pose.lower()+'_clearance.json']={'name':'Equipment clearance '+pose,'additive':True,'duration':1000,'priority':20,'loop':True,'interpolator':'linear','frames':[{'components':components}]}
config={'version':1,'skinType':'slim','hideHeadIfSkull':True,'removeArmorOffset':False,'elements':elements,'skinSize':{'x':256,'y':256},'textures':{'skin':{'customGridSize':False,'anim':[]}}}
data=dict(old,revision='KIZYA-PK02',parts=parts,config=config,animations=animations,base_face_uv=base,source_of_texture_details='New painted artwork generated from the owner reference; UV repacking is a native game-format export. No Minecraft block or CC0 block textures used.',native_game_verified=False)
(ROOT/'source/avatar_PK02.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'revision':'PK02','parts':len(parts),'block_textures_used':False}))
