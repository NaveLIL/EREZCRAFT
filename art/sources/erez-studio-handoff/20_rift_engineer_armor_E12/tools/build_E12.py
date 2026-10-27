"""Expose the actual leather belt above the cloth waistband bridges."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E11_parameters.json').read_text(encoding='utf-8'));d['revision']='EREZ-ENGINEER-ARMOR-E12'
for p in d['parts']:
    n=p['name']
    if n=='belt':p['offset']=[-4.78,8.25,-2.9];p['size']=[9.56,1.4,6.02];p['role']='worn_leather'
    elif n.startswith(('belt_buckle_','belt_tongue','belt_front_pouch_','pouch_flap_','pouch_clasp_')):p['offset'][2]-=.32
    elif n.startswith(('belt_rear_pouch_','rear_pouch_clasp_','belt_back_loop_')):p['offset'][2]+=.60
d['owner_corrections']+=['Place leather waist belt outside the moving cloth attachment bridges; shift brass buckle, pouches and keepers with the belt']
d['belt_visibility_fix']={'cause':'The leather belt surface was occluded by cloth waistband bridges introduced for skirt motion','old_bounds':{'x':[-4.48,4.48],'z':[-2.55,2.55]},'new_bounds':{'x':[-4.78,4.78],'z':[-2.9,3.12]},'cloth_geometry_unchanged':True}
(ROOT/'source/armor_E12_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
s=(ROOT/'tools/build_blender_E11.py').read_text(encoding='utf-8').replace('E11','E12')
(ROOT/'tools/build_blender_E12.py').write_text(s,encoding='utf-8')
print('E12: leather belt outside cloth, matched buckle and pouch mounts')
