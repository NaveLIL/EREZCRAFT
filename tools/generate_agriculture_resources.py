"""Extract whole declared-CC0 sprites and generate models/recipes; palette preparation is separate Java."""
from pathlib import Path
import hashlib,json,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';A=RES/'assets/interstice';D=RES/'data/interstice';SRC=ROOT/'art/sources/cc0/agriculture'
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def texture(t):return 'interstice:block/agriculture/'+t
sources=[]
def extract(archive,member,name,author,url):
    data=zipfile.ZipFile(ROOT/archive).read(member);p=SRC/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    sources.append(dict(file=name,source_member=member,source_url=url,author=author,license='CC0-1.0',sha256=hashlib.sha256(data).hexdigest(),archive=archive,archive_sha256=hashlib.sha256((ROOT/archive).read_bytes()).hexdigest()))
blockurl='https://opengameart.org/content/16x16-block-texture-set';itemurl='https://opengameart.org/content/assorted-minecraft-style-textures'
for plant,count in [('wheat',5),('carrots',4)]:
    for i in range(1,count+1):extract('research/cc0-20261008/blocks_2.zip',f'blocks/plants/crops/{plant}_stage{i}.png',f'{plant}_{i}.png','ARoachIFoundOnMyPillow',blockurl)
extract('research/cc0-20261008/blocks_2.zip','blocks/farmland.png','farmland.png','ARoachIFoundOnMyPillow',blockurl)
for source,name in [('corn.png','grain.png'),('purple carrot.png','root.png')]:extract('research/leaf-source-preview/assorted_2.zip',source,name,'JoeEnderman',itemurl)
extract('research/leaf-source-preview/assorted_1.zip','farming_bread.png','bread.png','JoeEnderman',itemurl)
prior=json.loads((A/'provenance/minerals.json').read_text(encoding='utf-8'))['outputs']
for name,path in [('stick.png','art/sources/cc0/minerals/stick.png'),('coal.png','art/sources/cc0/minerals/coal_lump.png'),('crystal.png','art/sources/cc0/minerals/lexxite_shard.png'),('shard.png','art/sources/cc0/minerals/ausene_shard.png')]:
    p=ROOT/path;shutil.copy2(p,SRC/name);original=next(e for e in prior if e['source']==p.name)
    sources.append(dict(file=name,source=path,provenance_parent='assets/interstice/provenance/minerals.json',sha256=hashlib.sha256(p.read_bytes()).hexdigest(),license='CC0-1.0',source_url=original['source_url'],author=original['author']))
write(SRC/'sources.json',dict(operation='Whole files extracted unchanged; see runtime provenance for bijective palette preparation',sources=sources))
shutil.copy2(ROOT/'art/sources/cc0/minerals/CC0-1.0.html',SRC/'CC0-1.0.html')

def model(name,d):write(A/f'models/block/agriculture/{name}.json',d)
def item(name,parent=None,tex=None):write(A/f'models/item/{name}.json',dict(parent=parent) if parent else dict(parent='minecraft:item/generated',textures={'layer0':texture(tex or name)}))
def cube(name,tex):model(name,dict(parent='minecraft:block/cube_all',textures={'all':tex}))
def state(name,variants):write(A/f'blockstates/{name}.json',dict(variants=variants))
for kind,maxage,src in [('ash_grain_crop',4,'grain'),('crimson_root_crop',3,'root')]:
    for age in range(maxage+1):model(f'{kind}_{age}',dict(parent='minecraft:block/crop',render_type='minecraft:cutout',textures={'crop':texture(f'{src}_stage_{age}')}))
    state(kind,{f'age={age}':{'model':f'interstice:block/agriculture/{kind}_{min(age,maxage)}'} for age in range(5)})
for wet in (False,True):
    model('farmland_'+str(wet).lower(),dict(parent='minecraft:block/farmland',textures={'dirt':'interstice:block/minerals/root_loam','top':texture('farmland_wet' if wet else 'farmland_dry')}))
state('toxic_farmland',{f'moisture={i}':{'model':'interstice:block/agriculture/farmland_'+str(i>0).lower()} for i in range(8)})
item('toxic_farmland','interstice:block/agriculture/farmland_false')
def box(fr,to,tex):
    face={'texture':tex}
    if tex in ('#silver','#metal'):face['uv']=[6,7,10,9]
    return dict(from_=fr,to=to,faces={direction:dict(face) for direction in ['north','south','west','east','up','down']})
def elements(entries):return [{('from' if k=='from_' else k):v for k,v in e.items()} for e in entries]
for lit in (False,True):
    model('retort_'+str(lit).lower(),dict(parent='minecraft:block/block',textures={'particle':'interstice:block/pyrolith','body':'interstice:block/pyrolith','lining':'interstice:block/vitriolite','silver':'interstice:block/minerals/riftsilver','glow':'interstice:block/phosphorite'},elements=elements([
        box([1,0,1],[15,12,15],'#body'),box([3,12,3],[13,15,13],'#lining'),box([5,15,5],[11,16,11],'#glow' if lit else '#lining'),box([3,-.01,0.9],[13,3,1.9],'#silver'),box([5,5,.9],[11,9,1.1],'#glow' if lit else '#lining')])) )
state('reaction_retort',{f'lit={str(lit).lower()}':{'model':'interstice:block/agriculture/retort_'+str(lit).lower()} for lit in (False,True)})
item('reaction_retort','interstice:block/agriculture/retort_false')
for filled in (False,True):
    model('reservoir_'+str(filled).lower(),dict(parent='minecraft:block/block',textures={'particle':'interstice:block/vitriolite','body':'interstice:block/vitriolite','cap':'interstice:block/pyrolith','indicator':'interstice:block/heavy_still_v2' if filled else 'interstice:block/riftstone'},elements=elements([
        box([0,0,0],[16,15,16],'#body'),box([2,15,2],[14,16,14],'#cap'),box([5,15.01,5],[11,16.01,11],'#indicator')])) )
state('nutrient_reservoir',{f'filled={str(f).lower()}':{'model':'interstice:block/agriculture/reservoir_'+str(f).lower()} for f in (False,True)})
item('nutrient_reservoir','interstice:block/agriculture/reservoir_false')
write(A/'models/item/nutrient_reservoir.json',{'parent':'interstice:block/agriculture/reservoir_false','overrides':[{'predicate':{'interstice:filled':1},'model':'interstice:block/agriculture/reservoir_true'}]})
wood='interstice:block/garden/paleheart_planks'
for part,parent in [('post','fence_post'),('side','fence_side'),('inventory','fence_inventory')]:model('fence_'+part,dict(parent='minecraft:block/'+parent,textures={'texture':wood}))
write(A/'blockstates/reinforced_fence.json',{'multipart':[{'apply':{'model':'interstice:block/agriculture/fence_post'}}]+[{'when':{face:'true'},'apply':dict(model='interstice:block/agriculture/fence_side',y=rotation,uvlock=True)} for face,rotation in [('north',0),('east',90),('south',180),('west',270)]]})
item('reinforced_fence','interstice:block/agriculture/fence_inventory')
for part,parent in [('closed','fence_gate'),('open','fence_gate_open'),('wall','fence_gate_wall'),('wall_open','fence_gate_wall_open')]:model('gate_'+part,dict(parent='minecraft:block/template_'+parent,textures={'texture':wood}))
state('reinforced_fence_gate',{f'facing={face},in_wall={str(wall).lower()},open={str(opened).lower()}':dict(model='interstice:block/agriculture/gate_'+('wall_open' if opened and wall else 'wall' if wall else 'open' if opened else 'closed'),y=rotation,uvlock=True) for face,rotation in [('south',0),('west',90),('north',180),('east',270)] for wall in (False,True) for opened in (False,True)})
item('reinforced_fence_gate','interstice:block/agriculture/gate_closed')
# Reuse the actual accepted flower head with a restrained enclosing case and hanging support.
head=json.loads((A/'models/block/minerals/luminous_bud.json').read_text(encoding='utf-8'))
for hanging in (False,True):
    part=json.loads(json.dumps(head));part['textures'].update(body='interstice:block/vitriolite',wood='interstice:block/garden/paleheart_log')
    part['elements']+=elements([box([4,0,4],[12,2,12],'#body'),box([4,14,4],[12,16,12],'#body')]+[box([x,2,z],[x+1,14,z+1],'#wood') for x,z in [(4,4),(11,4),(4,11),(11,11)]])
    if hanging:
        for element in part['elements']:
            for endpoint in ('from','to'):element[endpoint][1]*=.75
        part['elements']+=elements([box([7,12,7],[9,16,9],'#body')])
    model('lantern_'+str(hanging).lower(),part)
state('bioluminescent_lantern',{f'hanging={str(h).lower()}':{'model':'interstice:block/agriculture/lantern_'+str(h).lower()} for h in (False,True)})
item('bioluminescent_lantern','interstice:block/agriculture/lantern_false')
for name in ['riftsilver_wire','riftsilver_mesh','umbral_sorbent','phosphorite_paste','vitriolite_lining','pure_vitriolite_lining','ash_flour','plant_fiber','ash_grain','crimson_root','purified_bread','purified_root','mineral_fertilizer']:item(name)
model('cultivator',dict(parent='minecraft:block/block',render_type='minecraft:cutout',textures={'particle':wood,'wood':'interstice:block/garden/paleheart_log','metal':'interstice:block/minerals/riftsilver'},elements=elements([box([7,0,7],[9,14,9],'#wood'),box([3,13,6],[13,15,10],'#metal'),box([3,9,6],[5,13,10],'#metal')]),display={'gui':{'rotation':[30,45,0],'translation':[0,0,0],'scale':[.9,.9,.9]},'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,4,1],'scale':[.85,.85,.85]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1,4,1],'scale':[.8,.8,.8]}}))
item('slicing_cultivator','interstice:block/agriculture/cultivator')

def shaped(name,pattern,key,result,count=1):write(D/f'recipe/{name}.json',dict(type='minecraft:crafting_shaped',category='misc',pattern=pattern,key={k:({'tag':v[1:]} if v.startswith('#') else {'item':v}) for k,v in key.items()},result={'id':'interstice:'+result,'count':count}))
def loose(name,ingredients,result,count):write(D/f'recipe/{name}.json',dict(type='minecraft:crafting_shapeless',category='misc',ingredients=[{'item':i} for i in ingredients],result={'id':'interstice:'+result,'count':count}))
shaped('reaction_retort',['PVP','SFS','PVP'],{'P':'interstice:pyrolith','V':'interstice:vitriolite','S':'interstice:riftsilver_ingot','F':'minecraft:furnace'},'reaction_retort')
loose('riftsilver_wire',['interstice:riftsilver_ingot'],'riftsilver_wire',4)
loose('riftsilver_mesh',['interstice:riftsilver_wire']*4,'riftsilver_mesh',1)
shaped('reinforced_fence',['WSW','WSW','M M'],{'W':'#interstice:world_planks','S':'interstice:world_stick','M':'interstice:riftsilver_mesh'},'reinforced_fence',6)
shaped('reinforced_fence_gate',['MSM',' W ','WS '],{'W':'#interstice:world_planks','S':'interstice:world_stick','M':'interstice:riftsilver_mesh'},'reinforced_fence_gate')
shaped('nutrient_reservoir',['RLR','RLR'],{'R':'interstice:riftstone','L':'interstice:vitriolite_lining'},'nutrient_reservoir',4)
shaped('bioluminescent_lantern',['WPW','WBW','PLS'],{'W':'interstice:riftsilver_wire','P':'interstice:phosphorite_paste','B':'interstice:luminous_bud','L':'interstice:pure_vitriolite_lining','S':'interstice:world_stick'},'bioluminescent_lantern')
shaped('slicing_cultivator',['ILI',' W ',' WS'],{'I':'interstice:riftsilver_ingot','W':'interstice:riftsilver_wire','L':'interstice:pure_vitriolite_lining','S':'interstice:world_stick'},'slicing_cultivator')
loose('mineral_fertilizer',['interstice:phosphorite_paste','interstice:plant_fiber','interstice:root_loam'],'mineral_fertilizer',4)
for name,inputs,outputs,ticks in [
 ('grain_separation',{'ash_grain':2},{'ash_flour':2,'plant_fiber':1},200),
 ('umbral_sorbent',{'umbral_coal':1,'aerolite':1},{'umbral_sorbent':2},400),
 ('phosphorite_paste',{'phosphorite_crystal':1,'plant_fiber':1},{'phosphorite_paste':2},400),
 ('vitriolite_lining',{'vitriolite':1,'pyrolith':1},{'vitriolite_lining':4},400),
 ('pure_vitriolite_lining',{'vitriolite_shard':2,'pyrolith':1},{'pure_vitriolite_lining':1},600),
 ('purified_bread',{'ash_flour':4,'umbral_sorbent':1},{'purified_bread':4},800),
 ('purified_root',{'crimson_root':4,'umbral_sorbent':1},{'purified_root':4},600)]:
    write(D/f'recipe/retort_{name}.json',dict(type='interstice:retort',inputs=[{'ingredient':{'item':'interstice:'+i},'count':n} for i,n in inputs.items()],outputs=[{'id':'interstice:'+i,'count':n} for i,n in outputs.items()],ticks=ticks))
discovery=['world_stick','riftsilver_ingot','ash_grain','phosphorite_crystal']
write(D/'tags/item/agriculture_discovery_materials.json',{'replace':False,'values':['interstice:'+i for i in discovery]})
ordinary=['reaction_retort','riftsilver_wire','riftsilver_mesh','reinforced_fence','reinforced_fence_gate','nutrient_reservoir','bioluminescent_lantern','slicing_cultivator','mineral_fertilizer']
ordinary += [f'{wood}_{part}_world_sticks' for wood in ['paleheart','crown'] for part in ['fence','fence_gate']]
for name in ordinary:
    recipe='interstice:'+name
    write(D/f'advancement/recipes/agriculture/{name}.json',{'parent':'minecraft:recipes/root','criteria':{
        'has_native_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'#interstice:agriculture_discovery_materials'}]}},
        'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':recipe}}},
        'requirements':[['has_native_material','has_the_recipe']],'rewards':{'recipes':[recipe]}})
for wood in ['paleheart','crown']:
    for part in ['fence','fence_gate']:
        p=D/f'recipe/{wood}_{part}.json';r=json.loads(p.read_text(encoding='utf-8'));
        for value in r['key'].values():
            if value.get('item')=='minecraft:stick':value['item']='interstice:world_stick'
        write(D/f'recipe/{wood}_{part}_world_sticks.json',r)
def drop(name,item,conditions=[]):write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':item}],'conditions':conditions+[{'condition':'minecraft:survives_explosion'}]}]})
for name in ['reaction_retort','reinforced_fence','reinforced_fence_gate','bioluminescent_lantern']:drop(name,'interstice:'+name)
drop('toxic_farmland','interstice:root_loam')
drop('nutrient_reservoir','interstice:nutrient_reservoir',[{'condition':'minecraft:block_state_property','block':'interstice:nutrient_reservoir','properties':{'filled':'false'}}])
for block,itemid,maxage in [('ash_grain_crop','ash_grain',4),('crimson_root_crop','crimson_root',3)]:
    mature={'condition':'minecraft:block_state_property','block':'interstice:'+block,'properties':{'age':str(maxage)}}
    if maxage==3:mature={'condition':'minecraft:any_of','terms':[mature,{'condition':'minecraft:block_state_property','block':'interstice:'+block,'properties':{'age':'4'}}]}
    write(D/f'loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[
        {'type':'minecraft:item','name':'interstice:'+itemid,'conditions':[mature],'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':2,'max':4}}]},
        {'type':'minecraft:item','name':'interstice:'+itemid}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag,entries in [('minecraft:mineable/pickaxe',['reaction_retort','reinforced_fence','reinforced_fence_gate','nutrient_reservoir','bioluminescent_lantern']),('minecraft:needs_stone_tool',['reaction_retort','reinforced_fence','reinforced_fence_gate']),('minecraft:mineable/shovel',['toxic_farmland']),('minecraft:fences',['reinforced_fence']),('minecraft:wooden_fences',['reinforced_fence']),('minecraft:fence_gates',['reinforced_fence_gate']),('minecraft:crops',['ash_grain_crop','crimson_root_crop'])]:
    ns,path=tag.split(':');p=RES/f'data/{ns}/tags/block/{path}.json';d=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {'replace':False,'values':[]};d['values']=list(dict.fromkeys(d['values']+['interstice:'+v for v in entries]));write(p,d)
names={'reaction_retort':('Реакционная реторта','Reaction Retort'),'toxic_farmland':('Хемотрофная грядка','Chemotrophic Farmland'),'nutrient_reservoir':('Облицованный питательный канал','Lined Nutrient Reservoir'),'reinforced_fence':('Армированная ограда','Reinforced Fence'),'reinforced_fence_gate':('Армированная калитка','Reinforced Fence Gate'),'bioluminescent_lantern':('Биолюминесцентный фонарь','Bioluminescent Lantern'),'ash_grain':('Пепельный колос','Ashgrain Head'),'crimson_root':('Багровый корень','Crimson Root'),'ash_flour':('Пепельная мука','Ash Flour'),'plant_fiber':('Растительное волокно','Plant Fiber'),'riftsilver_wire':('Рифтосеребряная проволока','Riftsilver Wire'),'riftsilver_mesh':('Рифтосеребряная сетка','Riftsilver Mesh'),'umbral_sorbent':('Сумрачный сорбент','Umbral Sorbent'),'phosphorite_paste':('Фосфоритовая биопаста','Phosphorite Biopaste'),'vitriolite_lining':('Витриолитовая облицовка','Vitriolite Lining'),'pure_vitriolite_lining':('Чистая витриолитовая облицовка','Pure Vitriolite Lining'),'purified_bread':('Очищенный пепельный хлеб','Purified Ash Bread'),'purified_root':('Очищенный багровый корень','Purified Crimson Root'),'mineral_fertilizer':('Минеральная подкормка','Mineral Fertilizer'),'slicing_cultivator':('Срезной культиватор','Slicing Cultivator')}
for language,index in [('ru_ru',0),('en_us',1)]:
    p=A/f'lang/{language}.json';d=json.loads(p.read_text(encoding='utf-8'));
    for name,pair in names.items():d[('block' if name in ['reaction_retort','toxic_farmland','nutrient_reservoir','reinforced_fence','reinforced_fence_gate','bioluminescent_lantern'] else 'item')+'.interstice.'+name]=pair[index]
    values={'tooltip.interstice.native_root.toxic':('Сырая пища человека: Отравление I8с, Слабость I12с.','Raw human food: Poison I8s, Weakness I12s.'),'tooltip.interstice.mineral_fertilizer':('Одна стадия роста. Нужны своя грядка и нижний токсин.','One growth stage. Requires native farmland and lower toxin.'),'tooltip.interstice.cultivator':('Собирает одну зрелую культуру и расходует один посадочный предмет.','Harvests one mature crop, spending one planting item to replant.'),'menu.interstice.retort.inputs':('Сырьё','Inputs'),'menu.interstice.retort.preview':('Результат','Result'),'menu.interstice.retort.outputs':('Выход','Output'),'menu.interstice.retort.status.0':('Ожидание рецепта','Waiting for ingredients'),'menu.interstice.retort.status.1':('Переработка','Processing'),'menu.interstice.retort.status.2':('Нужен сумрачный уголь','Umbral coal required'),'menu.interstice.retort.status.3':('Освободите выход','Output space required')}
    values.update({'tooltip.interstice.native_crop':('Своя грядка, нижний красный токсин; свет не требуется.','Native farmland and lower red toxin; sunlight is unnecessary.'),'tooltip.interstice.reservoir.full':('Содержит одно ведро нижнего токсина.','Contains one bucket of lower toxin.'),'tooltip.interstice.reservoir.empty':('Заполняется серебряным ведром нижнего токсина.','Fill using a riftsilver lower-toxin bucket.'),'menu.interstice.retort.recipes':('Рецепты →','Recipes →'),'menu.interstice.retort.time':('Обработка: %sс','Processing: %ss'),'menu.interstice.retort.fuel':('Топливо: сумрачный уголь','Fuel: umbral coal')})
    d.update({k:v[index] for k,v in values.items()});write(p,d)
print('Extracted17 whole CC0 sources; agriculture models, recipes, loot and localization generated')
# Geometry-only reinforcement follows template generation, preserving all ready PNGs.
import subprocess,sys
subprocess.run([sys.executable,str(ROOT/'tools/generate_reinforced_models.py'),'--output',str(A/'models')],check=True)
