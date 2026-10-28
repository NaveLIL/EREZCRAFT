"""Original voxel geometry using READ-ONLY existing CC BY texture texels.
--install updates only Interstice's riftsteel_sword model; no game PNG changes.
"""
import argparse
from collections import deque
import hashlib
import json
import math
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def build(install=False):
    config_path=ROOT/'source/design.json'
    cfg=json.loads(config_path.read_text(encoding='utf-8-sig'))
    resources=Path(cfg['destination_project'])/'src/main/resources'
    tex=resources/'assets/interstice/textures/item/armaments/tool_greatsword_steel.png'
    assert sha(tex)==cfg['texture_sha256'], 'Existing texture changed; inspect before rebuilding'
    original=Image.open(tex).convert('RGBA')
    colors={key:original.getpixel(tuple(xy)) for key,xy in cfg['source_texels'].items()}
    rows=cfg['rows']; assert len({len(row) for row in rows})==1
    cells={(x,y):c for y,row in enumerate(rows) for x,c in enumerate(row) if c!='.'}
    assert set(cells.values())<=colors.keys()
    visited={next(iter(cells))}; queue=deque(visited)
    while queue:
        x,y=queue.popleft()
        for q in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
            if q in cells and q not in visited: visited.add(q); queue.append(q)
    assert len(visited)==len(cells), 'Detached geometry'
    elements=[]; transformed=[]; angle=math.radians(cfg['rotation_z_degrees'])
    for (x,y),c in cells.items():
        a=[cfg['x_offset']+x,cfg['y_top']-y-1,8-cfg['thickness']/2]
        b=[a[0]+1,a[1]+1,8+cfg['thickness']/2]
        assert all(-16<=value<=32 for value in a+b)
        u,v=cfg['source_texels'][c]
        uv=[(u+.25)*16/original.width,(v+.25)*16/original.height,
            (u+.75)*16/original.width,(v+.75)*16/original.height]
        sides={'north':True,'south':True,'west':(x-1,y) not in cells,
               'east':(x+1,y) not in cells,'up':(x,y-1) not in cells,'down':(x,y+1) not in cells}
        faces={s:{'uv':uv,'texture':'#layer0'} for s,on in sides.items() if on}
        elements.append({'name':f'pixel_{x}_{y}_{c}','from':a,'to':b,
                         'rotation':{'origin':cfg['origin'],'axis':'z','angle':cfg['rotation_z_degrees'],'rescale':False},
                         'shade':True,'faces':faces})
        for px in (a[0],b[0]):
            for py in (a[1],b[1]):
                transformed.append([8+(px-8)*math.cos(angle)-(py-8)*math.sin(angle),
                                    8+(px-8)*math.sin(angle)+(py-8)*math.cos(angle)])
    low=[min(p[i] for p in transformed) for i in range(2)]
    high=[max(p[i] for p in transformed) for i in range(2)]
    scale=14/max(high[i]-low[i] for i in range(2))
    offset=[round(-(low[i]+high[i]-16)*scale/2,5) for i in range(2)]
    model={'credit':'Original geometry: Dimon / EREZ. Texture: Malcolm Riley, CC BY 4.0; see provenance/armaments.json.',
           'parent':'minecraft:item/handheld','gui_light':'front',
           'textures':{'layer0':cfg['texture'],'particle':cfg['texture']},
           'display':{'gui':{'rotation':[0,0,0],'translation':offset+[0],'scale':[scale]*3}},'elements':elements}
    output=ROOT/'exports/assets/interstice/models/item/riftsteel_sword.json'; write(output,model)
    sprite=Image.new('RGBA',(len(rows[0]),len(rows)))
    for xy,c in cells.items(): sprite.putpixel(xy,colors[c])
    previews=ROOT/'preview'; previews.mkdir(exist_ok=True)
    sprite.resize((len(rows[0])*20,len(rows)*20),Image.Resampling.NEAREST).save(previews/'sword_front.png')
    card=Image.new('RGB',(1100,760),'#17232b'); draw=ImageDraw.Draw(card)
    font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',27)
    small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',19)
    draw.text((38,26),'MEZHDUMORYE / RIFTSTEEL LONGSWORD',font=font,fill='#dfe6e5')
    draw.text((38,67),'LS-01  |  Original silhouette, existing game item',font=small,fill='#9dafb7')
    art=sprite.resize((220,600),Image.Resampling.NEAREST); card.paste(art,(215,112),art)
    diagonal=sprite.rotate(-45,resample=Image.Resampling.NEAREST,expand=True)
    diagonal=diagonal.resize((diagonal.width*14,diagonal.height*14),Image.Resampling.NEAREST)
    card.paste(diagonal,(490,140),diagonal)
    draw.text((125,715),'FRONT / one voxel thick',font=small,fill='#a9b7bd')
    draw.text((606,715),'DIAGONAL SILHOUETTE',font=small,fill='#a9b7bd')
    card.save(previews/'sword_concept.png')
    pack=ROOT/'exports/Mezhdumorye_Riftsteel_LS01_Preview_1.21.1.zip'
    with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':34,'description':'Mezhdumorye: Riftsteel longsword LS-01 preview (requires Interstice)'}}))
        z.write(output,'assets/interstice/models/item/riftsteel_sword.json')
        z.writestr('README.txt','Requires the existing Interstice mod for Minecraft 1.21.1. Changes only the visual model of interstice:riftsteel_sword. No vanilla replacement, no new item or texture. Disable to restore the mod model.\n')
    destination=resources/'assets/interstice/models/item/riftsteel_sword.json'
    if install:
        old=ROOT/'source/baseline/riftsteel_sword.json'
        if not old.exists(): old.parent.mkdir(parents=True,exist_ok=True); old.write_bytes(destination.read_bytes())
        destination.write_bytes(output.read_bytes())
    report={'revision':cfg['revision'],'item_id':cfg['item_id'],'geometry_units':'1/16 Minecraft block',
            'cells':len(cells),'connected':len(visited)==len(cells),'thickness':cfg['thickness'],
            'length_model_units':max(y for x,y in cells)-min(y for x,y in cells)+1,
            'texture_unchanged':sha(tex)==cfg['texture_sha256'],'texture_sha256':sha(tex),
            'rotated_xy_bounds':[low,high],'gui_scale':scale,
            'inputs':{str(p):sha(p) for p in (config_path,Path(__file__),tex)},
            'outputs':{str(p.relative_to(ROOT)):sha(p) for p in (output,pack,previews/'sword_concept.png')},
            'installed':install,'destination':str(destination),'game_runtime_verified':False,
            'scope':'Geometry only; no published pack, Java registration, recipes, stats, saves or textures changed.'}
    write(ROOT/'reports/assets.json',report)
    print(json.dumps({k:report[k] for k in ('item_id','cells','length_model_units','texture_unchanged','installed')}))
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true')
    build(parser.parse_args().install)
