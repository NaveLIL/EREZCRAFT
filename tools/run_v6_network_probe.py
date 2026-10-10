"""Launch only a root-prepared isolated three-JVM network manifest; never builds or kills games."""
from pathlib import Path
import argparse,json,os,subprocess,time

ROOT=Path(__file__).resolve().parents[1]
LOCAL=(ROOT/'.verification').resolve()


def confined(value):
    path=Path(value).resolve()
    if not path.is_relative_to(LOCAL): raise ValueError(f'Profile/evidence is outside .verification: {path}')
    return path


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def atomic(path,data):
    temporary=path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    for attempt in range(20):
        try:
            temporary.replace(path)
            return
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(.005)


def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',required=True)
    parser.add_argument('--timeout-seconds',type=int,default=3000)
    args=parser.parse_args()
    manifest_path=confined(args.manifest);manifest=read(manifest_path)
    evidence=confined(manifest['evidence']);evidence.mkdir(parents=True,exist_ok=True)
    mode=manifest.get('mode','live')
    if mode not in ('live','cold'):raise ValueError('Manifest mode must be live or cold')
    if any((evidence/name).exists() for name in ('state.json','peer-1.json','peer-2.json','runner-request.json','launch.json')):
        raise ValueError('Each launch requires a fresh evidence directory; first-run cold input is separate')
    if mode=='cold':
        cold_input=confined(manifest['cold_input'])
        if cold_input.parent==evidence:raise ValueError('Cold input/evidence directories must differ')
        baseline=read(cold_input);prior=read(cold_input.parent/'v6-network-server.json');prior_launch=read(cold_input.parent/'launch.json')
        if not baseline.get('eligible') or not prior.get('passed') or not prior.get('generation_quiescent_before_halt') or not prior_launch.get('full_phase_matrix_passed'):
            raise ValueError('Cold must follow a fully passed and normally closed first three-JVM run')
        if any(code!=0 for code in prior_launch.get('exit_codes',{}).values()) or len(prior_launch.get('exit_codes',{}))!=3:
            raise ValueError('All three previous own JVMs must have exited normally before cold launch')
    entries={key:manifest[key] for key in ('server','peer1','peer2')}
    profiles=[confined(entry['cwd']) for entry in entries.values()]
    if len(set(profiles))!=3: raise ValueError('Server and both clients need different isolated profiles')
    if mode=='cold' and profiles[0]!=Path(baseline['server_directory']).resolve():
        raise ValueError('Cold server cwd must be the exact previous server profile')
    props=properties(profiles[0]/'server.properties')
    if props.get('server-port')!='26593' or props.get('online-mode')!='false' or props.get('allow-flight','false')!='false':
        raise ValueError('Root must prepare localhost port26593, offline mode and allow-flight=false')
    if 'eula=true' not in (profiles[0]/'eula.txt').read_text(): raise ValueError('Root-prepared server EULA file is required')
    for key,entry in entries.items():
        if not isinstance(entry['argv'],list) or not entry['argv']: raise ValueError('Commands must be explicit argv arrays')
        executable=Path(entry['argv'][0]).resolve()
        if not executable.is_file() or executable.name.lower() not in ('java','java.exe'): raise ValueError('Only explicit Java executables are accepted')
        confined(entry['log'])
        environment=entry.get('env',{})
        if set(environment)-{'MOD_CLASSES'}:raise ValueError('Only the explicit development MOD_CLASSES environment override is accepted')
        if 'MOD_CLASSES' not in environment:raise ValueError('Direct development launch requires its explicit compiled mod folders')
        for folder in environment['MOD_CLASSES'].split(os.pathsep):
            mod_id,separator,location=folder.partition('%%')
            if mod_id!='interstice' or not separator or not confined(location).is_dir():
                raise ValueError('Development mod folders must be frozen interstice outputs under .verification')
    processes={};logs={};launch={'launcher_pid':os.getpid(),'manifest':str(manifest_path),'mode':mode,'processes':{},'no_force_kills':True}
    if mode=='cold':launch['cold_input']=str(cold_input)
    def start(key):
        entry=entries[key];log=confined(entry['log']);log.parent.mkdir(parents=True,exist_ok=True);logs[key]=log.open('wb')
        child=subprocess.Popen(entry['argv'],cwd=confined(entry['cwd']),env=os.environ|entry['env'],stdin=subprocess.PIPE,stdout=logs[key],stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        processes[key]=child;launch['processes'][key]={'pid':child.pid,'cwd':entry['cwd'],'log':str(log)};atomic(evidence/'launch.json',launch)
        print(json.dumps({'started':key,'pid':child.pid,'log':str(log)}),flush=True)
    session='';timed_out=False
    try:
        start('server');deadline=time.monotonic()+args.timeout_seconds
        while not (evidence/'state.json').is_file():
            if processes['server'].poll() is not None: raise RuntimeError('Dedicated server exited before publishing its scenario state')
            if time.monotonic()>=deadline: raise TimeoutError('Dedicated startup timed out; child was not force-killed')
            time.sleep(.5)
        state=read(evidence/'state.json');session=state['session_id'];launch['session_id']=session
        start('peer1');start('peer2');last=0;timed_out=False
        while any(child.poll() is None for child in processes.values()):
            now=time.monotonic()
            if now-last>=15:
                try:
                    state=read(evidence/'state.json');print(json.dumps({'phase':state.get('phase'),'tick':state.get('server_tick'),'finished':state.get('finished')}),flush=True)
                except (ValueError,OSError): pass
                last=now
            if now>=deadline:
                timed_out=True;atomic(evidence/'runner-request.json',{'session_id':session,'request':'close','reason':'Own launcher bounded timeout'})
                child=processes['server']
                if child.poll() is None and child.stdin:
                    try:child.stdin.write(b'stop\n');child.stdin.flush()
                    except OSError:pass
                deadline=now+60
                if launch.get('cooperative_timeout_requested'):
                    launch['still_running_owned_pids']=[p.pid for p in processes.values() if p.poll() is None];break
                launch['cooperative_timeout_requested']=True
            time.sleep(.5)
    except BaseException as error:
        launch['launcher_error']=repr(error)
        if session:
            atomic(evidence/'runner-request.json',{'session_id':session,'request':'close','reason':'Own launcher error: '+repr(error)})
        child=processes.get('server')
        if child is not None and child.poll() is None and child.stdin:
            try:child.stdin.write(b'stop\n');child.stdin.flush()
            except OSError:pass
        close_deadline=time.monotonic()+60
        while any(p.poll() is None for p in processes.values()) and time.monotonic()<close_deadline:time.sleep(.5)
        launch['still_running_owned_pids']=[p.pid for p in processes.values() if p.poll() is None]
    launch['exit_codes']={key:child.poll() for key,child in processes.items()};launch['timed_out']=timed_out
    server_report=evidence/('v6-network-cold-server.json' if mode=='cold' else 'v6-network-server.json')
    server=read(server_report) if server_report.is_file() else {}
    peers=[read(evidence/f'peer-{id}.json') if (evidence/f'peer-{id}.json').is_file() else {} for id in (1,2)]
    pid_match='server' in processes and 'peer1' in processes and 'peer2' in processes and server.get('server_pid')==processes['server'].pid and all(peers[id-1].get('pid')==processes[f'peer{id}'].pid for id in (1,2))
    launch['actual_three_jvm_pid_match']=pid_match
    clean=all(p.get('own_client_clean_disconnect') for p in peers)
    own_exit=all(child.poll()==0 for child in processes.values())
    launch['minimum_network_passed']=mode=='live' and bool(server.get('minimum_network_passed')) and pid_match and clean
    launch['full_phase_matrix_passed']=mode=='live' and bool(server.get('passed')) and launch['minimum_network_passed'] and bool(server.get('generation_quiescent_before_halt')) and not timed_out and own_exit and not launch.get('launcher_error')
    launch['cold_restart_passed']=mode=='cold' and bool(server.get('passed')) and bool(server.get('cold_server_restart_checked')) and pid_match and clean and bool(server.get('generation_quiescent_before_halt')) and not timed_out and own_exit and not launch.get('launcher_error')
    if mode=='cold':
        launch['all_restarted_jvm_pids_different']=server.get('server_pid')!=baseline['previous_server_pid'] and all(peers[id-1].get('pid')!=baseline['peers'][str(id)]['previous_pid'] for id in (1,2))
        launch['cold_restart_passed'] &= launch['all_restarted_jvm_pids_different']
    atomic(evidence/'launch.json',launch)
    for stream in logs.values():stream.close()
    print(json.dumps(launch,indent=2),flush=True)
    return 0 if launch['full_phase_matrix_passed'] or launch['cold_restart_passed'] else 1


if __name__=='__main__':
    raise SystemExit(main())
