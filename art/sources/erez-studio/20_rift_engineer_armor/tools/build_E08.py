"""Final owner color correction: distinguish the facial respirator from the hood."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E07_parameters.json').read_text(encoding='utf-8'))
d['revision']='EREZ-ENGINEER-ARMOR-E08'
d['palette']['mask_shell']='#46535b'
for p in d['parts']:
    if p['name']=='respirator_center':p['role']='mask_shell'
d['owner_corrections']+=['Facial respirator in contrasting cold steel gray; dark rubber rim and visible wear retained']
(ROOT/'source/armor_E08_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
s=(ROOT/'tools/build_blender_E07.py').read_text(encoding='utf-8').replace('E07','E08')
# Original production tiles remain immutable E07 assets. E08 is a material palette revision.
s=s.replace("('E08_'+material+'_pixels32.png')","('E07_'+material+'_pixels32.png')")
s=s.replace("('worn_metal','worn_leather','rubber','back_patch')","('worn_metal','worn_leather','rubber','back_patch','mask_shell')")
s=s.replace("'back_patch':'rubber'}","'back_patch':'rubber','mask_shell':'metal'}")
needle="        if role=='back_patch':dst.putdata([tuple(round(c*.80) for c in (r,g,b))+(a,) for r,g,b,a in src.getdata()])"
replacement=needle+"""
        if role in ('worn_metal','mask_shell'):
            luminance=[.2126*r+.7152*g+.0722*b for r,g,b,a in src.getdata()];mean=sum(luminance)/len(luminance)
            base=(91,103,112) if role=='worn_metal' else (65,77,85)
            dst.putdata([tuple(min(170,round(c*max(.62,min(1.48,L/mean)))) for c in base)+(a,) for (r,g,b,a),L in zip(src.getdata(),luminance)])"""
assert needle in s;s=s.replace(needle,replacement)
s=s.replace('materials/generation_E08.json','materials/generation_E07.json')
(ROOT/'tools/build_blender_E08.py').write_text(s,encoding='utf-8')
print('E08 color correction prepared')
