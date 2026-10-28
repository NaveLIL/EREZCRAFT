"""Copy only this task's delta into an isolated Git checkout; preserve unfinished work."""
from pathlib import Path
import argparse
import difflib
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--refresh',action='store_true')
refresh=parser.parse_args().refresh
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
BASE=ROOT/'reports/integration_baseline'
hashes=json.loads((BASE/'source_sha256.json').read_text(encoding='utf-8'))
sha=lambda b:hashlib.sha256(b).hexdigest()
changes=[];new_files=[];patch=[]
new_paths=[
 'src/smoke/java/pro/erez/interstice/test/LongswordGameTests.java',
 'src/main/resources/assets/interstice/models/item/riftsteel_longsword.json',
 'src/main/resources/assets/interstice/provenance/riftsteel-longsword.json',
 'src/main/resources/data/interstice/recipe/riftsteel_longsword.json',
 'src/main/resources/data/interstice/advancement/recipes/equipment/riftsteel_longsword.json']
if (GAME/'docs/LONGSWORD.md').exists():new_paths.append('docs/LONGSWORD.md')
if (GAME/'docs/verification/LONGSWORD.json').exists():new_paths.append('docs/verification/LONGSWORD.json')
for name,expected in hashes.items():
    p=GAME/name
    if not p.is_file():raise RuntimeError('Baseline file disappeared: '+name)
    current=p.read_bytes()
    if sha(current)==expected:continue
    backup=BASE/name
    if backup.is_file():old=backup.read_bytes()
    else:
        committed=subprocess.check_output(['git','show','HEAD:'+name],cwd=GAME)
        variants=[committed,committed.replace(b'\r\n',b'\n').replace(b'\n',b'\r\n'),committed.replace(b'\r\n',b'\n')]
        old=next((v for v in variants if sha(v)==expected),None)
        if old is None:raise RuntimeError('Unrelated dirty file changed: '+name)
    changes.append(name)
    a=old.decode('utf-8').replace('\r\n','\n').splitlines(keepends=True)
    b=current.decode('utf-8').replace('\r\n','\n').splitlines(keepends=True)
    patch.extend(difflib.unified_diff(a,b,fromfile='a/'+name,tofile='b/'+name))
for name in new_paths:
    assert name not in hashes
    data=(GAME/name).read_text(encoding='utf-8')
    new_files.append(name)
    patch.extend(difflib.unified_diff([],data.splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+name))
patch_path=ROOT/'reports/longsword-only.patch'
patch_path.write_text(''.join(patch),encoding='utf-8',newline='\n')
candidate=GAME/'.verification/ls03'
if refresh:
    assert candidate.exists(),'No candidate to audit'
else:
    assert not candidate.exists(),'Candidate already exists; inspect rather than replace'
    subprocess.run(['git','worktree','add','--detach',str(candidate),'HEAD'],cwd=GAME,check=True)
    subprocess.run(['git','apply','--check',str(patch_path)],cwd=candidate,check=True)
    subprocess.run(['git','apply',str(patch_path)],cwd=candidate,check=True)
old_model='src/main/resources/assets/interstice/models/item/riftsteel_sword.json'
old_recipe='src/main/resources/data/interstice/recipe/riftsteel_sword.json'
for name in (old_model,old_recipe):
    assert sha((GAME/name).read_bytes())==hashes[name], 'Original sword resource changed'
    assert json.loads((GAME/name).read_text())==json.loads((candidate/name).read_text())
report={'candidate':str(candidate),'base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=GAME,text=True).strip(),
        'changed_existing':changes,'new_files':new_files,'patch_sha256':sha(patch_path.read_bytes()),
        'original_sword_model_and_recipe_unchanged':True,'unfinished_fauna_in_candidate':False,
        'version_override':'0.7.0-preview.16-longsword'}
(ROOT/'reports/candidate.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
