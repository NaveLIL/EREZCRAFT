from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib,base64,copy
ROOT=Path(__file__).resolve().parents[1]
canvas=Image.new('RGB',(1800,1620),(25,29,35));draw=ImageDraw.Draw(canvas)
title=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',35)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',30)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',23)
draw.text((30,18),'Spitter / SP02 → SP03 / проба более естественной поверхности',font=title,fill=(234,237,240))
draw.text((30,65),'Одинаковые геометрия, поза, свет и камера. Изменены контраст палитры и матовость материала.',font=small,fill=(180,194,201))
tiles=[('SP02 — прежний рисунок','spitter_original_SP03_quarter.png'),
       ('SP03 — мягкая поверхность','spitter_SP03_quarter.png'),
       ('SP02 — спереди','spitter_original_SP03_front.png'),
       ('SP03 — спереди','spitter_SP03_front.png')]
for i,(label,filename) in enumerate(tiles):
    x=i%2*900;y=115+i//2*750
    draw.text((x+30,y),label,font=font,fill=(232,235,239))
    picture=Image.open(ROOT/'preview'/filename).convert('RGB').resize((840,630),Image.Resampling.LANCZOS)
    canvas.paste(picture,(x+30,y+48))
review=ROOT/'preview/spitter_SP03_review.jpg';canvas.save(review,quality=94)
before=json.loads((ROOT/'source/spitter_SP02.bbmodel').read_text(encoding='utf8'))
after=json.loads((ROOT/'source/spitter_SP03.bbmodel').read_text(encoding='utf8'))
def geometry(d):
    d=copy.deepcopy(d)
    for key in ['name','textures','erezcraft_material_roles']:d.pop(key,None)
    return d
assert geometry(before)==geometry(after)
for old,new in zip(before['textures'],after['textures']):
    old_path=ROOT/'source'/old['name'];new_path=ROOT/'source'/new['name']
    a=Image.open(old_path).convert('RGBA');b=Image.open(new_path).convert('RGBA')
    assert a.size==b.size==(128,128) and a.getchannel('A').tobytes()==b.getchannel('A').tobytes()
    assert base64.b64decode(new['source'].split(',',1)[1])==new_path.read_bytes()
files={}
for rel in ['source/spitter_SP03.bbmodel','assembly/spitter_SP03.blend','exports/spitter_SP03.glb','preview/spitter_SP03_review.jpg']:
    p=ROOT/rel;assert p.is_file() and p.stat().st_size>100
    files[rel]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
report={'revision':'SP03','baseline':'SP02','geometry_uv_rig_and_animations_identical':True,
        'all_texture_dimensions_and_alpha_preserved':True,'embedded_textures_match':True,
        'accepted_colour_variant':'SP02','current_trial':'SP03','owner_accepted_trial':False,
        'studio_review_only':True,'game_verified':False,'files':files}
(ROOT/'reports/SURFACE_SP03.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({'review':str(review),'geometry_and_alpha_checked':True,'game_verified':False}))
