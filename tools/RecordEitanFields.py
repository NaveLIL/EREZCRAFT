"""Accept the real terrain run and its separate cold process without touching a world."""
from pathlib import Path
import argparse,json,shutil,hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--run-root',type=Path,required=True);a=p.parse_args()
run=a.run_root.resolve();assert run.parent==ROOT/'.verification' and run.name.startswith('eitan-drive-fields-')
profile=run/'studioEitanDrive'
reports={}
for mode in ['fields','fields-reload']:
    path=profile/f'eitan-drive-{mode}.json'
    if mode=='fields' and (profile/'eitan-drive-fields-route.json').is_file():path=profile/'eitan-drive-fields-route.json'
    d=json.loads(path.read_text(encoding='utf-8-sig'))
    assert d['passed'] and d['process_exit_code']==0 and d['source_changed_during_run'] is False,mode
    assert not d['owner_profile_used'] and d['dh_loaded'] and d['terrain_unchanged'],mode
    assert d['terrain_before_sha256']==d['terrain_after_sha256'],mode
    assert d['min_driver_health']>19.9 and d['max_tilt_rad']<0.96 and d['max_lateral_m']<6,mode
    reports[mode]=d
drive,cold=reports.values()
assert drive["world_seed"]==20261030 and (drive["route_x"],drive["route_z"])==(-640,704)
assert drive["round_trips_completed"]==4
for name,expected in drive["runtime_source_sha256"].items():
    assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==expected,name
numerical=json.loads((ROOT/".verification/eitan-drive/field-numerical.json").read_text())
assert numerical["passed"] and numerical["checks"]==4308
for name,expected in numerical["sources"].items():
    assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==expected,name
assert drive['outbound_m']>=60 and drive['return_m']>=57 and 4.3<drive['peak_m_s']<18
assert drive['ride_max_y']<=drive['terrain_max_y']+4,'Vehicle launched above the route'
assert max(drive['route_height_profile_every_2m'])-min(drive['route_height_profile_every_2m'])>=2
assert drive['ride_max_y']-drive['ride_min_y']>=1,'The vehicle did not actually climb and descend'
assert cold['cold_body_and_state_restored'] and cold['cold_real_driver_restored'] and cold['cold_drive_m']>=8
assert drive['runtime_source_sha256']==cold['runtime_source_sha256']
moving_errors=[v for phase,v in drive['view_trace']['phase_max_m'].items() if phase in ['4','5','6','7']]
assert moving_errors and max(moving_errors)<.005,'Driver camera separated from the rendered body'
cold_errors=[v for phase,v in cold['view_trace']['phase_max_m'].items() if phase in ['102','103']]
assert cold_errors and max(cold_errors)<.005,'Cold driver camera separated from the body'
baseline=json.loads((ROOT/'docs/verification/EITAN_DRIVE.json').read_text(encoding='utf-8-sig'))['runtime_source_sha256']
source_delta=sorted(name for name in set(baseline)|set(drive['runtime_source_sha256']) if baseline.get(name)!=drive['runtime_source_sha256'].get(name))
assert source_delta==['src/main/java/pro/erez/interstice/mixin/EitanCameraViewMixin.java','src/main/java/pro/erez/interstice/vehicle/EitanClearance.java','src/main/java/pro/erez/interstice/vehicle/EitanController.java','src/main/java/pro/erez/interstice/vehicle/EitanDynamics.java'],source_delta
artifact=json.loads((ROOT/"docs/verification/EITAN_ARTIFACT.json").read_text())
jar=Path(artifact["jar"])
assert hashlib.sha256(jar.read_bytes()).hexdigest()==artifact["sha256"]
resources=[]
for folder in ["data/interstice/eitan","assets/interstice/models/block/eitan_vehicle","assets/interstice/textures/block/eitan"]:
    resources.extend(path for path in (ROOT/"src/main/resources"/folder).rglob("*") if path.is_file())
resources.append(ROOT/"src/main/resources/assets/interstice/provenance/eitan_vehicle.json")
with zipfile.ZipFile(jar) as archive:
    for path in resources:
        assert archive.read(path.relative_to(ROOT/"src/main/resources").as_posix())==path.read_bytes(),path
assert len(resources)==artifact["resource_files_checked"]
assets=dict(unchanged=True,files=len(resources),baseline_jar_sha256=artifact["sha256"],baseline_jar_not_rebuilt=True)
dh_evidence={}
for mode,suffix in [('fields',''),('fields-reload','-reload')]:
    log_path=ROOT/'.verification/eitan-drive'/f'{run.name.removeprefix("eitan-drive-")}{suffix}.log'
    log=log_path.read_text(encoding='utf-8',errors='replace')
    lines=[line for line in log.splitlines() if 'DH Rendering successfully bound to:' in line or 'GLProxy creation successful.' in line or 'Overriding vanilla MC settings to better fit Distant Horizons' in line]
    assert len(lines)>=3,mode
    dh_evidence[mode]=dict(path=str(log_path.relative_to(ROOT)),sha256=hashlib.sha256(log_path.read_bytes()).hexdigest(),active_renderer=lines)
dest=ROOT/'docs/verification/eitan-fields';dest.mkdir(parents=True,exist_ok=True)
cards=[]
for name,label in [('natural-exterior','Сборка на естественном поле'),('natural-driving','Поездка по настоящему рельефу'),('natural-return','Возвращение и торможение')]:
    file=f"eitan-drive-{drive['mode']}-{name}.png"
    shutil.copyfile(profile/'screenshots'/file,dest/file)
    cards.append(f'<figure><a href="{file}"><img src="{file}" alt="{label}"></a><figcaption>{label}</figcaption></figure>')
(dest/'gallery.html').write_text('<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Eitan — полевые тесты</title><style>body{background:#142029;color:#d0dad8;font:17px system-ui;max-width:1500px;margin:32px auto;padding:0 20px}h1,a{color:#e3c889}figure{margin:24px 0;border:1px solid #9b8255;padding:12px}img{width:100%;display:block}figcaption{padding-top:10px}</style><h1>Eitan E01-R7 — естественный рельеф</h1><p>Полная сборка с DH. Земля не выравнивалась. Сборка и посадка — подготовка сценария; езда — настоящими клавишами, водитель в Survival. Мир принадлежит одноразовому профилю.</p>'+''.join(cards),encoding='utf-8')
failed_path=ROOT/".verification/eitan-drive-fields-03/studioEitanDrive/eitan-drive-fields-route.json"
failed=json.loads(failed_path.read_text(encoding="utf-8-sig"))
assert not failed["passed"] and failed["phase"]==6 and "reverse/return drive stuck" in failed["error"]
assert failed["route_x"]==drive["route_x"] and failed["route_z"]==drive["route_z"]
assert failed["geometry_sha256"]==drive["geometry_sha256"]
regression=dict(original_failure=str(failed_path.relative_to(ROOT)),original_sha256=hashlib.sha256(failed_path.read_bytes()).hexdigest(),same_seed_and_route=True,geometry_preserved=True,fix="bounded spring ride height; four loaded-chunk casts at 5 Hz; no assigned body pose or collision bypass; first-person eyes follow the rigid cabin frame")
result=dict(passed=True,checks=reports,numerical=numerical,regression=regression,assets=assets,baseline_source_delta=source_delta,dh_evidence=dh_evidence,owner_profile_used=False,production_changed=False,limits=['one prepared vehicle and one driver','bounded naturally selected route','assembly/mount are fixture setup','no Survival crafting, balancing or server capacity claim'])
(ROOT/'docs/verification/EITAN_FIELDS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(passed=True,outbound_m=drive['outbound_m'],return_m=drive['return_m'],climb_span_m=drive['ride_max_y']-drive['ride_min_y'],max_eye_error_m=max(moving_errors))))
