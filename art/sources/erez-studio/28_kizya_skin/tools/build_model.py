"""Native CPM geometry and UV packing. The owner's image is a visual reference only."""
from pathlib import Path
from PIL import Image
import json,math,hashlib,zipfile,io,random,shutil
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');random.seed(2801)
P={
 'skin':('gui-materials/steel_block.png',(245,220,220),(255,242,238)),
 'hair':('crown-tree/maple_log_side.png',(228,147,180),(255,203,223)),
 'hair_light':('crown-tree/maple_log_side.png',(244,176,203),(255,219,233)),
 'hair_shadow':('crown-tree/maple_log_side.png',(201,115,153),(240,171,199)),
 'white':('gui-materials/linen_pattern.png',(241,228,236),(255,250,253)),
 'white_shadow':('gui-materials/linen_pattern.png',(221,201,216),(247,235,244)),
 'pink':('gui-materials/linen_pattern.png',(207,137,172),(240,179,207)),
 'pink_light':('gui-materials/linen_pattern.png',(226,161,192),(251,204,226)),
 'pink_dark':('gui-materials/linen_pattern.png',(186,116,151),(219,156,186)),
 'eyes':('gui-materials/steel_block.png',(76,47,62),(113,75,91)),
 'blush':('gui-materials/linen_pattern.png',(232,165,190),(246,190,209)),
 'sole':('gui-materials/linen_pattern.png',(184,123,155),(214,161,188)),
}
atlas=Image.new('RGBA',(128,128),(0,0,0,0));regions={};provenance=[]
(ROOT/'source/originals').mkdir(exist_ok=True);(ROOT/'source/palette_tiles').mkdir(exist_ok=True)
for index,(name,(relative,lo,hi)) in enumerate(P.items()):
    path=GAME/'art/sources/cc0'/relative
    if relative.startswith('gui-materials'):
        meta=json.loads((path.parent/'sources.json').read_text());record=next(r for r in meta['sources'] if r['file']==path.name);expected=record['source_sha256'];author=record['author'];url=record['source_url']
    else:
        meta=json.loads((path.parent/'sources.json').read_text());record=meta['files'][path.name];expected=record['sha256'];author=meta['author'];url=record['source_url']
    digest=hashlib.sha256(path.read_bytes()).hexdigest();assert digest==expected
    im=Image.open(path).convert('RGBA');colors=sorted(set(p[:3] for p in im.getdata() if p[3]),key=lambda p:(.2126*p[0]+.7152*p[1]+.0722*p[2],p))
    lut={c:tuple(round(lo[k]+(hi[k]-lo[k])*i/(len(colors)-1 or 1)) for k in range(3)) for i,c in enumerate(colors)}
    assert len(set(lut.values()))==len(colors)
    out=Image.new('RGBA',im.size);out.putdata([(*lut[p[:3]],p[3]) if p[3] else p for p in im.getdata()]);assert out.getchannel('A').tobytes()==im.getchannel('A').tobytes()
    shutil.copyfile(path,ROOT/'source/originals'/path.name);tile=ROOT/'source/palette_tiles'/f'{name}.png';out.save(tile)
    x=1+(index%7)*18;y=1+(index//7)*18;atlas.paste(out,(x,y))
    regions[name]={'sx':x,'sy':y,'ex':x+16,'ey':y+16,'rot':'0','autoUV':False}
    provenance.append(dict(role=name,source_file=relative,source_sha256=digest,author=author,source_url=url,license='CC0-1.0',changes='Bijective RGB palette only; source drawing, 16x16 dimensions and alpha preserved; unmodified tile packed into CPM atlas',derived_tile_sha256=hashlib.sha256(tile.read_bytes()).hexdigest()))
parts=[];sid=100
def box(name,bone,offset,size,role,hide=(),rot=(0,0,0)):
    global sid
    sid+=1;parts.append(dict(name=name,bone=bone,offset=list(offset),size=list(size),role=role,hide_poses=list(hide),rotation=list(rot),id=sid,glow=False,uv={f:dict(regions[role]) for f in ['north','south','west','east','up','down']}))
H=('ARMOR_HEAD',);B=('ARMOR_BODY',);L=('ARMOR_LEGS',);F=('ARMOR_BOOTS',)
# Six vanilla-aligned body parts; default slim arm offsets retained.
box('face_base','head',(-4,-8,-4),(8,8,8),'skin')
box('blouse_body','body',(-4,0,-2),(8,12,4),'white')
for side in ['right','left']:
    arm=side+'_arm';leg=side+'_leg';x=-2 if side=='right' else -1
    box(side+'_sleeve',arm,(x,-2,-2),(3,12,4),'white')
    box(side+'_leg_base',leg,(-2,0,-2),(4,12,4),'skin')
# Quiet expression: dark mauve horizontal eyes with a pale lower edge.
for side,x in [('right',-3.15),('left',1.05)]:
    box(side+'_eye_lid','head',(x,-3.1,-4.055),(2.1,.58,.09),'eyes')
    box(side+'_eye_lower','head',(x+.13,-2.52,-4.065),(1.87,.28,.1),'white')
    box(side+'_cheek','head',(x+.42,-1.8,-4.055),(.94,.3,.08),'blush')
box('subtle_mouth','head',(-.3,-.57,-4.06),(.6,.12,.08),'pink_dark')
# Head hair cap has an open face. Tiny flyaway pieces are real geometry.
box('hair_crown','head',(-4.34,-8.42,-4.24),(8.68,1.12,8.65),'hair_light',H)
box('hair_nape','head',(-4.32,-7.55,3.0),(8.64,7.85,1.4),'hair',H)
for side in [-1,1]:box(f'hair_side_cap_{side}','head',((3.65 if side>0 else -4.38),-7.4,-3.7),(.73,7.75,7.5),'hair',H)
for i in range(13):
    x=-4.23+i*.66;length=5.05+(.55 if i in [5,6,7] else .2*math.sin(i*1.4))
    role=['hair','hair_light','hair'][i%3]
    box(f'fringe_{i:02}_upper','head',(x,-8.05,-4.43),(.7,length*.68,.52),role,H,rot=(0,0,math.sin(i)*1.5))
    box(f'fringe_{i:02}_tip','head',(x+.08*math.sin(i),-8.05+length*.64,-4.47),(.59,length*.4,.57),role,H,rot=(0,0,-math.sin(i)*2))
for i in range(6):
    x=-3.7+i*1.4
    box(f'crown_flyaway_{i}','head',(x,-8.7,-2+math.sin(i)*1.6),(.35,.34,2.6),'hair_light',H,rot=(0,math.sin(i)*8,0))
# Long body-bound layers preserve the nape/head transition while looking around.
for i in range(17):
    x=-4.25+i*.52;z=3.28+(.34 if i%3==0 else 0);start=-.85;length=11.4+1.5*math.sin(i*1.7)
    for j in range(3):
        yy=start+j*(length/3-.15);w=.61 if j<2 else .45
        box(f'back_lock_{i:02}_{j}','body',(x+math.sin(i+j*1.3)*.22,yy,z+math.sin(i*.8+j)*.25),(w,length/3+.2,.68),['hair','hair_light','hair_shadow'][i%3],B+('WEARING_ELYTRA',),rot=(math.sin(i+j)*2,0,math.sin(i+j)*3))
for side in [-1,1]:
    for i in range(4):
        x=side*(3.08+i*.27);z=-2.9-i*.1;length=9.6+i*.5
        for j in range(3):
            xx=x+side*math.sin(j*1.8+i)*.25
            box(f'front_lock_{side}_{i}_{j}','body',(xx-.31,-.4+j*(length/3-.1),z),(.65,length/3+.25,.72),['hair_light','hair','hair','hair_light'][i],B,rot=(0,0,side*math.sin(j+i)*4))
    for i in range(3):
        x=side*(3.9+i*.22)
        box(f'cheek_lock_{side}_{i}','head',(x-.25,-5.4+i*.6,-3.8),(.57,6.1-i*.35,.75),'hair_light' if i==0 else 'hair',H,rot=(0,0,side*3))
# White sweater and modest pink pinafore/shorts, matching the reference silhouette.
box('white_neckline','body',(-2.0,-.15,-2.11),(4,1.6,.17),'white_shadow',B)
box('white_collar','body',(-1.5,.1,-2.31),(3,1.5,.16),'white',B)
box('pinafore_bib','body',(-2.85,2.55,-2.25),(5.7,4.5,.35),'pink_light',B)
box('pinafore_front','body',(-3.8,6.25,-2.28),(7.6,5.7,.36),'pink',B)
box('pinafore_back','body',(-3.8,3.3,2.0),(7.6,8.65,.32),'pink',B)
for side in [-1,1]:
    xx=side*2.5-.35
    box(f'strap_{side}','body',(xx,.2,-2.29),(.7,6.0,.29),'pink_light',B)
    for yy in [6.5,9.85]:box(f'button_{side}_{yy}','body',(side*2.65-.17,yy,-2.69),(.34,.31,.13),'white',B)
    box(f'pinafore_side_{side}','body',((3.65 if side>0 else -4.0),4.5,-2.12),(.35,7.45,4.3),'pink',B)
for side in ['right','left']:
    arm=side+'_arm';leg=side+'_leg';x=-2 if side=='right' else -1
    box(side+'_puff_shoulder',arm,(x-.2,-1.95,-2.2),(3.4,2.1,4.4),'white',B)
    box(side+'_sleeve_fold',arm,(x-.12,3.5,-2.15),(3.24,.45,4.3),'white_shadow',B)
    box(side+'_soft_cuff',arm,(x-.3,7.6,-2.28),(3.6,2.4,4.56),'white',B)
    box(side+'_cuff_hem',arm,(x-.36,9.6,-2.34),(3.72,.34,4.68),'white_shadow',B)
    box(side+'_pinafore_shorts',leg,(-2.08,-.05,-2.12),(4.16,2.9,4.24),'pink',L)
    box(side+'_shorts_hem',leg,(-2.12,2.6,-2.16),(4.24,.35,4.32),'pink_light',L)
    box(side+'_sock',leg,(-2.02,3.9,-2.02),(4.04,6.1,4.04),'pink_light',L)
    box(side+'_sock_cuff',leg,(-2.07,3.8,-2.07),(4.14,.55,4.14),'white',L)
    for xx in [-1.45,.8]:box(side+'_sock_stripe_'+str(xx),leg,(xx,4.45,-2.07),(.65,5.4,.1),'white',L)
    box(side+'_sock_back_stripe',leg,(-.35,4.45,1.98),(.7,5.3,.1),'white',L)
    for sign in [-1,1]:box(side+'_sock_bow_'+str(sign),leg,(-.1+sign*.35,6.4,2.05),(.65,.28,.15),'white',L,rot=(0,0,sign*28))
    box(side+'_shoe',leg,(-2.2,9.7,-2.57),(4.4,2.3,4.8),'pink',F)
    box(side+'_sole',leg,(-2.23,11.62,-2.6),(4.46,.4,4.86),'sole',F)
    box(side+'_shoe_top_band',leg,(-2.24,9.57,-2.64),(4.48,.42,4.92),'pink_light',F)
    box(side+'_shoe_white_tab',leg,(-1.1,10.0,-2.68),(2.2,.44,.16),'white',F)
    box(side+'_shoe_toe',leg,(-2.22,10.65,-2.75),(4.44,.7,.24),'pink_light',F)

vec=lambda v:dict(zip('xyz',v))
elements=[]
for bone in ['head','body','right_arm','left_arm','right_leg','left_leg']:
    root={'id':bone,'name':bone,'show':False,'pos':vec([0,0,0]),'rotation':vec([0,0,0]),'children':[]}
    for p in [p for p in parts if p['bone']==bone]:
        root['children'].append({'name':p['name'],'storeID':p['id'],'show':True,'texture':True,'textureSize':1,'offset':vec(p['offset']),'pos':vec([0,0,0]),'rotation':vec(p['rotation']),'size':vec(p['size']),'rscale':vec([1,1,1]),'scale':vec([1,1,1]),'u':0,'v':0,'color':'ffffff','mirror':False,'mcScale':0,'glow':False,'recolor':False,'hidden':False,'faceUV':p['uv']})
    elements.append(root)
config={'version':1,'skinType':'slim','hideHeadIfSkull':True,'removeArmorOffset':False,'elements':elements,'skinSize':{'x':128,'y':128},'textures':{'skin':{'customGridSize':False,'anim':[]}}}
animations={}
for pose in sorted({x for p in parts for x in p['hide_poses']}):
    components=[{'storeID':p['id'],'pos':vec([0,0,0]),'rotation':vec([0,0,0]),'color':'ffffff','show':False,'scale':vec([1,1,1])} for p in parts if pose in p['hide_poses']]
    animations['v_'+pose.lower()+'_clearance.json']={'name':'Equipment clearance '+pose,'additive':True,'duration':1000,'priority':20,'loop':True,'interpolator':'linear','frames':[{'components':components}]}
data={'revision':'KIZYA-PK01','parts':parts,'config':config,'animations':animations,'pivots':{'head':[0,0,0],'body':[0,0,0],'right_arm':[-5,2,0],'left_arm':[5,2,0],'right_leg':[-1.9,12,0],'left_leg':[1.9,12,0]},'units':'Vanilla model pixels; all native player bones and gameplay dimensions retained','account':{'name':'kizya','uuid':'f644c73e-3c3f-4bbd-83e5-074e8c552b31','confirmed_by_owner':True},'reference_image':'references/owner_reference.png','source_of_texture_details':'Recolored verified CC0 tiles and native geometric detail, not the reference sheet as a texture','native_game_verified':False}
(ROOT/'source/avatar_PK01.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8');atlas.save(ROOT/'source/avatar_atlas_PK01.png')
(ROOT/'source/ASSET_PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n')
png=io.BytesIO();atlas.save(png,format='PNG')
with zipfile.ZipFile(ROOT/'exports/Kizya_Pink_PK01.cpmproject','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('config.json',json.dumps(config));z.writestr('skin.png',png.getvalue())
    for name,a in animations.items():z.writestr('animations/'+name,json.dumps(a))
(ROOT/'reports/MODEL_BUILD.json').write_text(json.dumps({'revision':'PK01','parts':len(parts),'quads':len(parts)*6,'texture_size':[128,128],'skin_type':'slim','source_geometry_sha256':hashlib.sha256((ROOT/'source/avatar_PK01.json').read_bytes()).hexdigest(),'game_verified':False},indent=2)+'\n')
print(json.dumps({'parts':len(parts),'quads':len(parts)*6,'atlas':[128,128]}))
