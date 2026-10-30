"""Capture A06 profile correction and fresh, isolated whole-vessel evidence."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/32_nordhavn_expedition')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
source=read(MODEL/'source/ne01.json');runtime=read(MODEL/'exports/minecraft/ne01_runtime.json')
geometry=read(MODEL/'reports/MODEL_INSPECTION.json');collision=read(MODEL/'reports/COLLISION_EXPORT.json')
render=read(MODEL/'reports/PREVIEW_RENDER.json')
assert source['revision']==runtime['revision']=='NE01-A06'
assert geometry['passed_geometry'] and geometry['passed_sampled_clearance']
assert not collision['clearance_failures'] and collision['sampled_routes']==72
assert render['source_sha256']==runtime['geometry_sha256']==sha(MODEL/'source/ne01.json')
assert render['assembly_sha256']==runtime['assembly_sha256']==sha(MODEL/'assembly/ne01.blend')
out=ROOT/'docs/verification/ne01-a06';out.mkdir(exist_ok=True)
proofs={};hashes={}
for label,root,file in [('hull','ne01h07','marine-vessel.json'),('me-create','ne01m05','marine-fullme-create.json'),('me-reload','ne01m05','marine-fullme-reload.json')]:
    profile=ROOT/'.verification'/root/'studioMarineAe2';proof=read(profile/file)
    assert proof['passed'] and proof['process_exit_code']==0 and proof['dh_loaded']
    assert proof['layout_revision']==source['revision'] and proof['geometry_sha256']==runtime['geometry_sha256']
    assert not proof['owner_profile_used']
    proofs[label]=proof
    dest=out/label;dest.mkdir(exist_ok=True)
    files=[profile/file]+sorted((profile/'screenshots').glob('ne01-native-*.png' if label=='hull' else 'ne01-fullme-'+label[3:]+'-*.png'))
    for f in files:
        target=dest/f.name;shutil.copy2(f,target);hashes[str(target.relative_to(ROOT))]=sha(target)
assert proofs['hull']['native_cells']==len(runtime['cells'])
assert proofs['hull']['native_walk_routes']==4
assert all(r['on_ground'] and not r['flying'] for r in proofs['hull']['walked_stairs'])
assert proofs['me-create']['ship_uuid']==proofs['me-reload']['ship_uuid']
for label in ['me-create','me-reload']:
    p=proofs[label]
    assert p['diamonds']==336 and p['gold_ingots']==64 and p['six_terminals_same_grid']
    assert len(p['native_menus'])==12
    assert p['power_loss_preserved_items'] and p['offline_native_transfer_rejected']
    assert p['controlled_move_distance']>30 and p['finite_fuel_remaining']<64
    settle=p['chunk_settlement']
    assert settle['stable_actual_server_ticks']>=20 and settle['generation_ref_count']==settle['save_futures_pending']==settle['save_futures_failed']==0
for name in ['side','hero']:
    f=MODEL/f'preview/ne01_{name}.png';assert sha(f)==render['views'][name]
    target=out/f.name;shutil.copy2(f,target);hashes[str(target.relative_to(ROOT))]=sha(target)
report=dict(revision=source['revision'],geometry_sha256=runtime['geometry_sha256'],assembly_sha256=runtime['assembly_sha256'],
            model_project=str(MODEL),hull_reference_m=[29.44,7.32],source_parts=len(source['parts']),native_cells=len(runtime['cells']),
            bridge_setback_delta_m=2.2,canopy_front_y_m=1.0,geometry=geometry,collision=collision,proofs=proofs,artifact_sha256=hashes,
            limitations=['Profile proportions adapted from public drawing, not a factory CAD reproduction.',
                         'Native tests use paused Sable bodies and controlled relocation, not propulsion.',
                         '50 km/h sea speed and live navigation panels remain separate required work.',
                         'No Survival, multiplayer, every-route or mechanism swept-volume acceptance.'])
(ROOT/'docs/verification/NE01_PROFILE_A06.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(MODEL/'reports/NATIVE_A06.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
native_record=dict(method='inspection',result='pass',observations={
    'GAME-WALK':dict(metric='native_walk_passed',unit='bool',value=True),
    'ME-COLD':dict(metric='native_me_cold_passed',unit='bool',value=True)},
    dependencies=['reports/NATIVE_A06.json','exports/minecraft/ne01_runtime.json','assembly/ne01.blend'],
    details=dict(setup='Inspection of isolated complete Minecraft 1.21.1 / NeoForge / DH / Sable / AE2 create and cold-reload proofs. Four ordinary keyboard stair routes, 24 native menus, actual storage cells, finite coal power. Physics paused; 40m body teleport is not propulsion.'),
    limitations=report['limitations'])
(MODEL/'verification/native-a06.json').write_text(json.dumps(native_record,indent=2)+'\n')
historical=MODEL/'reports/HISTORICAL_PROJECT_A02.json'
if read(MODEL/'project.json')['revision']=='NE01-A02' and not historical.exists():shutil.copy2(MODEL/'project.json',historical)
print(json.dumps(dict(revision=source['revision'],native_cells=len(runtime['cells']),stair_routes=4,native_menus=24,cold_reload=True)))
