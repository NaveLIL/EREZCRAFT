from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
for filename in ('build_game_resources.py','prepare_native.py','native_tail.java.txt','run_native.py','verify_review.py'):
    p=ROOT/'tools'/filename;p.write_text(p.read_text(encoding='utf-8').replace('E08','E09'),encoding='utf-8')
p=ROOT/'reports/native/run_03/run.json'
if p.exists():
    d=json.loads(p.read_text(encoding='utf-8'));d['termination_reason']='Owner requested a different mask material during startup; agent stopped its isolated client before acceptance and rebuilt the candidate.';p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
