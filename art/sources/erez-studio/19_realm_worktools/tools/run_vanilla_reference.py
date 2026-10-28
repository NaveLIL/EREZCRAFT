from pathlib import Path
import subprocess,os,json,time,datetime,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'reports/vanilla_reference'
GAME=Path(r'D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/si01/candidate';JDK=GAME/'.verification/toolchain/jdk-21.0.12.1+1'
shutil.copy2(OUT/'VanillaToolReference.java',CAND/'src/smoke/java/pro/erez/interstice/client/VanillaToolReference.java')
build=CAND/'build.gradle';text=build.read_text(encoding='utf8');needle="systemProperty 'interstice.studioSmoke', kind.toLowerCase()"
if needle in text:build.write_text(text.replace(needle,"systemProperty 'interstice.studioSmoke', providers.gradleProperty('studioMode').getOrElse(kind.toLowerCase())"),encoding='utf8')
run=GAME/'.verification/vc01';run.mkdir(exist_ok=False)
command=[str(JDK/'bin/java.exe'),'-classpath',str(CAND/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','-PintersticeRunRoot='+str(run),'-PprogressionPackSource='+str(GAME/'.verification/armor_a01/.verification/pack-source'),'-PstudioMode=comparison','runStudioWorktools']
env=dict(os.environ,JAVA_HOME=str(JDK));env['PATH']=str(JDK/'bin')+os.pathsep+env.get('PATH','')
record={'command':command,'passed':False,'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'harness_sha256':hashlib.sha256((OUT/'VanillaToolReference.java').read_bytes()).hexdigest(),'production_model_changes':False}
start=time.monotonic()
with (OUT/'gradle.log').open('x',encoding='utf8') as log:
 p=subprocess.Popen(command,cwd=CAND,env=env,stdout=log,stderr=subprocess.STDOUT);record['pid']=p.pid
 (OUT/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8');code=p.wait()
record.update(exit_code=code,duration_seconds=round(time.monotonic()-start,3))
proof=run/'studioWorktools/vanilla-tool-reference.json'
if proof.exists():
 record['proof']=json.loads(proof.read_text(encoding='utf8'));record['passed']=code==0 and record['proof'].get('passed') is True and record['proof'].get('same_player_held_views')==12
 target=ROOT/'preview/vanilla_reference';target.mkdir(exist_ok=True)
 record['screenshots']={}
 for recorded_name in record['proof'].get('screenshots',[]):
  name=recorded_name.replace('worktools-','reference-',1)
  p=proof.parent/'screenshots'/name;shutil.copy2(p,target/name);record['screenshots'][name]=hashlib.sha256(p.read_bytes()).hexdigest()
(OUT/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8');print(json.dumps({'passed':record['passed'],'exit_code':code,'report':str(OUT)}),flush=True)
raise SystemExit(0 if record['passed'] else 1)
