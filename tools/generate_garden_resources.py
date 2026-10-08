"""Use stock Minecraft model/state/loot templates and ready CC0 imagery; no bitmap edits."""
import json
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/interstice'
DATA=RES/'data'
VANILLA=zipfile.ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar')
BLOCKS={'paleheart_log':'oak_log','stripped_paleheart_log':'stripped_oak_log','paleheart_planks':'oak_planks',
        'paleheart_leaves':'azalea_leaves','paleheart_sapling':'oak_sapling','paleheart_slab':'oak_slab',
        'paleheart_stairs':'oak_stairs','paleheart_fence':'oak_fence','paleheart_fence_gate':'oak_fence_gate',
        'palestone':'stone','palestone_bricks':'stone_bricks','palestone_slab':'stone_slab',
        'palestone_stairs':'stone_stairs','palestone_wall':'stone_brick_wall','pale_fern':'fern','pale_litter':'moss_carpet'}
REVERSE={v:k for k,v in BLOCKS.items()}

def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def item(name):return 'interstice:'+name
def tag(kind,name,values,namespace='minecraft'):
    p=DATA/f'{namespace}/tags/{kind}/{name}.json'
    old=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {'replace':False,'values':[]}
    old['values']=list(dict.fromkeys(old['values']+[item(n) for n in values]));write(p,old)

def map_resource(value):
    if isinstance(value,str) and value.startswith('minecraft:'):
        tail=value[10:]
        if tail in REVERSE:return item(REVERSE[tail])
        if tail.startswith('block/'):
            for src,dst in sorted(REVERSE.items(),key=lambda kv:-len(kv[0])):
                if tail[6:]==src or tail[6:].startswith(src+'_'):return 'interstice:block/'+dst+tail[6+len(src):]
    return value

def transform(obj):
    if isinstance(obj,dict):return {k:transform(v) for k,v in obj.items()}
    if isinstance(obj,list):return [transform(v) for v in obj]
    return map_resource(obj)

def model_refs(obj):
    if isinstance(obj,dict):
        for key,value in obj.items():
            if key=='model':yield value
            else:yield from model_refs(value)
    elif isinstance(obj,list):
        for value in obj:yield from model_refs(value)

for name,source in BLOCKS.items():
    state=json.loads(VANILLA.read(f'assets/minecraft/blockstates/{source}.json'))
    for ref in set(model_refs(state)):
        original=json.loads(VANILLA.read('assets/minecraft/models/'+ref[10:]+'.json'))
        new=transform(original)
        # Model geometry is reused as-is; only resource references point to our ready recoloured PNGs.
        for key,tex in original.get('textures',{}).items():
            if tex.startswith('minecraft:block/'):
                tail=tex[16:]
                target=REVERSE.get(tail)
                if tail in ('oak_planks',):target='paleheart_planks'
                if tail=='azalea_leaves':target='paleheart_leaves'
                if tail=='oak_sapling':target='paleheart_sapling'
                if tail=='fern':target='pale_fern'
                if tail=='moss_block':target='pale_litter'
                if tail=='stone_bricks' and name=='palestone_wall':target='palestone'
                if target:new['textures'][key]='interstice:block/garden/'+target
                if tail.endswith('_top') and tail[:-4] in REVERSE:new['textures'][key]='interstice:block/garden/'+REVERSE[tail[:-4]]+'_top'
        path=map_resource(ref).split(':',1)[1]
        if name in ('paleheart_leaves','paleheart_sapling','pale_fern'):new['render_type']='minecraft:cutout_mipped' if name=='paleheart_leaves' else 'minecraft:cutout'
        write(ASSETS/f'models/{path}.json',new)
    write(ASSETS/f'blockstates/{name}.json',transform(state))
    inv=transform(json.loads(VANILLA.read(f'assets/minecraft/models/item/{source}.json')))
    # Fence/wall inventory parents have their own template geometry.
    parent=inv.get('parent','')
    if parent.endswith('_inventory'):
        old_parent=json.loads(VANILLA.read('assets/minecraft/models/'+json.loads(VANILLA.read(f'assets/minecraft/models/item/{source}.json'))['parent'][10:]+'.json'))
        m=transform(old_parent)
        for key in m.get('textures',{}):m['textures'][key]='interstice:block/garden/'+('paleheart_planks' if name.startswith('paleheart') else 'palestone')
        write(ASSETS/('models/'+parent.split(':')[1]+'.json'),m)
    if 'textures' in inv:inv['textures']={k:'interstice:block/garden/'+name for k in inv['textures']}
    write(ASSETS/f'models/item/{name}.json',inv)
    loot=transform(json.loads(VANILLA.read(f'data/minecraft/loot_table/blocks/{source}.json')))
    if name=='paleheart_leaves':
        # Azalea's stock table has no apples; replace its sapling item with our own species.
        loot=json.loads(json.dumps(loot).replace('minecraft:azalea',item('paleheart_sapling')))
    if name in ('pale_fern','pale_litter','paleheart_sapling'):
        loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':item(name)}],
               'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
    if name=='palestone':
        loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':item(name)}],
               'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
    write(DATA/f'interstice/loot_table/blocks/{name}.json',loot)

def recipe(name,obj,ingredient):
    write(DATA/f'interstice/recipe/{name}.json',obj)
    write(DATA/f'interstice/advancement/recipes/building_blocks/{name}.json',{
        'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':item(ingredient)}]}},
        'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':item(name)}}},
        'requirements':[['has_material','has_recipe']],'rewards':{'recipes':[item(name)]}})

for source,name in [('oak_planks','paleheart_planks'),('oak_slab','paleheart_slab'),('oak_stairs','paleheart_stairs'),
                    ('oak_fence','paleheart_fence'),('oak_fence_gate','paleheart_fence_gate'),
                    ('stone_slab','palestone_slab'),('stone_stairs','palestone_stairs'),('stone_brick_wall','palestone_wall')]:
    obj=transform(json.loads(VANILLA.read(f'data/minecraft/recipe/{source}.json')))
    # Do not turn every vanilla log into our planks; this recipe is tied to the custom log family.
    if name=='paleheart_planks':obj['ingredients']=[{'tag':'interstice:paleheart_logs'}]
    if name=='palestone_slab':obj['key']={'#':{'item':item('palestone')}}
    if name=='palestone_stairs':obj['key']={'#':{'item':item('palestone')}}
    if name=='palestone_wall':obj['key']={'#':{'item':item('palestone')}}
    recipe(name,obj,'paleheart_log' if name=='paleheart_planks' else ('paleheart_planks' if name.startswith('paleheart') else 'palestone'))
recipe('palestone_bricks',{'type':'minecraft:crafting_shaped','category':'building','pattern':['##','##'],
        'key':{'#':{'item':item('palestone')}},'result':{'id':item('palestone_bricks'),'count':4}},'palestone')
for target,count in [('palestone_bricks',1),('palestone_slab',2),('palestone_stairs',1),('palestone_wall',1)]:
    recipe(target+'_stonecutting',{'type':'minecraft:stonecutting','ingredient':{'item':item('palestone')},
            'result':{'id':item(target),'count':count}},'palestone')

wood=[name for name in BLOCKS if name.startswith('paleheart') or name=='stripped_paleheart_log']
stone=[name for name in BLOCKS if name.startswith('palestone')]
tag('block','mineable/axe',[n for n in wood if n not in ('paleheart_leaves','paleheart_sapling')])
tag('block','mineable/pickaxe',stone)
tag('block','leaves',['paleheart_leaves']);tag('item','leaves',['paleheart_leaves'])
tag('block','mineable/hoe',['paleheart_leaves','pale_litter'])
for kind in ('block','item'):
    tag(kind,'logs',['paleheart_log','stripped_paleheart_log']);tag(kind,'logs_that_burn',['paleheart_log','stripped_paleheart_log'])
    tag(kind,'paleheart_logs',['paleheart_log','stripped_paleheart_log'],'interstice')
    tag(kind,'planks',['paleheart_planks']);tag(kind,'saplings',['paleheart_sapling'])
    for shape in ('slab','stairs'):
        names=[n for n in BLOCKS if n.endswith('_'+shape)]
        tag(kind,shape+'s',names);tag(kind,'wooden_'+shape+'s',[n for n in names if n.startswith('paleheart')])
    tag(kind,'walls',['palestone_wall']);tag(kind,'fences',['paleheart_fence']);tag(kind,'wooden_fences',['paleheart_fence'])
    tag(kind,'fence_gates',['paleheart_fence_gate'])
tag('block','carpets',['pale_litter']);tag('item','carpets',['pale_litter'])

labels=['Бревно бледносердника','Обтёсанное бревно бледносердника','Доски бледносердника','Листва бледносердника','Саженец бледносердника',
        'Плита из бледносердника','Ступени из бледносердника','Забор из бледносердника','Калитка из бледносердника',
        'Бледный камень','Кирпичи бледного камня','Плита из бледного камня','Ступени из бледного камня','Ограда из бледного камня','Пепельная гребёнка','Бледная подстилка']
for language in ('ru_ru','en_us'):
    path=ASSETS/f'lang/{language}.json';obj=json.loads(path.read_text(encoding='utf-8'))
    obj.update({f'block.interstice.{name}':labels[i] if language=='ru_ru' else name.replace('_',' ').title() for i,name in enumerate(BLOCKS)})
    obj['biome.interstice.pale_gardens']='Бледные сады' if language=='ru_ru' else 'Pale Gardens';write(path,obj)

def tree():
    return {'trunk_provider':{'type':'minecraft:simple_state_provider','state':{'Name':'interstice:paleheart_log','Properties':{'axis':'y'}}},
            'trunk_placer':{'type':'minecraft:straight_trunk_placer','base_height':6,'height_rand_a':1,'height_rand_b':1},
            'foliage_provider':{'type':'minecraft:simple_state_provider','state':{'Name':'interstice:paleheart_leaves','Properties':{'distance':'7','persistent':'false','waterlogged':'false'}}},
            'foliage_placer':{'type':'minecraft:blob_foliage_placer','radius':1,'offset':0,'height':3},
            'dirt_provider':{'type':'minecraft:simple_state_provider','state':{'Name':'interstice:abyssal_turf'}},
            'minimum_size':{'type':'minecraft:two_layers_feature_size','limit':1,'lower_size':0,'upper_size':1},
            'decorators':[],'ignore_vines':True,'force_dirt':False}

# Alien pendant networks; readable Dynamic Trees JoCode path semantics, never stock tree silhouettes.
paths=['UUUUUU[WUWUWDD][EUEUEDD][NUNUNDD]',
       'UUUU[WWUUUENNDD][EEUUUWSSSDD][SSUUUNWWWDD]',
       'UUUUUUU[NNWWSSEEEEDDD][SSEENNWWWWDDD]']
variants=[{'weight':weight,'stem_width':width,'extra_height':extra,'branch_path':path,'tree':tree()}
          for path,weight,width,extra in zip(paths,[5,3,2],[1,2,1],[2,1,2])]
write(DATA/'interstice/interstice_trees/paleheart.json',{'attempts':3,'chance':1,'variants':variants})
print('Generated garden material family, native model/loot templates, 13 recipes and alien branch catalog.')

# A 16x32 ready vine sprite spans two blocks through UV halves; the PNG itself is unchanged.
for section in range(4):
    name='pale_vine_'+str(section);texture='pale_vine_tip' if section>=2 else 'pale_vine'
    uv=[0,8*(section&1),16,8+8*(section&1)]
    faces=[('north','south',[0,0,8],[16,16,8]),('east','west',[8,0,0],[8,16,16])]
    elements=[{'from':start,'to':end,'shade':False,'faces':{direction:{'uv':uv,'texture':'#vine'} for direction in (a,b)}} for a,b,start,end in faces]
    write(ASSETS/f'models/block/{name}.json',{'textures':{'vine':'interstice:block/garden/'+texture,'particle':'#vine'},'render_type':'minecraft:cutout','elements':elements})
write(ASSETS/'blockstates/pale_vine.json',{'variants':{f'section={n}':{'model':f'interstice:block/pale_vine_{n}'} for n in range(4)}})
write(ASSETS/'models/item/pale_vine.json',{'parent':'minecraft:item/generated','textures':{'layer0':'interstice:block/garden/pale_vine'}})
write(DATA/'interstice/loot_table/blocks/pale_vine.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'interstice:pale_vine'}],
    'conditions':[{'condition':'minecraft:match_tool','predicate':{'items':'minecraft:shears'}},{'condition':'minecraft:survives_explosion'}]}]})
tag('block','climbable',['pale_vine'])
tag('block','mineable/hoe',['pale_vine'])
for language,label in [('ru_ru','Бледная лиана'),('en_us','Pale Vine')]:
    path=ASSETS/f'lang/{language}.json';obj=json.loads(path.read_text(encoding='utf-8'));obj['block.interstice.pale_vine']=label;write(path,obj)
