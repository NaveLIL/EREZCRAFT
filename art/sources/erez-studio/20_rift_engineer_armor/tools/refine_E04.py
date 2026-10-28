"""Final visual pass: reference coat opening, visible leg straps, hood fasteners."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
if (ROOT/'source/armor_E04_parameters.json').exists():raise SystemExit('Historical E04 refinement already applied; use build_design.py')
h=ROOT/'reports/history/E03';h.mkdir(parents=True,exist_ok=True)
for p in (ROOT/'tools/build_design.py',ROOT/'tools/build_blender.py',ROOT/'reports/blender.json'):
    if p.exists():shutil.copy2(p,h/p.name)
(h/'review.json').write_text(json.dumps({'findings':['Front coat opening narrower than reference','Leg webbing hidden or absent','Rear hood strap too high; side buckles need proper depth'],'next_revision':'E04'},indent=2),encoding='utf-8')
p=ROOT/'tools/build_design.py';s=p.read_text(encoding='utf-8')
s=s.replace("'seam':'#34363d'","'seam':'#34363d','lining_edge':'#537a79'")
s=s.replace("inner_low=sign*.85;outer_low", "inner_low=sign*.85;outer_low")
s=s.replace("for back in (False,True):\n        z0=", "for back in (False,True):\n        inner_low=sign*(.85 if back else 2.1)\n        z0=")
s=s.replace("inner,.12,'lining')","inner,.12,'lining_edge')")
needle="    inherited(side+'_boot',"
insert="""    # Real straps on the trousers become visible through the reference's wider front opening.
    cuboid(side+'_leg_upper_webbing',bone,[-2.06,4.45,-2.17],[4.12,.48,4.34],'metal',bevel=.035)
    cuboid(side+'_leg_lower_webbing',bone,[-2.07,6.65,-2.18],[4.14,.58,4.36],'leather',bevel=.04)
    cuboid(side+'_leg_webbing_clasp',bone,[-.2,6.68,-2.42],[.4,.48,.12],'brass',bevel=.03)
    cuboid(side+'_knee_guard',bone,[-1.6,5.05,-2.25],[3.2,1.05,.24],'dark',bevel=.08)
"""
s=s.replace(needle,insert+needle)
s=s.replace("offset=[-4.2,-5.4,4.34]","offset=[-4.2,-2.8,4.34]")
old="""    cuboid(side+'_hood_strap','head',[-4.52 if sign<0 else 4.32,-5.4,-2.5],[.2,.8,5.5],'dark',source='head_strap_'+('-4.15' if sign<0 else '3.85'))
    ring(side+'_hood_buckle','head',sign*4.55,-4.95,-1.25,.17,1.15,.12,'brass')"""
new="""    xx=sign*4.37
    sideband=[[xx,-5.4,-2.5],[xx,-4.6,-2.5],[xx,-2.0,4.15],[xx,-2.8,4.15]]
    prism(side+'_hood_strap','head',sideband,[[x+sign*.17,y,z] for x,y,z in sideband],'dark',source='head_strap_'+('-4.15' if sign<0 else '3.85'))
    for yy in (-5.35,-4.3):cuboid(side+'_hood_buckle_cross_'+str(yy),'head',[sign*4.5-.08,yy,-2.25],[.16,.18,1.05],'brass',bevel=.025)
    for zz in (-2.25,-1.38):cuboid(side+'_hood_buckle_side_'+str(zz),'head',[sign*4.5-.08,-5.17,zz],[.16,.87,.18],'brass',bevel=.025)"""
assert old in s;s=s.replace(old,new)
s=s.replace('E03','E04');p.write_text(s,encoding='utf-8')
for n in ('build_blender.py','verify_review.py'):
    p=ROOT/'tools'/n;p.write_text(p.read_text(encoding='utf-8').replace('E03','E04'),encoding='utf-8')
print('E04 reference detail pass prepared')
