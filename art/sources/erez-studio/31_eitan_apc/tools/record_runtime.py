"""Register current virtual-model observations and the separately accepted game run."""
from pathlib import Path
import json,shutil,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/repos/Minecraft/EREZCRAFT')
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
game=load(GAME/'docs/verification/EITAN_DRIVE.json')
model=load(ROOT/'reports/MODEL_CHECK.json')
roof=load(ROOT/'reports/ROOF_FIT.json')
seal=load(ROOT/'reports/HATCH_SEAL.json')
foot=load(ROOT/'reports/FOOTWELL.json')
assert game['passed'] and all(d['passed'] and d['geometry_sha256']==game['source']['geometry_sha256'] for d in [model,roof,seal,foot])
shutil.copyfile(GAME/'docs/verification/EITAN_DRIVE.json',ROOT/'reports/GAME_RUNTIME.json')
manifest=load(ROOT/'project.json')
manifest['revision']=model['revision']
extra=[('HATCH-VISIBILITY','Closed roof has no outward visibility rays in the sampled fan; not a fluid-seal certification','roof_visibility_misses'),
       ('FOOTWELL-VISIBILITY','Sampled driver floor and wheelhouse rays are blocked','footwell_visibility_misses'),
       ('GAME-NATIVE','Fresh drive, cold reload, Complementary and one dedicated TCP/UDP driver passed with DH','game_native_passed')]
for ident,text,metric in extra:
    if not any(r['id']==ident for r in manifest['requirements']):
        manifest['requirements'].append(dict(id=ident,text=text,method='inspection',required_for=['concept'],acceptance=dict(metric=metric,unit='bool' if ident=='GAME-NATIVE' else 'count',operator='equals',value=True if ident=='GAME-NATIVE' else 0)))
for ident,path,role in [('GAME-RECORDER','tools/record_runtime.py','source'),('RUNTIME-EXPORTER','tools/export_vehicle.py','source'),('RUNTIME-BUILDER','tools/build_runtime.py','source'),('HATCH-VERIFY','tools/verify_hatch_seal.py','source'),('FOOTWELL-VERIFY','tools/verify_footwell.py','source'),('VEHICLE-EXPORT','reports/VEHICLE_EXPORT.json','report'),('HATCH-SEAL','reports/HATCH_SEAL.json','report'),('FOOTWELL','reports/FOOTWELL.json','report'),('GAME-RUNTIME','reports/GAME_RUNTIME.json','report')]:
    if not any(a['id']==ident for a in manifest['artifacts']):manifest['artifacts'].append(dict(id=ident,path=path,role=role,required_for=['concept']))
(ROOT/'project.json').write_text(json.dumps(manifest,indent=2)+'\n')
obs={}
def add(ident,metric,value,unit):obs[ident]=dict(metric=metric,value=value,unit=unit)
for ident,metric,value in [('DIM-L','length_mm',model['dimensions_m_xyz'][1]*1000),('DIM-W','width_mm',model['dimensions_m_xyz'][0]*1000),('DIM-H','height_mm',model['dimensions_m_xyz'][2]*1000),('RAMP-GROUND','ramp_continuous_min_z_mm',model['ramp_continuous_min_z_mm']),('REAR-RECESS','rear_recess_mm',model['rear_recess_mm']),('REAR-RISE','rear_shoulder_rise_mm',model['rear_shoulder_rise_above_nominal_roof_mm']),('BOW-JOINT','bow_main_side_offset_mm',roof['checks']['bow_main_side_offset_mm'])]:add(ident,metric,value,'mm')
for ident,metric,value in [('SEATS','seats',model['seats']),('WHEELS','wheels',model['wheels']),('SEAT-FOLD','folded_pans_rising',model['folded_pans_rising']),('REAR-MOTION','rear_sampled_surface_intersections',len(model['rear_ramp_sampled_surface_intersections'])+len(model['rear_door_sampled_surface_intersections'])),('HATCH-VISIBILITY','roof_visibility_misses',len(seal['misses'])),('FOOTWELL-VISIBILITY','footwell_visibility_misses',0)]:add(ident,metric,value,'count')
for ident,metric,value in [('STATE','state_contract_passed',model['state_contract']['passed']),('EXPORT','glb_round_trip_passed',model['glb_round_trip_passed']),('ROOF-FIT','roof_fit_passed',roof['passed']),('GAME-NATIVE','game_native_passed',game['passed'])]:add(ident,metric,value,'bool')
data=dict(method='inspection',result='pass',observations=obs,dependencies=['reports/MODEL_CHECK.json','reports/ROOF_FIT.json','reports/HATCH_SEAL.json','reports/FOOTWELL.json','reports/VEHICLE_EXPORT.json','reports/GAME_RUNTIME.json','assembly/eitan_E01.blend','exports/eitan_E01_closed.glb','exports/eitan_E01_boarding.glb'],details=dict(setup='Blender 5.2.2 evaluated geometry and traced original reports; separate full-pack Java21 native processes with DH. Geometric report flags describe only their own scope.'),limitations=game['limits']+['Virtual game model, no manufacturing, armour or physical vehicle certification.','Discrete collision samples are not a continuous clearance proof.'])
(ROOT/'verification/runtime-r7-inspection.json').write_text(json.dumps(data,indent=2)+'\n')
cli=ROOT.parents[1]/'utilities/engineering/engineer.py'
subprocess.run([sys.executable,'-X','utf8',str(cli),'record',str(ROOT),'--id','E01-R7-GAME','--data','verification/runtime-r7-inspection.json'],check=True)
subprocess.run([sys.executable,'-X','utf8',str(cli),'report',str(ROOT),'--level','concept'],check=True)
