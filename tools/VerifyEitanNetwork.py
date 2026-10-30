"""Run one owned dedicated JVM and one client; never touches the live server."""
from pathlib import Path
import argparse,json,os,subprocess,time
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--run-root',type=Path,required=True);p.add_argument('--wait-for-client-exit',action='store_true');a=p.parse_args();out=a.run_root.resolve()
assert out.parent==ROOT/'.verification' and out.name.startswith('eitan-drive-network-')
query="Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -match 'net\\.neoforged\\.devlaunch\\.Main|cpw\\.mods\\.bootstraplauncher\\.BootstrapLauncher|net\\.minecraft\\.client\\.main\\.Main' } | Select-Object -ExpandProperty ProcessId"
# The literal query is independent of the task path and never closes any process.
query=query.replace('\\\"','\"')
def wait_for_slot(exclude_server=False):
    slot_query=query
    if exclude_server:slot_query=slot_query.replace(' | Select-Object'," | Where-Object { $_.CommandLine -notmatch 'eitanNetworkServerRun' } | Select-Object")
    deadline=time.monotonic()+1800
    while True:
        check=subprocess.run(['powershell.exe','-NoProfile','-Command',slot_query],capture_output=True,text=True,check=True)
        if not check.stdout.strip():return
        assert a.wait_for_client_exit,'Minecraft already open: '+check.stdout.strip()
        assert time.monotonic()<deadline,'Another Minecraft still running; no process was closed'
        print('Waiting for existing Minecraft to exit: '+check.stdout.strip(),flush=True)
        time.sleep(10)
wait_for_slot()
subprocess.run([os.sys.executable,str(ROOT/'tools/PrepareEitanNetwork.py'),'--run-root',str(out)],check=True)
env=os.environ.copy();env['JAVA_HOME']=str(ROOT/'.verification/toolchain/jdk-21.0.12.1+1');java=str(Path(env['JAVA_HOME'])/'bin/java.exe')
base=[java,'-classpath','gradle/wrapper/gradle-wrapper.jar','org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','-Pmod_version=0.7.0-preview.102-eitan-drive','-PintersticeRunRoot='+str(out),'-PprogressionPackSource='+str(ROOT/'.verification/tide-release-20261028/client')]
server=None
with (out/'server-process.log').open('w',encoding='utf8') as slog,(out/'client-process.log').open('w',encoding='utf8') as clog:
    try:
        server=subprocess.Popen(base+['runEitanNetworkServer'],cwd=ROOT,env=env,stdout=slog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        ready=out/'eitanNetworkServer/eitan-network-ready.json';deadline=time.monotonic()+900
        while not ready.is_file():
            assert server.poll() is None,'Dedicated server stopped before ready; inspect server-process.log'
            assert time.monotonic()<deadline,'Dedicated startup deadline'
            time.sleep(.5)
        wait_for_slot(exclude_server=True)
        client=subprocess.Popen(base+['runStudioEitanNetwork'],cwd=ROOT,env=env,stdout=clog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        client.wait(timeout=1200)
        client_proof=out/'studioEitanNetwork/eitan-network-client.json'
        if client.returncode!=0 or not client_proof.is_file() or not json.loads(client_proof.read_text(encoding='utf-8-sig')).get('passed'):
            (out/'eitanNetworkServer/eitan-probe-stop').write_text('owned client failed; stop dedicated cleanly\n')
        server.wait(timeout=180)
        reports={}
        for key,path,code in [('server',out/'eitanNetworkServer/eitan-network-server.json',server.returncode),('client',out/'studioEitanNetwork/eitan-network-client.json',client.returncode)]:
            data=json.loads(path.read_text(encoding='utf-8-sig'));data['process_exit_code']=code;path.write_text(json.dumps(data,indent=2)+'\n');reports[key]=data
        result=dict(passed=all(r.get('passed') is True and r['process_exit_code']==0 for r in reports.values()) and reports['client'].get('sable_udp_channel_active') is True,server=reports['server'],client=reports['client'])
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
        assert result['passed'],'Dedicated/client acceptance failed; inspect result.json'
        print(json.dumps(dict(passed=True,root=str(out),peak=reports['server']['peak_m_s'])))
    finally:
        if server is not None and server.poll() is None:
            (out/'eitanNetworkServer/eitan-probe-stop').write_text('owned runner cleanup\n')
