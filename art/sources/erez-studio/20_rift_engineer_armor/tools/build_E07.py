"""Continuous mask harness, centered insignia and deliberately layered rear details."""
from pathlib import Path
import copy,json,math
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E06_parameters.json').read_text(encoding='utf-8'));parts=d['parts']
d['palette'].update(worn_metal='#363c42',worn_leather='#40372c',rubber='#181d24',back_patch='#22292e')
def box(name,bone,xyz,size,role,bevel=.04):
    p=dict(name=name,bone=bone,slot='helmet' if bone=='head' else 'chestplate',offset=list(xyz),size=list(size),role=role,rotation=[0,0,0],pivot=[0,0,0],glow=False,bevel=bevel);parts.append(p);return p
def line(name,a,b,width,depth,role):
    A=np.array(a,float);B=np.array(b,float);Y=B-A;Y/=np.linalg.norm(Y);X=np.array([Y[1],-Y[0],0.]);X/=np.linalg.norm(X);Z=np.cross(X,Y)
    vertices=[list(c+u*X+v*Z) for c in (A,B) for u,v in ((-width/2,-depth/2),(width/2,-depth/2),(width/2,depth/2),(-width/2,depth/2))]
    faces=[dict(indices=[0,3,2,1],side='cap'),dict(indices=[4,5,6,7],side='cap')]+[dict(indices=[i,(i+1)%4,(i+1)%4+4,i+4],side='side') for i in range(4)]
    parts.append(dict(name=name,bone='body',slot='chestplate',role=role,vertices=vertices,faces=faces,rotation=[0,0,0],pivot=[0,0,0],glow=False))

# The hood is 8.7 wide and 8.45 deep. Every strap segment overlaps its neighbour OUTSIDE it.
parts[:]=[p for p in parts if not p['name'].startswith('hood_horizontal_strap_')]
for sign in (-1,1):
    x=-4.53 if sign<0 else 4.35
    box('mask_harness_side_'+str(sign),'head',[x,-5.2,-4.25],[.18,.8,8.78],'worn_leather',.025)
box('mask_harness_rear_wrap','head',[-4.5,-5.2,4.35],[9,.8,.18],'worn_leather',.035)
# The front portions terminate under the visor cheek posts, with no floating strap ends.
for sign in (-1,1):
    box('mask_harness_front_anchor_'+str(sign),'head',[-4.5 if sign<0 else 3.7,-5.2,-4.42],[.8,.8,.2],'worn_leather',.03)
box('hood_lower_retention_back','head',[-4.49,-2.85,4.35],[8.98,.55,.18],'rubber',.03)
for sign in (-1,1):box('hood_lower_retention_side_'+str(sign),'head',[-4.52 if sign<0 else 4.35,-2.85,1.8],[.17,.55,2.7],'rubber',.02)

for p in parts:
    n=p['name']
    if n=='chest_emblem_patch':p['offset']=[.25,1.25,-2.64] # center=(1.70,2.65), exactly the star center
    if n in ('respirator_center','mask_lower_rim'):p['role']='rubber'
    if n.startswith('cheek_plate') or n=='respirator_center_ridge':p['role']='worn_metal'
    if p['role']=='leather':p['role']='worn_leather'
    if n in ('left_glove','right_glove'):p['role']='rubber'

# Separate rear cloth yoke, leather shoulder mounting and an inset insignia patch.
box('back_yoke_panel','body',[-3.6,1.0,2.57],[7.2,6.65,.14],'back_patch',.10)
box('back_yoke_upper_binding','body',[-3.8,.85,2.62],[7.6,.36,.22],'worn_leather',.05)
box('rear_insignia_patch','body',[-1.6,3.8,2.73],[3.2,3.2,.12],'rubber',.06)
# Raise the exact original 3D insignia to the outer face. Its center remains (0,5.4).
for p in parts:
    if p['name'].startswith('back_star_') and 'vertices' in p:
        for v in p['vertices']:v[2]+=.26
for sign in (-1,1):
    x=sign*3.72
    line('rear_equipment_strap_'+str(sign),[x,1.15,2.78],[x,8.6,2.68],.48,.18,'worn_leather')
    for y in (1.55,6.9):
        box('rear_strap_keeper_'+str(sign)+'_'+str(y),'body',[x-.34,y,2.80],[.68,.17,.15],'brass',.02)
    # A restrained row of sewn reinforcements, physically on the yoke edge.
    for i in range(5):box('rear_yoke_stitch_'+str(sign)+'_'+str(i),'body',[sign*3.25-.06,2.0+i*.9,2.735],[.12,.25,.045],'lining_edge',.012)
    # Visible mask intake vents; dark inset slats over the metal cheek area.
    for i in range(3):box('respirator_vent_'+str(sign)+'_'+str(i),'head',[sign*.96-.18,-2.8+i*.56,-5.67],[.36,.13,.07],'black',.018)

d.update(revision='EREZ-ENGINEER-ARMOR-E07',scope='Owner requested continuous mask harness, centered patches, rear equipment straps and distinct worn materials.')
d['owner_corrections']+=['Continuous exterior mask strap around hood','Centered chest star on square patch','Centered rear star on separate square patch','Rear yoke and restored equipment straps','Distinct worn metal, leather, rubber and fabric materials']
for glyph in d['glyphs']:
    if glyph['name']=='back_hebrew':glyph['center'][2]=2.90
(ROOT/'source/armor_E07_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'revision':d['revision'],'parts':len(parts)}))
