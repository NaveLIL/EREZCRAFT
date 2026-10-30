"""Snapshot the completed full-vessel AE2 create/cold proofs before design revision."""
from pathlib import Path
import hashlib
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/32_nordhavn_expedition')
PROFILE=ROOT/'.verification/ne01m04/studioMarineAe2'
out=ROOT/'docs/verification/ne01-me';out.mkdir(exist_ok=True)
records={};hashes={}
for mode in ['create','reload']:
    source=PROFILE/f'marine-fullme-{mode}.json';d=json.loads(source.read_text(encoding='utf-8-sig'))
    assert d['passed'] and d['process_exit_code']==0 and d['dh_loaded']
    assert d['six_terminals_same_grid'] and d['native_menu_opens']==12 and d['controlled_move_distance']==40
    assert d['diamonds']==336 and d['gold_ingots']==64
    assert d['power_loss_preserved_items'] and d['offline_native_transfer_rejected']
    assert d['finite_fuel_remaining']<64 and not d['owner_profile_used']
    records[mode]=d
    files=[source]+sorted((PROFILE/'screenshots').glob(f'ne01-fullme-{mode}-*.png'))
    for f in files:shutil.copy2(f,out/f.name);hashes[f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
assert records['create']['ship_uuid']==records['reload']['ship_uuid']
assert records['create']['native_shift_click_insertion']
design=json.loads((MODEL/'exports/minecraft/ne01_runtime.json').read_text())
assert design['revision']=='NE01-A05' and design['geometry_sha256']=='1302b87854b8e968047928a20a2ce38128d40e21a6c346b128eb28bb4fcae057'
dependencies={}
for pattern in ['appliedenergistics2-*.jar','sable-neoforge-*.jar']:
    for p in (PROFILE/'mods').glob(pattern):dependencies[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
result=dict(revision=design['revision'],geometry_sha256=design['geometry_sha256'],assembly_sha256=design['assembly_sha256'],
            runtime_sha256=hashlib.sha256((MODEL/'exports/minecraft/ne01_runtime.json').read_bytes()).hexdigest(),
            scope='Six real AE2 addon terminal hosts, two native DriveBlockEntity subclasses, finite fuel/power, native player rays and native quick-move packet; paused Sable body controlled 40m displacement, create and clean cold reload.',
            records=records,dependency_sha256=dependencies,copied_artifact_sha256=hashes,
            attempts=[dict(profile='ne01m01',result='Early network assertion after client ticks; added actual server-tick boot wait.'),
                      dict(profile='ne01m02',result='Missing drive links and storage-power notification; fixed using the pinned AE2 node interface and power event API.'),
                      dict(profile='ne01m03',result='Five screens worked; sixth crossed render cells. Added complete-screen routing to one authoritative host.'),
                      dict(profile='ne01m04',result='Create and cold reload passed, clean exits 0, 24 total native menu opens.')],
            limitations=['No two-client/concurrent multiplayer proof.','No sailing speed or propulsion acceptance in this fixture.',
                         'Off-grid hidden wiring loom uses actual AE2 connections; it is a custom ship backplane, not a run of visible cable blocks.',
                         'Fuel is a Minecraft solid-fuel adaptation, not a real diesel thermodynamic model.'])
(ROOT/'docs/verification/NE01_NATIVE_ME.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(MODEL/'reports/NATIVE_ME_A05.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(revision=design['revision'],cold_me_passed=True,native_menu_opens=24)))
