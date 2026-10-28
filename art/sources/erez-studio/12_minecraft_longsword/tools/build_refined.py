"""LS-03: preserve original detail scale and align taper centres to blade axis.

No game files are written. Original PNG unchanged. Geometry operations only.
"""
from pathlib import Path
import hashlib
import json
import math
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
config={
    'revision':'LS-03','item_id':'interstice:riftsteel_longsword',
    'name':'Riftsteel Longsword / original-detail revision',
    'destination_project':'D:/repos/Minecraft/EREZCRAFT',
    'geometry_authority':'tools/build_refined.py and the original read-only source PNG; each cell is 0.5 model units, identical to the existing generated item.',
    'thickness':1,'cell_size':.5,'texture':'interstice:item/armaments/tool_greatsword_steel',
    'texture_sha256':'eeeb1f0503d70a9a3a68a4d43cf71f314b927a4480572866ebf43188e500ccea',
    'texture_credit':'Malcolm Riley / unused-textures, CC BY 4.0, commit 8b85ad732023174d9a9da74e6bf2272d6c0bef17. PNG unchanged; original hilt, guard and lower blade geometry preserved. New upper-blade geometry reuses original straight-blade texels.',
    'operations':{'unchanged_original_rows':[7,31],'straight_extension_rows':[[6,21],[5,22],[4,23],[3,24]],
                  'tip_rows':[[2,25,'JPOOLLKE'],[1,26,'JPOOLLKE'],[0,27,'JPOOLKE'],[-1,29,'JPOLKE'],
                              [-2,30,'JPOKE'],[-3,32,'JPLE'],[-4,33,'JPE'],[-5,35,'J']]},
    'tip_alignment':{'row_center_x_plus_y':30.5,'cell_center_x_plus_y':31.5,
                     'method':'Reposition odd-width tip rows by half a texel in X; retain cell size, source UVs and row height.'},
    'acceptance':['Original hilt and guard identical in shape, texels, position and thickness',
                  'Original 0.5 model-unit pixel density preserved','No game PNG changes','No game resources installed',
                  'Preview approval pending; native hand poses not verified']}
write(ROOT/'source/design.json',config)
texture=Path(config['destination_project'])/'src/main/resources/assets/interstice/textures/item/armaments/tool_greatsword_steel.png'
assert sha(texture)==config['texture_sha256']
image=Image.open(texture).convert('RGBA')
original={(x,y):(x,y) for y in range(32) for x in range(32) if image.getpixel((x,y))[3]}
cells={(x,y):(x,y) for x,y in original if y>=7}
for row,left in config['operations']['straight_extension_rows']:
    for col in range(8):cells[left+col,row]=(20+col,7)
palette={c:(17+i,10) for i,c in enumerate('JPOOLLKE')}
tip_centres=[]
for row,left,pattern in config['operations']['tip_rows']:
    centered_left=config['tip_alignment']['row_center_x_plus_y']-row-(len(pattern)-1)*.5
    for col,c in enumerate(pattern):cells[centered_left+col,row]=palette[c]
    tip_centres.append(centered_left+(len(pattern)-1)*.5+row)
assert all(abs(c-30.5)<1e-9 for c in tip_centres),'Off-axis tip row'
assert all(cells[p]==p for p in original if p[1]>=7)
visited={next(iter(cells))};todo=list(visited)
while todo:
    x,y=todo.pop()
    for p in cells:
        if p in visited:continue
        dx,dy=abs(p[0]-x),abs(p[1]-y)
        if (dx==1 and dy==0) or (dy==1 and dx<1):
            visited.add(p);todo.append(p)
assert len(visited)==len(cells),'Disconnected geometry'
elements=[]
for (x,y),(u,v) in cells.items():
    a=[x*.5,(31-y)*.5,7.5];b=[a[0]+.5,a[1]+.5,8.5]
    assert all(-16<=n<=32 for n in a+b)
    rgba=image.getpixel((u,v));r,g,blue,_=rgba
    grip=r>g*1.2 and blue<12
    sides={'north':True,'south':True,'west':(x-1,y) not in cells,
           'east':(x+1,y) not in cells,'up':(x,y-1) not in cells,'down':(x,y+1) not in cells}
    uv=[(u+.25)*.5,(v+.25)*.5,(u+.75)*.5,(v+.75)*.5]
    elements.append({'name':f'pixel_{x}_{y}'+('_B' if grip else '_metal'),
                     'from':a,'to':b,'rotation':{'origin':[8,8,8],'axis':'z','angle':0,'rescale':False},
                     'shade':True,'faces':{side:{'uv':uv,'texture':'#layer0'} for side,on in sides.items() if on}})
lo=[min(e['from'][i] for e in elements) for i in range(3)]
hi=[max(e['to'][i] for e in elements) for i in range(3)]
scale=min(1,14/max(hi[i]-lo[i] for i in (0,1)))
offset=[round(-(lo[i]+hi[i]-16)*scale/2,5) for i in (0,1)]
display={
    'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,4,.5],'scale':[.85]*3},
    'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,4,.5],'scale':[.85]*3},
    'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[.68]*3},
    'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1.13,3.2,1.13],'scale':[.68]*3},
    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5]*3},
    'head':{'rotation':[0,180,0],'translation':[0,13,7],'scale':[1]*3},
    'fixed':{'rotation':[0,180,0],'scale':[1]*3},
    'gui':{'rotation':[0,0,0],'translation':offset+[0],'scale':[scale]*3}}
# A generated-item ancestor would discard the explicit elements in Minecraft's baker.
# Keep the vanilla handheld poses explicitly and use a non-generated root.
model={'credit':config['texture_credit'],'parent':'minecraft:block/block','gui_light':'front','ambientocclusion':False,
       'textures':{'layer0':config['texture'],'particle':config['texture']},
       'display':display,'elements':elements}
model_path=ROOT/'exports/assets/interstice/models/item/riftsteel_longsword.json';write(model_path,model)
pack=ROOT/'exports/Mezhdumorye_Riftsteel_LS03_Preview_1.21.1.zip'
with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':34,'description':'Mezhdumorye Riftsteel LS-03 visual preview; requires Interstice'}}))
    z.write(model_path,'assets/interstice/models/item/riftsteel_longsword.json')
    z.writestr('README.txt','Optional model preview for interstice:riftsteel_longsword. Requires an Interstice build registering the NEW long sword. The original interstice:riftsteel_sword remains unchanged. Original texture: Malcolm Riley, CC BY 4.0 (provided by the mod).\n')
old_axis=[((x+.5)+(31-y+.5))*.5/math.sqrt(2) for x,y in original]
new_axis=[((x+.5)+(31-y+.5))*.5/math.sqrt(2) for x,y in cells]
old_length=max(old_axis)-min(old_axis)+.5*math.sqrt(2)
new_length=max(new_axis)-min(new_axis)+.5*math.sqrt(2)
report={'revision':config['revision'],'cells':len(cells),'connected':len(visited)==len(cells),
        'cell_size':.5,'thickness':1,'original_lower_blade_guard_grip_preserved':True,
        'texture_unchanged':sha(texture)==config['texture_sha256'],
        'original_axial_extent':old_length,'candidate_axial_extent':new_length,
        'tip_row_centers_source':tip_centres,'tip_center_max_error':max(abs(c-30.5) for c in tip_centres),
        'length_increase_percent':round((new_length/old_length-1)*100,2),
        'inputs':{str(p):sha(p) for p in (Path(__file__),texture)},
        'outputs':{str(p.relative_to(ROOT)):sha(p) for p in (model_path,pack,ROOT/'source/design.json')},
        'installed':False,'native_game_verified':False,'approval':'Pending owner visual review'}
write(ROOT/'reports/assets.json',report)
print(json.dumps({k:report[k] for k in ('revision','cells','cell_size','length_increase_percent','original_lower_blade_guard_grip_preserved','installed')}))
