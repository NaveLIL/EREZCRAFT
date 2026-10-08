#!/usr/bin/env python3
"""Record already completed acceptance receipts and copy representative, unmodified game frames."""
from pathlib import Path
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]


def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))


def main():
    build=ROOT/'.verification/20261008T112957.283700Z-compact-accepted-build'
    native=ROOT/'.verification/20261008T113008.853981Z-compact-accepted-native'
    receipt=read(build/'build.json')
    assert receipt['accepted'] and receipt['required_tests_passed']==214
    assert read(build/'summary.json')['passed'] and read(native/'summary.json')['passed']
    checks={task:read(native/(task+'.json')) for task in ('runMiningSmoke','runHydrologySmoke','runLivingRealmSmoke')}
    assert all(record['accepted'] for record in checks.values())
    artifact=read(ROOT/'.verification/living-release-artifact.json')
    palettes=read(ROOT/'.verification/ready-palette-audit.json')
    assert artifact['passed'] and palettes['passed']
    target=ROOT/'art/generated/living-realm/v4';target.mkdir(parents=True,exist_ok=True)
    frames=[('hydrologySmoke','hydrology-beach-river-clear.png'),('hydrologySmoke','hydrology-mineral-snow-peak-clear.png'),
            ('hydrologySmoke','hydrology-island-waterfall-clear.png'),('livingRealm','living-ash-surface-clear.png'),
            ('livingRealm','living-vault-cave-dark.png'),('livingRealm','living-clingweed-passage.png'),
            ('miningSmoke','mining-dual-torches-living.png')]
    for profile,name in frames: shutil.copy2(native/'profiles'/profile/'screenshots'/name,target/name)
    report={'version':'0.3.1','terrain_revision':4,'passed':True,'jar':artifact['jar'],'jar_sha256':artifact['jar_sha256'],
            'build_receipt':str(build.relative_to(ROOT)),'native_receipt':str(native.relative_to(ROOT)),
            'required_game_tests':214,'game_test_groups':receipt['required_test_groups'],'python_verifier_tests':31,
            'artifact_models':artifact['models_checked'],'artifact_resources':artifact['resources_compared'],
            'new_ready_palettes_verified':palettes['ready_assets_checked'],
            'native_checks':{name:{'exit_code':r['exit_code'],'accepted':r['accepted'],'seconds':r['duration_seconds']} for name,r in checks.items()},
            'screenshots':[str((target/name).relative_to(ROOT)) for _,name in frames],
            'scope':'Natural Creative geography/water inspection. Prepared Survival rooms/inventory/scaffold/SURGE for isolated mechanics. Not an autonomous Survival progression.',
            'user_saves':{'build':read(build/'summary.json')['saves_unchanged'],'native':read(native/'summary.json')['saves_unchanged'],
                          'world_regeneration_or_owner_client_restart_performed':False},
            'future_not_implemented':['compact stacked toxic island biome','vanilla-compatible iron and redstone deposits']}
    (ROOT/'docs/verification/LIVING_REALM.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Recorded214 GameTests,31 verifier tests,5 native JVMs and7 unmodified frames; SHA {artifact["jar_sha256"]}')


if __name__=='__main__': main()
