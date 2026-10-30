"""Package actual Blender renders, preserve the original concept, audit PNG palettes."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib,math
ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
source=json.loads((ROOT/'source/skat_SK02.json').read_text())
native=json.loads((ROOT/'reports/SAVED_BLEND_SK02.json').read_text())
glb=json.loads((ROOT/'reports/GLB_IMPORT_SK02.json').read_text())
assert native['source_parts_matched']==len(source['parts']) and glb['mesh_count']==len(source['parts'])
palette_checks=[]
for record in json.loads((ROOT/'source/ASSET_PROVENANCE_SK02.json').read_text()):
    original=ROOT/record['saved_original'];derived=ROOT/record['derived_file']
    assert sha(original)==record['source_sha256'];assert sha(derived)==record['derived_sha256']
    a=Image.open(original).convert('RGBA');b=Image.open(derived).convert('RGBA')
    assert a.size==b.size==(16,16);assert a.getchannel('A').tobytes()==b.getchannel('A').tobytes()
    lut={}
    for old,new in zip(a.getdata(),b.getdata()):
        if old[3]:
            if old[:3] in lut:assert lut[old[:3]]==new[:3]
            lut[old[:3]]=new[:3]
    assert len(set(lut.values()))==len(lut)
    palette_checks.append({'name':record['name'],'source_hash':True,'palette_only':True,'bijective':True,'alpha_and_size_preserved':True})
fontpath='C:/Windows/Fonts/arial.ttf';regular=ImageFont.truetype(fontpath,26);title=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',36)
small=ImageFont.truetype(fontpath,20)
canvas=Image.new('RGB',(2000,1570),(18,24,27));draw=ImageDraw.Draw(canvas)
draw.text((32,20),'СКАТ / SK02 — ЭКСПЕДИЦИОННЫЙ КАТАМАРАН',font=title,fill=(228,231,215))
draw.text((32,67),'Рендеры одной редактируемой 3D-модели Blender • около 26 × 11 блоков',font=regular,fill=(151,181,176))
panels=[('hero','Общий вид'),('aft','Корма и воздушные движители'),('cutaway','Разрез: трюм под палубой'),('hold','24 грузовых модуля и кормовая лестница')]
for i,(view,label) in enumerate(panels):
    x=22+(i%2)*990;y=116+(i//2)*690
    im=Image.open(ROOT/f'preview/skat_SK02_{view}.png').convert('RGB');im.thumbnail((968,637),Image.Resampling.LANCZOS)
    canvas.paste(im,(x+(968-im.width)//2,y));draw.text((x+10,y+644),label,font=regular,fill=(228,231,215))
draw.text((32,1507),'1296 слотов — проектная ёмкость. Плавучесть и игровые инвентари ещё не реализованы.',font=small,fill=(194,202,193))
canvas.save(ROOT/'preview/skat_SK02_review.jpg',quality=93)
# Comparison uses the unedited concept image and actual model render, with clear labels.
comparison=Image.new('RGB',(2000,840),(18,24,27));d=ImageDraw.Draw(comparison)
for i,(file,label) in enumerate([('references/skat_concept_SK01.png','Первоначальный концепт / imagegen'),('preview/skat_SK02_hero.png','Текущая 3D-модель / Blender')]):
    im=Image.open(ROOT/file).convert('RGB');im.thumbnail((970,730),Image.Resampling.LANCZOS)
    comparison.paste(im,(15+i*1000+(970-im.width)//2,75+(730-im.height)//2));d.text((25+i*1000,22),label,font=regular,fill=(228,231,215))
comparison.save(ROOT/'preview/skat_SK02_reference_comparison.jpg',quality=93)
variants=Image.new('RGB',(2000,850),(18,24,27));d=ImageDraw.Draw(variants)
for i,(rev,label) in enumerate([('SK01','Маленький SK01 / 15.2 × 8.8 блока'),('SK02','Большой SK02 / 25.7 × 11.1 блока')]):
    im=Image.open(ROOT/f'preview/skat_{rev}_hero.png').convert('RGB');im.thumbnail((970,730),Image.Resampling.LANCZOS)
    variants.paste(im,(15+i*1000+(970-im.width)//2,75+(730-im.height)//2));d.text((25+i*1000,22),label,font=regular,fill=(228,231,215))
d.text((25,812),'Обе версии сохранены целиком. Ракурсы вписаны независимо; масштаб изображений различается.',font=small,fill=(170,189,182))
variants.save(ROOT/'preview/skat_small_and_large.jpg',quality=93)
paths=['source/skat_SK02.json','assembly/skat_SK02.blend','exports/skat_SK02.glb','references/skat_concept_SK01.png','references/imagegen_prompt.txt','README.md','INTEGRATION.md']
paths += [f'preview/skat_SK02_{view}.png' for view in ['hero','aft','cutaway','hold','cabin','top','side']]
paths += ['preview/skat_SK02_review.jpg','preview/skat_SK02_reference_comparison.jpg','reports/SAVED_BLEND_SK02.json','reports/GLB_IMPORT_SK02.json']
paths += ['preview/skat_small_and_large.jpg','source/selection.json','preview/preserved/small_SK01_as_seen.png','preview/preserved/large_SK02_as_seen.png']
report={'revision':'SK02','active_source':'source/skat_SK02.json','preserved_variants':['SK01','SK02'],'parts':len(source['parts']),
    'cargo_modules':24,'cargo_capacity_target_slots':1296,'capacity_implemented':False,'palette_checks':palette_checks,'both_variants_preserved':True,
    'saved_scene_verified':True,'self_contained_glb_verified':True,'native_game_verified':False,'physics_verified':False,
    'imagegen_mode':'built-in','prompt_file':'references/imagegen_prompt.txt',
    'artifacts':[{'file':name,'bytes':(ROOT/name).stat().st_size,'sha256':sha(ROOT/name)} for name in paths]}
(ROOT/'reports/SK02_ARTIFACTS.json').write_text(json.dumps(report,indent=2)+'\n')
small_files=['source/skat_SK01.json','assembly/skat_SK01.blend','exports/skat_SK01.glb']+[f'preview/skat_SK01_{v}.png' for v in ['hero','aft','cutaway','hold','cabin','top','side']]
preserved={'owner_request':'Preserve small SK01 and large SK02','small_variant':[{'file':name,'sha256':sha(ROOT/name),'bytes':(ROOT/name).stat().st_size} for name in small_files],
    'as_seen_snapshots':[{'file':name,'sha256':sha(ROOT/name)} for name in ['preview/preserved/small_SK01_as_seen.png','preview/preserved/large_SK02_as_seen.png']],
    'large_report':'reports/SK02_ARTIFACTS.json','both_variants_exist':True}
small_check=json.loads((ROOT/'reports/PRESERVED_SK01_CHECK.json').read_text());assert small_check['saved_scene_reopened'] and small_check['self_contained_glb_reimported']
preserved['small_reopen_check']='reports/PRESERVED_SK01_CHECK.json'
(ROOT/'reports/BOTH_VARIANTS_PRESERVED.json').write_text(json.dumps(preserved,indent=2)+'\n')
print(json.dumps({'revision':'SK02','parts':len(source['parts']),'palettes_verified':len(palette_checks),'artifacts_hashed':len(paths),'physics_verified':False}))
