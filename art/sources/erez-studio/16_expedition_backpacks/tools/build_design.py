"""Original backpack silhouettes; authored meshes are the common worn/item source."""
from pathlib import Path
import hashlib,json,math,zipfile
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parents[1]
for folder in ['source','preview','assembly','exports','reports']:(ROOT/folder).mkdir(exist_ok=True)
CAT=REPO/'catalog/minecraft_backpack_reference';CAT.mkdir(parents=True,exist_ok=True)
GAME=REPO.parent/'Minecraft/EREZCRAFT'
jar=REPO/'creations/15_armor_inventory_icons/exports/interstice-0.7.0-preview.20-icons.jar'
client=Path('C:/Users/Dimon/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
textures={};sources=[]
for archive,entries in [(client,{'canvas':'assets/minecraft/textures/block/white_wool.png','leather':'assets/minecraft/textures/models/armor/leather_layer_1.png','metal':'assets/minecraft/textures/models/armor/iron_layer_1.png','steve':'assets/minecraft/textures/entity/player/wide/steve.png'}),
    (jar,{'old_cloth':'assets/interstice/textures/gui/backpack_cloth.png','old_metal':'assets/interstice/textures/gui/retort_metal.png'})]:
    with zipfile.ZipFile(archive) as z:
        for key,name in entries.items():
            data=z.read(name);p=CAT/(key+'.png')
            if p.exists():assert p.read_bytes()==data
            else:p.write_bytes(data)
            textures[key]=str(p);sources.append({'material':key,'archive':str(archive),'entry':name,'sha256':hashlib.sha256(data).hexdigest(),'operation':'Unchanged local reference; no new painted PNG'})
roles=['fabric','edge','leather','metal','dark','accent','light']
new={};old={}
def component(parts,name,vertices,faces,role,texture=None,anchor='back'):
    parts.append({'name':name,'vertices':vertices,'faces':faces,'role':role,'texture':texture or ('leather' if role=='leather' else 'metal' if role in ['metal','accent','light'] else 'canvas'),'anchor':anchor})
def box(parts,name,xyz,size,role='fabric',rotation=0,pivot=None,anchor='back',texture=None):
    x,y,z=xyz;w,h,d=size
    vertices=[[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],[x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]]
    if rotation:
        a=math.radians(rotation);co,si=math.cos(a),math.sin(a);px,py,pz=pivot or xyz
        vertices=[[px+co*(X-px)-si*(Y-py),py+si*(X-px)+co*(Y-py),Z] for X,Y,Z in vertices]
    component(parts,name,vertices,[[1,0,3,2],[4,5,6,7],[0,4,7,3],[5,1,2,6],[0,1,5,4],[7,6,2,3]],role,texture,anchor)
def panel(parts,name,x,y,z,w,h,d,cut=.6,role='fabric',anchor='back'):
    # Eight-sided outline removes the box silhouette. Insets/closures remain separate parts.
    q=[(x+cut,y),(x+w-cut,y),(x+w,y+cut),(x+w,y+h-cut),(x+w-cut,y+h),(x+cut,y+h),(x,y+h-cut),(x,y+cut)]
    vertices=[[X,Y,z] for X,Y in q]+[[X,Y,z+d] for X,Y in q]
    faces=[list(reversed(range(8))),list(range(8,16))]+[[i,(i+1)%8,(i+1)%8+8,i+8] for i in range(8)]
    component(parts,name,vertices,faces,role,anchor=anchor)
def cylinder(parts,name,center,length,radius,role='fabric',axis='x',segments=8):
    x,y,z=center;vertices=[]
    for end in [-length/2,length/2]:
        for i in range(segments):
            a=2*math.pi*i/segments;u,v=radius*math.cos(a),radius*math.sin(a)
            vertices.append([x+end,y+u,z+v] if axis=='x' else [x+u,y+end,z+v])
    faces=[list(reversed(range(segments))),list(range(segments,2*segments))]+[[i,(i+1)%segments,(i+1)%segments+segments,i+segments] for i in range(segments)]
    if axis=='y':faces=[list(reversed(f)) for f in faces]
    component(parts,name,vertices,faces,role)
def harness(parts,heavy=False):
    for s in [-1,1]:
        x=s*2.6
        panel(parts,f'padded_front_strap_{s}',x-.56,.4,-2.56,1.12,9.6,.34,.22,'leather','front')
        box(parts,f'shoulder_bridge_{s}',[x-.56,.12,-2.4],[1.12,.48,4.95],'leather',anchor='bridge')
        box(parts,f'strap_adjuster_{s}',[x-.62,7.1,-2.98],[1.24,.85,.46],'metal',anchor='front')
        box(parts,f'webbing_tail_{s}',[x-.3,8,-2.66],[.6,2.5,.2],'dark',anchor='front')
    box(parts,'sternum_strap',[-2.6,4.6,-2.75],[5.2,.6,.28],'leather',anchor='front')
    box(parts,'sternum_quick_release',[-.62,4.42,-3.05],[1.24,.96,.55],'metal',anchor='front')
    if heavy:
        for s in [-1,1]:panel(parts,f'lumbar_pad_{s}',(-4.6 if s<0 else 2.3),9.5,1.9,2.3,2.1,.8,.3,'dark')
def seam(parts,name,x,y,z,w,h):
    box(parts,name+'_top',[x,y,z],[w,.18,.13],'edge')
    box(parts,name+'_bottom',[x,y+h-.18,z],[w,.18,.13],'edge')
def fastener(parts,name,x,y,z,length=4,role='leather'):
    box(parts,name+'_webbing',[x-.38,y,z],[.76,length,.18],role)
    panel(parts,name+'_buckle',x-.58,y+length-.82,z+.16,1.16,1.05,.3,.18,'metal')
    box(parts,name+'_buckle_slot',[x-.27,y+length-.48,z+.48],[.54,.25,.08],'dark')
for tier in ['field_backpack','expedition_backpack','rift_backpack']:
    parts=[];harness(parts,tier!='field_backpack')
    if tier=='field_backpack':
        palette={'fabric':'#b7a17a','edge':'#d1bc92','leather':'#a88460','metal':'#b99a60','dark':'#4b5146','accent':'#67765a','light':'#c7dbbb'}
        panel(parts,'canvas_body',-3.8,1.3,2.22,7.6,9.65,3.65,.85)
        panel(parts,'gusset_shadow',-3.93,2.2,4.48,7.86,8.2,1.12,.8,'dark')
        panel(parts,'front_canvas',-3.64,2.1,5.5,7.28,8.45,.6,.65)
        panel(parts,'folded_top',-4,.55,2.3,8,2.2,3.7,.65,'edge')
        box(parts,'rolled_mouth_fold',[-3.5,.48,4.95],[7,.6,1.05],'fabric')
        panel(parts,'front_pocket',-2.85,6,6.01,5.7,3.7,1.1,.55)
        panel(parts,'pocket_lid',-3.02,5.68,6.75,6.04,1.6,.5,.38,'edge')
        seam(parts,'pocket_seam',-2.5,6.35,7.15,5,2.75)
        for x in [-2.25,2.25]:fastener(parts,'top_closure_'+str(x),x,1.7,6.04,3.1)
        panel(parts,'pocket_clasp',-.6,6.32,7.23,1.2,1.15,.3,.2,'metal')
        cylinder(parts,'side_canteen',[-4.02,7.25,4.2],3.8,.78,'accent','y')
        cylinder(parts,'canteen_cap',[-4.02,5.17,4.2],.45,.48,'metal','y')
        box(parts,'canteen_keeper',[-4.85,6.75,3.45],[.27,1.15,1.5],'leather')
        panel(parts,'flat_side_pouch',3.65,5.2,3.1,1.05,4.4,2.25,.38,'dark')
        cylinder(parts,'bedroll',[0,11.3,4.35],7.4,.82,'accent')
        for x in [-2.35,2.35]:box(parts,'bedroll_keeper_'+str(x),[x-.28,10.6,5.04],[.56,1.5,.18],'leather')
        label='Полевой';concept='Compact roll-top canvas pack with one useful outer pocket, canteen and bedroll'
    elif tier=='expedition_backpack':
        palette={'fabric':'#3f6269','edge':'#6d9296','leather':'#8e8067','metal':'#a8bdc2','dark':'#233b43','accent':'#b69b68','light':'#b8e2dc'}
        panel(parts,'tall_body',-3.8,.65,2.22,7.6,11.2,3.85,.8)
        panel(parts,'reinforced_lower',-3.95,8.8,2.3,7.9,3.2,3.9,.62,'dark')
        panel(parts,'storm_flap',-4.04,.4,2.5,8.08,3.2,4.05,.7,'edge')
        panel(parts,'main_front_panel',-3.45,3.35,6.02,6.9,7.4,.6,.5,'dark')
        for s in [-1,1]:
            x=-3.25 if s<0 else .25
            panel(parts,f'tall_pocket_{s}',x,5,6.42,3,4.65,1.15,.45)
            panel(parts,f'pocket_flap_{s}',x-.1,4.7,7.25,3.2,1.35,.42,.3,'edge')
            fastener(parts,f'flap_closure_{s}',s*2.4,1.5,6.63,2.6)
            box(parts,f'base_compression_{s}',[s*2.5-.26,10.35,6.4],[.52,1.4,.28],'leather')
        panel(parts,'map_case',-5.02,3.55,3.15,1.35,6.9,2.65,.4,'dark')
        panel(parts,'map_case_lid',-5.14,3.45,3,1.58,1.2,2.95,.32,'edge')
        cylinder(parts,'sample_flask',[4.2,7.45,4.5],4.8,.74,'accent','y')
        cylinder(parts,'flask_lid',[4.2,4.83,4.5],.45,.45,'metal','y')
        panel(parts,'carry_handle',-1.5,-.15,3.1,3,.65,1.2,.25,'dark')
        box(parts,'handle_opening',[-.9,-.18,3.2],[1.8,.35,.8],'fabric')
        for y in [8.0,10.4]:box(parts,'side_keeper_'+str(y),[3.64,y,3.1],[1.35,.45,2.7],'leather')
        label='Экспедиционный';concept='Tall storm-flap expedition pack, split pockets, map case and sample flask'
    else:
        palette={'fabric':'#2b3c50','edge':'#506779','leather':'#66767a','metal':'#9bb0ba','dark':'#152330','accent':'#49acb7','light':'#9fefdf'}
        panel(parts,'soft_main_compartment',-4.15,.9,2.22,8.3,10.65,3.6,.95)
        panel(parts,'perimeter_frame',-4.55,1.1,4.35,9.1,10.3,1.6,1.0,'edge')
        panel(parts,'inset_front',-3.8,1.7,5.95,7.6,8.8,.45,.8,'dark')
        panel(parts,'upper_armored_flap',-4,.65,3.4,8,2.4,3.0,.8,'fabric')
        panel(parts,'lower_cargo_pocket',-3.35,7.8,6.18,6.7,2.6,.78,.58,'fabric')
        for s in [-1,1]:
            panel(parts,f'side_pod_{s}',(-5.15 if s<0 else 3.85),4.3,3.2,1.3,5.7,2.95,.4,'fabric')
            box(parts,f'frame_rail_{s}',[s*3.5-.3,3.15,6.25],[.6,4.2,.45],'metal')
            box(parts,f'side_signal_{s}',[s*4.6-.18,5.2,6],[.36,2.7,.2],'accent')
            panel(parts,f'flap_lock_{s}',s*2.7-.5,2.4,6.34,1,.9,.35,.2,'metal')
        # A recessed contained core: compact horizontal proportions, framed and purposefully restrained.
        panel(parts,'core_recess',-2.35,3.5,6.17,4.7,3.4,.5,.7,'metal')
        panel(parts,'core_dark_gasket',-1.95,3.78,6.7,3.9,2.8,.18,.58,'dark')
        panel(parts,'resonant_core',-1.35,4.08,6.92,2.7,2.15,.2,.5,'accent')
        box(parts,'core_inner_light',[-.72,4.58,7.17],[1.44,1.13,.12],'light')
        for s in [-1,1]:box(parts,f'core_retainer_{s}',[s*1.8-.2,4.35,7.08],[.4,1.65,.18],'metal')
        for x in [-2,-1,0,1,2]:box(parts,'lower_vent_'+str(x),[x-.23,9.0,7],[.46,.85,.13],'dark')
        panel(parts,'base_shoe',-3.7,10.2,2.4,7.4,1.65,4.4,.48,'edge')
        panel(parts,'top_handle',-1.7,.05,3,3.4,.65,1.6,.25,'dark')
        label='Разломный';concept='Soft cargo body in a chamfered exoframe; recessed contained anomaly core and protected side pods'
    new[tier]={'name_ru':label,'concept':concept,'palette':palette,'parts':parts}
    # Capture the old BackpackLayer exactly at its bare-player attachment coordinates.
    parts=[];reinforced=tier!='field_backpack';rift=tier=='rift_backpack';half=4.5 if rift else 4.3 if reinforced else 4;depth=4.3 if rift else 4.1 if reinforced else 3.5;back=2.15;outer=back+depth;front=-2.15
    rgb=(.52,.40,.76) if rift else (.45,.92,.96) if reinforced else (.85,.72,1)
    old_palette={}
    def oldbox(name,from_,to,scale=1,texture='old_cloth',rgb_=None):
        role=name;old_palette[role]='#'+''.join(f'{round(255*v*scale):02x}' for v in (rgb_ or rgb));box(parts,name,from_,[b-a for a,b in zip(from_,to)],role,texture=texture)
    oldbox('body',[-half,1.4,back],[half,11.4,outer]);oldbox('lid',[-half-.2,.75,back-.1],[half+.2,2.1,outer+.3],.85)
    oldbox('outer_pocket',[-2.65,6,outer-.06],[2.65,10.8,outer+1.15],.88);oldbox('outer_flap',[-2.85,5.75,outer+.05],[2.85,6.7,outer+1.32],.72)
    if reinforced:
        oldbox('left_side',[-5.2,5,back+.65],[-4.15,10.5,outer-.45],.75);oldbox('right_side',[4.15,5,back+.65],[5.2,10.5,outer-.45],.75)
    strap=(.48,.38,.65) if rift else (.75,.95,1) if reinforced else (.95,.58,.32);metal=(.33,.85,.78) if rift else (1,.78,.22) if reinforced else (.9,.95,1)
    for i,c in enumerate([-2.65,2.65]):
        oldbox(f'strap_front_{i}',[c-.45,.25,front-.12],[c+.45,11.2,front+.06],rgb_=strap);oldbox(f'strap_bridge_{i}',[c-.45,.25,front],[c+.45,1,back+.3],rgb_=strap)
        oldbox(f'back_webbing_{i}',[c-.3,2.25,outer+.02],[c+.3,5.55,outer+.2],rgb_=strap)
        oldbox(f'front_buckle_{i}',[c-.62,7.65,front-.24],[c+.62,8.75,front-.1],texture='old_metal',rgb_=metal)
        oldbox(f'back_buckle_{i}',[c-.48,3.8,outer+.18],[c+.48,4.7,outer+.35],texture='old_metal',rgb_=metal)
    oldbox('pocket_clasp',[-.65,6.35,outer+1.16],[.65,7.35,outer+1.36],texture='old_metal',rgb_=metal)
    old[tier]={'name_ru':label,'palette':old_palette,'parts':parts}
data={'revision':'BACKPACKS-B02','status':'B01 direction accepted; B02 improves native-game pocket contrast','units':'Minecraft model pixels, 16 per block; X right, Y down from torso pivot, Z back',
    'authoritative_parts':True,'placement_reference':{'bare_back':2.22,'bare_front':-2.56,'pivot':'native body'},'textures':textures,'sources':sources,
    'proposed':new,'previous':old,'unchanged_gameplay':{'field_backpack':54,'expedition_backpack':72,'rift_backpack':84},
    'limitations':['Blender proposal; native pose, clipping, armor and elytra acceptance remains pending','External pouches, flasks and core are cosmetic; no new gameplay functionality is implied','No gameplay or owner-profile files changed']}
assert all(p['vertices'] and p['faces'] for tier in new.values() for p in tier['parts'])
target=ROOT/'source/backpacks_B02.json';target.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
(ROOT/'reports/design.json').write_text(json.dumps({'geometry_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'parts':{k:len(v['parts']) for k,v in new.items()},'game_files_changed':False},indent=2),encoding='utf-8')
print('Backpack review meshes:',{k:len(v['parts']) for k,v in new.items()})
