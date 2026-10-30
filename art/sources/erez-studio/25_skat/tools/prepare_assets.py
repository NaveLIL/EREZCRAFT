"""Local CC0 sources; change RGB palettes only, never the source pixel layout."""
from pathlib import Path
from PIL import Image
import json, hashlib, shutil, argparse
ROOT = Path(__file__).resolve().parents[1]
GAME = Path('D:/repos/Minecraft/EREZCRAFT')
parser=argparse.ArgumentParser();parser.add_argument('--revision',default='SK01');args=parser.parse_args()
suffix='' if args.revision=='SK01' else '_'+args.revision
for name in ['source/textures','source/originals','assembly','exports','preview','references','reports']:
    (ROOT/name).mkdir(parents=True, exist_ok=True)
steel_meta = json.loads((GAME/'art/sources/cc0/gui-materials/sources.json').read_text())['sources'][0]
wood_meta = json.loads((GAME/'art/sources/cc0/crown-tree/sources.json').read_text())
specs = [
    ('graphite','gui-materials/steel_block.png',(25,33,36),(47,59,62),.78,0),
    ('steel','gui-materials/steel_block.png',(62,75,77),(100,116,116),.72,0),
    ('copper','gui-materials/steel_block.png',(111,57,31),(177,108,57),.6,0),
    ('brass','gui-materials/steel_block.png',(130,96,44),(201,164,83),.57,0),
    ('teal','gui-materials/steel_block.png',(23,65,63),(46,104,96),.76,0),
    ('ivory','gui-materials/steel_block.png',(144,147,126),(195,196,166),.8,0),
    ('rubber','gui-materials/steel_block.png',(17,22,23),(27,33,34),.94,0),
    ('glass','gui-materials/steel_block.png',(18,50,58),(32,76,82),.25,0),
    ('lamp','gui-materials/steel_block.png',(186,127,50),(252,221,147),.65,1.3),
    ('cyan','gui-materials/steel_block.png',(31,112,108),(91,206,184),.65,.65),
    ('wood','crown-tree/maple_planks.png',(58,46,33),(107,85,55),.9,0),
]
if args.revision=='SK02':
    specs=[s for s in specs if s[0] not in ['glass','graphite','steel']]+[
        ('graphite','gui-materials/steel_block.png',(19,25,28),(38,47,50),.81,0),
        ('steel','gui-materials/steel_block.png',(53,64,67),(82,95,97),.72,0),
        ('deck','gui-materials/steel_block.png',(37,44,46),(65,75,76),.85,0),
        ('glass','gui-materials/steel_block.png',(65,132,142),(105,180,185),.16,.015),
    ]
(ROOT/f'source/textures{suffix}').mkdir(exist_ok=True)
manifest=[];provenance=[]
for name,relative,low,high,roughness,emission in specs:
    src=GAME/'art/sources/cc0'/relative
    digest=hashlib.sha256(src.read_bytes()).hexdigest()
    if relative.startswith('gui-materials'):
        record=steel_meta;expected=record['source_sha256'];author=record['author'];url=record['source_url']
    else:
        record=wood_meta['files'][src.name];expected=record['sha256'];author=wood_meta['author'];url=record['source_url']
    assert digest==expected,(relative,digest,expected)
    original=ROOT/'source/originals'/src.name
    if not original.exists():shutil.copyfile(src,original)
    im=Image.open(src).convert('RGBA');assert im.size==(16,16)
    colors=set(p[:3] for p in im.getdata() if p[3])
    lum=lambda p:.2126*p[0]+.7152*p[1]+.0722*p[2]
    ordered=sorted(colors,key=lambda c:(lum(c),c))
    lut={c:tuple(round(low[i]+(high[i]-low[i])*rank/(len(ordered)-1 or 1)) for i in range(3)) for rank,c in enumerate(ordered)}
    assert len(set(lut.values()))==len(lut),'Palette mapping must preserve source color classes'
    out=Image.new('RGBA',im.size);out.putdata([(*lut[p[:3]],p[3]) if p[3] else p for p in im.getdata()])
    assert out.getchannel('A').tobytes()==im.getchannel('A').tobytes()
    dest=ROOT/f'source/textures{suffix}/{name}.png';out.save(dest)
    manifest.append({'name':name,'file':dest.relative_to(ROOT).as_posix(),'roughness':roughness,'emission':emission})
    provenance.append({'name':name,'source_file':relative,'saved_original':original.relative_to(ROOT).as_posix(),
        'source_sha256':digest,'derived_file':dest.relative_to(ROOT).as_posix(),'derived_sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),
        'license':'CC0-1.0','author':author,'source_url':url,'size':[16,16],
        'changes':'Bijective RGB palette substitution only. Original pixel positions, dimensions and alpha preserved.'})
(ROOT/f'source/texture_manifest{suffix}.json').write_text(json.dumps(manifest,indent=2)+'\n')
(ROOT/f'source/ASSET_PROVENANCE{suffix}.json').write_text(json.dumps(provenance,indent=2)+'\n')
print(json.dumps({'textures':len(manifest),'source_hashes_verified':True,'palette_only':True}))
