from pathlib import Path
import argparse,datetime,hashlib,json,os,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/ai01'
p=argparse.ArgumentParser();p.add_argument('--run-id',required=True);args=p.parse_args();assert args.run_id.isdigit()
run=CAND/'.verification'/('icons'+args.run_id);assert not run.exists();run.mkdir(parents=True)
out=ROOT/'reports/native'/('run_'+args.run_id);out.mkdir(parents=True,exist_ok=False)
jdk=GAME/'.verification/toolchain/jdk-21.0.12.1+1'
command=[str(jdk/'bin/java.exe'),'-classpath',str(CAND/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain',
    '-PintersticeRunRoot='+str(run),'-PprogressionPackSource='+str(GAME/'.verification/armor_a01/.verification/pack-source'),'runProgressionCreate']
env=dict(os.environ,JAVA_HOME=str(jdk));env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
sha=lambda b:hashlib.sha256(b).hexdigest()
source=CAND/'src/smoke/java/pro/erez/interstice/client/ArmorIconsSmoke.java';shutil.copy2(source,out/source.name)
record={'command':command,'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'harness_sha256':sha(source.read_bytes()),'owner_profile_used':False,'passed':False}
start=time.monotonic()
with (out/'gradle.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen(command,cwd=CAND,env=env,stdout=log,stderr=subprocess.STDOUT);record['pid']=process.pid
    (out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf-8');code=process.wait()
record.update(exit_code=code,duration_seconds=round(time.monotonic()-start,3))
proof=run/'progressionNative/armor-icons.json'
if proof.exists():record['proof']=json.loads(proof.read_text(encoding='utf-8'));record['passed']=code==0 and record['proof']['passed']
if record['passed']:
    with zipfile.ZipFile(CAND/'build/libs/interstice-0.7.0-preview.20-icons.jar') as z:
        for item in (CAND/'build/classes/java/main').rglob('*.class'):
            name=item.relative_to(CAND/'build/classes/java/main').as_posix();assert z.read(name)==item.read_bytes(),name
        manifest=json.loads((ROOT/'source/icon_manifest.json').read_text(encoding='utf-8'))
        for row in manifest['icons']:assert sha(z.read(row['model_path'].removeprefix('src/main/resources/')))==row['model_sha256']
    from PIL import Image
    hashes={}
    for name in record['proof']['screenshots']:
        file=proof.parent/'screenshots'/name
        with Image.open(file) as image:assert image.size==(1920,1080)
        shutil.copy2(file,ROOT/'preview'/name);hashes[name]=sha(file.read_bytes())
    record['screenshot_sha256']=hashes
(out/'run.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps({'passed':record['passed'],'exit_code':code,'report':str(out)}))
raise SystemExit(0 if record['passed'] else 1)
