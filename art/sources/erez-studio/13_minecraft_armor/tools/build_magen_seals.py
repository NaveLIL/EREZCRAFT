"""A04: close two skin gaps observed on actual native player models."""
from pathlib import Path
import json,copy,hashlib
ROOT=Path(__file__).resolve().parents[1]
previous=json.loads((ROOT/'source/armor_design_A03.json').read_text(encoding='utf-8'))
data=copy.deepcopy(previous)
cubes=data['sets']['aegis']['cubes']
cubes.extend([
 {'name':'neck_flexible_seal','bone':'head','slot':'head','from':[-4.6,-.55,-4.6],'size':[9.2,.75,9.2],'role':'cloth'},
 {'name':'waist_flexible_seal','bone':'body','slot':'legs','from':[-4.35,10.8,-2.68],'size':[8.7,1.5,5.36],'role':'cloth'}])
data['revision']='ARMOR-A04';data['state']='Native player QA: neck and waist skin gaps closed on Magen; other sets unchanged'
assert len(cubes)==124
assert all(data['sets'][tier]==previous['sets'][tier] for tier in ['riftsilver','riftsteel'])
text=json.dumps(data,indent=2,ensure_ascii=False)+'\n'
paths=[ROOT/'source/armor_design_A04.json',Path('D:/repos/Minecraft/EREZCRAFT/src/main/resources/assets/interstice/armor/armor_design.json'),
 Path('D:/repos/Minecraft/EREZCRAFT/.verification/ag03/src/main/resources/assets/interstice/armor/armor_design.json')]
for p in paths:
    if p.exists():assert json.loads(p.read_text(encoding='utf-8'))['revision'] in ['ARMOR-A03','ARMOR-A04'],p
    p.write_text(text,encoding='utf-8')
requirements=ROOT/'source/requirements.json';r=json.loads(requirements.read_text(encoding='utf-8'));r['authoritative_geometry']='armor_design_A04.json'
r['native_qa_fixes']=['Close exposed skin below the rear helmet edge','Close exposed skin at the waist with a flexible undersuit band']
requirements.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('A04: 124 Magen cubes; silver and steel unchanged')
