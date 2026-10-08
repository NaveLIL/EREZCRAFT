"""Ready CC0 cave textures: palette only. Geometry is independent of image patterns."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'art/sources/cc0/cave'
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/interstice'
DATA=RES/'data/interstice'
SOURCES={entry['file']:entry for entry in json.loads((SOURCE/'sources.json').read_text())['sources']}
OUTPUTS=[]

def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def recolour(name,source,target):
    original=SOURCE/source
    assert sha(original)==SOURCES[source]['sha256'], f'Source pattern changed: {source}'
    image=Image.open(original).convert('RGBA')
    assert image.size==(16,16)
    pixels=[image.getpixel((x,y)) for y in range(image.height) for x in range(image.width)]
    colors=dict.fromkeys(pixels)
    opaque=[p for p in pixels if p[3]>0]
    luminance=lambda p:p[0]*.2126+p[1]*.7152+p[2]*.0722
    mean=sum(map(luminance,opaque))/len(opaque)
    used=set()
    for color in colors:
        if color[3]==0:
            replacement=color
        else:
            light=.35+.65*luminance(color)/mean
            replacement=tuple(max(0,min(255,round(channel*light))) for channel in target)+(color[3],)
            while replacement in used:
                replacement=replacement[:2]+((replacement[2]+1)%256,)+replacement[3:]
        assert replacement not in used, 'Pattern classes collapsed'
        colors[color]=replacement
        used.add(replacement)
    result=Image.new('RGBA',image.size)
    replacements=[colors[p] for p in pixels]
    result.putdata(replacements)
    assert all(a[3]==b[3] for a,b in zip(pixels,replacements))
    assert len(set(pixels))==len(set(replacements))
    output=ASSETS/f'textures/block/cave/{name}.png'
    output.parent.mkdir(parents=True,exist_ok=True)
    result.save(output)
    OUTPUTS.append(dict(output=f'block/cave/{name}.png',source=source,
                        source_url=SOURCES[source]['source_url'],source_sha256=sha(original),
                        output_sha256=sha(output),palette_colors=len(colors)))
    return image,result

samples=[]
for name,source,color in [('glow_bloom','flower3.png',(119,170,164)),
                          ('sting_frond','root1.png',(159,105,90)),
                          ('ash_spire','sandstone.png',(99,88,106)),
                          ('garden_spire','sandstone.png',(153,142,153)),
                          ('vault_spire','sandstone.png',(108,123,142))]:
    samples.append(recolour(name,source,color))

for name in ('glow_bloom','hanging_glow_bloom','sting_frond'):
    tex='glow_bloom' if name=='hanging_glow_bloom' else name
    write(ASSETS/f'models/block/{name}.json',dict(parent='minecraft:block/cross',render_type='minecraft:cutout',
          textures={'cross':f'interstice:block/cave/{tex}'}))
    variant={'model':f'interstice:block/{name}'}
    if name=='hanging_glow_bloom':variant['x']=180
    write(ASSETS/f'blockstates/{name}.json',{'variants':{'':variant}})
    write(ASSETS/f'models/item/{name}.json',dict(parent='minecraft:item/generated',textures={'layer0':f'interstice:block/cave/{tex}'}))
    write(DATA/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{
        'rolls':1,'entries':[{'type':'minecraft:item','name':f'interstice:{name}'}],
        'conditions':[{'condition':'minecraft:match_tool','predicate':{'items':'minecraft:shears'}},
                      {'condition':'minecraft:survives_explosion'}]}]})

def cuboid(width,y,height):
    return {'from':[8-width/2,y,8-width/2],'to':[8+width/2,y+height,8+width/2],
            'faces':{direction:{'texture':'#stone'} for direction in ('down','up','north','south','west','east')}}

for name in ('ash_spire','garden_spire','vault_spire'):
    variants={}
    for thickness,width in [('base',9),('middle',7),('frustum',5),('tip',3),('tip_merge',3)]:
        elements=[cuboid(width-step*.65,step*4,4) for step in range(4)]
        write(ASSETS/f'models/block/{name}_{thickness}.json',{'textures':{'stone':f'interstice:block/cave/{name}','particle':f'interstice:block/cave/{name}'},'elements':elements})
        for direction in ('up','down'):
            variant={'model':f'interstice:block/{name}_{thickness}'}
            if direction=='down':variant['x']=180
            variants[f'thickness={thickness},vertical_direction={direction}']=variant
    write(ASSETS/f'blockstates/{name}.json',{'variants':variants})
    write(ASSETS/f'models/item/{name}.json',dict(parent=f'interstice:block/{name}_tip'))
    write(DATA/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{
        'rolls':1,'entries':[{'type':'minecraft:item','name':f'interstice:{name}'}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

write(DATA/'tags/block/cave_spires.json',{'replace':False,'values':['interstice:'+name for name in ('ash_spire','garden_spire','vault_spire')]})
write(DATA/'tags/block/cave_flora.json',{'replace':False,'values':['interstice:'+name for name in ('glow_bloom','hanging_glow_bloom','sting_frond','clingweed')]})
write(ASSETS/'provenance/cave-ready.json',{
    'license':'CC0-1.0','author':'ARoachIFoundOnMyPillow','generator':'tools/generate_cave_resources.py',
    'operation':'One-to-one palette substitution only; source resolution, pixel positions, pattern classes and alpha preserved',
    'pattern_and_alpha_verified':True,'outputs':OUTPUTS})
preview=Image.new('RGBA',(5*128,256),(30,23,34,255))
for index,(source,target) in enumerate(samples):
    preview.paste(source.resize((112,112),Image.Resampling.NEAREST),(index*128+8,8))
    preview.paste(target.resize((112,112),Image.Resampling.NEAREST),(index*128+8,136))
previewpath=ROOT/'art/generated/caves/palette-only.png'
previewpath.parent.mkdir(parents=True,exist_ok=True)
preview.save(previewpath)
print('CAVE_RESOURCES passed: 5 ready patterns recoloured; positions, alpha and colour classes preserved.')
