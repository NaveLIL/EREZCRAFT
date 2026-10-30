"""Record the completed native stair test against its authored geometry export."""
from pathlib import Path
import hashlib
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/repos/Blender/creations/32_nordhavn_expedition')
PROFILE=ROOT/'.verification/ne01h06/studioMarineAe2'
proof=json.loads((PROFILE/'marine-vessel.json').read_text(encoding='utf-8-sig'))
data=json.loads((MODEL/'exports/minecraft/ne01_runtime.json').read_text())
assert proof['passed'] and proof['process_exit_code']==0 and proof['dh_loaded']
assert proof['native_cells']==len(data['cells']) and proof['native_walk_routes']==4
assert all(not r['flying'] and r['on_ground'] for r in proof['walked_stairs'])
assert data['revision']=='NE01-A05' and data['geometry_sha256']=='1302b87854b8e968047928a20a2ce38128d40e21a6c346b128eb28bb4fcae057'
out=ROOT/'docs/verification/ne01-hull';out.mkdir(exist_ok=True)
files=[PROFILE/'marine-vessel.json']+sorted((PROFILE/'screenshots').glob('ne01-native-*.png'))
hashes={}
for f in files:
    shutil.copy2(f,out/f.name);hashes[f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
result=dict(revision=data['revision'],geometry_sha256=data['geometry_sha256'],assembly_sha256=data['assembly_sha256'],
            profile=str(PROFILE.relative_to(ROOT)),proof=proof,copied_artifact_sha256=hashes,
            scope='Native Creative walking on four stair flights on paused Sable hull with DH. Source geometry and exported collision accepted for these routes.',
            previous_attempts=[dict(profile='ne01h02',result='BE pose did not update cached collision; replaced with blockstate pose.'),
                               dict(profile='ne01h03',result='Insufficient stepping headroom at fore stair approach.'),
                               dict(profile='ne01h04',result='Main stair approach was behind an aft bulkhead.'),
                               dict(profile='ne01h05',result='Sable expanded stepping volume touched a canopy post.'),
                               dict(profile='ne01h06',result='All four routes passed, grounded and without flight; clean exit 0.')],
            limitations=['No Survival or multiplayer acceptance.','Physics was paused; no sea propulsion or stability acceptance.',
                         'Mechanisms were opened through authoritative blockstates; ordinary player click and actor-blocked closure are separate checks.',
                         'This is four stair routes, not every possible route or a continuous mechanism swept path.'])
(ROOT/'docs/verification/NE01_NATIVE_GEOMETRY.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(revision=data['revision'],native_stair_routes=4,clean_exit=True,images=len(files)-1)))
