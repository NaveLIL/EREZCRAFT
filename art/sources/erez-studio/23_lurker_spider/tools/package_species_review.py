from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,copy,hashlib,base64
ROOT=Path(__file__).resolve().parents[1]
variants=[('SK01','skirmisher'),('SP01','spitter'),('SP02','spitter')]
source=json.loads((ROOT/'source/lurker_LS03.bbmodel').read_text(encoding='utf8'))
atlas=Image.open(ROOT/'source/cave_rift_spider_lurker.png').convert('RGBA')
def geometry(d):
    d=copy.deepcopy(d)
    for key in ['name','textures','erezcraft_material_roles']:d.pop(key,None)
    for e in d['elements']:
        for face in e['faces'].values():
            if face.get('texture') is not None:face['texture']=0
    return d
checks=[]
for rev,role in variants:
    stem=f'{role}_{rev}';d=json.loads((ROOT/f'source/{stem}.bbmodel').read_text(encoding='utf8'))
    assert geometry(d)==geometry(source)
    for texture in d['textures']:
        p=ROOT/'source'/texture['name'];image=Image.open(p).convert('RGBA')
        assert image.size==atlas.size and image.getchannel('A').tobytes()==atlas.getchannel('A').tobytes()
        assert base64.b64decode(texture['source'].split(',',1)[1])==p.read_bytes()
    files={}
    for rel in [f'source/{stem}.bbmodel',f'assembly/{stem}.blend',f'exports/{stem}.glb',f'preview/{stem}_quarter.png',f'preview/{stem}_front.png']:
        p=ROOT/rel;assert p.is_file() and p.stat().st_size>100
        files[rel]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
    render=json.loads((ROOT/f'reports/blender_{rev}.json').read_text())
    checks.append({'revision':rev,'role':role,'variant_scale':render['minecraft_scale'],'geometry_identical_to_LS03':True,
                   'eye_positions_unchanged':True,'uv_unchanged':True,'animations_unchanged':True,'all_texture_alpha_and_dimensions_preserved':True,
                   'embedded_texture_bytes_match':True,'files':files,'game_verified':False})

canvas=Image.new('RGB',(1800,1640),(25,29,35));draw=ImageDraw.Draw(canvas)
title=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',36)
label=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',29)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
draw.text((30,18),'EREZCRAFT / реальные птицееды — цветовые пробы',font=title,fill=(234,237,240))
draw.text((30,65),'Ракурсы вписаны отдельно; игровые масштабы указаны в подписях. Blender-прототипы.',font=small,fill=(175,189,199))
tiles=[
 ('Lurker — принятый большой / 0,85','LS04 • медные колени, тёмное тело','lurker_LS04_quarter.png'),
 ('Skirmisher — «Бешеный апельсин» / 0,60','SK01 • Pterinochilus murinus','skirmisher_SK01_quarter.png'),
 ('Spitter A — оливково-лаймовый / 0,55','SP01 • Poecilotheria rufilata, усиленная палитра','spitter_SP01_quarter.png'),
 ('Spitter B — изумруд и синие челюсти / 0,55','SP02 • Ephebopus cyanognathus, усиленная палитра','spitter_SP02_quarter.png')]
for i,(heading,latin,file) in enumerate(tiles):
    x=i%2*900;y=115+i//2*750
    draw.text((x+30,y),heading,font=label,fill=(230,234,238))
    draw.text((x+30,y+39),latin,font=small,fill=(180,194,201))
    image=Image.open(ROOT/'preview'/file).convert('RGB').resize((840,630),Image.Resampling.LANCZOS)
    canvas.paste(image,(x+30,y+77))
path=ROOT/'preview/species_palette_review.jpg';canvas.save(path,quality=94)
references=[
 {'species':'Pterinochilus murinus','used_for':'SK01 / Skirmisher','interpretation':'Orange colour form, existing pixel artwork recoloured; not a biological anatomy reconstruction',
  'sources':['https://felicina.ru/news/beshenyj-apelsin-zagadochnyj-frukt-ili-neobychnoe-zhivotnoe/','https://senecaparkzoo.org/mombasa-golden-starburst-tarantula/']},
 {'species':'Poecilotheria rufilata','used_for':'SP01 / Spitter','interpretation':'Olive-green/yellow body, dark markings and rusty setae; lime saturation deliberately intensified for Interstice',
  'sources':['https://www.giantspiders.com/species/poecilotheria-spp/','https://www.thedefiantforest.com/blogs/news/giant-red-slate-tarantula-care-and-species-guide-poecilotheria-rufilata']},
 {'species':'Ephebopus cyanognathus','used_for':'SP02 / Spitter','interpretation':'Blue-violet chelicerae, yellow limb bands, olive/emerald family of colours; deliberately brighter fantasy interpretation, not an exact adult or juvenile specimen',
  'sources':['https://www.urbantarantulas.com/products/ephebopus-cyanognathus-blue-fang','https://exoticsunlimitedusa.com/products/ephebopus-cyanognathus-blue-fang-tarantula']}
]
(ROOT/'references/species_sources.json').write_text(json.dumps({'checked_on':'2026-10-28','reference_only':True,'downloaded_photographs_used_as_textures':False,'species':references},indent=2)+'\n',encoding='utf8')
report={'review':str(path),'review_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'variants':checks,'reference_only_species':True,'game_verified':False}
(ROOT/'reports/SPECIES_PALETTES.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({'review':str(path),'variants':[c['revision'] for c in checks],'geometry_alpha_uv_animations_checked':True,'game_verified':False}))
