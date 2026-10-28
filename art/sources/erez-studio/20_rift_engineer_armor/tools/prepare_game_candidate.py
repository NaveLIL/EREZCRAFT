from pathlib import Path
import json,hashlib,shutil,subprocess,re
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/wt01';DEST=GAME/'.verification/ea01'
if not DEST.exists():
    subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True,stdout=subprocess.DEVNULL)
    for n in ('src/main','src/smoke','tools'):shutil.copytree(BASE/n,DEST/n,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
    for n in ('build.gradle','gradle.properties'):shutil.copy2(BASE/n,DEST/n)
    p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.24-engineer',p.read_text()),encoding='utf-8')
    p=DEST/'src/smoke/java/pro/erez/interstice/client/WorktoolsNative.java';p.write_text(p.read_text().replace('"interstice.progressionSmoke"','"interstice.disabledEngineerWorktools"'),encoding='utf-8')
snap={f.relative_to(DEST).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in (DEST/'src/main').rglob('*') if f.is_file()}
if not (ROOT/'reports/game_baseline.json').exists():(ROOT/'reports/game_baseline.json').write_text(json.dumps({'base':str(BASE),'files':snap},indent=2),encoding='utf-8')
d=json.loads((ROOT/'source/armor_E04.json').read_text(encoding='utf-8'));d['revision']='EREZ-ENGINEER-ARMOR-E05'
for p in d['parts']:
    if p['name'].startswith('chest_star_'):
        for v in p['vertices']:v[0]-=.35;v[1]-=.25
d['atlas']['file']='armor_E05_atlas.png';d['scope']='E04 appearance conditionally accepted; chest star moved away from lapel. Native armor/cloth validation in progress.'
d['approval']={'E04':'Accepted subject to exact game geometry/texture and moving coat','star_change':{'x':-.35,'y':-.25,'z':0},'final_native_acceptance':'Pending'}
(ROOT/'source/armor_E05.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');shutil.copy2(ROOT/'source/armor_E04_atlas.png',ROOT/'source/armor_E05_atlas.png')
assets=DEST/'src/main/resources/assets/interstice'
(assets/'armor').mkdir(exist_ok=True);shutil.copy2(ROOT/'source/armor_E05.json',assets/'armor/engineer.json')
(assets/'textures/models/armor').mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/'source/armor_E05_atlas.png',assets/'textures/models/armor/engineer.png')
print(DEST)
