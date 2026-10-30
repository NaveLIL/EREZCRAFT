"""Read-only artifact comparisons; writes a bounded proof without touching profiles."""
from pathlib import Path
import argparse, hashlib, json, zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--version', required=True)
parser.add_argument('--profile', required=True)
parser.add_argument('--component-log', default='.verification/skat-20261029/components98.log')
args = parser.parse_args()
profile = Path(args.profile).resolve()
assert profile.is_relative_to((ROOT / '.verification').resolve())
read = lambda p: json.loads(Path(p).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
jar = ROOT / f'build/libs/interstice-{args.version}.jar'
baseline = ROOT / 'build/libs/interstice-0.7.0-preview.61-toxin-waves.jar'
with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(jar) as new:
    png = [p for p in old.namelist() if p.endswith('.png')]
    geometry = [p for p in old.namelist() if p.endswith('.class') and any(s in p for s in ['/worldgen/', '/geometry/'])]
    assert all(old.read(p) == new.read(p) for p in png + geometry)
    changed = [p for p in old.namelist() if p.endswith('.class') and old.read(p) != new.read(p)]
    runtime_files = 0
    for tree in ['build/classes/java/main', 'build/resources/main']:
        base = ROOT / tree
        for actual in base.rglob('*'):
            if actual.is_file():
                assert new.read(actual.relative_to(base).as_posix()) == actual.read_bytes(), str(actual)
                runtime_files += 1
    textures = read(ROOT / 'src/main/resources/assets/interstice/provenance/skat_textures.json')
    source = Path('D:/repos/Blender/creations/25_skat')
    for entry in textures:
        actual = new.read(f"assets/interstice/textures/block/skat/{entry['name']}.png")
        assert hashlib.sha256(actual).hexdigest() == entry['derived_sha256']
        assert actual == (source / entry['derived_file']).read_bytes()
    provenance = read(ROOT / 'src/main/resources/assets/interstice/provenance/skat_sk03.json')
    assert all(sha(source / path) == digest for path, digest in {**provenance['files'], **provenance['base_files']}.items())
    assert provenance['revision'] == 'SK03'
    template = json.loads(new.read('data/interstice/skat/sk03.json'))

create = read(profile / 'skat-create.json')
resume_file = profile / 'skat-resume.json'
resume = read(resume_file) if resume_file.exists() else create
cold = read(profile / 'skat-reload.json')
for evidence in [resume, cold]:
    assert evidence['passed'] and evidence['process_exit_code'] == 0
    assert evidence['dh_loaded'] and not evidence['owner_profile_used']
    assert evidence['version'] == args.version
    assert not evidence.get('fixture_propellers_repaired', 0)
    assert not evidence.get('fixture_capsize_reset_before_test', False)
    assert not evidence.get('entry_respawn_from_failed_fixture', False)
    assert evidence['systems']['passed']
    light = evidence['systems']['lighting']
    assert light['passed'] and light['real_console_panel'] and light['independent_circuits']
    assert light['all_off'] and light['dynamic_api'] and light['world_headlight_level'] > 1
    assert light['gpu_atlas_bytes'] == 228480 and light['mass_preserved']
    assert light['hold_aisle_min_light'] >= 10
    assert light['both_hand_toxin_light']
    assert light['offhand_torch_patch_red'] > light['torch_dark_patch_red'] + 3
    assert light['mainhand_torch_patch_red'] > light['torch_dark_patch_red'] + 3
    assert light['torch_removed_patch_red'] < light['mainhand_torch_patch_red'] - 3
    assert light['invalid_channel_denied'] and light['remote_control_denied']
    assert evidence['structure_cells_checked'] == len(template['cells'])
    for key in ['forehold_reached','real_stair_descent','real_stair_ascent','hatch_occupant_veto','boarding_passed_1','boarding_passed_-1','cargo_real_client_quick_move','cargo_automation','bulk_refuel','cabin_surge_shelter']:
        assert evidence['systems'][key], key
assert cold['survival_cold_login_health'] == 20
assert cold['cold_login_dimension_restores'] > 0
assert cold['cold_login_trace'] and all(point['health'] == 20 for point in cold['cold_login_trace'])
assert cold['lighting_loaded_mask'] == resume['lighting_saved_mask'] == 6
assert cold['systems']['lighting']['toxin_gpu_illumination']
assert cold['cold_powered_drivetrain'] and resume['cold_powered_drivetrain']
for x in [-3, 3]:
    assert abs(cold[f'loaded_engine_saved_rpm_{x}']) > 1
    assert abs(cold[f'loaded_propeller_saved_rpm_{x}']) > 1
assert cold['systems']['lighting']['beauty_no_night_vision']
assert cold['systems']['lighting']['beauty_no_hud'] and cold['systems']['lighting']['beauty_lighting_mask'] == 7
assert cold['systems']['lighting']['toxin_lit_patch_red'] > cold['systems']['lighting']['toxin_dark_patch_red'] + 3
assert resume['ship_uuid'] == cold['ship_uuid']
assert set(resume['inventory_ids']) == set(cold['inventory_ids'])
cpu = read(ROOT / '.verification/skat-20261029/java-geometry.json')
assert cpu['passed']
screenshots = read(ROOT / 'docs/verification/skat-sk03/screenshots.json')
for shot in screenshots:
    assert sha(ROOT / shot['file']) == shot['sha256']
    assert shot['source_run'] == args.version + ' / reload'
    raw_png = (ROOT / shot['file']).read_bytes()
    assert raw_png[:8] == b'\x89PNG\r\n\x1a\n'
    assert int.from_bytes(raw_png[16:20], 'big') == 1920
    assert int.from_bytes(raw_png[20:24], 'big') == 1080
    assert shot['width'] == 1920 and shot['height'] == 1080
    assert not shot['night_vision'] and not shot['hud'] and shot['lighting_mask'] == 7
component_log = ROOT / args.component_log
component_text = component_log.read_text(encoding='utf-8-sig')
assert 'All 7 required tests passed' in component_text and 'BUILD SUCCESSFUL' in component_text

report = {
    'status': 'local_native_verified_owner_and_dedicated_pending',
    'candidate': args.version, 'jar_sha256': sha(jar), 'cpu_geometry': cpu,
    'runtime_files_match_jar': runtime_files, 'light_parts': len(template['lights']), 'shared_light_field_bytes': 228480,
    'revision': 'SK03', 'source_meshes': template['source_meshes'], 'cells': len(template['cells']),
    'source_files_preserved': True, 'provenance': provenance, 'existing_png_count': len(png),
    'existing_png_preserved': True, 'worldgen_geometry_class_count': len(geometry),
    'worldgen_geometry_preserved': True, 'new_texture_count': len(textures),
    'new_textures_match_author_source': True, 'changed_existing_classes': changed,
    'native': resume, 'cold': cold, 'production_changed': False,
    'screenshots': screenshots, 'gallery': 'docs/verification/skat-sk03/gallery.html',
    'component_checks': {'passed': 7, 'scope': 'isolated GameTestServer without DH/Sable', 'log_sha256': sha(component_log)},
    'final_client_opened': False,
    'limitations': ['Owner visual acceptance', 'Dedicated server with two real players',
                    'Natural Survival voyage, balance and multiple-ship capacity',
                    'New lighting with Complementary enabled; these native runs used shaders OFF',
                    'Open hatch leaf has no moving collision',
                    'Cargo weight does not affect mass; crane remains decorative per SK02 design'],
    'temporary_jar_cleanup': {'executed': False, 'approval_review': 'blocked by policy'},
}
destination = ROOT / 'docs/verification/SKAT_SK03.json'
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'report': str(destination), 'sha256': report['jar_sha256'],
                  'native_passed': True, 'cold_passed': True}))
