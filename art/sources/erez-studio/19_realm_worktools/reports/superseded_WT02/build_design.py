"""WT02: exact Minecraft element geometry, shared with Blender; no new painted sprites."""
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
    # Native one-unit grid. Greedy rectangles share exact edges, without angled fragments.
    metal='steel' if steel else 'silver';shadow='dark' if steel else 'silver_shadow'
    pixels={}
    def row(y,left,right,edge=False):
        for x in range(left,right+1):pixels[x,y]='edge' if edge else metal
    if kind=='pickaxe':
        row(14,3,12,True);row(13,1,14)
        for y,left,right in ((12,0,4),(12,6,9),(12,11,15),(11,0,2),(11,6,9),(11,13,15),(10,0,1),(10,7,8),(10,14,15)):row(y,left,right)
        for x in (0,15):
            for y in (10,11,12):pixels[x,y]='edge'
    elif kind=='axe':
        for y,l,r in ((15,3,6),(14,2,9),(13,1,11 if not steel else 12),(12,1,11 if not steel else 12),(11,1,9),(10,2,9),(9,2,5)):
            row(y,l,r);pixels[l,y]='edge'
    elif kind=='shovel':
        for y,l,r in ((16,7,8),(15,6,9),(14,5,10),(13,5,10),(12,5,10),(11,6,9),(10,7,8)):
            row(y,l,r)
            if y>=12:pixels[l,y]=pixels[r,y]='edge'
        if steel:row(11,4,11)
    else:
        row(14,2,6,True);row(13,2,9);row(12,2,11);row(11,6,9);row(10,7,8)
    # Assert one connected metal head, including the actual socket area over the haft.
    todo=[next(iter(pixels))];seen=set(todo)
    while todo:
        x,y=todo.pop()
        for n in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if n in pixels and n not in seen:seen.add(n);todo.append(n)
    assert len(seen)==len(pixels),(kind,'disconnected head')
    remaining=dict(pixels);counter=0
    while remaining:
        x,y=min(remaining,key=lambda k:(k[1],k[0]));role=remaining[x,y];w=1
        while remaining.get((x+w,y))==role:w+=1
        h=1
        while all(remaining.get((xx,y+h))==role for xx in range(x,x+w)):h+=1
        for xx in range(x,x+w):
            for yy in range(y,y+h):del remaining[xx,yy]
        box('continuous_'+kind+'_'+str(counter),x,y,6.8 if steel else 7.0,w,h,2.4 if steel else 2.0,role);counter+=1
    box('eye_reinforcement',7.0,11.0,6.55 if steel else 6.8,2,2.4,2.9 if steel else 2.4,shadow)
    for z in (6.3 if steel else 6.55,9.45 if steel else 9.2):
        box('eye_pin_'+str(z),7.6,11.9,z,.8,.8,.25,'brass')
        if steel:box('small_inlay_'+str(z),7.22,11.98,z,.22,.6,.25,'cyan')

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
design={'revision':'WT02','units':'16 native model units per block','authoritative_geometry':'This JSON: models/item/elements, imported directly into Blender','stage':'Visual candidate; special tool abilities are proposals, not active',
    'models':models,'previous':previous,'textures':textures,'texture_provenance':provenance,
    'baseline_jar':str(BASE),'baseline_sha256':sha(BASE.read_bytes())}
(ROOT/'source/worktools_WT02.json').write_text(json.dumps(design,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:len(v['model']['elements']) for k,v in models.items()}))
