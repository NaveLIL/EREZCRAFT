"""Isolated 49-JAR dedicated functional acceptance for the client comfort batch."""
import argparse
from datetime import datetime, timezone
import hashlib, json, os
from pathlib import Path
import shutil, subprocess, time

ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'.verification/client-comfort-dedicated-20261010'
FROZEN=ROOT/'.verification/optimization-draconic-server-c/runtime'
SOURCE=Path('D:/repos/erezcraft/.local/client-comfort/shader-source')
JAVA_HOME=ROOT/'.verification/toolchain/jdk-21.0.12.1+1'
EXCLUDE_PREFIXES=('xaerominimap-','xaeroworldmap-','MouseTweaks-','lambdynamiclights-','ImmediatelyFast-','sodium-','derenderpatcher-',
    'Controlling-','Searchables-','InventoryProfilesNext-','libIPN-','kotlinforforge-','iris-')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def arg(value):return '"'+str(value).replace('\\','/')+'"'

def prepare():
    assert DEST.resolve().is_relative_to((ROOT/'.verification').resolve())
    assert not DEST.exists(), 'The test profile must be new'
    profile=DEST/'profile';runtime=DEST/'runtime';resources=runtime/'probeResources';classes=runtime/'probeClasses'
    for p in [profile/'mods',profile/'config',resources/'META-INF',classes]:p.mkdir(parents=True,exist_ok=True)
    copied=[];excluded=[]
    sources=sorted((SOURCE/'mods').glob('*.jar'));assert len(sources)==62,len(sources)
    for source in sources:
        if source.name.startswith(EXCLUDE_PREFIXES):excluded.append({'filename':source.name,'sha256':sha(source)});continue
        target=profile/'mods'/source.name;shutil.copyfile(source,target)
        assert sha(source)==sha(target)
        copied.append({'filename':source.name,'sha256':sha(target),'bytes':target.stat().st_size,'source':str(source)})
    assert len(copied)==49 and len(excluded)==13,(len(copied),len(excluded))
    assert any('DistantHorizons-3.3.3' in r['filename'] for r in copied)
    for name in ['DistantHorizons.toml','fml.toml']:
        source=SOURCE/'config'/name
        if source.exists():shutil.copyfile(source,profile/'config'/name)
    (profile/'eula.txt').write_text('eula=true\n')
    (profile/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=26620\nonline-mode=false\nallow-flight=false\nview-distance=4\nsimulation-distance=4\nlevel-name=isolated-comfort-check\nlevel-seed=20261010\nmax-players=10\nwhite-list=true\nenforce-whitelist=true\n')
    (profile/'whitelist.json').write_text('[]\n')
    (resources/'META-INF/neoforge.mods.toml').write_text('modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="erezcraft_comfort_server_probe"\nversion="1.0.0"\ndisplayName="EREZCRAFT isolated comfort dedicated check"\n[[dependencies.erezcraft_comfort_server_probe]]\nmodId="interstice"\ntype="required"\nversionRange="[0.7.0-preview.3]"\nordering="AFTER"\nside="BOTH"\n')
    source_cp=(FROZEN/'classpath-args.txt').read_text().splitlines()[1].strip('"')
    cp=[Path(p) for p in source_cp.split(';')];assert all(p.is_file() for p in cp)
    compile_cp=cp+[profile/'mods'/r['filename'] for r in copied]
    source=Path(__file__).parent/'DedicatedComfortCheck.java'
    javac_args=runtime/'javac-args.txt'
    javac_args.write_text('\n'.join(['-encoding','UTF-8','-cp',arg(';'.join(str(p) for p in compile_cp)),'-d',arg(classes),arg(source)]))
    compiled=subprocess.run([str(JAVA_HOME/'bin/javac.exe'),'@'+str(javac_args)],capture_output=True,text=True)
    (DEST/'compile.log').write_text(compiled.stdout+compiled.stderr)
    if compiled.returncode:raise RuntimeError('Dedicated checker compile failed; inspect compile.log')
    for name in ['classpath-args.txt','legacy-classpath.txt','log4j2.xml','program-args.txt']:shutil.copyfile(FROZEN/name,runtime/name)
    vm=(FROZEN/'vm-args.txt').read_text().replace(FROZEN.as_posix(),runtime.as_posix()).replace('-Derezcraft.optimizationDedicatedProbe=true','-Derezcraft.comfortDedicatedProbe=true')
    assert 'BootstrapLauncher' not in vm
    assert 'cpw.mods.bootstraplauncher.BootstrapLauncher' in (runtime/'program-args.txt').read_text()
    assert '-Dlog4j2.configurationFile='+ (runtime/'log4j2.xml').as_uri() in vm
    (runtime/'vm-args.txt').write_text(vm)
    command=[str(JAVA_HOME/'bin/java.exe'),'-Xms512m','-Xmx6G','-XX:+UseZGC','-XX:+ZGenerational',
        '@'+str(runtime/'classpath-args.txt'),'@'+str(runtime/'vm-args.txt'),'@'+str(runtime/'program-args.txt')]
    manifest={'prepared_utc':datetime.now(timezone.utc).isoformat(),'argv':command,
        'env':{'MOD_CLASSES':f'erezcraft_comfort_server_probe%%{classes};erezcraft_comfort_server_probe%%{resources}'},'cwd':str(profile),
        'java_home':str(JAVA_HOME),'real_neoforge':'21.1.252','minecraft':'1.21.1','server_jar_count':49,'server_ip':'127.0.0.1','server_port':26620,
        'source_client_jar_count':62,'copied_artifacts':copied,'excluded_client_artifacts':excluded,
        'sources_sha256':{str(source):sha(source),str(Path(__file__).resolve()):sha(Path(__file__).resolve())},
        'runtime_argument_files_sha256':{name:sha(runtime/name) for name in ['classpath-args.txt','legacy-classpath.txt','log4j2.xml','program-args.txt','vm-args.txt']},
        'scope':'Dedicated functional compatibility only; not target hardware load/10-player capacity'}
    (DEST/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'prepared':str(DEST),'server_jars':49,'excluded_client_jars':13,'no_game_started':True}),flush=True)

def run():
    manifest=json.loads((DEST/'manifest.json').read_text());profile=Path(manifest['cwd'])
    assert profile==DEST/'profile' and not (profile/'isolated-comfort-check/level.dat').exists()
    assert not (DEST/'launch.json').exists()
    for row in manifest['copied_artifacts']:assert sha(profile/'mods'/row['filename'])==row['sha256']
    log=(DEST/'server.log').open('wb')
    child=subprocess.Popen(manifest['argv'],cwd=profile,env=os.environ|manifest['env'],stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    report={'started_utc':datetime.now(timezone.utc).isoformat(),'owned_pid':child.pid,'argv':manifest['argv'],'cwd':str(profile),'no_force_kills':True}
    (DEST/'launch.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({'started':child.pid,'log':str(DEST/'server.log')}),flush=True)
    timed_out=False;deadline=time.monotonic()+600
    while child.poll() is None and time.monotonic()<deadline:time.sleep(0.5)
    if child.poll() is None:
        timed_out=True
        if child.stdin:child.stdin.write(b'stop\n');child.stdin.flush()
        end=time.monotonic()+60
        while child.poll() is None and time.monotonic()<end:time.sleep(0.5)
    log.close();result_path=profile/'client-comfort-server-validation.json'
    result=json.loads(result_path.read_text()) if result_path.exists() else {}
    report.update({'exit_code':child.poll(),'timed_out':timed_out,'validation':str(result_path),
        'passed':bool(result.get('passed')) and result.get('server_pid')==child.pid and child.poll()==0 and not timed_out})
    if child.poll() is None:report['still_running_owned_pid']=child.pid
    (DEST/'launch.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2),flush=True)
    return 0 if report['passed'] else 1

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',action='store_true');args=parser.parse_args()
    if args.run:raise SystemExit(run())
    prepare()
