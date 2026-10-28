"""WT01: exact Minecraft element geometry, shared with Blender; no new painted sprites."""
from pathlib import Path
import json, zipfile, hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parents[1]
BASE=REPO/'creations/18_expedition_tools/exports/interstice-0.7.0-preview.22-tools.jar'
CAT=REPO/'catalog/minecraft_expedition_tool_reference'
ASSETS=ROOT/'exports/assets/interstice'
(ASSETS/'models/item').mkdir(parents=True,exist_ok=True)
(ASSETS/'textures/item/worktools').mkdir(parents=True,exist_ok=True)
sha=lambda b:hashlib.sha256(b).hexdigest()
textures={};provenance=[]
roles={'silver':('minecraft_block_iron_block.png','#c8a7d4'),
       'silver_shadow':('minecraft_block_iron_block.png','#715485'),
       'steel':('minecraft_block_iron_block.png','#4c6974'),
       'edge':('minecraft_block_iron_block.png','#bfd3d8'),
       'dark':('minecraft_block_iron_block.png','#26373e'),
       'brass':('minecraft_block_iron_block.png','#b69b68'),
       'grip':('minecraft_block_white_wool.png','#4c5550'),
       'wood':('minecraft_block_white_wool.png','#88735b'),
       'cyan':('minecraft_block_sea_lantern.png','#72c8cd')}
for role,(file,tint) in roles.items():
    source=CAT/file
    im=Image.open(source).convert('RGBA');rgb=tuple(int(tint[i:i+2],16) for i in (1,3,5))
    out=im.copy();out.putdata([tuple(round(c*(.2126*r+.7152*g+.0722*b)/255) for c in rgb)+(a,) for r,g,b,a in im.getdata()])
    dest=ASSETS/'textures/item/worktools'/(role+'.png');out.save(dest)
    ref='interstice:item/worktools/'+role;textures[ref]=str(dest)
    assert im.size==out.size and im.getchannel('A').tobytes()==out.getchannel('A').tobytes()
    provenance.append({'reference':ref,'original':str(source),'source_sha256':sha(source.read_bytes()),'tint':tint,'sha256':sha(dest.read_bytes()),'operation':'Pointwise luminance palette only; no pixel rearrangement, size or alpha changes'})

models={};previous={};parts=[]
SIDES=('north','south','east','west','up','down')
def box(name,x,y,z,w,h,d,role,angle=0,axis='z'):
    dims={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}
    e={'name':name,'from':[x,y,z],'to':[round(x+w,4),round(y+h,4),round(z+d,4)],
       'faces':{s:{'texture':'#'+role,'uv':[1,1,round(1+min(14,dims[s][0]),4),round(1+min(14,dims[s][1]),4)]} for s in SIDES}}
    if angle:e['rotation']={'origin':[x+w/2,y+h/2,z+d/2],'axis':axis,'angle':angle,'rescale':False}
    parts.append(e)
def handle(steel,kind):
    # All grips stay centered on x/z=8, within normal 16-unit item silhouette.
    top=12 if kind in ('pickaxe','axe') else 11
    box('haft',7.3,.7,7.3,1.4,top,1.4,'dark' if steel else 'wood')
    box('grip',7.15,1.5,7.15,1.7,4.7,1.7,'grip')
    for y in (2,3.3,4.6):box('grip_binding_'+str(y),7.1,y,7.1,1.8,.22,1.8,'dark')
    box('pommel',7.05,.45,7.05,1.9,.65,1.9,'steel' if steel else 'silver_shadow')
    box('neck_band',7.0,top-.1,7,2,.55,2,'brass')
def bolt(x,y,z,steel):
    box('socket_pin',x,y,z,.65,.65,.25,'brass')
    if steel:box('resonance_inlay',x-.65,y+.1,z,.35,.45,.25,'cyan')
def head(steel,kind):
    metal='steel' if steel else 'silver';shadow='dark' if steel else 'silver_shadow'
    if kind=='pickaxe':
        box('eye_socket',6.8,11.3,6.7,2.4,3.0,2.6,shadow)
        box('crown',3.2,13.3,6.9,9.6,1.25,2.2,metal)
        for side in (-1,1):
            x=2.05 if side<0 else 11.75
            box('curved_arm_'+str(side),x,12.2,7.05,2.2,1.15,1.9,metal,22.5*side)
            box('forged_tip_'+str(side),.9 if side<0 else 13.55,11.4,7.25,1.55,.8,1.5,'edge',45*side)
        box('cutting_ridge',3.55,14.55,7.3,8.9,.3,1.4,'edge')
        box('socket_cap',7.05,14.25,7.0,1.9,.75,2,metal)
        bolt(7.7,12.4,9.32,steel)
        if steel:
            for z in (6.6,9.1):box('cheek_brace_'+str(z),6.4,11.75,z,3.2,.65,.3,'steel')
    elif kind=='axe':
        box('eye_socket',7.0,10.4,6.45,2.1,4.7,3.1,shadow)
        box('blade_root',4.8,11.5,6.8,2.4,2.65,2.4,metal)
        box('blade_web',3.1,10.4,7,2.0,4.5,2,metal)
        box('bearded_blade',1.75,9.5,7.2,1.7,5.1,1.6,metal)
        box('upper_slope',2.3,14.25,7.25,2.1,.7,1.5,metal,-22.5)
        box('lower_edge',1.25,9.75,7.45,.7,4.45,1.1,'edge')
        box('cutting_toe',1.8,9.25,7.45,1.35,.65,1.1,'edge',22.5)
        box('poll',9.0,12.0,6.8,2,2.2,2.4,metal)
        box('poll_face',10.85,12.15,6.65,.55,1.9,2.7,'edge')
        bolt(7.7,12.4,9.57,steel)
        if steel:box('blade_reinforcing_rib',3.25,12.0,9.05,3.4,.6,.4,'dark')
    elif kind=='shovel':
        box('socket',7.0,9.5,6.9,2,2.4,2.2,shadow)
        box('shoulder',5.5,11.5,7.0,5,1.2,1.5,metal)
        box('blade_panel',5.15,12.45,7.15,5.7,2.25,1.2,metal)
        box('blade_nose',5.8,14.7,7.15,4.4,.7,1.2,'edge')
        box('left_edge',4.85,12.7,7.25,.4,1.7,1.0,'edge')
        box('right_edge',10.75,12.7,7.25,.4,1.7,1,'edge')
        box('left_bevel',5.2,14.35,7.25,1.3,.45,1,'edge',22.5)
        box('right_bevel',9.5,14.35,7.25,1.3,.45,1,'edge',-22.5)
        box('center_ridge',7.65,11.6,8.4,.7,2.65,.35,shadow)
        box('left_step',4.9,11.35,6.85,1.3,.5,2.0,shadow)
        box('right_step',9.8,11.35,6.85,1.3,.5,2.0,shadow)
        if steel:box('neck_guard',6.8,10.1,6.7,2.4,.6,2.6,'steel')
    else:
        box('socket',6.95,10.3,6.8,2.1,2.4,2.4,shadow)
        box('cross_arm',3.9,11.65,6.95,3.3,1.05,2.1,metal)
        box('elbow',3.2,11.5,7.0,1.2,2.2,2.0,metal,22.5)
        box('cultivation_blade',1.4,12.9,6.7,3.8,1.0,2.6,metal)
        box('cultivation_edge',1.05,13.65,6.55,4.25,.4,2.9,'edge')
        box('blade_ridge',2.25,12.4,7.2,2,.5,1.6,shadow)
        box('poll',9,11.65,7.1,1.6,.95,1.8,metal)
        bolt(7.7,11.3,9.24,steel)
        if steel:box('yoke_brace',5.9,11.1,6.65,1.35,.5,2.7,'steel')

with zipfile.ZipFile(BASE) as z:
    for steel in (False,True):
        tier='riftsteel' if steel else 'riftsilver'
        for kind in ('pickaxe','axe','shovel','hoe'):
            name=tier+'_'+kind;old=json.loads(z.read('assets/interstice/models/item/'+name+'.json'));previous[name]=old
            for ref in old.get('textures',{}).values():
                if ref.startswith('#') or ref in textures:continue
                ns,p=ref.split(':',1);data=z.read('assets/'+ns+'/textures/'+p+'.png');dest=ROOT/'source'/('previous_'+ns+'_'+p.replace('/','_')+'.png');dest.write_bytes(data);textures[ref]=str(dest)
            handle(steel,kind);head(steel,kind)
            model={'parent':'minecraft:block/block','ambientocclusion':False,'gui_light':'front','render_type':'minecraft:cutout',
                'textures':{**{r:'interstice:item/worktools/'+r for r in roles},'particle':'interstice:item/worktools/'+('steel' if steel else 'silver')},'elements':list(parts),
                'display':{'gui':{'rotation':[12,-25,-35],'scale':[.83]*3},
                'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,3,.2],'scale':[.62]*3},
                'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,3,.2],'scale':[.62]*3},
                'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[.58]*3},
                'firstperson_lefthand':{'rotation':[0,-90,-25],'translation':[1.13,3.2,1.13],'scale':[.58]*3},
                'ground':{'translation':[0,2,0],'scale':[.45]*3},'fixed':{'rotation':[0,180,0],'scale':[.8]*3}}}
            models[name]={'tier':tier,'kind':kind,'model':model}
            (ASSETS/'models/item'/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8');parts.clear()
design={'revision':'WT01','units':'16 native model units per block','authoritative_geometry':'This JSON: models/item/elements, imported directly into Blender','stage':'Visual candidate; special tool abilities are proposals, not active',
    'models':models,'previous':previous,'textures':textures,'texture_provenance':provenance,
    'baseline_jar':str(BASE),'baseline_sha256':sha(BASE.read_bytes())}
(ROOT/'source/worktools_WT01.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:len(v['model']['elements']) for k,v in models.items()}))
