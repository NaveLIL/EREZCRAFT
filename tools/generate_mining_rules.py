"""Gameplay data for the realm's mining chain. Visual assets have a separate CC0 pipeline."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'src/main/resources/data/interstice'
def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

ORES={'riftsilver_seam':'raw_riftsilver','umbral_coal_ore':'umbral_coal','phosphorite_ore':'phosphorite_crystal','vitriolite_ore':'vitriolite_shard'}
for ore,drop in ORES.items():
    write(DATA/f'loot_table/blocks/{ore}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[
        {'type':'minecraft:item','name':'interstice:'+ore,'conditions':[{'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}],
         'functions':[{'function':'minecraft:copy_state','block':'interstice:'+ore,'properties':['host']}]},
        {'type':'minecraft:item','name':'interstice:'+drop,'functions':[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'function':'minecraft:explosion_decay'}]}]}]}]})
for block,item in [('coal_torch','coal_torch'),('coal_wall_torch','coal_torch'),('living_torch','living_torch'),('living_wall_torch','living_torch'),('root_loam','root_loam'),('rift_shale','rift_shale')]:
    write(DATA/f'loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'interstice:'+item}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name,values in [('mineral_ores',list(ORES)),('stone_mineral_ores',['phosphorite_ore','vitriolite_ore']),('iron_mineral_ores',['riftsilver_seam'])]:
    write(DATA/f'tags/block/{name}.json',{'replace':False,'values':['interstice:'+n for n in values]})
write(DATA/'tags/item/world_planks.json',{'replace':False,'values':['interstice:'+n for n in ['gloomcrown_planks','paleheart_planks','crown_planks']]})

def recipe(name,data,ingredient):
    write(DATA/f'recipe/{name}.json',data)
    predicate={'items':ingredient}
    write(DATA/f'advancement/recipes/building_blocks/{name}.json',{'parent':'minecraft:recipes/root',
        'criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[predicate]}},
                    'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'interstice:'+name}}},
        'requirements':[['has_material','has_recipe']],'rewards':{'recipes':['interstice:'+name]}})
recipe('world_stick',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'tag':'interstice:world_planks'}],'result':{'id':'interstice:world_stick','count':2}},'#interstice:world_planks')
for name,source,count in [('coal_torch','umbral_coal',4),('living_torch','luminous_bud',1)]:
    recipe(name,{'type':'minecraft:crafting_shaped','category':'misc','pattern':['B','S'],
                'key':{'B':{'item':'interstice:'+source},'S':{'item':'interstice:world_stick'}},'result':{'id':'interstice:'+name,'count':count}},'interstice:'+source)
for name,source in [('phosphorite','phosphorite_crystal'),('vitriolite','vitriolite_shard')]:
    recipe(name+'_from_shards',{'type':'minecraft:crafting_shaped','category':'building','pattern':['CC','CC'],
                'key':{'C':{'item':'interstice:'+source}},'result':{'id':'interstice:'+name,'count':1}},'interstice:'+source)
for ore,output in [('riftsilver_seam','riftsilver_ingot'),('umbral_coal_ore','umbral_coal'),('phosphorite_ore','phosphorite_crystal'),('vitriolite_ore','vitriolite_shard')]:
    for kind,time in [('smelting',200),('blasting',100)]:
        recipe(ore+'_'+kind,{'type':'minecraft:'+kind,'category':'misc','ingredient':{'item':'interstice:'+ore},'result':{'id':'interstice:'+output},'experience':.7 if ore=='riftsilver_seam' else .1,'cookingtime':time},'interstice:'+ore)
print('MINING_RULES: host-preserving silk loot, bounded tool tags, five world-only recipes and eight ore furnace recipes.')
