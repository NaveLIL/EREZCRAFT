"""Owner-directed flat, component-specific light-gray respirator materials."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E08_parameters.json').read_text(encoding='utf-8'))
d['revision']='EREZ-ENGINEER-ARMOR-E09'
flat={'mask_cheek':'#b8bec2','mask_filter':'#939ca3','mask_ridge':'#c9ced1','mask_rim':'#a5aeb4','mask_hose':'#d3d7d9','mask_coupler':'#8c969e','mask_joint':'#56616a'}
d['palette'].update(flat)
for p in d['parts']:
    n=p['name']
    if n.startswith('cheek_plate_'):p['role']='mask_cheek'
    elif n=='respirator_center':p['role']='mask_filter'
    elif n=='respirator_center_ridge':p['role']='mask_ridge'
    elif n=='mask_lower_rim':p['role']='mask_rim'
    elif n.startswith('brass_breathing_hose_'):p['role']='mask_hose'
    elif n.startswith(('hose_upper_coupler_','hose_lower_coupler_')):p['role']='mask_coupler'
d['flat_mask_materials']=flat
d['owner_corrections']+=['Remove mottled/worn textures from facial mask','Each respirator component uses its own uniform light-gray shade; vents remain dark']
(ROOT/'source/armor_E09_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
s=(ROOT/'tools/build_blender_E08.py').read_text(encoding='utf-8').replace('E08','E09')
needle="    elif role in ('worn_metal'"
replacement="""    elif role in data['flat_mask_materials']:
        base=tuple(int(hexcolor[i:i+2],16) for i in (1,3,5));dst=Image.new('RGBA',(1,1),base+(255,))
        original=ROOT/'source/armor_E09_parameters.json';operation='Uniform authored 3D component color; no texture noise, wear or baked lighting'
    elif role in ('worn_metal'"""
assert needle in s;s=s.replace(needle,replacement)
# Uniform shader colors are authored materials, not transformations of a raster original.
old="    provenance.append({'role':role,'original':str(original),'derived':derived.name,'sha256':sha(derived.read_bytes()),'operation':operation,'tint':hexcolor})"
new="""    row={'role':role,'original':str(original),'sha256':sha(derived.read_bytes()),'operation':operation,'tint':hexcolor}
    row['material_file' if role in data['flat_mask_materials'] else 'derived']=derived.name
    provenance.append(row)"""
assert old in s;s=s.replace(old,new)
# Keep the physical bevel below half the plate thickness. At exactly half,
# Blender may emit collapsed edge quads that the strict native icon baker rejects.
s=s.replace("bevel.width=p['bevel'];", "bevel.width=min(p['bevel'],.45*min(p['size'])) if 'size' in p else p['bevel'];")
s=s.replace("(ROOT/'preview').glob('*.png')", "(ROOT/'preview').glob('*E09*.png')")
(ROOT/'tools/build_blender_E09.py').write_text(s,encoding='utf-8')
print('E09: uniform mask colors authored for each component')
