"""Import unchanged CC-BY-4.0 item sprites pinned to the existing art library revision."""
from pathlib import Path
import hashlib,json,urllib.request,shutil
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT/.verification/armor_a01')
CAT=Path('D:/repos/Blender/catalog/minecraft_aegis_electronics')
REV='8b85ad732023174d9a9da74e6bf2272d6c0bef17'
NAMES={'abyssal_alloy':'ingot_abyssium','abyssal_plate':'part_plate_abyssium','ceramic_substrate':'part_plate_small_ceramic',
 'etched_circuit_board':'part_electronic_circuit_printed','resonant_processor':'part_electronic_circuit_integrated',
 'aegis_optics':'gem_lens','aegis_control_unit':'part_electronic_magic_circuit_2',
 'aegis_helmet':'armor_helmet_adamantium','aegis_chestplate':'armor_chest_adamantium','aegis_leggings':'armor_legs_adamantium','aegis_boots':'armor_boots_adamantium'}
CAT.mkdir(parents=True,exist_ok=True);records=[]
for name,sprite in NAMES.items():
    url=f'https://raw.githubusercontent.com/MalcolmRiley/unused-textures/{REV}/items/{sprite}.png'
    target=CAT/(sprite+'.png')
    if not target.exists():target.write_bytes(urllib.request.urlopen(url).read())
    dest=GAME/f'src/main/resources/assets/interstice/textures/item/aegis/{sprite}.png';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,dest)
    art=GAME/'art/sources/cc-by/aegis'/target.name;art.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,art)
    if sprite=='part_electronic_magic_circuit_2':
        meta=CAT/(sprite+'.png.mcmeta')
        if not meta.exists():meta.write_bytes(urllib.request.urlopen(url+'.mcmeta').read())
        shutil.copy2(meta,str(dest)+'.mcmeta');shutil.copy2(meta,art.parent/meta.name)
    records.append({'item':name,'sprite':sprite,'source_url':url,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'operation':'unchanged original bytes'})
license_data=(GAME/'art/sources/cc-by/armaments/LICENSE.txt').read_bytes()
(CAT/'LICENSE.txt').write_bytes(license_data);(GAME/'art/sources/cc-by/aegis/LICENSE.txt').write_bytes(license_data)
data={'author':'Malcolm Riley','license':'CC-BY-4.0','repository':'https://github.com/MalcolmRiley/unused-textures','revision':REV,'sources':records}
for p in [CAT/'sources.json',GAME/'art/sources/cc-by/aegis/sources.json',GAME/'src/main/resources/assets/interstice/provenance/aegis-sprites.json']:
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
print('Imported',len(records),'unchanged sprites')
