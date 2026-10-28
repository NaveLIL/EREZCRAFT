"""Run one owned full-pack client and collect authentic screenshots with input provenance."""
from pathlib import Path
import argparse,datetime,hashlib,json,os,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CANDIDATE=GAME/'.verification/ag03'
parser=argparse.ArgumentParser();parser.add_argument('--run-id',default='01');args=parser.parse_args()
assert args.run_id.isdigit() and len(args.run_id)<4
runroot=CANDIDATE/'.verification'/('g'+args.run_id);assert not runroot.exists(),'A new owned profile is required'
runroot.mkdir(parents=True)
out=ROOT/'reports/player_gallery'/('run_'+args.run_id);out.mkdir(parents=True,exist_ok=False)
source=ROOT/'tools/gallery_java/ArmorPlayerGallery.java'
shutil.copy2(source,CANDIDATE/'src/smoke/java/pro/erez/interstice/client/ArmorPlayerGallery.java')
jdk=GAME/'.verification/toolchain/jdk-21.0.12.1+1'
command=[str(jdk/'bin/java.exe'),'-classpath',str(CANDIDATE/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain',
    '--no-daemon','--console=plain','-PintersticeRunRoot='+str(runroot),'-PprogressionPackSource='+str(GAME/'.verification/armor_a01/.verification/pack-source'),'runProgressionCreate']
env=dict(os.environ,JAVA_HOME=str(jdk));env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
record={'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':command,'profile':str(runroot/'progressionNative'),
 'harness_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'owner_profile_used':False,
 'geometry_sha256':hashlib.sha256((CANDIDATE/'src/main/resources/assets/interstice/armor/armor_design.json').read_bytes()).hexdigest()}
shutil.copy2(source,out/source.name)
(out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
start=time.monotonic()
with (out/'gradle.log').open('w',encoding='utf-8') as stream:
    process=subprocess.Popen(command,cwd=CANDIDATE,env=env,stdout=stream,stderr=subprocess.STDOUT);record['pid']=process.pid
    (out/'run.json').write_text(json.dumps(record,indent=2),encoding='utf-8');code=process.wait()
record.update(exit_code=code,duration_seconds=round(time.monotonic()-start,3),ended_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
proof=runroot/'progressionNative/armor-player-gallery.json'
record['passed']=False
if proof.exists():
    record['proof']=json.loads(proof.read_text());record['passed']=code==0 and record['proof']['passed']
if record['passed']:
    # The screenshot harness must not change any released runtime class.
    released=ROOT/'exports/interstice-0.7.0-preview.17-armor.jar';count=0
    with zipfile.ZipFile(released) as jar:
        for p in (CANDIDATE/'build/classes/java/main').rglob('*.class'):
            name=p.relative_to(CANDIDATE/'build/classes/java/main').as_posix();assert jar.read(name)==p.read_bytes(),name;count+=1
    record['released_runtime_classes_identical']=count
    target=ROOT/'preview/player_gallery';target.mkdir(exist_ok=True)
    from PIL import Image
    shots={}
    for name in record['proof']['screenshots']:
        p=proof.parent/'screenshots'/name;assert p.exists(),name
        with Image.open(p) as image:assert image.size==(1920,1080),(name,image.size)
        shutil.copy2(p,target/name);shots[name]=hashlib.sha256(p.read_bytes()).hexdigest()
    record['screenshot_sha256']=shots;shutil.copy2(proof,out/proof.name)
(out/'run.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps({'passed':record['passed'],'exit_code':code,'evidence':str(out),'duration_seconds':record['duration_seconds']},ensure_ascii=False))
raise SystemExit(0 if record['passed'] else 1)
