"""Owner-directed tailoring corrections; preserve E04 and the native cloth prototype separately."""
from pathlib import Path
import json,copy,math,hashlib
from PIL import Image
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E04_parameters.json').read_text(encoding='utf-8'));original=copy.deepcopy(d);P=d['palette']
P.update(undercloth='#151a22',mask_plate='#444c55',collar='#222831')
parts=d['parts']
def remove(prefixes):
    parts[:]=[p for p in parts if not any(p['name'].startswith(k) for k in prefixes)]
def box(name,bone,xyz,size,role,bevel=.06):
    p={'name':name,'bone':bone,'slot':'helmet' if bone=='head' else 'leggings' if bone.endswith('leg') else 'chestplate','role':role,'offset':list(xyz),'size':list(size),'rotation':[0,0,0],'pivot':[0,0,0],'glow':False,'bevel':bevel};parts.append(p);return p
SIDES={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),'east':(5,1,2,6),'down':(0,1,5,4),'up':(7,6,2,3)}
def prism(name,bone,front,back,role):
    p={'name':name,'bone':bone,'slot':'helmet' if bone=='head' else 'chestplate','role':role,'vertices':front+back,'faces':[{'indices':list(v),'side':s} for s,v in SIDES.items()],'rotation':[0,0,0],'pivot':[0,0,0],'glow':False};parts.append(p);return p
def line(name,bone,a,b,width,depth,role):
    A=np.array(a,float);B=np.array(b,float);Y=B-A;Y/=np.linalg.norm(Y);X=np.array([Y[1],-Y[0],0.]);X/=np.linalg.norm(X);Z=np.cross(X,Y)
    vertices=[list(c+u*X+v*Z) for c in (A,B) for u,v in ((-width/2,-depth/2),(width/2,-depth/2),(width/2,depth/2),(-width/2,depth/2))]
    faces=[{'indices':[0,3,2,1],'side':'cap'},{'indices':[4,5,6,7],'side':'cap'}]+[{'indices':[i,(i+1)%4,(i+1)%4+4,i+4],'side':'side'} for i in range(4)]
    p={'name':name,'bone':bone,'slot':'helmet' if bone=='head' else 'chestplate','role':role,'vertices':vertices,'faces':faces,'rotation':[0,0,0],'pivot':[0,0,0],'glow':False};parts.append(p);return p

# Remove the hanging/flat collar strips and toy-like glove knuckle bumps.
remove(('shoulder_harness','sculpted_collar_','collar_binding_','collar_rear','rear_collar_binding','tailored_front_seam_',
        'right_glove_knuckle_','left_glove_knuckle_','head_rear_strap','right_hood_strap','left_hood_strap'))
for p in parts:
    n=p['name']
    if n=='visor_glass':p['offset'][0]=-2.25;p['size'][0]=4.5
    if n=='visor_inner_highlight':p['offset'][0]=-1.85;p['size'][0]=3.7
    if n.startswith('bandolier_buckle'):
        cx,cy,z=.65,6.35,-3.61;p['offset']=[p['offset'][0]-cx,p['offset'][1]-cy,p['offset'][2]-z];p['pivot']=[cx,cy,z];p['rotation']=[0,0,-35]
    if n in ('right_sleeve','left_sleeve'):
        p['role']='undercloth';p['offset']=[-3.05 if n.startswith('right') else -1.05,-1.9,-2.05];p['size']=[4.1,11.9,4.1]
    if n in ('right_glove','left_glove'):
        p['role']='undercloth';p['offset']=[-3.02 if n.startswith('right') else -1.02,8.2,-2.02];p['size']=[4.04,1.95,4.04];p['bevel']=.08
    if n in ('respirator_center','respirator_center_ridge'):p['role']='mask_plate' if n.endswith('ridge') else 'undercloth'
    if 'hood_cheek_fold' in n:p['offset'][1]=-5.2;p['size'][1]=1.6
    if n.startswith('cheek_plate'):p['role']='mask_plate'
    if n.startswith('chest_star_'):
        for v in p['vertices']:v[0]-=.35;v[1]-=.25
    if n.startswith(('wrist_device','wrist_vertical','wrist_center','wrist_fastener')):
        # Rotate the full instrument onto the OUTER side, preserving its vertical screen and stack order.
        t=math.radians(15);R=np.array([[math.cos(t),0,math.sin(t)],[0,1,0],[-math.sin(t),0,math.cos(t)]])
        M=np.array([[0,0,-.62],[0,1,0],[1,0,0]]);origin=np.array([3.35,0,0]);T=R@(np.array([1.755,0,-1.5])-origin)+origin
        p['mesh_transform']={'matrix':(R@M).tolist(),'translation':T.tolist()}

# Continuous diagonal harness, folded under the belt instead of ending in air.
A=[-3.5,.3,-3.1];B=[.65,6.35,-3.22];C=[2.9,9.15,-2.35]
line('shoulder_harness_upper','body',A,B,.96,.32,'leather');line('shoulder_harness_tucked_tail','body',B,C,.96,.32,'leather')
line('harness_shoulder_wrap_front','body',A,[-3.9,-.15,0],.96,.28,'leather')
line('harness_shoulder_wrap_back','body',[-3.9,-.15,0],[-3.9,.35,2.75],.96,.28,'leather')
line('harness_rear_side_anchor','body',[-3.9,.35,2.75],[-3.9,9.0,2.35],.9,.25,'leather')

# A real high U-shaped collar: outer walls, angled rear panel and continuous upper binding.
back=[[-4.55,-2.2,4.7],[4.55,-2.2,4.7],[4.15,1.3,2.85],[-4.15,1.3,2.85]]
prism('high_collar_back','body',back,[[x,y,z+.4] for x,y,z in back],'collar')
line('high_collar_back_binding','body',[-4.65,-2.22,4.8],[4.65,-2.22,4.8],.25,.24,'metal')
for sign in (-1,1):
    wall=[[sign*4.55,-1.75,-2.7],[sign*4.55,-2.2,4.7],[sign*4.15,1.3,2.85],[sign*4.15,1.5,-2.9]]
    prism('high_collar_side_'+str(sign),'body',wall,[[x+sign*.4,y,z] for x,y,z in wall],'collar')
    line('high_collar_side_binding_'+str(sign),'body',[sign*4.65,-1.78,-2.72],[sign*4.65,-2.23,4.78],.24,.18,'metal')
    box('collar_front_fold_'+str(sign),'body',[sign*3.65-.38,-.9,-3.25],[.76,2.2,.6],'panel',.1)
    # Horizontal mask mounting webbing; the lower rear strip is the collar, not an angled head strap.
    box('hood_horizontal_strap_'+str(sign),'head',[sign*4.4-.08,-5.2,-2.5],[.16,.8,5.0],'dark',.025)

# Distinct under-sleeve and raised outer cloth sections.
for side in ('right','left'):
    bone=side+'_arm';x=-3.16 if side=='right' else -1.16
    box(side+'_upper_sleeve_shell',bone,[x,-.15,-2.18],[4.32,2.5,4.36],'coat',.1)
    box(side+'_forearm_shell',bone,[x,3.0,-2.17],[4.32,4.4,4.34],'coat',.1)
    box(side+'_elbow_overlap',bone,[x-.04,2.5,-2.2],[4.4,.65,4.4],'panel',.1)
box('chest_emblem_patch','body',[.45,1.32,-2.64],[2.9,2.8,.12],'undercloth',.04)

# Lateral backing plate and two straps that actually cross the instrument mounting edges.
remove(('left_sleeve_webbing_1','left_sleeve_catch_1'))
backing=box('wrist_lateral_backing','left_arm',[3.02,3.2,-1.6],[.28,4.7,3.2],'undercloth',.12)
t=math.radians(15);R=np.array([[math.cos(t),0,math.sin(t)],[0,1,0],[-math.sin(t),0,math.cos(t)]]);origin=np.array([3.2,0,0]);backing['mesh_transform']={'matrix':R.tolist(),'translation':(origin-R@origin).tolist()}
def device_edge(z,y):
    t=math.radians(15);x=4.13-3.35
    return [3.35+math.cos(t)*x+math.sin(t)*z,y,-math.sin(t)*x+math.cos(t)*z]
for n,y in enumerate((3.55,7.3)):
    for z in (-2.32,2.12):box('wrist_clamp_'+str(n)+'_z'+str(z),'left_arm',[-1.2,y,z],[4.4,.42,.2],'steel',.025)
    front=device_edge(-1.3,y+.21);back=device_edge(1.3,y+.21)
    line('wrist_clamp_'+str(n)+'_front_bend','left_arm',[3.2,y+.21,-2.22],front,.42,.17,'steel')
    line('wrist_clamp_'+str(n)+'_outer','left_arm',front,back,.42,.17,'steel')
    line('wrist_clamp_'+str(n)+'_back_bend','left_arm',back,[3.2,y+.21,2.22],.42,.17,'steel')
    box('wrist_clamp_'+str(n)+'_inner','left_arm',[-1.2,y,-2.12],[.17,.42,4.24],'steel',.025)
    box('wrist_clamp_'+str(n)+'_catch','left_arm',[3.2,y-.08,-2.42],[.6,.58,.16],'brass',.025)

# Preserve the working hidden waistband attachment, now authored directly into the new source.
bridges=[]
for p in list(parts):
    if not (p['name'].endswith('_front_skirt') or p['name'].endswith('_rear_skirt') or p['name'].endswith('_side_gusset')):continue
    q=copy.deepcopy(p);q['name']+='_waist_bridge';sign=-1 if q['bone']=='right_leg' else 1;px=-1.9 if sign<0 else 1.9
    low=min(v[1] for v in q['vertices']);high=max(v[1] for v in q['vertices'])
    for v in q['vertices']:
        f=(v[1]-low)/(high-low)
        if p['name'].endswith('_side_gusset'):v[0]-=sign*1.8*f
        else:v[0]=sign*(4.35 if abs(v[0]+px)>3 else .45)-px
        v[2]-=(1 if v[2]>0 else -1)*.4*f;v[1]=low-1.3+1.3*f
    bridges.append(q)
parts.extend(bridges)
d.update(revision='EREZ-ENGINEER-ARMOR-E06',scope='Owner-directed appearance corrections; exact native mesh and moving cloth validation in progress.',
    waist_attachment={'hidden_bridge_parts':len(bridges),'pinned_body_y':8.1,'visible_skirt_top_y':9.4})
d['owner_corrections']=['Buckle aligned to diagonal harness','Harness tail tucked into belt','Centered visor and centered highlight','Mask mounted to hood with tall wraparound collar','Lateral wrist instrument on backing plate with two clamps','Richer contrasty pixel fabric','Compact gloves without paw-like knuckle blocks']
(ROOT/'source/armor_E06_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Technical pixel-grid normalization of the generated production tile; no new concept/render image.
src=Image.open(ROOT/'source/materials/fabric_E06_generated.png').convert('RGBA');tile=Image.new('RGBA',(32,32))
tile.putdata([src.getpixel((int((x+.5)*src.width/32),int((y+.5)*src.height/32))) for y in range(32) for x in range(32)])
tile.save(ROOT/'source/materials/fabric_E06_pixels32.png')
(ROOT/'source/materials/generation.json').write_text(json.dumps({'method':'Built-in image_gen','reference':'../reference/owner_reference.jpg','asset':'Flat unlit seamless 32x32 logical-pixel charcoal fabric tile only; not a character render','original_sha256':hashlib.sha256((ROOT/'source/materials/fabric_E06_generated.png').read_bytes()).hexdigest(),'normalization':'Sample logical pixel centers to32x32 for a native nearest-filtered texture; retain generated pixel layout','normalized_sha256':hashlib.sha256((ROOT/'source/materials/fabric_E06_pixels32.png').read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'parts':len(parts),'revision':d['revision']}))
