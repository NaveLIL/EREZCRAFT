"""Show a snipped flower by changing model geometry/state selection, never the accepted PNGs."""
from pathlib import Path
import copy
import json
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/interstice'
source=json.loads((ASSETS/'models/block/tide_sprout_flower.json').read_text(encoding='utf-8'))
spent=copy.deepcopy(source)
spent['elements']=[element for element in spent['elements']
                   if not all(face.get('texture')=='#core' for face in element['faces'].values())]
# Retain the ring of petals and its stalk. Its rim no longer exposes the luminous bud tissue.
for element in spent['elements']:
    for face in element['faces'].values():
        if face.get('texture')=='#core':face['texture']='#petal'
def write(path,obj):
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
write(ASSETS/'models/block/tide_sprout_flower_harvested.json',spent)
path=ASSETS/'blockstates/tide_sprout.json'
state=json.loads(path.read_text(encoding='utf-8'))
base={}
for key,value in state['variants'].items():
    properties=dict(part.split('=',1) for part in key.split(','))
    properties.pop('harvested',None)
    original=copy.deepcopy(value)
    if original.get('model')=='interstice:block/tide_sprout_flower_harvested':original['model']='interstice:block/tide_sprout_flower'
    base[tuple(sorted(properties.items()))]=original
variants={}
for properties,model in base.items():
    for harvested in ('false','true'):
        choices=dict(properties);choices['harvested']=harvested
        variant=copy.deepcopy(model)
        if harvested=='true' and choices['bloomed']=='true' and variant.get('model')=='interstice:block/tide_sprout_flower':
            variant['model']='interstice:block/tide_sprout_flower_harvested'
        variants[','.join(key+'='+value for key,value in sorted(choices.items()))]=variant
write(path,{'variants':variants})
assert len(spent['elements'])==len(source['elements'])-1
assert all(face.get('texture')!='#core' for e in spent['elements'] for face in e['faces'].values())
assert len(variants)==112
print('HARVESTED_SPROUT: central bud removed, original stalk/petals retained, 112 states covered; no bitmap changes.')
