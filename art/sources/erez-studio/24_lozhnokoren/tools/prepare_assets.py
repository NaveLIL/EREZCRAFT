from pathlib import Path
from PIL import Image
import json,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT')
for directory in ['source','source/textures','source/originals','assembly','preview','reports','exports','references']:(ROOT/directory).mkdir(parents=True,exist_ok=True)
concept=Path('C:/Users/Dimon/.codex/generated_images/01a18166-1151-7823-b315-5c455d286cdd/exec-2059f00a-b07d-467f-9a61-5b4ba4ac2b00.png')
shutil.copyfile(concept,ROOT/'references/lozhnokoren_concept_LK01.png')
specs=[
 ('bark','crown-tree/maple_log_side.png','crown-tree/sources.json',(42,34,25),(88,73,50)),
 ('dark_bark','crown-tree/maple_log_side.png','crown-tree/sources.json',(28,24,20),(65,54,37)),
 ('joints','pale-gardens/beech_log_side.png','pale-gardens/sources.json',(33,19,22),(65,34,31)),
 ('cut_wood','crown-tree/maple_log_top.png','crown-tree/sources.json',(94,74,45),(151,126,84)),
 ('moss','pale-gardens/grass_top.png','pale-gardens/sources.json',(44,49,21),(113,119,48)),
 ('horn','pale-gardens/beech_log_side.png','pale-gardens/sources.json',(18,18,17),(64,57,41)),
 ('eyes','crown-tree/maple_log_top.png','crown-tree/sources.json',(90,52,13),(210,157,53)),
]
provenance=[];textures=[]
for name,relative,metadata,low,high in specs:
    original=GAME/'art/sources/cc0'/relative
    data=json.loads((GAME/'art/sources/cc0'/metadata).read_text())
    record=data['files'].get(Path(relative).name,data['files'].get(Path(relative).stem))
    expected=record['sha256'] if isinstance(record,dict) else record
    digest=hashlib.sha256(original.read_bytes()).hexdigest();assert digest==expected,(relative,digest,expected)
    saved=ROOT/'source/originals'/original.name
    if not saved.exists():shutil.copyfile(original,saved)
    image=Image.open(original).convert('RGBA');colors={p[:3] for p in image.getdata() if p[3]}
    luminance=lambda p:.2126*p[0]+.7152*p[1]+.0722*p[2]
    minimum=min(map(luminance,colors));maximum=max(map(luminance,colors));lut={}
    for color in colors:
        t=(luminance(color)-minimum)/(maximum-minimum or 1)
        lut[color]=tuple(round(low[i]+(high[i]-low[i])*t) for i in range(3))
    pixels=[(*lut[p[:3]],p[3]) if p[3] else p for p in image.getdata()]
    result=Image.new('RGBA',image.size);result.putdata(pixels)
    assert image.size==(16,16) and result.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    destination=ROOT/f'source/textures/{name}.png';result.save(destination)
    provenance.append({'name':name,'source_file':relative,'source_sha256':digest,'license':data['license'],'author':data['author'],
                       'source_url':record.get('source_url') if isinstance(record,dict) else data['source_url'],
                       'derived_file':str(destination.relative_to(ROOT)),'derived_sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),
                       'changes':'RGB palette substitution only; dimensions, pixel positions and alpha preserved','size':[16,16]})
    textures.append({'name':name,'file':str(destination.relative_to(ROOT)),'roughness':.22 if name=='eyes' else .55 if name=='horn' else .93,
                     'specular':.45 if name=='eyes' else .1,'emission':.02 if name=='eyes' else .05})
(ROOT/'source/texture_manifest.json').write_text(json.dumps(textures,indent=2)+'\n',encoding='utf8')
(ROOT/'source/ASSET_PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf8')
print(json.dumps({'textures':len(textures),'cc0_hashes_verified':True,'all_16x16':True,'palette_only':True,'concept_copied':True}))
