"""CC0 coastal/mineral veneers: palette only, with native layer model geometry."""
from pathlib import Path
from PIL import Image
import json,zipfile,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'art/sources/cc0/realm-surfaces'
ASSETS=ROOT/'src/main/resources/assets/interstice'
DATA=ROOT/'src/main/resources/data/interstice'
def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
if not (SRC/'sources.json').exists():
    SRC.mkdir(parents=True,exist_ok=True);arc=ROOT/'research/cc0-20261008/blocks_2.zip';z=zipfile.ZipFile(arc)
    rows=[]
    for file in ('sand_ugly_2.png','snow.png','gravel.png'):
        (SRC/file).write_bytes(z.read('blocks/'+file));rows.append({'file':file,'sha256':sha(SRC/file),'archive_sha256':sha(arc),'source_url':'https://opengameart.org/content/16x16-block-texture-set','author':'ARoachIFoundOnMyPillow','license':'CC0-1.0'})
    write(SRC/'sources.json',{'retrieved':'2026-10-08','sources':rows})
    shutil.copyfile(ROOT/'art/sources/cc0/block-texture-set/CC0-1.0.html',SRC/'CC0-1.0.html')
sources={s['file']:s for s in json.loads((SRC/'sources.json').read_text())['sources']};outputs=[]
for name,file,color in [('toxic_sand','sand_ugly_2.png',(139,87,83)),('mineral_frost','snow.png',(178,186,194)),('mineral_powder','gravel.png',(164,151,167))]:
    assert sha(SRC/file)==sources[file]['sha256'];image=Image.open(SRC/file).convert('RGBA');assert image.size==(16,16)
    pixels=[image.getpixel((x,y)) for y in range(16) for x in range(16)];colors=dict.fromkeys(pixels);used=set()
    luma=lambda p:p[0]*.2126+p[1]*.7152+p[2]*.0722
    mean=sum(luma(p) for p in pixels if p[3])/sum(bool(p[3]) for p in pixels)
    for p in colors:
        light=.35+.65*luma(p)/mean
        q=tuple(max(0,min(255,round(v*light))) for v in color)+(p[3],) if p[3] else p
        while q in used:q=q[:2]+((q[2]+1)%256,)+q[3:]
        colors[p]=q;used.add(q)
    result=Image.new('RGBA',image.size);mapped=[colors[p] for p in pixels];result.putdata(mapped)
    assert all(a[3]==b[3] for a,b in zip(pixels,mapped)) and len(set(pixels))==len(set(mapped))
    out=ASSETS/f'textures/block/minerals/{name}.png';out.parent.mkdir(parents=True,exist_ok=True);result.save(out)
    outputs.append({'output':f'block/minerals/{name}.png','source':file,**sources[file],'output_sha256':sha(out)})
    if name!='mineral_frost':
        write(ASSETS/f'blockstates/{name}.json',{'variants':{'':{'model':f'interstice:block/minerals/{name}'}}})
        write(ASSETS/f'models/block/minerals/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'interstice:block/minerals/{name}'}})
    write(ASSETS/f'models/item/{name}.json',{'parent':f'interstice:block/minerals/{name}'})
    write(DATA/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'interstice:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
vanilla=zipfile.ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar')
state=json.loads(vanilla.read('assets/minecraft/blockstates/snow.json'))
for key,variant in state['variants'].items():
    model=variant['model'];obj=json.loads(vanilla.read('assets/minecraft/models/'+model.split(':')[1]+'.json'))
    obj['textures']={k:'interstice:block/minerals/mineral_frost' for k in obj.get('textures',{})}
    stem=model.split('/')[-1];variant['model']='interstice:block/minerals/mineral_frost_'+stem
    write(ASSETS/f'models/block/minerals/mineral_frost_{stem}.json',obj)
write(ASSETS/'blockstates/mineral_frost.json',state)
write(ASSETS/'models/block/minerals/mineral_frost.json',{'parent':'interstice:block/minerals/mineral_frost_snow_height2'})
write(ASSETS/'provenance/realm-surfaces.json',{'generator':'tools/generate_surface_resources.py','operation':'Bijective colour palette substitution only; original coordinates,16x16 resolution and alpha preserved','pattern_and_alpha_verified':True,'outputs':outputs})
tag=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/shovel.json'
obj=json.loads(tag.read_text(encoding='utf-8')) if tag.exists() else {'replace':False,'values':[]}
obj['values']=list(dict.fromkeys(obj['values']+['interstice:'+n for n in ('toxic_sand','mineral_frost','mineral_powder')]))
write(tag,obj)
for locale,names in [('ru_ru',{'toxic_sand':'Токсичный песок','mineral_frost':'Минеральная изморось','mineral_powder':'Рыхлая минеральная пыль'}),('en_us',{'toxic_sand':'Toxic Sand','mineral_frost':'Mineral Frost','mineral_powder':'Loose Mineral Dust'})]:
    path=ASSETS/f'lang/{locale}.json';obj=json.loads(path.read_text(encoding='utf-8'))
    obj.update({'block.interstice.'+key:value for key,value in names.items()});write(path,obj)
print('REALM_SURFACES: three unused ready CC0 patterns, palette/alpha verified; native layer geometry reused.')
