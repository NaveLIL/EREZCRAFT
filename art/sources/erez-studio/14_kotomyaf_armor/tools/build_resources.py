"""Recipes, localization, tags and original box-based inventory models; no painted textures."""
from pathlib import Path
import json,math
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT/.verification/kt01')
RES=GAME/'src/main/resources'
design=json.loads((ROOT/'source/kotomyaf_design_A04.json').read_text(encoding='utf-8'))
def write(name,data):
    p=RES/name
    # Preserve final derived inventory miniatures from project 15.
    if p.exists() and data.get("loader") != "interstice:armor_icon":
        previous=json.loads(p.read_text(encoding='utf-8'))
        if isinstance(previous,dict) and previous.get('loader')=='interstice:armor_icon':return
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
kinds={'helmet':'head','chestplate':'chest','leggings':'legs','boots':'feet'}
patterns={'helmet':['WFW','W W'],'chestplate':['W W','WFW','WWW'],'leggings':['WWW','WFW','W W'],'boots':['W W','WFW']}
for kind,slot in kinds.items():
    name='kotomyaf_'+kind
    write('data/interstice/recipe/'+name+'.json',{'type':'minecraft:crafting_shaped','category':'equipment','pattern':patterns[kind],
          'key':{'W':{'tag':'minecraft:wool'},'F':{'tag':'minecraft:fishes'}},'result':{'id':'interstice:'+name,'count':1}})
    write('data/interstice/advancement/recipes/kotomyaf/'+name+'.json',{'parent':'minecraft:recipes/root','criteria':{'has_wool':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'#minecraft:wool'}]}}},
          'requirements':[['has_wool']],'rewards':{'recipes':['interstice:'+name]}})
    tags=['enchantable/armor','enchantable/durability','enchantable/equippable','trimmable_armor',
          'enchantable/'+{'head':'head_armor','chest':'chest_armor','legs':'legs_armor','feet':'foot_armor'}[slot]]
    for tag in tags:
        path='data/minecraft/tags/item/'+tag+'.json';p=RES/path;data=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
        if 'interstice:'+name not in data['values']:data['values'].append('interstice:'+name)
        write(path,data)
    # A real 3D item icon assembled from the SAME cuboids as the wearable model.
    # Vanilla block textures supply the Siamese palette; no raster asset is authored.
    textures={'plate':'white_wool','edge':'white_concrete','shadow':'brown_wool','cloth':'brown_concrete','strap':'pink_terracotta','buckle':'light_blue_concrete','dark':'black_concrete'}
    cubes=[c for c in design['sets']['kotomyaf']['cubes'] if c['slot']==slot]
    if slot in ('chest','legs','feet'):
        # One centered representative limb plus torso parts gives a readable native inventory icon.
        cubes=[c for c in cubes if c['bone'] in (('body',) if slot=='chest' else ('body','right_leg') if slot=='legs' else ('right_leg',))]
    elements=[]
    shift={'head':(8,4,8),'chest':(8,13,8),'legs':(8,13,8),'feet':(8,19,8)}[slot]
    for c in cubes:
        x,y,z=c['from'];dx,dy,dz=c['size'];sx,sy,sz=shift
        e={'from':[round(x+sx,4),round(sy-y-dy,4),round(z+sz,4)],'to':[round(x+sx+dx,4),round(sy-y,4),round(z+sz+dz,4)],
           'faces':{s:{'texture':'#'+c['role'],'uv':[0,0,16,16]} for s in ['north','south','east','west','up','down']}}
        if c.get('rotation'):
            # Inventory uses the same silhouette but native item models quantize single-axis rotation.
            angles=c['rotation'];axis=next((i for i,a in enumerate(angles) if a),None)
            if axis is not None:
                angle=min([-45,-22.5,0,22.5,45],key=lambda a:abs(a-(-angles[axis] if axis!=1 else angles[axis])))
                if angle:
                    px,py,pz=c['rotation_pivot'];e['rotation']={'origin':[px+sx,sy-py,pz+sz],'axis':'xyz'[axis],'angle':angle,'rescale':False}
        elements.append(e)
    write('assets/interstice/models/item/'+name+'.json',{'ambientocclusion':False,'gui_light':'front','textures':{**{k:'minecraft:block/'+v for k,v in textures.items()},'particle':'minecraft:block/white_wool'},
       'elements':elements,'display':{'gui':{'rotation':[15,155,0],'translation':[0,0,0],'scale':[.8,.8,.8]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.35,.35,.35]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]},'thirdperson_righthand':{'rotation':[75,0,0],'translation':[0,2,0],'scale':[.35,.35,.35]},'firstperson_righthand':{'rotation':[0,155,0],'translation':[0,0,0],'scale':[.55,.55,.55]}}})
ru={'helmet':'Ушастый капюшон Котомяф','chestplate':'Шерстяная курточка Котомяф','leggings':'Шерстяные штанишки Котомяф','boots':'Мягкие лапки Котомяф'}
en={'helmet':'Kotomyaf Cat Hood','chestplate':'Kotomyaf Wool Jacket','leggings':'Kotomyaf Wool Leggings','boots':'Kotomyaf Soft Paws'}
for lang,names in [('ru_ru',ru),('en_us',en)]:
    p=RES/'assets/interstice/lang'/f'{lang}.json';data=json.loads(p.read_text(encoding='utf-8'))
    data.update({'item.interstice.kotomyaf_'+kind:name for kind,name in names.items()})
    data.update({'tooltip.interstice.kotomyaf.material':'Сиамская шерсть, рыбка и немного кошачьего упрямства' if lang=='ru_ru' else 'Siamese wool, fish and a little feline stubbornness',
       'tooltip.interstice.kotomyaf.set':'Полный комплект: нет урона от падения; криперы и маленькие зомби боятся в радиусе 8 блоков' if lang=='ru_ru' else 'Full set: no fall damage; creepers and baby zombies fear you within 8 blocks',
       'tooltip.interstice.kotomyaf.sounds':'Прыжок — мяу! Движение на шифте — муррр…' if lang=='ru_ru' else 'Jump: meow! Sneak-walk: purrr…'})
    write('assets/interstice/lang/'+lang+'.json',data)
write('assets/interstice/provenance/kotomyaf.json',{'author':'EREZ / Codex for the Kotomyaf request','geometry':'Original KOTOMYAF-A04 box geometry, open face','texture_policy':'Runtime references to installed Minecraft textures, no copied PNG or OGG in this mod',
    'sounds':['minecraft:entity.cat.ambient','minecraft:entity.cat.purr'],'sound_policy':'Finite native instances, queued at completion, never explicitly stopped by this feature'})
print('Kotomyaf: four recipes, recipe unlocks, 3D item models, RU/EN, repair/enchantment tags')
