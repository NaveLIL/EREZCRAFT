"""Proportion refinement against the supplied full-height front/back reference."""
from pathlib import Path
import shutil,json
ROOT=Path(__file__).resolve().parents[1]
if (ROOT/'source/armor_E03_parameters.json').exists():raise SystemExit('Historical E03 refinement already applied; use build_design.py')
history=ROOT/'reports/history/E02';history.mkdir(parents=True,exist_ok=True)
for p in (ROOT/'tools/build_design.py',ROOT/'tools/build_blender.py',ROOT/'reports/blender.json'):
    if p.exists():shutil.copy2(p,history/p.name)
(history/'review.json').write_text(json.dumps({'findings':['Waist too low relative to reference','Coat too long over boots','Back insignia too low after moving belt'],'next_revision':'E03'},indent=2),encoding='utf-8')
p=ROOT/'tools/build_design.py';s=p.read_text(encoding='utf-8')
# Raise the visual waist while preserving the native body/leg pivots for future armor animation.
s=s.replace("[.96,10.9,.32]","[.96,9.7,.32]").replace("'body',1.35,7.25,-3.61","'body',.65,6.35,-3.61")
s=s.replace("-2.72,1.0)","-2.72,1.0)")
s=s.replace("star('chest_star',2.05,3.48","star('chest_star',2.05,2.9").replace("star('back_star',0,6.35","star('back_star',0,5.4")
s=s.replace("'center':[0,2.65,2.63]","'center':[0,1.75,2.63]")
# The skirt remains tied to each leg; extend its upper end behind the raised belt/pouches.
s=s.replace(",-.45,z0]",",-2.6,z0]").replace("9.05,z1]","8.05,z1]")
s=s.replace("yy0=8.65;fraction=(yy0+.45)/9.5","yy0=7.65;fraction=(yy0+2.6)/10.65")
s=s.replace("9.1,z1-.025]","8.1,z1-.025]")
s=s.replace(",-.45,z0-.035]",",-2.6,z0-.035]").replace("8.65,z1-.035]","7.65,z1-.035]")
s=s.replace(",-.45,-2.55]",",-2.6,-2.55]").replace(",-.45,2.85]",",-2.6,2.85]")
s=s.replace("9.05,3.25]","8.05,3.25]").replace("9.05,-2.95]","8.05,-2.95]")
s=s.replace("sign*4.0-px,8.5,z+","sign*4.0-px,7.5,z+")
needle='# Bake each cuboid'
s=s.replace(needle,"""# Visual waist adjustment: real native pivots are retained; the coat conceals the higher seam.
for p in parts:
    if p['name'].startswith(('belt','pouch','rear_pouch')):
        p['offset'][1]-=1.3

"""+needle)
s=s.replace('E02','E03');p.write_text(s,encoding='utf-8')
p=ROOT/'tools/build_blender.py';p.write_text(p.read_text(encoding='utf-8').replace('E02','E03'),encoding='utf-8')
print('E03 proportions prepared')
