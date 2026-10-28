from pathlib import Path
import subprocess,os,json,hashlib,argparse
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01');JDK=Path('D:/repos/Minecraft/EREZCRAFT/.verification/toolchain/jdk-21.0.12.1+1')
p=argparse.ArgumentParser();p.add_argument('--run-id',required=True);a=p.parse_args();assert a.run_id.isdigit()
out=ROOT/'reports/cloth'/('run_'+a.run_id);out.mkdir(parents=True,exist_ok=False);classes=out/'classes';classes.mkdir()
cache=Path('C:/Users/Dimon/.gradle/caches/modules-2/files-2.1')
dependencies=[next((cache/path).rglob('*.jar')) for path in ('org.joml/joml/1.10.5','com.google.code.gson/gson/2.10.1')];classpath=os.pathsep.join(map(str,dependencies))
source=CAND/'src/main/java/pro/erez/interstice/client/EngineerCloth.java';probe=ROOT/'tools/EngineerClothProbe.java'
commands=[[str(JDK/'bin/javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(source),str(probe)],
          [str(JDK/'bin/java.exe'),'-Xmx1G','-cp',str(classes)+os.pathsep+classpath,'EngineerClothProbe',str(ROOT/'source/armor_E07.json'),str(out/'result.json')]]
record={'commands':commands,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'probe_sha256':hashlib.sha256(probe.read_bytes()).hexdigest(),'passed':False}
with (out/'console.log').open('w',encoding='utf-8') as log:
    for cmd in commands:
        r=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
        if r.returncode:record['exit_code']=r.returncode;break
    else:record.update(exit_code=0,passed=json.loads((out/'result.json').read_text())['passed'])
(out/'run.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(record))
raise SystemExit(record['exit_code'])
