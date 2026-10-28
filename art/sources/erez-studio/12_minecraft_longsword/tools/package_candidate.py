"""Version and package the already-tested functional payload; verify metadata-only delta."""
from pathlib import Path
import datetime as dt
import hashlib
import json
import shutil
import subprocess
import time
import zipfile

ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
CANDIDATE=GAME/'.verification/ls03'
JDK=GAME/'.verification/toolchain/jdk-21.0.12.1+1'
old_version='0.7.0-preview.15';version='0.7.0-preview.16-longsword'
sha=lambda b:hashlib.sha256(b).hexdigest()
old_jar=CANDIDATE/f'build/libs/interstice-{old_version}.jar'
with zipfile.ZipFile(old_jar) as archive:
    previous={name:archive.read(name) for name in archive.namelist() if not name.endswith('/')}
native_dir=CANDIDATE/'.verification/20261027T040135.612048Z-longsword-native'
assert json.loads((native_dir/'summary.json').read_text())['passed']
command=[str(JDK/'bin/java.exe'),'-classpath',str(CANDIDATE/'gradle/wrapper/gradle-wrapper.jar'),
         'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain',
         '-Pmod_version='+version,'assemble']
started=dt.datetime.now(dt.timezone.utc).isoformat();start=time.monotonic()
with (ROOT/'reports/package.log').open('w',encoding='utf-8') as log:
    result=subprocess.run(command,cwd=CANDIDATE,stdout=log,stderr=subprocess.STDOUT)
assert result.returncode==0,'Packaging failed; inspect reports/package.log'
jar=CANDIDATE/f'build/libs/interstice-{version}.jar'
with zipfile.ZipFile(jar) as archive:
    current={name:archive.read(name) for name in archive.namelist() if not name.endswith('/')}
assert previous.keys()==current.keys(),'Unexpected release contents'
changed=[name for name in current if current[name]!=previous[name]]
assert changed==['META-INF/neoforge.mods.toml'],changed
assert previous[changed[0]].replace(old_version.encode(),version.encode())==current[changed[0]]
for name in ('src/main/resources/assets/interstice/models/item/riftsteel_sword.json',
             'src/main/resources/data/interstice/recipe/riftsteel_sword.json'):
    baseline=json.loads((ROOT/'reports/integration_baseline/source_sha256.json').read_text())
    assert sha((GAME/name).read_bytes())==baseline[name],'Original sword changed'
for dest in (ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name):shutil.copy2(jar,dest)
source_jar=CANDIDATE/f'build/libs/interstice-{version}-sources.jar'
shutil.copy2(source_jar,ROOT/'exports'/source_jar.name)
record={'started_utc':started,'duration_seconds':round(time.monotonic()-start,3),'command':command,
        'exit_code':result.returncode,'native_tested_version':old_version,'packaged_version':version,
        'payload_equivalence':'Every JAR entry is byte-identical to the native-tested build except the single mod-version string.',
        'changed_entries':changed,'jar':str(jar),'jar_sha256':sha(jar.read_bytes()),
        'native_tested_jar_sha256':sha(old_jar.read_bytes()),'source_jar_sha256':sha(source_jar.read_bytes()),
        'source_classes_and_resources_unchanged':True,'original_sword_unchanged':True,
        'published':False,'installed_in_owner_profile':False}
(ROOT/'reports/package.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(record,ensure_ascii=False))
