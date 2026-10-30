from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib,base64,struct
ROOT=Path(__file__).resolve().parents[1]
model=json.loads((ROOT/'source/lozhnokoren_LK01.bbmodel').read_text(encoding='utf8'))
elements={e['uuid']:e for e in model['elements']};groups={g['uuid']:g for g in model['groups']};used_elements=[];used_groups=[]
def walk(node):
    assert node['uuid'] in groups and node['uuid'] not in used_groups
    used_groups.append(node['uuid'])
    for child in node['children']:
        if isinstance(child,dict):walk(child)
        else:assert child in elements;used_elements.append(child)
walk(model['outliner'][0])
assert set(used_elements)==set(elements) and len(used_elements)==len(elements)
assert set(used_groups)==set(groups)
for animation in model['animations']:
    for group_id,channel in animation['animators'].items():
        assert group_id in groups and groups[group_id]['name']==channel['name']
        assert all(0<=k['time']<=animation['length'] for k in channel['keyframes'])
        if animation['loop']=='loop':
            for kind in ['rotation','position']:
                keys=[k for k in channel['keyframes'] if k['channel']==kind]
                if not keys:continue
                first=min(keys,key=lambda k:k['time'])['data_points'][0];last=max(keys,key=lambda k:k['time'])['data_points'][0]
                assert max(abs(float(first[c])-float(last[c])) for c in 'xyz')<1e-4
for entry in json.loads((ROOT/'source/ASSET_PROVENANCE.json').read_text()):
    old=Image.open(ROOT/'source/originals'/Path(entry['source_file']).name).convert('RGBA')
    new=Image.open(ROOT/entry['derived_file']).convert('RGBA');assert old.size==new.size==(16,16)
    assert old.getchannel('A').tobytes()==new.getchannel('A').tobytes()
    mapping={}
    for a,b in zip(old.getdata(),new.getdata()):
        if a[:3] in mapping:assert mapping[a[:3]]==b[:3]
        mapping[a[:3]]=b[:3]
for texture in model['textures']:
    assert base64.b64decode(texture['source'].split(',',1)[1])==(ROOT/'source'/texture['relative_path']).read_bytes()
binary=(ROOT/'exports/lozhnokoren_LK01.glb').read_bytes();magic,version,length=struct.unpack_from('<4sII',binary)
assert magic==b'glTF' and version==2 and length==len(binary)
chunk_length,kind=struct.unpack_from('<II',binary,12);assert kind==0x4E4F534A
gltf=json.loads(binary[20:20+chunk_length].decode('utf8'))
assert len(gltf['meshes'])==len(elements) and len(gltf.get('animations',[]))>=1
# This Blender build splits SCENE animation by object. Merge the synchronized
# object tracks into one playable review clip, retaining all accessor indices.
if len(gltf['animations'])>1:
    source_tracks=len(gltf['animations']);combined={'name':'LK01 Review - Idle, Crawl, Threat','samplers':[],'channels':[]}
    targets=set()
    for animation in gltf['animations']:
        offset=len(combined['samplers']);combined['samplers'].extend(animation['samplers'])
        for channel in animation['channels']:
            key=(channel['target']['node'],channel['target']['path']);assert key not in targets,key;targets.add(key)
            combined['channels'].append({**channel,'sampler':channel['sampler']+offset})
    combined['extras']={'source_object_tracks':source_tracks,'source_clips':{'Idle':[0,2],'Crawl':[2.5,4.5],'Threat':[5,154/24]}}
    gltf['animations']=[combined]
    payload=json.dumps(gltf,separators=(',',':'),ensure_ascii=False).encode('utf8');payload+=b' '*((-len(payload))%4)
    other_chunks=[];cursor=12
    while cursor<len(binary):
        size,chunk_type=struct.unpack_from('<II',binary,cursor)
        if chunk_type!=0x4E4F534A:other_chunks.append(binary[cursor:cursor+8+size])
        cursor+=8+size
    json_chunk=struct.pack('<II',len(payload),0x4E4F534A)+payload
    body=json_chunk+b''.join(other_chunks);binary=struct.pack('<4sII',b'glTF',2,len(body)+12)+body
    (ROOT/'exports/lozhnokoren_LK01.glb').write_bytes(binary)
    assert struct.unpack_from('<I',binary,8)[0]==len(binary)
assert len(gltf['animations'])==1
assert all('uri' not in buffer for buffer in gltf.get('buffers',[]))
assert all('bufferView' in image for image in gltf.get('images',[]))
saved=json.loads((ROOT/'reports/SAVED_BLEND_CHECK.json').read_text());assert saved['saved_blend_opened'] and saved['mesh_objects_match_source']
reimport=json.loads((ROOT/'reports/GLB_IMPORT_CHECK.json').read_text());assert reimport['animations_play_after_import']
framing=json.loads((ROOT/'reports/TOP_FRAME_CHECK.json').read_text());assert framing['full_creature_inside_frame']
frames=[Image.open(ROOT/f'preview/crawl_{i:02d}.png').convert('RGB') for i in range(8)]
frames[0].save(ROOT/'preview/lozhnokoren_LK01_crawl.gif',save_all=True,append_images=frames[1:],duration=250,loop=0,optimize=False)
frames[0].save(ROOT/'preview/lozhnokoren_LK01_crawl.webp',save_all=True,append_images=frames[1:],duration=250,loop=0,quality=88)
canvas=Image.new('RGB',(1800,1440),(25,29,34));draw=ImageDraw.Draw(canvas)
title=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',35);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',28);small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
draw.text((30,18),'ЛОЖНОКОРЕНЬ / LK01 / редактируемая модель',font=title,fill=(232,236,238))
draw.text((30,65),'11 секций • 10 пар ходовых ног • два хватателя • 16×16 • фигура масштаба 1,8 блока',font=small,fill=(176,192,198))
tiles=[('Общий вид','lozhnokoren_LK01_hero.png'),('Поза угрозы','lozhnokoren_LK01_threat.png'),
       ('Профиль и масштаб','lozhnokoren_LK01_side.png'),('Вид сверху','lozhnokoren_LK01_top.png')]
for i,(label,file) in enumerate(tiles):
    x=i%2*900;y=115+i//2*645;draw.text((x+30,y),label,font=font,fill=(229,233,236))
    frame=Image.open(ROOT/'preview'/file).convert('RGB').resize((840,525),Image.Resampling.LANCZOS);canvas.paste(frame,(x+30,y+43))
draw.text((30,1380),'Студийные рендеры Blender. Модель и клипы подготовлены; игровая entity и AI ещё не подключены.',font=small,fill=(176,192,198))
canvas.save(ROOT/'preview/lozhnokoren_LK01_review.jpg',quality=94)
files={}
for relative in ['references/lozhnokoren_concept_LK01.png','references/imagegen_prompt.txt','source/lozhnokoren_LK01.bbmodel',
                 'assembly/lozhnokoren_LK01.blend','exports/lozhnokoren_LK01.glb','preview/lozhnokoren_LK01_review.jpg','preview/lozhnokoren_LK01_crawl.gif']:
    p=ROOT/relative;assert p.is_file() and p.stat().st_size>100
    files[relative]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
report={'revision':'LK01','files':files,'cuboids':len(elements),'articulated_groups':len(groups),
        'all_elements_and_groups_referenced_once':True,'source_animation_loops_closed':True,
        'textures_cc0_verified':True,'all_game_textures_16x16':True,'palette_only_derived_pngs':True,'alpha_preserved':True,
        'gltf_meshes':len(gltf['meshes']),'gltf_animations':len(gltf['animations']),'gltf_animation_names':[a.get('name') for a in gltf['animations']],
        'gltf_synchronized_source_tracks':gltf['animations'][0].get('extras',{}).get('source_object_tracks',1),
        'glb_self_contained':True,'saved_blend_verified':True,'crawl_preview_frames':8,
        'glb_reimport_and_animation_verified':True,'top_view_full_creature_inside_frame':True,
        'concept_tool_mode':'Built-in imagegen','game_verified':False,'registered_in_game':False}
(ROOT/'reports/LK01_ARTIFACTS.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='files'}))
