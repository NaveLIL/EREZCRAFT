"""Summarize measured movement; never infer speed from RPM or configured limits."""
from pathlib import Path
import argparse,json,statistics,hashlib
p=argparse.ArgumentParser();p.add_argument('proof');p.add_argument('--output')
a=p.parse_args();source=Path(a.proof)
d=json.loads(source.read_text(encoding='utf-8-sig'))
samples=[s for s in d.get('propulsion_samples',[]) if s['phase']==3 and 'kmh' in s]
assert samples,'No measured straight-run samples'
assert all(s['kmh']>=0 for s in samples)
tail=samples[-20:]
result=dict(source=str(source),source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
    trial_passed=d.get('passed',False),process_exit_code=d.get('process_exit_code'),
    source_version=d.get('version'),samples=len(samples),straight_seconds=(samples[-1]['tick']-samples[0]['tick'])/20,
    maximum_measured_kmh=max(s['kmh'] for s in samples),
    last_10_seconds_mean_kmh=statistics.mean(s['kmh'] for s in tail),
    last_10_seconds_min_kmh=min(s['kmh'] for s in tail),
    last_10_seconds_max_kmh=max(s['kmh'] for s in tail),
    max_tilt_degrees=d.get('max_tilt_all_phases',0)*180/3.141592653589793,
    actual_driven_path_m=d.get('driven_path_m'),terrain_at_finish=d.get('terrain_in_world_bounds'),
    native_propulsion=d.get('passed_runtime',False),pilot_failsafe=d.get('pilot_failsafe',False),
    hud_max_kmh=d.get('hud_max_speed_mps',0)*3.6,
    target_50_kmh_met=d.get('passed',False) and d.get('process_exit_code')==0 and
        45<=statistics.mean(s['kmh'] for s in tail)<=51 and max(s['kmh'] for s in samples)<=53,
    limitations=['Controlled native trial, not Survival or multiplayer acceptance.',
                 'Configured speed limit and RPM are not measurements.'])
if a.output:Path(a.output).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(result,ensure_ascii=False))
