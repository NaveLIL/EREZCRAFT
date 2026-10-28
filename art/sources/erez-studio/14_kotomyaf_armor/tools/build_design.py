"""Original wearable Siamese-cat geometry. One source for Blender and the native renderer."""
from pathlib import Path
import json,hashlib,sys
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT.parent/'13_minecraft_armor/source/armor_design_A04.json'
reference=json.loads(BASE.read_text(encoding='utf-8'))
palette={'plate':'#efdfc2','edge':'#fff1d7','shadow':'#725345','cloth':'#4b3732','strap':'#da9e9a','buckle':'#70d7ec','dark':'#24242b'}
cubes=[]
def box(name,bone,slot,xyz,size,role='plate',rotation=None,pivot=None):
    c=dict(name=name,bone=bone,slot=slot,**{'from':xyz},size=size,role=role)
    if rotation:c.update(rotation=rotation,rotation_pivot=pivot or [0,0,0])
    cubes.append(c)
# Cream hood surrounds a chocolate point mask. Ears use stepped silhouettes,
# not flat pasted triangles. The eyes and muzzle are actual shallow geometry.
box('hood_crown','head','head',[-4.45,-8.5,-4.45],[8.9,1.1,8.9])
box('hood_top_tuft','head','head',[-2.5,-8.9,-2.8],[5,.4,5.6],'edge')
box('hood_back','head','head',[-4.45,-7.4,3.55],[8.9,7.7,1])
box('neck_fluff_rear','head','head',[-4.6,-.6,3.1],[9.2,1.1,1.5],'edge')
for s in (-1,1):
    box(f'hood_side_{s}','head','head',[(-4.65 if s<0 else 3.65),-7.4,-4.45],[1,7.3,8.1])
    box(f'cheek_fluff_{s}','head','head',[(-4.8 if s<0 else 3.2),-2.5,-4.7],[1.6,2.5,1.5],'edge')
    box(f'cheek_tuft_{s}','head','head',[(-5.05 if s<0 else 3.85),-1.3,-4.4],[1.2,1.2,1.2])
    # Three ear tiers with a matching inset pink heart.
    for j,(w,h) in enumerate([(3.4,1.5),(2.5,1.35),(1.35,1.3)]):
        y=-9.5-sum([1.35,1.3][:j]);center=s*3.05
        box(f'ear_{s}_{j}','head','head',[center-w/2,y,-2.15],[w,h,2.0],'cloth')
    for j,(w,h) in enumerate([(1.9,1.15),(1.05,1.1)]):
        box(f'ear_inner_{s}_{j}','head','head',[s*3.05-w/2,-9.25-j*1.15,-2.28],[w,h,.22],'strap')
box('point_mask','head','head',[-3.8,-7.45,-4.7],[7.6,6.65,.55],'shadow')
box('mask_brow','head','head',[-3.4,-7.75,-4.75],[6.8,.45,.65],'cloth')
box('mask_nose_bridge','head','head',[-1.15,-5.5,-4.95],[2.3,2.8,.5],'cloth')
for s in (-1,1):
    cx=s*2.15
    box(f'eye_socket_{s}','head','head',[cx-1.35,-5.85,-5.02],[2.7,2.05,.3],'dark')
    box(f'blue_eye_{s}','head','head',[cx-1.05,-5.65,-5.21],[2.1,1.55,.2],'buckle')
    box(f'eye_pupil_{s}','head','head',[cx-.3,-5.6,-5.43],[.6,1.45,.15],'dark')
    box(f'eye_glint_{s}','head','head',[cx-.8,-5.6,-5.6],[.5,.5,.12],'edge')
    box(f'muzzle_lobe_{s}','head','head',[(-2.1 if s<0 else .05),-3.25,-5.4],[2.05,1.7,1.05],'plate')
    box(f'muzzle_upper_{s}','head','head',[(-1.65 if s<0 else .05),-3.55,-5.35],[1.6,.4,.8],'edge')
    for j in range(2):box(f'whisker_dot_{s}_{j}','head','head',[s*(1.1+j*.55)-.12,-2.6+j*.2,-6.5],[.24,.24,.12],'cloth')
    for j,angle in enumerate([-12,0,12]):
        anchor=[s*2.1,-2.45,-6.2]
        box(f'whisker_{s}_{j}','head','head',[(-7.1 if s<0 else 2.1),-2.55,-6.3],[5,.17,.19],'edge',rotation=[0,0,s*angle],pivot=anchor)
box('pink_nose','head','head',[-.6,-3.45,-6.55],[1.2,.6,.45],'strap')
box('nose_tip','head','head',[-.3,-2.9,-6.58],[.6,.35,.35],'strap')
box('mouth_stem','head','head',[-.10,-2.6,-6.55],[.2,.65,.14],'dark')
for s in (-1,1):box(f'mouth_smile_{s}','head','head',[(-.75 if s<0 else .1),-2,-6.48],[.65,.18,.15],'cloth')
# Wool jacket with soft layered bib and a tiny collar bell. No bulky metal pauldrons.
box('wool_jacket','body','chest',[-4.3,0,-2.38],[8.6,11.4,4.76])
box('jacket_back','body','chest',[-3.8,.6,2.35],[7.6,8.8,.7])
box('jacket_hem','body','chest',[-4.45,10.35,-2.6],[8.9,1.3,5.2],'edge')
box('collar_band','body','chest',[-4.25,-.4,-2.7],[8.5,1,5.4],'shadow')
box('bib_top','body','chest',[-3.3,.7,-2.9],[6.6,2.2,.65],'edge')
box('bib_middle','body','chest',[-2.7,2.8,-2.93],[5.4,2.1,.75],'edge')
box('bib_tip','body','chest',[-1.9,4.8,-2.95],[3.8,1.5,.8],'edge')
box('bell_loop','body','chest',[-.32,.1,-3.5],[.64,1,.5],'buckle')
box('bell','body','chest',[-.8,.85,-3.6],[1.6,1.2,.85],'buckle')
box('bell_slit','body','chest',[-.55,1.55,-3.65],[1.1,.16,.12],'dark')
for s,bone in [(-1,'right_arm'),(1,'left_arm')]:
    x=-3.2 if s<0 else -1.2
    box(f'sleeve_{s}',bone,'chest',[x,-1.95,-2.25],[4.4,10.4,4.5])
    box(f'shoulder_fluff_{s}',bone,'chest',[x-.2,-2.3,-2.4],[4.8,1.15,4.8],'edge')
    box(f'sleeve_cuff_{s}',bone,'chest',[x-.15,6.25,-2.4],[4.7,1.45,4.8],'shadow')
    box(f'point_mitten_{s}',bone,'chest',[x-.05,7.65,-2.45],[4.5,2.55,4.9],'cloth')
    box(f'mitten_pad_{s}',bone,'chest',[x+1.3,8.65,-2.65],[1.8,1,.25],'strap')
    for j in range(3):box(f'mitten_toe_{s}_{j}',bone,'chest',[x+.55+j*1.2,7.9,-2.65],[.65,.55,.25],'shadow')
# Separate leggings and boots: full costume remains modular with normal slot ownership.
box('waist_seal','body','legs',[-4.22,10.5,-2.45],[8.44,1.9,4.9])
for s,bone in [(-1,'right_leg'),(1,'left_leg')]:
    box(f'wool_leg_{s}',bone,'legs',[-2.15,-.05,-2.2],[4.3,8.6,4.4])
    box(f'knee_point_{s}',bone,'legs',[-1.6,5.4,-2.43],[3.2,2.2,.55],'shadow')
    box(f'boot_cuff_{s}',bone,'feet',[-2.28,7.65,-2.4],[4.56,1.05,4.8],'edge')
    box(f'point_boot_{s}',bone,'feet',[-2.15,8.65,-2.45],[4.3,3.35,4.9],'cloth')
    box(f'paw_toe_{s}',bone,'feet',[-2.3,10.25,-3.35],[4.6,1.75,6.0],'shadow')
    box(f'paw_sole_{s}',bone,'feet',[-2.3,11.9,-3.35],[4.6,.3,6.0],'cloth')
    for j in range(3):box(f'boot_toe_{s}_{j}',bone,'feet',[-1.6+j*1.15,10.8,-3.55],[.7,.55,.25],'plate')
# Keep a 0.2-unit gap at native leg pivots, including cuffs/toes.
for c in cubes:
    if c['bone']=='right_leg':c['size'][0]=min(c['size'][0],1.8-c['from'][0])
    if c['bone']=='left_leg':
        end=c['from'][0]+c['size'][0];c['from'][0]=max(c['from'][0],-1.8);c['size'][0]=end-c['from'][0]
# A02: daughter requests an OPEN FACE, retaining the ears and whiskers.
# Preserve the first proposal separately; no authored eyes, nose, muzzle or mask remains.
remove=('point_mask','mask_brow','mask_nose_bridge','eye_socket_','blue_eye_','eye_pupil_','eye_glint_',
        'muzzle_','whisker_','pink_nose','nose_tip','mouth_')
cubes=[c for c in cubes if not c['name'].startswith(remove)]
for s in (-1,1):
    box(f'point_hood_rim_{s}','head','head',[(-4.55 if s<0 else 3.95),-7.4,-4.65],[.6,6.5,.6],'shadow')
    box(f'whisker_anchor_{s}','head','head',[(-4.65 if s<0 else 3.65),-3.1,-4.95],[1,1.4,.55],'shadow')
    for j,angle in enumerate([-12,0,12]):
        anchor=[s*4.05,-2.45,-5.25]
        box(f'open_face_whisker_{s}_{j}','head','head',[(-7.55 if s<0 else 4.05),-2.55,-5.35],[3.5,.17,.19],'edge',rotation=[0,0,s*angle],pivot=anchor)
# A04: the user explicitly removed the tail. No tail geometry is authored or exported.
assert all('tail' not in c['name'] for c in cubes)
assert len({c['name'] for c in cubes})==len(cubes)
assert all(min(c['size'])>0 and c['role'] in palette for c in cubes)
design={'revision':'KOTOMYAF-A04','state':'Approved open face and whiskers; tail removed at user request','units':reference['units'],'bones':reference['bones'],
 'sets':{'kotomyaf':{'palette':palette,'cubes':cubes}},'textures_reference_catalog':reference['textures_reference_catalog'],
 'native_texture':'minecraft:textures/models/armor/iron_layer_1.png','texture_policy':'Unchanged installed Minecraft texture, tinted at runtime; no new painted PNG.',
 'features':['Stepped chocolate ears with pink inset','Open face: original player skin remains visible','Six whiskers mounted outside the face opening','Wool bib, cuffs and paw boots','No tail'],
 'limitations':['Extra ears and whiskers are cosmetic geometry and do not enlarge the player hitbox.']}
p=ROOT/'source/kotomyaf_design_A04.json';p.write_text(json.dumps(design,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
native=Path('D:/repos/Minecraft/EREZCRAFT/.verification/kt01/src/main/resources/assets/interstice/armor/kotomyaf.json')
if '--preview-only' not in sys.argv:native.write_bytes(p.read_bytes())
(ROOT/'reports/design.json').write_text(json.dumps({'cubes':len(cubes),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'review_approved':False},indent=2),encoding='utf-8')
print('KOTOMYAF',len(cubes),'cubes')
