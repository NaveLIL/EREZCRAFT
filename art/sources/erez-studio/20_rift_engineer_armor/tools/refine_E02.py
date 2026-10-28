"""Recorded refinement after inspecting the first real Blender render."""
from pathlib import Path
import shutil,json
ROOT=Path(__file__).resolve().parents[1]
if (ROOT/'source/armor_E02_parameters.json').exists():raise SystemExit('Historical E02 refinement already applied; use canonical build_design.py')
history=ROOT/'reports/history/E01';history.mkdir(parents=True,exist_ok=True)
for p in (ROOT/'tools/build_design.py',ROOT/'tools/build_blender.py',ROOT/'reports/blender.json'):
    if p.exists():shutil.copy2(p,history/p.name)
(history/'review.json').write_text(json.dumps({'reviewer':'Codex visual inspection','findings':['Fabric too noisy','Visor too wide','Wrist device square rather than tall','Collar piping did not follow its depth slope','Boots touch in neutral pose'],'next_revision':'E02'},indent=2),encoding='utf-8')
p=ROOT/'tools/build_design.py';s=p.read_text(encoding='utf-8')
a=s.index('def line(');b=s.index('\ndef ring(',a)
s=s[:a]+'''def line(name,bone,a,b,width,depth,role,glow=False):
    import numpy as np
    A=np.array(a,float);B=np.array(b,float);Y=B-A;Y/=np.linalg.norm(Y)
    X=np.array([Y[1],-Y[0],0.]);X/=np.linalg.norm(X);Z=np.cross(X,Y)
    vertices=[list(c+u*X+v*Z) for c in (A,B) for u,v in ((-width/2,-depth/2),(width/2,-depth/2),(width/2,depth/2),(-width/2,depth/2))]
    # Explicit ring connectivity; line depth now follows the actual 3D endpoints.
    faces=[{'indices':[0,3,2,1],'side':'cap'},{'indices':[4,5,6,7],'side':'cap'}]
    for i in range(4):faces.append({'indices':[i,(i+1)%4,(i+1)%4+4,i+4],'side':'side'})
    p={'name':name,'bone':bone,'slot':slot(bone),'role':role,'vertices':vertices,'faces':faces,'rotation':[0,0,0],'pivot':[0,0,0],'glow':glow}
    parts.append(p);return p
''' +s[b:]
s=s.replace("offset=[-3.95,-5.6,-4.52]","offset=[-3.95,-5.1,-4.52]")
s=s.replace("offset=[-2.9,-5.08,-5.12],size=[5.8,.48,.13]","offset=[-2.7,-4.58,-5.12],size=[4.5,.48,.13]")
s=s.replace("[-2.68,-5.03,-5.265],[4.9,.13,.035]","[-2.5,-4.53,-5.265],[3.7,.13,.035]")
s=s.replace("offset=[-4.06,-6.4,-4.6]","offset=[-4.06,-5.9,-4.6]")
s=s.replace("[sign*3.05,2.15,-3.24],.18,.13,'steel'","[sign*3.14,1.15,-3.3],.16,.13,'metal'")
s=s.replace("[-1.12,3.45,-3.2],[3.65,3.25,.95]","[.25,3.7,-3.2],[2.55,3.85,.95]")
s=s.replace("[-.87,3.7,-3.41],[3.15,2.75,.24]","[.48,3.95,-3.41],[2.1,3.35,.24]")
s=s.replace("[.75,4.0,-3.71],[.75,2.15,.12]","[1.25,4.3,-3.71],[.65,2.55,.12]")
s=s.replace("[.9,4.2,-3.85],[.18,1.7,.025]","[1.38,4.5,-3.85],[.16,2.1,.025]")
s=s.replace("for y in (3.75,6.1)","for y in (4.1,7.0)").replace("[-.6,y,-3.7]","[.64,y,-3.7]")
s=s.replace("-2.72,1.25)","-2.72,1.0)").replace("2.65,1.35)","2.65,1.2)")
s=s.replace("'right_leg':[0,0,0],'left_leg':[0,0,0]","'right_leg':[0,0,3],'left_leg':[0,0,-3]")
s=s.replace('E01','E02');p.write_text(s,encoding='utf-8')
p=ROOT/'tools/build_blender.py';s=p.read_text(encoding='utf-8')
needle="tiles={}\n"
s=s.replace(needle,"""# Reuse the preserved S03 cloth texels instead of high-contrast wool noise.
legacy=json.loads((ROOT/'source/reference/avatar_S03.json').read_text(encoding='utf-8'))
uv=next(p for p in legacy['parts'] if p['name']=='hood_base')['uv']['south']
cloth=Image.open(ROOT/'source/reference/avatar_atlas_S03.png').convert('RGBA').crop((uv['sx'],uv['sy'],uv['ex'],uv['ey']))
refs['S03_cloth']=cloth;cloth_path=ROOT/'source/reference/S03_cloth_tile.png';cloth.save(cloth_path)
provenance.append({'asset':'Preserved S03 hood cloth','file':str(cloth_path),'source_region':uv,'operation':'Unchanged subrectangle from original S03 atlas','sha256':sha(cloth_path.read_bytes())})
tiles={}
""")
s=s.replace("name='black_wool' if role in ('coat','panel','dark','black','seam') else 'brown_wool' if role=='lining' else 'dark_oak_log' if role=='leather'", "name='S03_cloth' if role in ('coat','panel','dark','seam') else 'black_wool' if role=='black' else 'brown_wool' if role in ('lining','leather')")
s=s.replace("if role in ('light','cyan'):strength=.9+.1*strength", "if role in ('light','cyan'):strength=.9+.1*strength\n        elif role in ('black','leather'):strength=.8+.2*strength")
s=s.replace("original=CAT/(name+'.png')", "original=cloth_path if name=='S03_cloth' else CAT/(name+'.png')")
s=s.replace('E01','E02');p.write_text(s,encoding='utf-8')
print('E02 refinement applied')
