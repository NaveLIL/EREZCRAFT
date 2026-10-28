from pathlib import Path
import shutil,subprocess,json,hashlib,re
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/et01';DEST=GAME/'.verification/wt01'
if not DEST.exists():
    subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True,stdout=subprocess.DEVNULL)
    for n in ('src/main','src/smoke','tools'):shutil.copytree(BASE/n,DEST/n,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
    for n in ('build.gradle','gradle.properties'):shutil.copy2(BASE/n,DEST/n)
    p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.23-worktools',p.read_text()),encoding='utf-8')
    p=DEST/'src/smoke/java/pro/erez/interstice/client/ToolsNative.java';p.write_text(p.read_text().replace('"interstice.progressionSmoke"','"interstice.disabledWT01ToolsNative"'),encoding='utf-8')
snapshot={f.relative_to(DEST).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in (DEST/'src/main').rglob('*') if f.is_file()}
if not (ROOT/'reports/baseline.json').exists():(ROOT/'reports/baseline.json').write_text(json.dumps({'base':str(BASE),'base_jar_sha256':'18b010f793c50f07a7857a60c5c0476a9d2524ddf02bed4d7f3b3654da165faf','files':snapshot},indent=2),encoding='utf-8')
shutil.copytree(ROOT/'exports/assets',DEST/'src/main/resources/assets',dirs_exist_ok=True)
print(DEST)
