"""Rare tall, multi-tier crown presets and first food; ready sprite references only."""
import copy
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/interstice'
DATA=RES/'data/interstice'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

base=json.loads((DATA/'interstice_trees/paleheart.json').read_text(encoding='utf-8'))['variants'][0]['tree']
tree=copy.deepcopy(base)
tree['trunk_placer'].update(base_height=20,height_rand_a=2,height_rand_b=1)
tree['foliage_placer'].update(radius=3,height=3)
arms=['[WUWUWDD]','[EUEUEDD]','[NUNUNDD]','[SUSUSDD]']
top=['[WUWWUUD]','[EUEEUUD]','[NUNNUUD]','[SUSSUUD]']
paths=['U'*9+arms[0]+arms[1]+'U'*6+arms[2]+arms[3]+'U'*6+''.join(top),
       'U'*10+arms[2]+arms[3]+'U'*5+arms[0]+arms[1]+'U'*7+''.join(reversed(top))]
write(DATA/'interstice_trees/paleheart_crown.json',{'attempts':1,'chance':8,'variants':[
    {'weight':1,'stem_width':2,'extra_height':2,'vine_attempts':8,'vine_length':8,'fruit_count':4,'branch_path':path,'tree':tree} for path in paths]})

for name,tex in [('crown_sapling','crown_sapling')]:
    write(ASSETS/f'blockstates/{name}.json',{'variants':{'':{'model':f'interstice:block/{name}'}}})
    write(ASSETS/f'models/block/{name}.json',{'parent':'minecraft:block/cross','render_type':'minecraft:cutout','textures':{'cross':f'interstice:block/garden/{tex}'}})
    write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'interstice:block/garden/{tex}'}})
    write(DATA/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'interstice:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

# Empty breaking loot is intentional: climbing and direct ripe-pod harvesting are required.
write(DATA/'loot_table/blocks/crown_fruit.json',{'type':'minecraft:block','pools':[]})
write(ASSETS/'blockstates/crown_fruit.json',{'variants':{f'age={age}':{'model':'interstice:block/crown_fruit'+('' if age==3 else '_unripe')} for age in range(4)}})
for suffix,texture in [('', 'tide_heart'),('_unripe','tide_heart_unripe')]:
    write(ASSETS/f'models/block/crown_fruit{suffix}.json',{
        'render_type':'minecraft:cutout','ambientocclusion':False,
        'textures':{'pod':'interstice:block/garden/'+texture,'particle':'#pod'},
        'elements':[{'from':[3,0,8],'to':[13,13,8],'faces':{'north':{'texture':'#pod','uv':[0,0,16,16]},'south':{'texture':'#pod','uv':[0,0,16,16]}}},
                    {'from':[8,0,3],'to':[8,13,13],'faces':{'east':{'texture':'#pod','uv':[0,0,16,16]},'west':{'texture':'#pod','uv':[0,0,16,16]}}}]
    })
write(ASSETS/'models/item/tide_heart.json',{'parent':'minecraft:item/generated','textures':{'layer0':'interstice:block/garden/tide_heart'}})

for kind in ('block','item'):
    p=RES/f'data/minecraft/tags/{kind}/saplings.json'
    d=json.loads(p.read_text(encoding='utf-8'));d['values']=list(dict.fromkeys(d['values']+['interstice:crown_sapling']));write(p,d)
translations={
    'ru_ru':{'item.interstice.tide_heart':'Приливное сердце','block.interstice.crown_fruit':'Плод венечной кроны','block.interstice.crown_sapling':'Саженец венечного бледносердника',
             'effect.interstice.tide_heart_reaction':'Послевкусие приливного сердца','tooltip.interstice.tide_heart.food':'Еда: 3 деления голода. После еды остаётся саженец.',
             'tooltip.interstice.tide_heart.benefit':'Ночное зрение: 40 секунд.','tooltip.interstice.tide_heart.cost':'Затем слабость и свечение: 45 секунд.',
             'tooltip.interstice.tide_heart.repeat':'Повторная порция не обновляет эффект и не отменяет последствия.',
             'message.interstice.tide_heart.aftertaste':'Зрение вернулось к норме. Теперь ты слабее и заметнее.',
             'message.interstice.tide_heart.unripe':'Плод ещё не созрел.'},
    'en_us':{'item.interstice.tide_heart':'Tide Heart','block.interstice.crown_fruit':'Crown Pod','block.interstice.crown_sapling':'Crowned Paleheart Sapling',
             'effect.interstice.tide_heart_reaction':'Tide Heart Aftertaste','tooltip.interstice.tide_heart.food':'Food: 3 hunger bars. Leaves a sapling after eating.',
             'tooltip.interstice.tide_heart.benefit':'Night Vision: 40 seconds.','tooltip.interstice.tide_heart.cost':'Then Weakness and Glowing: 45 seconds.',
             'tooltip.interstice.tide_heart.repeat':'Another serving does not refresh the effect or remove its cost.',
             'message.interstice.tide_heart.aftertaste':'Your sight returns to normal. You are now weaker and more visible.',
             'message.interstice.tide_heart.unripe':'The fruit is not ripe yet.'}}
for language,values in translations.items():
    p=ASSETS/f'lang/{language}.json';d=json.loads(p.read_text(encoding='utf-8'));d.update(values);write(p,d)
print('Generated rare giant crown presets, pod/food/sapling resources and explicit effect explanations.')
