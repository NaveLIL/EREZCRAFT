from pathlib import Path
import subprocess,os,json,time,datetime,hashlib,shutil,argparse
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/kizya28')
parser=argparse.ArgumentParser();parser.add_argument('--run-id',required=True);args=parser.parse_args();assert args.run_id.isdigit()
# The owner's running game is never closed or re-used by this test.
snapshot="Get-CimInstance Win32_Process -Filter \"name='java.exe' OR name='javaw.exe'\" | Select-Object ProcessId,WorkingSetSize,@{Name='Game';Expression={$_.CommandLine -match 'LaunchTesting|BootstrapLauncher'}},@{Name='OwnKizya';Expression={$_.CommandLine -match 'kizya28'}} | ConvertTo-Json -Compress"
def processes():
    result=subprocess.run(['powershell','-NoProfile','-Command',snapshot],capture_output=True,text=True)
    if result.returncode or result.stderr.strip():raise RuntimeError('Cannot reliably inspect Java processes; refusing to launch')
    value=json.loads(result.stdout) if result.stdout.strip() else []
    return [value] if isinstance(value,dict) else value
guard=processes()
if any(p['Game'] or int(p['WorkingSetSize'])>3000000000 for p in guard):
    print(json.dumps({'started':False,'reason':'Another heavy JVM is still running; owner game untouched'}));raise SystemExit(3)
run=CAND/'.verification'/('kizya'+args.run_id);assert not run.exists();run.mkdir(parents=True)
out=ROOT/'reports/native'/('run_'+args.run_id);out.mkdir(parents=True,exist_ok=False)
jdk=Path('D:/repos/Minecraft/EREZCRAFT/.verification/toolchain/jdk-21.0.12.1+1')
command=[str(jdk/'bin/java.exe'),'-Xmx1G','-classpath',str(CAND/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','-Dorg.gradle.jvmargs=-Xmx1G',
    '-PintersticeRunRoot='+str(run),'-PprogressionPackSource=D:/repos/Minecraft/EREZCRAFT/.verification/armor_a01/.verification/pack-source','runProgressionCreate']
env=dict(os.environ,JAVA_HOME=str(jdk));env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
source=CAND/'src/smoke/java/pro/erez/interstice/client/KizyaNative.java';record={'command':command,'owner_profile_used':False,'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'harness_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'passed':False}
start=time.monotonic()
with (out/'gradle.log').open('w',encoding='utf8') as log:
    process=subprocess.Popen(command,cwd=CAND,env=env,stdout=log,stderr=subprocess.STDOUT);record['pid']=process.pid
    (out/'run.json').write_text(json.dumps(record,indent=2)+'\n')
    while process.poll() is None:
        time.sleep(12)
        others=[p for p in processes() if p['Game'] and not p['OwnKizya']]
        if others:
            record['interrupted_reason']='Another game started; only this owned test process tree was stopped'
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
            break
    code=process.wait()
record.update(exit_code=code,duration_seconds=round(time.monotonic()-start,2));proof=run/'progressionNative/kizya-native.json'
if proof.exists():record['proof']=json.loads(proof.read_text());record['passed']=code==0 and record['proof']['passed']
if 'proof' in record:
    (ROOT/'preview/native').mkdir(exist_ok=True)
    for name in record['proof']['screenshots']:
        path=proof.parent/'screenshots'/name
        if path.exists():shutil.copy2(path,ROOT/'preview/native'/name)
(out/'run.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'passed':record['passed'],'exit_code':code,'report':str(out)}))
raise SystemExit(0 if record['passed'] else 1)
