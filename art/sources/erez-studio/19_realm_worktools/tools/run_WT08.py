from pathlib import Path
import subprocess,os,json,time,hashlib,shutil,datetime
ROOT=Path(__file__).resolve().parents[1];GAME=Path(r'D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/si02/candidate';OUT=ROOT/'reports/WT08';JDK=GAME/'.verification/toolchain/jdk-21.0.12.1+1'
env=dict(os.environ,JAVA_HOME=str(JDK));env['PATH']=str(JDK/'bin')+os.pathsep+env.get('PATH','')
for label,mode,short,proof_name in [('worktools','worktools','s31w','worktools-native.json')]:
 out=OUT/label;out.mkdir(exist_ok=False);run=GAME/'.verification'/short;run.mkdir(exist_ok=False)
 command=[str(JDK/'bin/java.exe'),'-classpath',str(CAND/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','-PintersticeRunRoot='+str(run),'-PprogressionPackSource='+str(GAME/'.verification/armor_a01/.verification/pack-source'),'-PstudioMode='+mode]
 if label=='worktools':command+=['assemble']
 command+=['runStudioWorktools'];record={'command':command,'passed':False,'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()};start=time.monotonic()
 with (out/'gradle.log').open('x',encoding='utf8') as log:
  p=subprocess.Popen(command,cwd=CAND,env=env,stdout=log,stderr=subprocess.STDOUT);record['pid']=p.pid;(out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8');code=p.wait()
 record.update(exit_code=code,duration_seconds=round(time.monotonic()-start,3))
 proof=run/'studioWorktools'/proof_name
 if proof.exists():
  record['proof']=json.loads(proof.read_text(encoding='utf8'));record['passed']=code==0 and record['proof'].get('passed') is True and record['proof'].get('distant_horizons_loaded') is True
  if label=='comparison':record['passed']=record['passed'] and record['proof'].get('calibrated_to_vanilla') is True
  target=ROOT/'preview/WT08/native'/label;target.mkdir(parents=True,exist_ok=True);record['screenshots']={}
  for name in record['proof'].get('screenshots',[]):
   image=proof.parent/'screenshots'/name;shutil.copy2(image,target/name);record['screenshots'][name]=hashlib.sha256(image.read_bytes()).hexdigest()
 (out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf8');print(json.dumps({'label':label,'passed':record['passed'],'exit_code':code,'seconds':record['duration_seconds']}),flush=True)
 if not record['passed']:raise SystemExit(1)
