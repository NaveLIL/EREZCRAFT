from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'reports/blender_E09.json';d=json.loads(p.read_text(encoding='utf-8'));d['outputs']={k:v for k,v in d['outputs'].items() if 'E09' in k};d['bevel_export_fix']='Keep box bevel width below 45 percent of minimum thickness; no collapsed float32 faces'
p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
p=ROOT/'tools/build_blender_E09.py';s=p.read_text(encoding='utf-8').replace("(ROOT/'preview').glob('*.png')","(ROOT/'preview').glob('*E09*.png')");p.write_text(s,encoding='utf-8')
p=ROOT/'reports/native/run_03/run.json';d=json.loads(p.read_text(encoding='utf-8'));d['termination_reason']='Owner requested a different mask material during startup; agent stopped its isolated client before acceptance and rebuilt the candidate.';p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
