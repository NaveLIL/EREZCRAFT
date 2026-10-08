"""Airy clingweed geometry; the separately authored transparent sprite is never edited here."""
from pathlib import Path
import copy
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/interstice'
VANILLA=zipfile.ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar')
CROSS=json.loads(VANILLA.read('assets/minecraft/models/block/cross.json'))
ORIENTATIONS=json.loads(VANILLA.read('assets/minecraft/blockstates/end_rod.json'))['variants']

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

for name,height,width in [('clingweed',16,1),('clingweed_short',12,.82),('clingweed_slender',14,.72)]:
    model=copy.deepcopy(CROSS)
    model['render_type']='minecraft:cutout'
    model['textures']['cross']='interstice:block/cave/clingweed'
    for element in model['elements']:
        for point in ('from','to'):
            element[point][0]=8+(element[point][0]-8)*width
            element[point][2]=8+(element[point][2]-8)*width
        element['to'][1]=height
    write(ASSETS/f'models/block/cave/{name}.json',model)

variants={}
for facing,orientation in ORIENTATIONS.items():
    values=[]
    for name,weight in [('clingweed',3),('clingweed_short',2),('clingweed_slender',1)]:
        item={key:value for key,value in orientation.items() if key!='model'}
        item.update(model=f'interstice:block/cave/{name}',weight=weight)
        values.append(item)
    variants[facing]=values
write(ASSETS/'blockstates/clingweed.json',{'variants':variants})
print('CLINGWEED_MODELS: two transparent crossed tendril planes, three tuft dimensions, six anchored orientations; no cuboid curtains.')
