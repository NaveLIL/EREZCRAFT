"""Isolate the released runtime and add only a native screenshot/test harness."""
from pathlib import Path
import hashlib,json,shutil,subprocess,re
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT');DEST=GAME/'.verification/ag03';VERIFIED=GAME/'.verification/armor_a01'
assert not DEST.exists(),'Inspect the existing gallery checkout instead of resetting it'
subprocess.run(['git','worktree','add','--detach',str(DEST),'HEAD'],cwd=GAME,check=True)
for patch in [ROOT.parent/'12_minecraft_longsword/reports/longsword-only.patch',ROOT/'reports/armor-only.patch']:
    subprocess.run(['git','apply','--check',str(patch)],cwd=DEST,check=True)
    subprocess.run(['git','apply',str(patch)],cwd=DEST,check=True)
record=json.loads((ROOT/'reports/integration.json').read_text(encoding='utf-8'))
for name in record['new_files']:
    source=VERIFIED/name;target=DEST/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
freeze=json.loads((ROOT/'reports/final-code-freeze.json').read_text())['files']
for name in freeze:
    a=(VERIFIED/name).read_bytes();b=(DEST/name).read_bytes()
    assert a==b or a.replace(b'\r\n',b'\n')==b.replace(b'\r\n',b'\n'),name
# Preserve the previous two-JVM acceptance harness in its original checkout.
p=DEST/'src/smoke/java/pro/erez/interstice/client/ExpeditionProgressionSmoke.java'
s=p.read_text(encoding='utf-8').replace('System.getProperty("interstice.progressionSmoke","")','System.getProperty("interstice.disabledForGallery","")')
p.write_text(s,encoding='utf-8')
p=DEST/'gradle.properties';p.write_text(re.sub(r'(?m)^mod_version=.*$','mod_version=0.7.0-preview.17-armor',p.read_text()),encoding='utf-8')
p=DEST/'build.gradle';s=p.read_text();s += '''
// Only this disposable gallery: capture native 1920x1080 frames.
neoForge.runs.progressionCreate.programArguments.set([])
neoForge.runs.progressionCreate.programArgument '--width'
neoForge.runs.progressionCreate.programArgument '1920'
neoForge.runs.progressionCreate.programArgument '--height'
neoForge.runs.progressionCreate.programArgument '1080'
'''
# The run already declares these four arguments. Replacing their values avoids plugin-internal APIs.
s=s[:s.index('// Only this disposable gallery:')]
start=s.index('        progressionCreate {');end=s.index('        progressionReload {',start)
part=s[start:end].replace("'1280'","'1920'").replace("'800'","'1080'")
s=s[:start]+part+s[end:];p.write_text(s,encoding='utf-8')
source=ROOT/'tools/gallery_java/ArmorPlayerGallery.java';target=DEST/'src/smoke/java/pro/erez/interstice/client/ArmorPlayerGallery.java'
shutil.copy2(source,target)
out=ROOT/'reports/player_gallery';out.mkdir(exist_ok=True)
(out/'candidate.json').write_text(json.dumps({'checkout':str(DEST),'base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=GAME,text=True).strip(),
 'released_jar_sha256':record['jar_sha256'],'released_runtime_sources_unchanged':True,'test_harness_source':str(source)},indent=2),encoding='utf-8')
print(DEST)
