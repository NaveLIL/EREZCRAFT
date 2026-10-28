from pathlib import Path
import json,zipfile,hashlib,io
from PIL import Image
root=Path(r'D:/repos/Blender/creations/19_realm_worktools');jar=Path(r'C:/Users/Dimon/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
record={'minecraft':'1.21.1','source_jar':str(jar),'source_jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'rows':[]}
with zipfile.ZipFile(jar) as z:
 def resolve(name):
  raw=z.read('assets/minecraft/models/'+name+'.json');d=json.loads(raw);chain=[{'path':'assets/minecraft/models/'+name+'.json','sha256':hashlib.sha256(raw).hexdigest()}]
  if 'parent' in d and not d['parent'].removeprefix('minecraft:').startswith('builtin/'):
   p,c=resolve(d['parent'].removeprefix('minecraft:'));chain+=c;d=p|d|{'display':p.get('display',{})|d.get('display',{})}
  return d,chain
 for kind in ['pickaxe','axe','shovel','hoe']:
  d,chain=resolve('item/iron_'+kind);data=z.read('assets/minecraft/textures/item/iron_'+kind+'.png');im=Image.open(io.BytesIO(data)).convert('RGBA')
  ours=json.loads((Path(r'D:/repos/Minecraft/EREZCRAFT/src/main/resources/assets/interstice/models/item')/('riftsteel_'+kind+'.json')).read_text())
  record['rows'].append({'kind':kind,'vanilla_model_chain':chain,'vanilla_texture_sha256':hashlib.sha256(data).hexdigest(),'vanilla_texture_size':im.size,'vanilla_alpha_bounds':im.getchannel('A').getbbox(),'vanilla_display':d['display'],'WT04_display':ours['display']})
(root/'reports/vanilla_reference/static-reference.json').write_text(json.dumps(record,indent=2),encoding='utf8');print(json.dumps({'rows':len(record['rows']),'source_hash':record['source_jar_sha256']}))
