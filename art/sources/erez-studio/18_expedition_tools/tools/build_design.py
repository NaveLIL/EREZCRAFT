"""Native Minecraft cube geometry and palette-only texture variants, for Blender review."""
from pathlib import Path
import json,math,hashlib,zipfile,io
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parents[1]
for folder in ('source','exports','preview','assembly','reports'):(ROOT/folder).mkdir(exist_ok=True)
ASSETS=ROOT/'exports/assets/interstice';(ASSETS/'models/item').mkdir(parents=True,exist_ok=True);(ASSETS/'textures/item/expedition_tools').mkdir(parents=True,exist_ok=True)
CAT=REPO/'catalog/minecraft_expedition_tool_reference';CAT.mkdir(parents=True,exist_ok=True)
BASE=REPO/'creations/16_expedition_backpacks/exports/interstice-0.7.0-preview.21-backpacks.jar'
MC=Path('C:/Users/Dimon/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
sha=lambda b:hashlib.sha256(b).hexdigest()
provenance=[];resolved={};images={}
def texture(ref):
    if ref in resolved:return resolved[ref]
    ns,name=ref.split(':',1);entry='assets/'+ns+'/textures/'+name+'.png';archive=MC if ns=='minecraft' else BASE
    with zipfile.ZipFile(archive) as z:data=z.read(entry)
    f=CAT/(ns+'_'+name.replace('/','_')+'.png')
    if f.exists():assert f.read_bytes()==data
    else:f.write_bytes(data)
    resolved[ref]=str(f);provenance.append({'reference':ref,'archive':str(archive),'entry':entry,'sha256':sha(data),'operation':'Unchanged reference'})
    return str(f)
roles={'body':('minecraft:block/iron_block','#3d5560'),'frame':('minecraft:block/iron_block','#a1b3b4'),
 'shadow':('minecraft:block/iron_block','#26343e'),'brass':('minecraft:block/iron_block','#b29968'),
 'grip':('minecraft:block/white_wool','#424b49'),'rope':('minecraft:block/white_wool','#c5ad82'),
 'rope_seam':('minecraft:block/white_wool','#77684e'),
 'cyan':('minecraft:block/sea_lantern','#65c0c9'),'glass':('minecraft:block/iron_block','#264e58')}
for role,(ref,tint) in roles.items():
    src=Image.open(texture(ref)).convert('RGBA');out=src.copy();rgb=tuple(int(tint[i:i+2],16) for i in (1,3,5))
    # Pointwise palette replacement only. Coordinates, dimensions, alpha and pattern unchanged.
    out.putdata([tuple(round(c*(.2126*r+.7152*g+.0722*b)/255) for c in rgb)+(a,) for r,g,b,a in src.getdata()])
    ref2='interstice:item/expedition_tools/'+role;dest=ASSETS/'textures/item/expedition_tools'/(role+'.png');out.save(dest)
    assert src.size==out.size and src.getchannel('A').tobytes()==out.getchannel('A').tobytes()
    resolved[ref2]=str(dest);provenance.append({'reference':ref2,'source':ref,'sha256':sha(dest.read_bytes()),'tint':tint,'operation':'Palette only; same dimensions, texel positions and alpha'})

with zipfile.ZipFile(BASE) as z:
    def original(name):
        entry='assets/interstice/models/item/'+name+'.json';data=z.read(entry);(ROOT/'source'/('previous_'+name+'.json')).write_bytes(data);return json.loads(data)
    old={name:original(name) for name in ('tether_spool','lift_controller','structure_surveyor','rift_lens')}
for model in old.values():
    for ref in model.get('textures',{}).values():
        if not ref.startswith('#'):texture(ref)

faces=('north','south','east','west','up','down')
models={};parts=[]
def box(name,xyz,size,role,angle=0,origin=None):
    assert angle in (0,-45,-22.5,22.5,45)
    e={'name':name,'from':list(xyz),'to':[round(xyz[i]+size[i],5) for i in range(3)],
       'faces':{s:{'texture':'#'+role,'uv':[1,1,15,15]} for s in faces}}
    if angle:e['rotation']={'origin':list(origin or [xyz[i]+size[i]/2 for i in range(3)]),'axis':'z','angle':angle,'rescale':False}
    parts.append(e)
def plate(name,x,y,z,w,h,d,role,cut=.55):
    # Native stepped outline. Three non-overlapping panels avoid coplanar front faces.
    box(name+'_center',[x,y+cut,z],[w,h-2*cut,d],role)
    box(name+'_top',[x+cut,y+h-cut,z],[w-2*cut,cut,d],role)
    box(name+'_bottom',[x+cut,y,z],[w-2*cut,cut,d],role)
def oct_ring(name,cx,cy,z,r,thick,depth,role):
    straight=2*r*math.tan(math.pi/8)
    for i in range(8):
        a=i*math.pi/4;xx=cx+r*math.cos(a);yy=cy+r*math.sin(a)
        if i in (0,4):size=[thick,straight,depth];angle=0
        elif i in (2,6):size=[straight,thick,depth];angle=0
        else:size=[straight,thick,depth];angle=-45 if i in (1,5) else 45
        box(name+'_'+str(i),[xx-size[0]/2,yy-size[1]/2,z],size,role,angle)
def save(name,label,use,grip):
    model={'parent':'minecraft:block/block','ambientocclusion':False,'gui_light':'front','render_type':'minecraft:cutout',
      'textures':{**{r:'interstice:item/expedition_tools/'+r for r in roles},'particle':'interstice:item/expedition_tools/body'},'elements':list(parts),
      'display':{'gui':{'rotation':[20,-25,0],'translation':[0,0,0],'scale':[.95,.95,.95]},
       'ground':{'translation':[0,2,0],'scale':[.45,.45,.45]},'fixed':{'rotation':[0,180,0],'scale':[.9,.9,.9]},
       'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,2.3,0],'scale':[.75,.75,.75]},
       'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,2.3,0],'scale':[.75,.75,.75]},
       'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[.7,.7,.7]},
       'firstperson_lefthand':{'rotation':[0,-90,-25],'translation':[1.13,3.2,1.13],'scale':[.7,.7,.7]}}}
    third={'tether_spool':.42,'lift_controller':.33,'structure_surveyor':.38}[name]
    first={'tether_spool':.50,'lift_controller':.45,'structure_surveyor':.48}[name]
    for ctx in ('thirdperson_righthand','thirdperson_lefthand'):model['display'][ctx]['scale']=[third]*3
    for ctx in ('firstperson_righthand','firstperson_lefthand'):model['display'][ctx]['scale']=[first]*3
    models[name]={'label':label,'actual_use':use,'grip_center':grip,'model':model}
    (ASSETS/'models/item'/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8');parts.clear()

# Tether spool: open 8-sided flanges, axle, visibly wound line and a stowed hook.
plate('lower_mount',4.8,1.2,6.3,6.4,2,3.4,'body')
box('carrying_grip',[5.8,.5,6.65],[4.4,1.55,2.7],'grip')
for z in (6.25,9.15):oct_ring('flange_'+str(z),7.8,8.5,z,4.15,.72,.65,'frame')
plate('rope_drum',4.6,5.35,6.95,6.4,6.3,2.2,'rope',.8)
for y in (5.8,6.8,7.8,8.8,9.8,10.8):box('rope_winding_'+str(y),[4.9,y,9.2],[5.8,.15,.2],'rope_seam')
plate('hub',6.5,7.2,9.85,2.6,2.6,.55,'body',.45)
box('axle_bolt',[7.25,7.95,10.43],[1.1,1.1,.3],'brass')
for i in range(4):
    a=i*math.pi/2;xx=7.8+2.1*math.cos(a);yy=8.5+2.1*math.sin(a)
    box('hub_spoke_'+str(i),[xx-1.2 if i%2==0 else xx-.28,yy-.28 if i%2==0 else yy-1.2,9.65],[2.4,.56,.3] if i%2==0 else [.56,2.4,.3],'body')
box('hook_keep',[11.9,4.7,7.4],[1.1,3.3,1.4],'grip')
box('hook_spine',[13.1,4.4,7.8],[.65,3.8,.7],'frame')
box('hook_base',[12.05,4.05,7.8],[1.7,.65,.7],'frame')
box('hook_tip',[11.8,4.1,7.8],[.55,1.5,.7],'frame')
box('hook_gate',[12.15,5.6,7.8],[.4,2,.5],'brass',22.5)
save('tether_spool','Страховочная катушка','ПКМ по лебёдке — закрепиться; Shift+ПКМ — отсоединиться. Шнур до 32 блоков.',[8,1.3,8])

# Lift controller: a single conspicuous thumb control, no invented keypad/commands.
plate('grip_core',5.6,.7,6.1,4.8,7.5,3.6,'shadow',.5)
box('grip_front',[6.05,1.45,9.65],[3.9,5.45,.35],'grip')
for y in (2,3.1,4.2,5.3):box('grip_groove_'+str(y),[6.2,y,10.02],[3.6,.2,.1],'shadow')
plate('head_frame',3.8,7,5.9,8.4,7.6,4,'frame',.85)
plate('head_body',4.2,7.45,9.85,7.6,6.6,.6,'body',.75)
plate('inset_control',5.1,8.6,10.48,5.8,4.1,.3,'shadow',.5)
plate('single_thumb_control',6,9,10.8,4,2.7,.55,'grip',.45)
box('control_separator',[6.5,10.25,11.37],[3,.18,.1],'frame')
for y,angle in ((11.1,45),(9.45,-45)):
    box('direction_left_'+str(y),[7.15,y,11.4],[.8,.22,.1],'brass',angle)
    box('direction_right_'+str(y),[7.8,y,11.4],[.8,.22,.1],'brass',-angle)
box('field_window',[5.55,13.1,10.48],[4.9,.4,.2],'cyan')
for x in (4.65,10.8):box('corner_fastener_'+str(x),[x,7.8,10.5],[.5,.5,.2],'brass')
box('link_collar',[7.1,14.45,6.9],[1.8,.8,1.7],'shadow')
box('link_rod',[7.55,15.2,7.25],[.9,1.5,1],'brass')
save('lift_controller','Пульт подъёмника','Shift+ПКМ по якорю — привязка. Обычное использование переключает направление; механика сохранена.',[8,4,8])

# Surveyor: optical head plus an open sight, visibly distinct from a magic amulet.
plate('surveyor_handle',5.7,.6,6.3,4.6,6.2,3.4,'shadow',.5)
box('handle_wrap',[6.05,1.3,9.7],[3.9,4.5,.35],'grip')
plate('instrument_case',3.25,6.2,5.8,9.5,7.65,4.2,'body',.8)
plate('rear_cover',3.5,6.5,5.35,9,6.9,.5,'frame',.7)
oct_ring('optics_bezel',8,10.3,10.05,2.7,.65,.65,'brass')
plate('optical_recess',5.85,8.15,10.04,4.3,4.3,.15,'shadow',.5)
plate('rift_glass',6.6,8.9,10.23,2.8,2.8,.16,'glass',.45)
box('reticle_vertical',[7.88,9.15,10.42],[.24,2.3,.09],'cyan')
box('reticle_horizontal',[6.85,10.18,10.42],[2.3,.24,.09],'cyan')
for x in (4.25,11):box('side_dial_'+str(x),[x,7.2,10.05],[.8,1.2,.65],'frame')
box('sight_left',[5.1,13.7,6.55],[.65,2.3,2.5],'frame')
box('sight_right',[10.25,13.7,6.55],[.65,2.3,2.5],'frame')
box('sight_top',[5.1,15.4,6.55],[5.8,.6,2.5],'frame')
box('sight_notch',[7.75,14.05,8.6],[.5,.7,.2],'brass')
for x in (5.4,6.5,7.6,8.7,9.8):box('calibration_tick_'+str(x),[x,6.9,10.1],[.25,.4,.15],'brass')
save('structure_surveyor','Искатель структур','ПКМ — поиск структур; Shift+ПКМ — показать сохранённую отметку. Прицел декоративный, без новых игровых функций.',[8,3,8])

design={'revision':'EXPEDITION-TOOLS-T01','units':'Minecraft model units, 16 per block','geometry_authority':'This source file; native JSON exports and Blender are derived views',
 'status':'Visual prototype, approval pending; not installed','models':models,'previous':old,'textures':resolved,'texture_provenance':provenance,
 'constraints':['Native Minecraft box geometry only; no render-only bevels or subdivisions','Existing source textures and palette-only variants','No recipe, statistics or behavior changes','Display transforms need in-game verification after visual approval']}
payload=json.dumps(design,indent=2,ensure_ascii=False)+'\n';(ROOT/'source/tools_T01.json').write_text(payload,encoding='utf-8')
(ROOT/'reports/build.json').write_text(json.dumps({'source_sha256':sha(payload.encode()),'base_jar_sha256':sha(BASE.read_bytes()),'models':{n:{'elements':len(d['model']['elements']),'max_quads':len(d['model']['elements'])*6} for n,d in models.items()},'native_game_verified':False},indent=2)+'\n',encoding='utf-8')
print(json.dumps({n:len(d['model']['elements']) for n,d in models.items()}))
