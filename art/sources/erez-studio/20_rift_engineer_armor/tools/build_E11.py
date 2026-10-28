"""Owner: darker respirator components and a clean chest without emblem or backing."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E10_parameters.json').read_text(encoding='utf-8'))
d['revision']='EREZ-ENGINEER-ARMOR-E11'
flat={'mask_cheek':'#66727d','mask_filter':'#414e5a','mask_ridge':'#6d7984','mask_rim':'#52606c','mask_hose':'#515f6b','mask_coupler':'#384550','mask_joint':'#252f39'}
d['flat_mask_materials']=flat;d['palette'].update(flat)
d['parts']=[p for p in d['parts'] if not p['name'].startswith('chest_star_') and p['name']!='chest_emblem_patch']
d['owner_corrections']+=['Darken the flat respirator materials while preserving component differences','Remove the front Star of David and its square backing; retain rear insignia']
d['front_insignia']=False
(ROOT/'source/armor_E11_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
s=(ROOT/'tools/build_blender_E10.py').read_text(encoding='utf-8').replace('E10','E11')
(ROOT/'tools/build_blender_E11.py').write_text(s,encoding='utf-8')
print('E11: darker mask, clean chest, rear insignia retained')
