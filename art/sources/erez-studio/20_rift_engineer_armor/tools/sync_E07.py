from pathlib import Path
import shutil,json,hashlib
ROOT=Path(__file__).resolve().parents[1];tools=ROOT/'tools';CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
# Preserve the exact previously tested E06 candidate before replacing its resources.
archive=ROOT/'exports/intermediate/E06';archive.mkdir(parents=True,exist_ok=True)
for file in (CAND/'build/libs').glob('*engineer*.jar'):shutil.copy2(file,archive/file.name)
for filename in ('build_game_resources.py','prepare_native.py','run_cloth_probe.py'):
    p=tools/filename;p.write_text(p.read_text(encoding='utf-8').replace('E06','E07'),encoding='utf-8')
p=tools/'native_tail.java.txt';p.write_text(p.read_text(encoding='utf-8').replace('E06','E07'),encoding='utf-8')
p=tools/'run_native.py';s=p.read_text(encoding='utf-8').replace("ROOT/'preview/native'","ROOT/'preview/native/E07'").replace("mkdir(exist_ok=True)","mkdir(parents=True,exist_ok=True)");p.write_text(s,encoding='utf-8')
old=ROOT/'reports/native/run_01/run.json';d=json.loads(old.read_text(encoding='utf-8'));d['termination_reason']='Agent stopped its own isolated client startup because the regression server had not yet finished; no visual acceptance attempted.';old.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
