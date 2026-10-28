from pathlib import Path
import subprocess,shutil,json,hashlib
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/bp01';DEST=GAME/'.verification/sk01'
if not DEST.exists():
    subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True,stdout=subprocess.DEVNULL)
    for n in ('src/main','src/smoke','tools'):shutil.copytree(BASE/n,DEST/n,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
    for n in ('build.gradle','gradle.properties'):shutil.copy2(BASE/n,DEST/n)
    p=DEST/'src/smoke/java/pro/erez/interstice/client/BackpackNative.java';s=p.read_text(encoding='utf-8').replace('"interstice.progressionSmoke"','"interstice.disabledSkinBackpacks"');p.write_text(s,encoding='utf-8')
    p=DEST/'build.gradle';s=p.read_text(encoding='utf-8')
    jar=(ROOT.parents[1]/'catalog/custom_player_models/CustomPlayerModels-1.21-0.6.27a.jar').as_posix()
    s+='\n// Owned 3D avatar compatibility profile only. No production dependency change.\ndependencies { smokeImplementation files("'+jar+'"); runtimeOnly files("'+jar+'") }\n'
    p.write_text(s,encoding='utf-8')
snapshot={f.relative_to(DEST).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in (DEST/'src/main').rglob('*') if f.is_file()}
(ROOT/'reports/candidate.json').write_text(json.dumps({'path':str(DEST),'main_source_sha256':snapshot},indent=2),encoding='utf-8')
print(DEST)
