"""Refine the preserved S03 source into textured, bone-bound E02 armor geometry."""
from pathlib import Path
import json,math,hashlib,zipfile,random
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parents[1]
BASE=json.loads((ROOT/'source/reference/avatar_S03.json').read_text(encoding='utf-8'))
old={p['name']:p for p in BASE['parts']};parts=[];reused=[];replaced=[]
P={'coat':'#26272d','panel':'#30323a','dark':'#15171c','lining':'#28434a','steel':'#7e8585','metal':'#5a6265','brass':'#a28a58','leather':'#4a4235','cyan':'#38cbd0','light':'#a9c9c0','black':'#0a0d10','seam':'#34363d'}
pivots={'head':[0,0,0],'body':[0,0,0],'right_arm':[-5,2,0],'left_arm':[5,2,0],'right_leg':[-1.9,12,0],'left_leg':[1.9,12,0]}
SIDES={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),'east':(5,1,2,6),'down':(0,1,5,4),'up':(7,6,2,3)}
def slot(bone):return 'helmet' if bone=='head' else 'leggings' if bone.endswith('leg') else 'chestplate'
def cuboid(name,bone,xyz,size,role,rotation=(0,0,0),pivot=None,glow=False,source=None,bevel=0):
    p={'name':name,'bone':bone,'slot':slot(bone),'offset':list(xyz),'size':list(size),'role':role,'rotation':list(rotation),'pivot':list(pivot or (0,0,0)),'glow':glow,'bevel':bevel}
    if source:p['derived_from']=source
    parts.append(p);return p
def inherited(name,**change):
    src=old[name];p=cuboid(name,src['bone'],src['offset'],src['size'],src['role'],src.get('rotation',(0,0,0)),src.get('pivot'),src.get('glow',False),name)
    p.update(change);reused.append(name);return p
def prism(name,bone,front,back,role,glow=False,source=None):
    p={'name':name,'bone':bone,'slot':slot(bone),'role':role,'vertices':front+back,'faces':[{'indices':v,'side':s} for s,v in SIDES.items()], 'rotation':[0,0,0],'pivot':[0,0,0],'glow':glow}
    if source:p['derived_from']=source;replaced.append(source)
    parts.append(p);return p
def plate(name,bone,front,thickness,role,source=None):
    return prism(name,bone,front,[[x,y,z+thickness] for x,y,z in front],role,source=source)
def line(name,bone,a,b,width,depth,role,glow=False):
    import numpy as np
    A=np.array(a,float);B=np.array(b,float);Y=B-A;Y/=np.linalg.norm(Y)
    X=np.array([Y[1],-Y[0],0.]);X/=np.linalg.norm(X);Z=np.cross(X,Y)
    vertices=[list(c+u*X+v*Z) for c in (A,B) for u,v in ((-width/2,-depth/2),(width/2,-depth/2),(width/2,depth/2),(-width/2,depth/2))]
    # Explicit ring connectivity; line depth now follows the actual 3D endpoints.
    faces=[{'indices':[0,3,2,1],'side':'cap'},{'indices':[4,5,6,7],'side':'cap'}]
    for i in range(4):faces.append({'indices':[i,(i+1)%4,(i+1)%4+4,i+4],'side':'side'})
    p={'name':name,'bone':bone,'slot':slot(bone),'role':role,'vertices':vertices,'faces':faces,'rotation':[0,0,0],'pivot':[0,0,0],'glow':glow}
    parts.append(p);return p

def ring(name,bone,cx,cy,z,w,h,bar,role):
    cuboid(name+'_top',bone,[cx-w/2,cy-h/2,z],[w,bar,.14],role,bevel=.04)
    cuboid(name+'_bottom',bone,[cx-w/2,cy+h/2-bar,z],[w,bar,.14],role,bevel=.04)
    for x in (cx-w/2,cx+w/2-bar):cuboid(name+'_side_'+str(x),bone,[x,cy-h/2+bar,z],[bar,h-2*bar,.14],role,bevel=.04)
def cylinder(name,bone,a,b,r,role,steps=8):
    # Quads plus triangular cap fans, all frozen in the authoritative mesh JSON.
    import numpy as np
    va=np.array(a,float);vb=np.array(b,float);axis=(vb-va);axis/=np.linalg.norm(axis);helper=np.array([0.,0.,1.])
    if abs(np.dot(axis,helper))>.9:helper=np.array([0.,1.,0.])
    u=np.cross(axis,helper);u/=np.linalg.norm(u);v=np.cross(axis,u)
    vertices=[(c+r*(u*math.cos(t*2*math.pi/steps)+v*math.sin(t*2*math.pi/steps))).tolist() for c in (va,vb) for t in range(steps)]
    vertices+= [va.tolist(),vb.tolist()];faces=[]
    for t in range(steps):
        n=(t+1)%steps;faces.append({'indices':[t,n,steps+n,steps+t],'side':'side'})
        faces.append({'indices':[2*steps,n,t],'side':'cap'});faces.append({'indices':[2*steps+1,steps+t,steps+n],'side':'cap'})
    parts.append({'name':name,'bone':bone,'slot':slot(bone),'role':role,'vertices':vertices,'faces':faces,'rotation':[0,0,0],'pivot':[0,0,0],'glow':False})

# Retain the actual S03 body and six-bone rig, then reshape its silhouette and fittings.
inherited('hood_base',offset=[-4.35,-8.65,-4.12],size=[8.7,8.8,8.45],role='coat',label=None,bevel=.14)
prism('coat_torso','body',[[-4.5,0,-2.45],[4.5,0,-2.45],[4.25,11.7,-2.35],[-4.25,11.7,-2.35]],
      [[-4.5,0,2.45],[4.5,0,2.45],[4.25,11.7,2.35],[-4.25,11.7,2.35]],'coat',source='coat_torso')
for side in ('right','left'):
    inherited(side+'_sleeve',offset=[-3.12 if side=='right' else -1.12,-1.9,-2.16],size=[4.24,11.9,4.32],bevel=.12)
    inherited(side+'_trousers',role='dark')

# Hood: shaped brow and fabric flaps. The narrow luminous slit stays inside a dark recess.
inherited('visor_frame',offset=[-3.95,-5.1,-4.52],size=[7.9,1.8,.55],role='black',bevel=.13)
inherited('visor_glass',offset=[-2.7,-4.58,-5.12],size=[4.5,.48,.13],role='cyan',glow=True)
cuboid('visor_inner_highlight','head',[-2.5,-4.53,-5.265],[3.7,.13,.035],'light',glow=True)
inherited('brow_guard',offset=[-4.06,-5.9,-4.6],size=[8.12,.9,.85],role='dark',bevel=.12)
for side,sign in [('right',-1),('left',1)]:
    cuboid(side+'_hood_fold','head',[-4.42 if sign<0 else 3.42,-8.2,-4.35],[1,2.6,.6],'panel',bevel=.1)
    cuboid(side+'_hood_cheek_fold','head',[-4.5 if sign<0 else 3.75,-5.65,-4.65],[.75,3.3,.8],'coat',bevel=.12)
    # Side straps follow the hood rather than floating beside it.
    cuboid(side+'_hood_strap','head',[-4.52 if sign<0 else 4.32,-5.4,-2.5],[.2,.8,5.5],'dark',source='head_strap_'+('-4.15' if sign<0 else '3.85'))
    ring(side+'_hood_buckle','head',sign*4.55,-4.95,-1.25,.17,1.15,.12,'brass')
inherited('head_rear_strap',offset=[-4.2,-5.4,4.34],size=[8.4,.85,.16],role='dark')

# Respirator: tapered center, angular cheek plates and slender brass hoses, not two gold blocks.
inherited('respirator_center',offset=[-1.4,-3.35,-5.55],size=[2.8,3.2,.6],role='dark',bevel=.14)
cuboid('respirator_center_ridge','head',[-.6,-3.1,-6.04],[1.2,2.9,.23],'panel',bevel=.07)
for sign in (-1,1):
    x=sign
    front=[[x*.95,-3.45,-5.36],[x*2.9,-3.1,-4.96],[x*2.55,-.35,-4.92],[x*1.35,-.05,-5.43]]
    if sign<0:front=list(reversed(front))
    plate('cheek_plate_'+str(sign),'head',front,.28,'metal',source='respirator_bridge')
    a=[sign*2.9,-2.45,-5.25];b=[sign*1.9,-.55,-5.4]
    cylinder('brass_breathing_hose_'+str(sign),'head',a,b,.22,'brass')
    cylinder('hose_upper_coupler_'+str(sign),'head',[a[0],a[1]-.22,a[2]],[a[0],a[1]+.18,a[2]],.3,'dark')
    cylinder('hose_lower_coupler_'+str(sign),'head',[b[0],b[1]-.18,b[2]],[b[0],b[1]+.22,b[2]],.29,'dark')
inherited('mask_lower_rim',offset=[-1.75,-.16,-5.8],size=[3.5,.34,.5],role='dark',bevel=.08)

# Tall collar and lapels, thin gunmetal bindings instead of bright rectangular rails.
for sign in (-1,1):
    xf=sign*3.3;xo=sign*4.8
    front=[[xf,-.7,-3.5],[xo,-.7,-2.35],[xo,2.15,-2.35],[sign*3.05,2.15,-3.2]]
    if sign<0:front=list(reversed(front))
    plate('sculpted_collar_'+str(sign),'body',front,.45,'panel',source='collar_-4.3' if sign<0 else 'collar_3.65')
    line('collar_binding_'+str(sign),'body',[xf,-.65,-3.54],[sign*3.14,1.15,-3.3],.16,.13,'metal')
inherited('collar_rear',offset=[-4.35,-.75,2.35],size=[8.7,2.35,.6],role='panel',bevel=.1)
cuboid('rear_collar_binding','body',[-4.15,-.8,2.97],[8.3,.24,.12],'metal',bevel=.04)
inherited('inner_shirt',offset=[-.8,1.0,-2.55],size=[1.6,8.7,.13],role='lining')
for sign in (-1,1):
    front=[[sign*3.8,1.6,-2.64],[sign*2.95,2,-2.78],[sign*1.0,9.65,-2.73],[sign*1.45,9.65,-2.61]]
    if sign<0:front=list(reversed(front))
    plate('tailored_front_seam_'+str(sign),'body',front,.12,'panel')
for i in range(4):inherited('shirt_button_'+str(i),offset=[-.13,3+i*1.5,-2.76],size=[.26,.26,.13],role='metal',bevel=.025)

# A real bandolier: continuous leather web, shaped loops, small brass catches and central buckle.
inherited('shoulder_harness',offset=[-.48,0,-.16],size=[.96,10.9,.32],role='leather',rotation=[0,0,-35],pivot=[-3.5,.4,-3.05],bevel=.06)
for i in range(5):
    t=1.3+i*1.25;cx=-3.5+math.sin(math.radians(35))*t;cy=.4+math.cos(math.radians(35))*t
    cuboid('bandolier_loop_'+str(i),'body',[-.65,-.32,-.18],[1.3,.64,.36],'dark',(0,0,-35),[cx,cy,-3.27],bevel=.07)
    cuboid('bandolier_catch_'+str(i),'body',[-.63,-.11,-.15],[.23,.5,.28],'brass',(0,0,-35),[cx,cy,-3.55],bevel=.035)
ring('bandolier_buckle','body',1.35,7.25,-3.61,1.25,1.45,.23,'brass')

# Belt is wrapped on all four sides; pouches are formed, with dark flaps and small brass latches.
inherited('belt',offset=[-4.48,9.65,-2.55],size=[8.96,1.28,5.1],role='leather',bevel=.08)
ring('belt_buckle','body',0,10.22,-2.84,1.18,1.3,.23,'brass')
cuboid('belt_tongue','body',[-.12,9.9,-2.99],[.24,.7,.13],'brass',bevel=.02)
for sign in (-1,1):
    cx=sign*3.15
    cuboid('belt_front_pouch_'+str(sign),'body',[cx-.92,10.15,-3.35],[1.84,2.25,.95],'leather',bevel=.15)
    cuboid('pouch_flap_'+str(sign),'body',[cx-.97,10.1,-3.59],[1.94,.86,.29],'dark',bevel=.08)
    cuboid('pouch_clasp_'+str(sign),'body',[cx-.2,10.62,-3.94],[.4,.6,.12],'brass',bevel=.035)
    cuboid('belt_rear_pouch_'+str(sign),'body',[cx-.85,10.05,2.25],[1.7,1.8,.9],'leather',bevel=.12)
    cuboid('rear_pouch_clasp_'+str(sign),'body',[cx-.17,10.3,3.17],[.34,.45,.13],'brass',bevel=.03)
for i in range(3):cuboid('belt_back_loop_'+str(i),'body',[-2.3+i*2.3,9.45,2.57],[.38,1.65,.18],'dark',bevel=.03)

# Split skirt panels flare toward the hem; each is still explicitly owned by a native leg bone.
for side,sign in [('right',-1),('left',1)]:
    bone=side+'_leg';px=pivots[bone][0]
    inner_top=sign*.45;outer_top=sign*4.35;inner_low=sign*.85;outer_low=sign*6.15
    for back in (False,True):
        z0=2.65 if back else -2.65;z1=3.05 if back else -3.05
        front=[[outer_top-px,-.45,z0],[inner_top-px,-.45,z0],[inner_low-px,9.05,z1],[outer_low-px,9.05,z1]]
        if not back:front=list(reversed(front))
        thickness=.3 if back else .3
        plate(side+('_rear_skirt' if back else '_front_skirt'),bone,front,thickness,'coat',source=side+'_coat_panel_'+('2.25' if back else '-2.65'))
        # Real narrow binding across the hem, tapering with the panel.
        yy0=8.65;fraction=(yy0+.45)/9.5
        ox=outer_top+(outer_low-outer_top)*fraction;ix=inner_top+(inner_low-inner_top)*fraction;zz=z0+(z1-z0)*fraction
        hem=[[ox-px,yy0,zz-.025],[ix-px,yy0,zz-.025],[inner_low-px,9.1,z1-.025],[outer_low-px,9.1,z1-.025]]
        if not back:hem=list(reversed(hem))
        plate(side+('_rear_hem' if back else '_front_hem'),bone,hem,.35,'metal')
        if not back:
            # Cyan lining follows the inner opening, not the outer contour.
            inner=[[inner_top-px,-.45,z0-.035],[inner_top-px-sign*.19,-.45,z0-.035],[inner_low-px-sign*.19,8.65,z1-.035],[inner_low-px,8.65,z1-.035]]
            plate(side+'_inner_lining',bone,inner,.12,'lining')
    # Side gusset closes the front/back panels and reinforces the flared silhouette.
    outer=[[outer_top-px,-.45,-2.55],[outer_top-px,-.45,2.85],[outer_low-px,9.05,3.25],[outer_low-px,9.05,-2.95]]
    prism(side+'_side_gusset',bone,outer,[[x+sign*.27,y,z] for x,y,z in outer],'panel',source=side+'_outer_coat')
    # A pair of narrow long folds makes the coat tailored rather than a flat rectangular slab.
    for front in (False,True):
        z=-2.98 if front else 3.05
        line(side+('_front_fold' if front else '_back_fold'),bone,[sign*2.6-px,1.0,z],[sign*4.0-px,8.5,z+(-.2 if front else .2)],.18,.15,'seam')
    inherited(side+'_boot',offset=[-2.22,8.6,-2.45],size=[4.44,3.65,4.95],role='black',bevel=.12)['slot']='boots'
    inherited(side+'_boot_toecap',offset=[-2.22,10.55,-2.87],size=[4.44,.92,.6],role='metal',bevel=.1)['slot']='boots'
    cuboid(side+'_boot_sole',bone,[-2.3,11.85,-2.8],[4.6,.5,5.35],'dark',bevel=.07)['slot']='boots'
    cuboid(side+'_boot_instep',bone,[-2.1,9.55,-2.65],[4.2,.48,.35],'leather',bevel=.05)['slot']='boots'

# Layered sleeves, restrained gray webbing and proper glove cuffs.
for side in ('right','left'):
    bone=side+'_arm';xx=-3.25 if side=='right' else -1.25
    inherited(side+'_tailored_shoulder',offset=[xx,-2.05,-2.35],size=[4.5,1.85,4.7],role='panel',bevel=.23)
    cuboid(side+'_upper_sleeve_overlay',bone,[xx+.05,.25,-2.3],[4.4,2.8,.25],'coat',bevel=.08)
    for n,y in enumerate((2.0,7.15)):
        cuboid(side+'_sleeve_webbing_'+str(n),bone,[xx,y,-2.36],[4.5,.55,4.72],'steel',bevel=.04)
        # Only the small catch is brass, leaving most of the band cloth gray.
        outerx=xx-.12 if side=='right' else xx+4.2
        cuboid(side+'_sleeve_catch_'+str(n),bone,[outerx,y-.1,-1.0],[.4,.8,.7],'brass',bevel=.04)
    inherited(side+'_glove',offset=[xx,8,-2.27],size=[4.5,2.15,4.54],role='dark',bevel=.13)
    for f in range(3):cuboid(side+'_glove_knuckle_'+str(f),bone,[xx+.35+f*1.15,9.25,-2.43],[.8,.48,.24],'coat',bevel=.06)

# Move the old wrist device to the reference's viewer-right forearm; narrow vertical luminous insert.
device=cuboid('wrist_device','left_arm',[.25,3.7,-3.2],[2.55,3.85,.95],'metal',bevel=.17,source='wrist_device')
cuboid('wrist_device_inset','left_arm',[.48,3.95,-3.41],[2.1,3.35,.24],'dark',bevel=.08)
cuboid('wrist_vertical_window','left_arm',[1.25,4.3,-3.71],[.65,2.55,.12],'cyan',glow=True)
cuboid('wrist_center_highlight','left_arm',[1.38,4.5,-3.85],[.16,2.1,.025],'light',glow=True)
for y in (4.1,7.0):cuboid('wrist_fastener_'+str(y),'left_arm',[.64,y,-3.7],[.35,.25,.12],'steel',bevel=.02)

# Chest and back insignia are actual thin geometry, not an unverified image projection.
def star(name,cx,cy,z,r,bone='body'):
    pts=[(cx+math.sin(a*math.pi/3)*r,cy-math.cos(a*math.pi/3)*r,z) for a in range(6)]
    for tri in ((0,2,4),(1,3,5)):
        for i in range(3):line(name+'_'+str(tri[0])+'_'+str(i),bone,pts[tri[i]],pts[tri[(i+1)%3]],.14,.065,'light')
star('chest_star',2.05,3.48,-2.72,1.0)
star('back_star',0,6.35,2.65,1.2)

# Bake each cuboid's real bevel into explicit mesh geometry in build_blender.py, then freeze it in JSON.
data={'revision':'EREZ-ENGINEER-ARMOR-E02','units':'16 model units per block','basis':{'project':'17_rift_engineer_skin','revision':BASE['revision'],'sha256':hashlib.sha256((ROOT/'source/reference/avatar_S03.json').read_bytes()).hexdigest()},
      'palette':P,'pivots':pivots,'parts':parts,'glyphs':[{'name':'back_hebrew','text':'ארז','visual_left_to_right':'זרא','bone':'body','slot':'chestplate','center':[0,2.65,2.63],'width':6.4,'height':2.05,'role':'light'}],
      'review_pose':{'right_arm':[0,0,7],'left_arm':[0,0,-7],'right_leg':[0,0,3],'left_leg':[0,0,-3]},
      'scope':'Visual armor prototype only; stats, recipes, native implementation and tests pending','lineage':{'reused_or_refined_names':reused,'replaced_part_names':sorted(set(replaced))}}
(ROOT/'source/armor_E02_parameters.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'parts':len(parts),'derived_parts':len(reused)+len(set(replaced))}))
