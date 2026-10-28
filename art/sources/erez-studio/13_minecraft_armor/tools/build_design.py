"""Original knight/expedition armor geometry for visual review; no mod edits."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT.parents[1]/'catalog/minecraft_1_21_1_reference'
CLIENT=Path('C:/Users/Dimon/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
CATALOG.mkdir(parents=True,exist_ok=True)
names=['models/armor/leather_layer_1.png','models/armor/leather_layer_2.png',
       'models/armor/leather_layer_1_overlay.png','models/armor/leather_layer_2_overlay.png',
       'models/armor/iron_layer_1.png','models/armor/iron_layer_2.png',
       'entity/player/wide/steve.png']
sources=[]
with zipfile.ZipFile(CLIENT) as jar:
    for name in names:
        data=jar.read('assets/minecraft/textures/'+name)
        target=CATALOG/name;target.parent.mkdir(parents=True,exist_ok=True)
        if target.exists():assert target.read_bytes()==data
        else:target.write_bytes(data)
        sources.append({'minecraft_resource':'minecraft:textures/'+name,'local_path':str(target),
                        'sha256':hashlib.sha256(data).hexdigest(),'operation':'unchanged local reference extraction'})
(CATALOG/'sources.json').write_text(json.dumps({'origin':str(CLIENT),'version':'Minecraft Java 1.21.1',
    'copyright':'Mojang / Microsoft; reference from the installed game. Do not bundle these PNGs in a mod.',
    'sources':sources},indent=2),encoding='utf-8')

bones={'head':[0,0,0],'body':[0,0,0],'right_arm':[-5,2,0],'left_arm':[5,2,0],
       'right_leg':[-1.9,12,0],'left_leg':[1.9,12,0]}
sets={}
for tier in ('riftsilver','riftsteel'):
    heavy=tier=='riftsteel'
    palette=({'plate':'#386369','edge':'#83a8aa','shadow':'#253d45','cloth':'#243238',
              'strap':'#65503d','buckle':'#b8a06b','dark':'#15252c'} if heavy else
             {'plate':'#b4bdc9','edge':'#e2e6e7','shadow':'#657382','cloth':'#343e49',
              'strap':'#795b42','buckle':'#ae9a72','dark':'#25323d'})
    cubes=[]
    def box(name,bone,slot,xyz,size,role='plate',rotation=None,pivot=None):
        value={'name':name,'bone':bone,'slot':slot,'from':xyz,'size':size,'role':role}
        if rotation:value.update(rotation=rotation,rotation_pivot=pivot or [0,0,0])
        cubes.append(value)
    # Head: a stepped helmet with an actual face opening; no solid face cube.
    box('helmet_crown','head','head',[-4.5,-8.6,-4.5],[9,1.5,9])
    box('helmet_crest','head','head',[-3.5,-9.1,-3.7],[7,.5,7.4],'edge')
    box('helmet_rear','head','head',[-4.5,-7.1,3.6],[9,6.6,1])
    for s in (-1,1):
        box('temple_'+str(s),'head','head',[(-4.7 if s<0 else 3.7),-7.1,-4.5],[1,5.8,8.1])
        box('cheek_'+str(s),'head','head',[(-4.2 if s<0 else 2.5),-4.1,-5.1],[1.7,3.7,1.1])
        box('cheek_trim_'+str(s),'head','head',[(-4.2 if s<0 else 2.5),-.7,-5.2],[1.7,.5,1.2],'edge')
    box('brow','head','head',[-4.4,-6.1,-5.2],[8.8,1,1],'edge')
    box('brow_plate','head','head',[-4.3,-7.2,-4.9],[8.6,1.1,.9])
    if heavy:
        box('visor_nose','head','head',[-.5,-5.1,-5.3],[1,2.7,.9])
        box('chin_plate','head','head',[-2.5,-2.1,-5],[5,1.6,.9])
        box('chin_edge','head','head',[-2.5,-.5,-5.2],[5,.5,1],'edge')
        for x in (-1.5,0,1.5):box('breather_'+str(x),'head','head',[x-.25,-1.8,-5.2],[.5,1,.35],'dark')
    else:
        box('nose_short','head','head',[-.4,-5.1,-5.2],[.8,1.4,.6])
    # Chest: dark underlayer, distinct breastplates, overlapping abdomen and back plate.
    box('chest_underlayer','body','chest',[-4.35,-.1,-2.4],[8.7,11.7,4.8],'cloth')
    box('back_plate','body','chest',[-4.5,.5,2.35],[9,8.1,1.2])
    box('back_spine','body','chest',[-.6,.7,3.5],[1.2,7.8,.45],'edge')
    box('gorget_front','body','chest',[-3.4,-.55,-3.2],[6.8,1.2,1.2],'edge')
    box('gorget_rear','body','chest',[-3.5,-.6,2.4],[7,1.2,1],'edge')
    for s in (-1,1):
        x=-4.55 if s<0 else .2
        box('breastplate_'+str(s),'body','chest',[x,1,-3.8],[4.35,4.5,1.5])
        box('breastplate_rim_'+str(s),'body','chest',[x,5.3,-4],[4.35,.55,1.6],'edge')
    box('sternum','body','chest',[-.45,1.2,-4.05],[.9,4.3,.45],'shadow')
    for i in range(3):
        box('abdomen_'+str(i),'body','chest',[-3.9,5.8+i*1.5,-3.3+i*.16],[7.8,1.65,1.05])
        box('abdomen_edge_'+str(i),'body','chest',[-3.9,7.15+i*1.5,-3.38+i*.16],[7.8,.3,1.1],'edge')
    box('belt','body','chest',[-4.5,10,-2.85],[9,1.45,5.7],'strap')
    box('belt_clasp','body','chest',[-1,10.05,-3.5],[2,1.3,.8],'buckle')
    # A diagonal expedition strap stays on the chest bone, separate from the plates.
    box('cross_strap','body','chest',[-.55,-.1,-4.45],[1.1,10.1,.45],'strap',
        rotation=[0,0,30],pivot=[0,5,-4.2])
    box('strap_clasp','body','chest',[1.1,2.2,-4.65],[1.3,1.6,.55],'buckle')
    for s in (-1,1):
        box('belt_pouch_'+str(s),'body','chest',[(-4.2 if s<0 else 2.3),8.7,-4.2],[1.9,2.8,1.8],'strap')
        box('pouch_flap_'+str(s),'body','chest',[(-4.3 if s<0 else 2.2),8.6,-4.4],[2.1,1.05,2],'shadow')
    # Arm plates follow the existing Minecraft shoulder pivots, including in walking poses.
    for s,bone in ((-1,'right_arm'),(1,'left_arm')):
        x=-3 if s<0 else -1
        box('arm_underlayer_'+str(s),bone,'chest',[x-.25,-1.7,-2.25],[4.5,11.8,4.5],'cloth')
        width=6.2 if heavy else 5.3
        left=x-(width-4)/2
        box('pauldron_cap_'+str(s),bone,'chest',[left,-2.6,-3],[width,2.1,6])
        box('pauldron_crown_'+str(s),bone,'chest',[left+.7,-3.1,-2.4],[width-1.4,.5,4.8],'edge')
        box('pauldron_lame_'+str(s),bone,'chest',[left+.2,-.6,-3.05],[width-.4,1.35,6.1],'shadow')
        box('pauldron_rim_'+str(s),bone,'chest',[left+.2,.5,-3.15],[width-.4,.4,6.3],'edge')
        box('upper_arm_plate_'+str(s),bone,'chest',[x-.15,1.4,-2.8],[4.3,3.1,1])
        box('vambrace_'+str(s),bone,'chest',[x-.25,6.1,-2.8],[4.5,3.9,1.05])
        box('vambrace_rim_'+str(s),bone,'chest',[x-.35,9.65,-2.95],[4.7,.5,5.3],'edge')
        box('elbow_band_'+str(s),bone,'chest',[x-.3,5.1,-2.4],[4.6,.8,4.8],'strap')
    # Greaves / boots / upper-leg tassets are separate slot-assigned elements.
    for s,bone in ((-1,'right_leg'),(1,'left_leg')):
        box('leg_cloth_'+str(s),bone,'legs',[-2.1,0,-2.1],[4.2,10,4.2],'cloth')
        box('thigh_'+str(s),bone,'legs',[-2.35,.4,-2.75],[4.7,3.6,1])
        box('thigh_rim_'+str(s),bone,'legs',[-2.4,3.6,-2.9],[4.8,.45,1.2],'edge')
        box('kneecap_'+str(s),bone,'legs',[-2.45,4.8,-3.05],[4.9,1.8,1.45])
        box('knee_center_'+str(s),bone,'legs',[-1.5,5,-3.35],[3,1.25,.45],'edge')
        box('boot_underlayer_'+str(s),bone,'feet',[-2.25,7.2,-2.35],[4.5,4.8,4.7],'shadow')
        box('greave_'+str(s),bone,'feet',[-2.45,7,-2.9],[4.9,3.8,1.1])
        box('ankle_band_'+str(s),bone,'feet',[-2.5,10,-2.95],[5,1,5.9],'strap')
        box('sabatons_'+str(s),bone,'feet',[-2.55,10.7,-3.3],[5.1,1.5,6.05])
        box('toe_plate_'+str(s),bone,'feet',[-2.4,10.5,-3.6],[4.8,.8,1.4],'edge')
        box('sole_'+str(s),bone,'feet',[-2.6,11.85,-3.45],[5.2,.55,6.3],'dark')
    # The silver tier uses the same family but a lighter profile and fewer armor layers.
    if not heavy:
        cubes=[c for c in cubes if not c['name'].startswith(('back_spine','pauldron_lame','abdomen_2','abdomen_edge_2'))]
    # Leave a visible gap between plates/boots at the canonical 3.8-unit leg spacing.
    for c in cubes:
        if c['bone']=='right_leg' and c['role']!='cloth':c['size'][0]=min(c['size'][0],1.8-c['from'][0])
        if c['bone']=='left_leg' and c['role']!='cloth':
            right=c['from'][0]+c['size'][0]
            c['from'][0]=max(c['from'][0],-1.8);c['size'][0]=right-c['from'][0]
    sets[tier]={'palette':palette,'cubes':cubes}

design={'revision':'ARMOR-A01','state':'Visual proposal, not installed in Minecraft',
        'style':'Knight silhouette with expedition straps and pouches; restrained vanilla pixel detail',
        'units':'Minecraft model units; 16 per block. Coordinates x right, y down, front negative z.',
        'bones':bones,'sets':sets,'baseline_tints':{'riftsteel':'#386369','riftsilver':'#b4bdc9'},
        'textures_reference_catalog':str(CATALOG),'reference_sources':sources,
        'gameplay':'Existing item IDs, recipes, protection, durability, enchantments and spider resistance are not modified.',
        'limitations':['Blender visual proposal, not a native game screenshot.',
                       'Wearable renderer, trims, dye behavior, slim arms, first-person arms and animation clipping remain unverified.',
                       'Pouches are visual details, not additional inventory slots.']}
path=ROOT/'source/armor_design.json';path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(design,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert all(min(c['size'])>0 for s in sets.values() for c in s['cubes'])
assert all(c['bone'] in bones for s in sets.values() for c in s['cubes'])
report={'revision':design['revision'],'cubes':{k:len(v['cubes']) for k,v in sets.items()},
        'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'mod_files_modified':False,'native_game_verified':False}
(ROOT/'reports').mkdir(exist_ok=True)
(ROOT/'reports/design.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
