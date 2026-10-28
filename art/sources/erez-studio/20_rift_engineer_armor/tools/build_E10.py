"""Muted component colors and integrated cheek intake cartridges instead of exposed hoses."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
d=json.loads((ROOT/'source/armor_E09_parameters.json').read_text(encoding='utf-8'));parts=d['parts']
flat={'mask_cheek':'#7d8892','mask_filter':'#596672','mask_ridge':'#8f9aa4','mask_rim':'#6c7883','mask_hose':'#66737c','mask_coupler':'#4b5863','mask_joint':'#343f49'}
d['flat_mask_materials']=flat;d['palette'].update(flat);d['revision']='EREZ-ENGINEER-ARMOR-E10'
parts[:]=[p for p in parts if not p['name'].startswith(('brass_breathing_hose_','hose_upper_coupler_','hose_lower_coupler_'))]
SIDES={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),'east':(5,1,2,6),'down':(0,1,5,4),'up':(7,6,2,3)}
def panel(name,sign,front,thickness,role):
    front=[[sign*x,y,z] for x,y,z in front]
    parts.append(dict(name=name,bone='head',slot='helmet',role=role,vertices=front+[[x,y,z+thickness] for x,y,z in front],faces=[dict(indices=list(v),side=k) for k,v in SIDES.items()],rotation=[0,0,0],pivot=[0,0,0],glow=False))
for sign in (-1,1):
    panel('mask_intake_frame_'+str(sign),sign,[[1.5,-2.85,-5.43],[2.68,-2.7,-5.12],[2.34,-.48,-5.19],[1.5,-.28,-5.52]],.17,'mask_coupler')
    panel('mask_intake_recess_'+str(sign),sign,[[1.65,-2.63,-5.48],[2.47,-2.5,-5.28],[2.22,-.72,-5.34],[1.65,-.55,-5.55]],.07,'mask_joint')
    for i in range(4):
        y=-2.43+i*.44;xh=2.43-i*.065;zh=-5.32-i*.015
        panel('mask_intake_louver_'+str(sign)+'_'+str(i),sign,[[1.69,y,-5.59],[xh,y+.08,zh],[xh-.015,y+.20,zh],[1.69,y+.12,-5.59]],.045,'mask_rim')
# Cover the small vanilla-shirt strip visible between the split coat and the leg pivots.
parts.append(dict(name='trouser_waist_underlayer',bone='body',slot='leggings',offset=[-4.1,8.8,-2.15],size=[8.2,3.4,4.3],role='undercloth',rotation=[0,0,0],pivot=[0,0,0],glow=False,bevel=.03))
d['owner_corrections']+=['Muted blue-gray component palette to harmonize with charcoal armor','Replace exposed pale hose shapes with integrated side intake frames and recessed louvers','Opaque dark waist underlayer prevents vanilla skin showing through the coat split']
(ROOT/'source/armor_E10_parameters.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
s=(ROOT/'tools/build_blender_E09.py').read_text(encoding='utf-8').replace('E09','E10')
(ROOT/'tools/build_blender_E10.py').write_text(s,encoding='utf-8')
print('E10: harmonized gray components and integrated side intakes')
