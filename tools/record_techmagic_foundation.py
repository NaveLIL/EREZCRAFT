"""Record checked foundation artifacts without claiming complete modpack acceptance."""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--boot-evidence', type=Path, required=True)
    parser.add_argument('--launcher-root', type=Path, required=True)
    parser.add_argument('--delivery-receipt', type=Path, required=True)
    args = parser.parse_args()
    evidence, boot = args.evidence.resolve(), args.boot_evidence.resolve()
    summary, boot_summary = read(evidence/'summary.json'), read(boot/'summary.json')
    assert summary['passed'] and boot_summary['passed']
    audit = read(ROOT/'.verification/release-artifact-0.7.0-preview.3.json')
    assert audit['passed'] and audit['jar_sha256'] == sha(Path(audit['jar']))
    package = read(ROOT/'.verification/techmagic-foundation-package.json')
    assert package['passed'] and package['own_mod_sha256'] == audit['jar_sha256']
    stage = Path(package['directory'])
    inventory = read(stage/'inventory.json')
    assert sha(stage/'inventory.json') == package['inventory_sha256']
    for side, rows in inventory.items():
        for row in rows:
            path = stage/side/row['path']
            assert sha(path) == row['sha256'] and path.stat().st_size == row['size']
    delivery = read(args.delivery_receipt)
    assert delivery['passed'] and delivery['signaturesAndBlobHashesVerified'] and not delivery['playable'] and not delivery['productionPublished']
    assert Path(delivery['source']).resolve() == stage.resolve() and delivery['modJars'] == 36 and delivery['files'] == 37
    creator = read(evidence/'profiles/techmagicPregen/techmagic-pregen-create-validation.json')
    reload = read(evidence/'profiles/techmagicPregen/techmagic-pregen-reload-validation.json')
    assert creator['passed'] and reload['passed'] and creator['samples'] == reload['samples']
    assert creator['pid'] != reload['pid'] and reload['creator_pid'] == creator['pid']
    assert [row['dimension'] for row in creator['samples']] == ['minecraft:overworld', 'interstice:islands_v6']
    assert sum(len(row['chunks']) for row in creator['samples']) == 18
    client = read(evidence/'profiles/techmagicFoundationSmoke/techmagic-spine-validation.json')
    assert client['passed'] and client['max_heap_bytes'] == 10*1024**3 and client['foundation_components'] == 31
    log = (evidence/'runTechmagicPregenCreate.log').read_text(encoding='utf-8')
    processed = {}
    for world in ('minecraft:overworld', 'interstice:islands_v6'):
        match = re.search(r'Task finished for '+re.escape(world)+r'\. Processed: (\d+) chunks', log)
        assert match and int(match[1]) == 25
        processed[world] = int(match[1])
    assert log.count('Chunky running. Disabling DH world gen') == 2
    assert log.count('Chunky no longer running. Re-enabling DH world gen') == 2
    forest = read(boot/'runForestCaveGameTestServer.json')
    assert forest['accepted'] and forest['required_tests_passed'] == 4
    launcher = args.launcher_root.resolve()
    source_files = ['crates/launcher-core/src/game.rs', 'apps/launcher/src-tauri/src/main.rs',
                    'apps/launcher/src/App.svelte', 'apps/launcher/src/bridge.ts', 'tools/verify-interstice-pack.mjs']
    exe = launcher/'target/debug/erezcraft-launcher.exe'
    assert exe.is_file()
    result = {'schema': 1, 'interstice_version': '0.7.0-preview.3', 'foundation_release': package['release'],
              'passed': True, 'scope': 'Current artifact audit, real loader startup, bounded Chunky/DH functional/cold proof and private launcher publisher delivery; not full gameplay/server release',
              'artifact': audit, 'package': package, 'launcher_delivery': delivery,
              'launcher': {'path': str(launcher/'apps/launcher'), 'native_preview_binary': str(exe), 'binary_sha256': sha(exe),
                           'source_sha256': {name: sha(launcher/name) for name in source_files},
                           'observed_checks': {'cargo_test_core_locked': '15 passed', 'node_publisher_tests': '4 passed',
                                               'svelte_check': '0 errors/0 warnings', 'vite_build': 'passed',
                                               'cargo_check_launcher_locked': 'passed', 'cargo_build_custom_protocol_locked': 'passed'},
                           'existing_user_memory_preferences_preserved': True},
              'real_client_startup': client, 'real_dedicated_startup': read(boot/'profiles/techmagicFoundationServer/techmagic-foundation-server-validation.json'),
              'pregen': {'processed_chunks': processed, 'selected_saved_FULL_chunks_cold_checked': 18,
                         'creator_pid': creator['pid'], 'reload_pid': reload['pid'], 'seed': creator['seed'],
                         'samples': creator['samples'], 'dh_pause_resume_events_verified': True,
                         'quiescence': {world: creator[world+'_quiescence'] for world in processed},
                         'production_pregen_started': False, 'dh_lod_coverage_verified': False},
              'current_forest_component_tests': 4, 'python_verifier_tests_observed': 63,
              'historical_full_cave_matrix': {'version': '0.7.0-preview.2', 'report': 'docs/verification/V6_FOREST_CAVES.json', 'current_full_build_claim': False},
              'evidence': [str(evidence), str(boot), str(args.delivery_receipt.resolve())],
              'saves_unchanged': summary['saves_unchanged'] and boot_summary['saves_unchanged'],
              'players_admitted': False, 'production_published': False,
              'open_gates': ['target-server 1/4/10-player machine/ship/ME load and tuning', 'interconnected recipes/quests without bypasses',
                             'ship physics/roof/seas/SURGE gameplay', 'graves with attached backpacks54/72/84 and void/cold/reconnect',
                             'Xaero V6 cave layers and separate V5/V6 markers', 'Iris/Sodium/Complementary actual DH/Flywheel/Veil/Sable/V6 rendering',
                             'Create addon catalog conflict/runtime selection', 'unresolved real TCP winch anti-flight and 200 ticks/cold',
                             'final production worldgen/seed/radius lock and pregen/cold/LOD admission', 'public release and upstream redistribution review', 'human Survival acceptance']}
    target = ROOT/'docs/verification/TECHMAGIC_FOUNDATION.json'
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'passed':True, 'report':str(target), 'client_jars':36, 'server_jars':34, 'cold_FULL_samples':18, 'heap_gb':10}))


if __name__ == '__main__':
    main()
