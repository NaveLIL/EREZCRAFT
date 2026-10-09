"""V5 forest ecology: whole declared-CC0 sprites, palette-only, own blocks/models/loot."""
from pathlib import Path
from PIL import Image
import hashlib,io,json,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]; A=ROOT/'src/main/resources/assets/interstice'; D=ROOT/'src/main/resources/data/interstice'; SRC=ROOT/'art/sources/cc0/forest-ecology'
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(data):return hashlib.sha256(data).hexdigest()
sources=[]
for archive,member,name,author,url in [
 ('research/cc0-20261008/blocks_2.zip','blocks/plants/thistle.png','thistle.png','ARoachIFoundOnMyPillow','https://opengameart.org/content/16x16-block-texture-set'),
 ('research/leaf-source-preview/assorted_2.zip','glow mushroom poisonous cap.png','poison_cap.png','JoeEnderman','https://opengameart.org/content/assorted-minecraft-style-textures')]:
 archive_path=ROOT/archive;data=zipfile.ZipFile(archive_path).read(member);SRC.mkdir(parents=True,exist_ok=True);(SRC/name).write_bytes(data)
 sources.append(dict(file=name,source_member=member,source_url=url,author=author,license='CC0-1.0',sha256=sha(data),archive=archive,archive_sha256=sha(archive_path.read_bytes())))
write(SRC/'sources.json',dict(retrieved='2026-10-09',operation='Whole source files; only bijective palette substitution',sources=sources))
shutil.copy2(ROOT/'art/sources/cc0/minerals/CC0-1.0.html',SRC/'CC0-1.0.html')
outputs=[]
for source,name,base in [('thistle.png','venom_reed',(140,91,109)),('thistle.png','venom_fiber',(179,155,130)),('poison_cap.png','spore_pod_armed',(174,168,105)),('poison_cap.png','spore_pod_spent',(112,96,108))]:
 original=Image.open(SRC/source).convert('RGBA');assert original.size==(16,16)
 pixels=list(original.get_flattened_data());colors=sorted(set(pixels),key=lambda c:(c[0]*.2126+c[1]*.7152+c[2]*.0722,c));palette={};used=set()
 for i,color in enumerate(colors):
  scale=.32+.9*i/max(1,len(colors)-1);rgb=tuple(min(255,int(v*scale)) for v in base);mapped=(*rgb,color[3])
  while mapped in used: mapped=(mapped[0],mapped[1],(mapped[2]+1)%256,mapped[3])
  palette[color]=mapped;used.add(mapped)
 result=Image.new('RGBA',original.size);result.putdata([palette[p] for p in pixels]);target=A/f'textures/block/forest/{name}.png';target.parent.mkdir(parents=True,exist_ok=True);result.save(target)
 stored=Image.open(target).convert('RGBA');forward={};reverse={}
 for first,last in zip(pixels,stored.get_flattened_data()):
  assert first[3]==last[3] and (first not in forward or forward[first]==last) and (last not in reverse or reverse[last]==first);forward[first]=last;reverse[last]=first
 outputs.append(dict(source=source,output=f'block/forest/{name}.png',source_sha256=sha((SRC/source).read_bytes()),output_sha256=sha(target.read_bytes()),classes=len(colors)))
write(A/'provenance/forest-ecology.json',dict(license='CC0-1.0',sources='art/sources/cc0/forest-ecology/sources.json',generator='tools/generate_forest_ecology_resources.py',pattern_and_alpha_verified=True,operation='Bijective palette substitution; full16x16 source coordinates and alpha preserved',outputs=outputs))
write(A/'models/block/forest/venom_reed.json',dict(parent='minecraft:block/cross',render_type='minecraft:cutout',textures={'cross':'interstice:block/forest/venom_reed'}))
write(A/'blockstates/venom_reed.json',dict(variants={'':{'model':'interstice:block/forest/venom_reed'}}))
write(A/'models/item/venom_reed.json',dict(parent='minecraft:item/generated',textures={'layer0':'interstice:block/forest/venom_reed'}))
write(A/'models/item/venom_fiber.json',dict(parent='minecraft:item/generated',textures={'layer0':'interstice:block/forest/venom_fiber'}))
for phase,height,texture in [('armed',5,'spore_pod_armed'),('primed',6,'spore_pod_armed'),('spent',2,'spore_pod_spent')]:
 planes=[]
 for angle in [-45,45]:planes.append({'from':[1,0,8],'to':[15,height,8],'rotation':{'origin':[8,0,8],'axis':'y','angle':angle,'rescale':True},'faces':{side:{'uv':[0,0,16,16],'texture':'#pod'} for side in ['north','south']}})
 write(A/f'models/block/forest/spore_pod_{phase}.json',dict(parent='minecraft:block/block',render_type='minecraft:cutout',textures={'pod':'interstice:block/forest/'+texture,'particle':'interstice:block/forest/'+texture},elements=planes))
write(A/'blockstates/spore_pod.json',{'multipart':[{'when':{'phase':phase},'apply':{'model':f'interstice:block/forest/spore_pod_{phase}'}} for phase in ['armed','primed','spent']]})
write(A/'models/item/spore_pod_shell.json',dict(parent='minecraft:item/generated',textures={'layer0':'interstice:block/forest/spore_pod_spent'}))
write(D/'loot_table/blocks/venom_reed.json',{'type':'minecraft:block','pools':[]})
write(D/'loot_table/blocks/spore_pod.json',{'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'interstice:spore_pod','properties':{'phase':'spent'}}],'entries':[{'type':'minecraft:item','name':'interstice:spore_pod_shell'}]}]})
write(D/'recipe/retort_venom_fiber.json',dict(type='interstice:retort',inputs=[{'ingredient':{'item':'interstice:venom_fiber'},'count':4},{'ingredient':{'item':'interstice:umbral_sorbent'},'count':1}],outputs=[{'id':'interstice:plant_fiber','count':4}],ticks=600))
sounds=json.loads((A/'sounds.json').read_text(encoding='utf-8'));sounds['spore_pod_warning']={'subtitle':'subtitles.interstice.spore_pod.warn','sounds':[{'name':'minecraft:block.brewing_stand.brew','type':'event'}]};write(A/'sounds.json',sounds)
write(ROOT/'tools/forest_ecology_lang.json',{
 'block.interstice.venom_reed':['Жалящий камыш','Venom reed'],
 'block.interstice.spore_pod':['Споровый стручок','Spore pod'],
 'item.interstice.venom_fiber':['Ядовитое волокно','Venom fiber'],
 'item.interstice.spore_pod_shell':['Обезвреженная оболочка','Defused pod shell'],
 'tooltip.interstice.spore_pod_shell':['Ставится безопасной. Биопаста вооружает; ножницы обезвреживают живой стручок.','Placed safely. Biopaste arms it; shears defuse a live pod.'],
 'subtitles.interstice.spore_pod.warn':['Стручок надувается','Spore pod swells']})
print('Verified4 whole16x16CC0 forest palettes; species, three pod phases and fiber-cleaning recipe generated')
