"""Author a bounded CPM avatar; the concept image is never used as a texture."""
from pathlib import Path
import json, math, zipfile, hashlib, io
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
for name in ('source','exports','reports','assembly','preview/native'):(ROOT/name).mkdir(parents=True,exist_ok=True)
P={'coat':'#252b36','panel':'#303847','dark':'#121923','lining':'#285b60','steel':'#9caaab','metal':'#60717a','brass':'#ac9463','leather':'#4b4134','cyan':'#55c6ce','light':'#abc7c9','black':'#080e14'}
parts=[];roots={k:[] for k in ('head','body','right_arm','left_arm','right_leg','left_leg')};sid=100
def box(name,bone,xyz,size,role,hide=(),rot=(0,0,0),glow=False,label=None):
    global sid
    sid+=1;p={'name':name,'bone':bone,'offset':xyz,'size':size,'role':role,'hide_poses':list(hide),'rotation':list(rot),'id':sid,'glow':glow}
    if label:p['label']=label
    parts.append(p);roots[bone].append(p);return p
H=('ARMOR_HEAD',);B=('ARMOR_BODY',);L=('ARMOR_LEGS',);F=('ARMOR_BOOTS',)
# Standard body dimensions and pivots; no gameplay scaling or custom hitbox.
box('hood_base','head',[-4,-8,-4],[8,8,8],'coat',label='under_mask')
box('coat_torso','body',[-4.1,0,-2.15],[8.2,11.7,4.3],'coat')
for side in ('right','left'):
    x=-3 if side=='right' else -1
    box(side+'_sleeve',side+'_arm',[x,-2,-2],[4,12,4],'coat')
    box(side+'_trousers',side+'_leg',[-2,0,-2],[4,12,4],'dark')
# Mask, straps and brow are actual geometry. Hide only the projecting details under helmets.
box('visor_frame','head',[-3.7,-5.55,-4.35],[7.4,2.2,.55],'black',H)
box('visor_glass','head',[-2.8,-5.1,-4.94],[5.6,.65,.12],'cyan',H,glow=True)
box('respirator_bridge','head',[-1.8,-3.65,-4.65],[3.6,3.05,1.2],'metal',H)
box('respirator_center','head',[-1.05,-3.3,-5.95],[2.1,2.55,.35],'dark',H)
for x in (-3.25,2.1):
    box('respirator_filter_'+str(x),'head',[x,-3.05,-4.7],[1.15,1.65,.8],'brass',H)
    box('filter_grille_'+str(x),'head',[x+.2,-2.8,-5.54],[.75,1.05,.12],'dark',H)
for x in (-4.15,3.85):
    box('head_strap_'+str(x),'head',[x,-5.4,-3],[.3,1,6.6],'leather',H)
box('head_rear_strap','head',[-4,-5.4,3.95],[8,1,.25],'leather',H)
box('brow_guard','head',[-4.2,-6.4,-4.45],[8.4,.65,.65],'panel',H)
# Collar sits below the mask and is controlled by the helmet slot as well as chest armor.
for x in (-4.3,3.65):box('collar_'+str(x),'body',[x,-.75,-2.55],[.65,2,5.1],'metal',H+B)
box('collar_rear','body',[-3.65,-.75,2.05],[7.3,1.75,.6],'panel',H+B)
box('chest_seal','body',[-2.6,1.35,-2.31],[5.2,5.3,.2],'dark',B,label='star')
box('back_signature','body',[-3.7,1.2,2.16],[7.4,4.1,.18],'panel',B,label='hebrew')
for x in (-3.8,3.3):box('coat_lapel_'+str(x),'body',[x,1,-2.5],[.5,8.4,.35],'lining',B)
for n in range(4):box('diagonal_harness_'+str(n),'body',[-3.6+n*1.5,5.7+n*.65,-2.6],[1.6,.8,.3],'leather',B)
box('belt','body',[-4.35,9.85,-2.45],[8.7,1.25,4.9],'leather',B)
box('belt_buckle','body',[-.65,9.8,-2.83],[1.3,1.3,.45],'brass',B)
for x in (-3.65,1.9):
    box('belt_pouch_'+str(x),'body',[x,10.35,-2.9],[1.75,2,1.1],'leather',B+L)
    box('pouch_flap_'+str(x),'body',[x,10.3,-4.03],[1.75,.7,.15],'brass',B+L)
box('sample_pod','body',[-4.6,5.9,-.2],[.8,2.7,1.5],'metal',B)
box('sample_core','body',[-4.78,6.35,0],[.2,1.7,1.1],'cyan',B,glow=True)
# Independent leg panels follow the native leg bones; no rigid skirt bridging the legs.
for side in ('left','right'):
    leg=side+'_leg'
    for z in (-2.65,2.25):
        box(side+'_coat_panel_'+str(z),leg,[-1.82,.15,z],[3.64,7.55,.4],'panel',L+('WEARING_ELYTRA',))
        box(side+'_coat_hem_'+str(z),leg,[-1.82,7.2,z-.02],[3.64,.55,.44],'steel',L+('WEARING_ELYTRA',))
    outer=1.92 if side=='left' else -2.32
    box(side+'_outer_coat',leg,[outer,.15,-2.2],[.4,7.15,4.4],'coat',L+('WEARING_ELYTRA',))
    box(side+'_boot',leg,[-2.16,8.7,-2.4],[4.32,3.3,4.65],'black',F)
    box(side+'_boot_toecap',leg,[-2.16,10.35,-2.8],[4.32,1.05,.5],'metal',F)
for side in ('right','left'):
    arm=side+'_arm';x=-3.2 if side=='right' else -1.2
    for y in (1.4,6.7):box(side+'_sleeve_band_'+str(y),arm,[x,y,-2.18],[4.4,.65,4.36],'steel',B)
    box(side+'_glove',arm,[x,8,-2.18],[4.4,2,4.36],'dark',B)
box('wrist_device','right_arm',[-3.25,3.1,-2.65],[4.1,2.8,.75],'metal',B)
box('wrist_screen','right_arm',[-2.7,3.65,-3.43],[3,1.5,.12],'cyan',B,glow=True)
box('sleeve_badge','left_arm',[-.9,.1,-2.2],[3.4,1.05,.25],'brass',B)

# S03 restores the concept's tailoring, shoulder layering and asymmetric hardware.
for p in list(parts):
    if p['name'].startswith('diagonal_harness_'):
        parts.remove(p);roots[p['bone']].remove(p)
    if p['name']=='chest_seal':p['offset']=[.65,1.1,-2.43];p['size']=[2.85,3.25,.22]
    if '_coat_panel_' in p['name']:p['rotation'][0]=-3 if p['offset'][2]<0 else 3
    if '_coat_hem_' in p['name']:p['rotation'][0]=-3 if p['offset'][2]<0 else 3
    if p['name']=='visor_glass':p['size'][1]=.48
    if p['name']=='wrist_screen':p['size']=[2.5,1.6,.12]
box('inner_shirt','body',[-.75,1,-2.35],[1.5,8.7,.2],'lining',B)
for i in range(4):box('shirt_button_'+str(i),'body',[-.13,3+i*1.45,-2.6],[.26,.26,.18],'metal',B)
strap=box('shoulder_harness','body',[-.425,0,0],[.85,10.5,.28],'leather',B,rot=(0,0,-36))
strap['pivot']=[-3.4,.2,-2.82]
box('harness_buckle','body',[-.45,4.2,-3.14],[1.35,1.25,.22],'brass',B)
box('harness_buckle_hole','body',[-.1,4.46,-3.39],[.65,.68,.1],'dark',B)
for side in ('left','right'):
    arm=side+'_arm';x=-3.25 if side=='right' else -1.25
    box(side+'_tailored_shoulder',arm,[x,-1.9,-2.4],[4.5,1.7,4.8],'panel',B)
    box(side+'_shoulder_stitch',arm,[x,-1.9,-2.65],[4.5,.35,.3],'metal',B)
for i in range(3):box('wrist_indicator_'+str(i),'right_arm',[-2.4,3.9+i*.4,-3.58],[1.7,.15,.06],'dark',B)
for x in (-3.65,1.9):
    box('pouch_clasp_'+str(x),'body',[x+.6,11,-4.19],[.55,.65,.12],'dark',B+L)
for side,x in [('right',-3.3),('left',2.1)]:
    for i in range(3):box(side+'_filter_slot_'+str(i),'head',[x+.2,-2.7+i*.3,-5.7],[.75,.12,.08],'metal',H)
box('mask_lower_rim','head',[-1.8,-.72,-5.9],[3.6,.3,.45],'panel',H)

atlas=Image.new('RGBA',(256,256),(0,0,0,0));cursor=[1,1,0];rectangles={};tile_cache={}
def tile(w,h,role,label=None):
    w=max(1,w);h=max(1,h)
    key=(w,h,role,label)
    if key in tile_cache:return dict(tile_cache[key])
    if cursor[0]+w+1>256:cursor[0]=1;cursor[1]+=cursor[2]+2;cursor[2]=0
    assert cursor[1]+h+1<=256,'atlas overflow'
    x,y=cursor[:2];cursor[0]+=w+2;cursor[2]=max(cursor[2],h)
    patch=Image.new('RGBA',(w,h),P[role]);d=ImageDraw.Draw(patch)
    # Deliberate low-frequency textile seams, not random speckle.
    rgb=tuple(int(P[role][i:i+2],16) for i in (1,3,5))
    hi=tuple(min(255,c+7) for c in rgb);lo=tuple(max(0,c-5) for c in rgb)
    if role in ('coat','panel','leather'):
        for yy in range(0,h,3):
            for xx in range(0,w,3):
                delta=(-7,-2,4,9)[((xx//3)*3+(yy//3)*7)%4]
                d.rectangle((xx,yy,min(xx+2,w-1),min(yy+2,h-1)),fill=tuple(max(0,min(255,c+delta)) for c in rgb))
    if w>3 and h>3:
        d.line((0,0,w-1,0),fill=hi);d.line((0,0,0,h-1),fill=hi);d.line((w-1,1,w-1,h-1),fill=lo)
        if role in ('coat','panel','leather'):
            for yy in range(3,h-1,5):d.line((1,yy,min(2,w-2),yy),fill=hi)
    if label=='star':
        cx=(w-1)/2;r=min(w,h)*.38
        pts=[(cx+(math.sin(a*math.pi/3))*r,(h-1)/2-math.cos(a*math.pi/3)*r) for a in range(6)]
        d.line([pts[i] for i in (0,2,4,0)],fill=P['light'],width=1)
        d.line([pts[i] for i in (1,3,5,1)],fill=P['light'],width=1)
    if label=='hebrew':
        # Draw each actual Hebrew glyph independently, explicitly from right to left.
        font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',max(8,int(h*.85)))
        widths=[d.textlength(c,font=font) for c in 'ארז'];total=sum(widths)+2
        xx=(w+total)/2
        for c,cw in zip('ארז',widths):
            xx-=cw;d.text((xx,(h-font.getbbox(c)[3])/2),c,font=font,fill=P['light']);xx-=1
    if label=='under_mask':
        # Flush face survives helmet clearance: no empty face in open-face helmets.
        d.rectangle((1,4,w-2,8),fill=P['black'])
        d.rectangle((3,6,w-4,6),fill=P['cyan'])
        d.rectangle((5,9,10,14),fill=P['metal'])
        d.rectangle((6,10,9,14),fill=P['dark'])
        d.rectangle((2,10,3,12),fill=P['brass']);d.rectangle((12,10,13,12),fill=P['brass'])
    atlas.paste(patch,(x,y));r={'sx':x,'sy':y,'ex':x+w,'ey':y+h,'rot':'0','autoUV':False};tile_cache[key]=r;return dict(r)

elements=[]
for bone,children in roots.items():
    el={'id':bone,'name':bone,'show':False,'pos':{'x':0,'y':0,'z':0},'rotation':{'x':0,'y':0,'z':0},'children':[]}
    for p in children:
        w,h,d=p['size'];uv={}
        for face,ww,hh in [('north',w,h),('south',w,h),('west',d,h),('east',d,h),('up',w,d),('down',w,d)]:
            label=p.get('label') if (face=='south' and p.get('label')=='hebrew') or (face=='north' and p.get('label') in ('star','under_mask')) else None
            density=8 if label=='star' else 4 if label=='hebrew' else 2
            uv[face]=tile(math.ceil(ww*density),math.ceil(hh*density),p['role'],label)
        p['uv']=uv
        vec=lambda v:dict(zip(('x','y','z'),v))
        el['children'].append({'name':p['name'],'storeID':p['id'],'show':True,'texture':True,'textureSize':1,
          'offset':vec(p['offset']),'pos':vec(p.get('pivot',[0,0,0])),'rotation':vec(p['rotation']),'size':vec(p['size']),
          'rscale':vec([1,1,1]),'scale':vec([1,1,1]),'u':0,'v':0,'color':'ffffff','mirror':False,'mcScale':0,
          'glow':p['glow'],'recolor':False,'hidden':False,'faceUV':uv})
    elements.append(el)
config={'version':1,'skinType':'default','hideHeadIfSkull':True,'removeArmorOffset':False,'elements':elements,
        'skinSize':{'x':256,'y':256},'textures':{'skin':{'customGridSize':False,'anim':[]}}}
animations={}
for pose in sorted({a for p in parts for a in p['hide_poses']}):
    comps=[{'storeID':p['id'],'pos':vec([0,0,0]),'rotation':vec([0,0,0]),'color':'ffffff','show':False,'scale':vec([1,1,1])} for p in parts if pose in p['hide_poses']]
    animations['v_'+pose.lower()+'_clearance.json']={'name':'Equipment clearance '+pose,'additive':True,'duration':1000,'priority':20,'loop':True,'interpolator':'linear','frames':[{'components':comps}]}
author={'revision':'EREZ-ENGINEER-S03','units':'Minecraft model units, 16 per block','parts':parts,'palette':P,'config':config,'animations':animations,
        'note':'Original pixel textures and geometry authored for this explicitly requested personal avatar; no third-party skin used.'}
(ROOT/'source/avatar_S03.json').write_text(json.dumps(author,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
png=io.BytesIO();atlas.save(png,format='PNG');(ROOT/'source/avatar_atlas_S03.png').write_bytes(png.getvalue())
with zipfile.ZipFile(ROOT/'exports/EREZ_Rift_Engineer_S03.cpmproject','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('config.json',json.dumps(config));z.writestr('skin.png',png.getvalue())
    for name,a in animations.items():z.writestr('animations/'+name,json.dumps(a))
report={'parts':len(parts),'quads_upper_bound':len(parts)*6,'atlas_size':[256,256],'atlas_used_height':cursor[1]+cursor[2],
        'equipment_pose_rules':list(animations),'geometry_sha256':hashlib.sha256((ROOT/'source/avatar_S03.json').read_bytes()).hexdigest(),'native_verified':False}
(ROOT/'reports/model_build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
