"""Audit the engineer candidate against the published TI02 jar and frozen D02 design."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
VERSION = '0.7.0-preview.39-engineer-d02'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def verify(reference, candidate, baseline=None):
    spec = load(ROOT / 'art/designs/engineer_D02.json')
    recipes = RES / 'data/interstice/recipe'
    seen = []
    for identifier, row in spec['proposed_recipes'].items():
        if identifier == 'armor_upgrades':
            continue
        name = identifier.split(':')[1]
        actual = load(recipes / (name + '.json'))
        assert actual['type'] == 'minecraft:crafting_shapeless'
        assert Counter(x['item'] for x in actual['ingredients']) == Counter(row['ingredients'])
        assert actual['result'] == {'id': identifier, 'count': row['count']}
        assert len(actual['ingredients']) <= 9
        seen.append(name)
    for row in spec['proposed_recipes']['armor_upgrades']:
        name = 'engineer_' + row['slot']
        actual = load(recipes / (name + '.json'))
        assert actual['type'] == 'minecraft:smithing_transform'
        for field in ('base', 'template', 'addition'):
            assert actual[field] == {'item': row[field]}
        assert actual['result'] == {'id': 'interstice:' + name, 'count': 1}
        seen.append(name)
    for resource, reagents, value in [
        ('power_cell', ['interstice:phosphorite_paste', 'minecraft:redstone', 'minecraft:redstone'], {'interstice_engineer_energy_milli': 1800000}),
        ('breathing_cassette', ['interstice:pure_vitriolite_lining', 'minecraft:charcoal', 'interstice:phosphorite_paste'], {'interstice_engineer_air_ticks': 12000}),
    ]:
        name = 'engineer_' + resource + '_refill'
        actual = load(recipes / (name + '.json'))
        assert actual['type'] == 'interstice:engineer_refill'
        assert Counter(x['item'] for x in actual['ingredients']) == Counter(['interstice:engineer_' + resource] + reagents)
        assert actual['result']['components']['minecraft:custom_data'] == value
        seen.append(name)
    # A new acquisition recipe can depend only on old resources or the new assembly/adapter.
    # Service recipes intentionally consume and return the same physical module.
    graph = {}
    for name in seen:
        if name.endswith('_refill'):
            continue
        actual = load(recipes / (name + '.json'))
        ingredients = actual.get('ingredients', []) + [actual[k] for k in ('template', 'base', 'addition') if k in actual]
        values = [x['item'] for x in ingredients]
        assert not set(values) & {'minecraft:echo_shard', 'minecraft:netherite_ingot', 'minecraft:heart_of_the_sea', 'minecraft:nether_star', 'interstice:aegis_control_unit', 'interstice:abyssal_alloy', 'interstice:resonant_processor'}
        graph[name] = [x.split(':')[1] for x in values if x.startswith('interstice:engineer_')]
    def visit(name, chain):
        assert name not in chain, f'Acquisition cycle: {chain + [name]}'
        for dependency in graph.get(name, []):
            visit(dependency, chain + [name])
    for name in graph:
        visit(name, [])
    resources_before = {str(p.relative_to(RES)): digest(p.read_bytes()) for p in RES.rglob('*') if p.is_file()}
    subprocess.run([sys.executable, '-X', 'utf8', str(ROOT / 'tools/build_engineer_d02.py')], check=True, capture_output=True)
    resources_after = {str(p.relative_to(RES)): digest(p.read_bytes()) for p in RES.rglob('*') if p.is_file()}
    assert resources_before == resources_after, 'Generator was not idempotent'
    allowed_classes = {'ToxicLiquidBlock', 'gear/RealmArmor', 'gear/RealmGear', 'gear/RealmWorktools', 'gear/RouteMarkers', 'mixin/AegisNightVisionMixin', 'mixin/AegisOutlineMixin', 'mixin/AegisOutlineColorMixin'}
    def permitted_class(name):
        short = name.removeprefix('pro/erez/interstice/').removesuffix('.class').split('$')[0]
        return short in allowed_classes or short.startswith(('gear/Engineer', 'client/Engineer', 'mixin/Engineer'))
    with zipfile.ZipFile(reference) as old, zipfile.ZipFile(candidate) as new:
        old_names, new_names = set(old.namelist()), set(new.namelist())
        assert old_names <= new_names, 'Published archive entries were removed'
        changed = sorted(n for n in old_names if not n.endswith('/') and old.read(n) != new.read(n))
        changed_classes = [n for n in changed if n.endswith('.class')]
        assert all(permitted_class(n) for n in changed_classes), changed_classes
        added_classes = sorted(n for n in new_names - old_names if n.endswith('.class'))
        assert all(permitted_class(n) for n in added_classes), added_classes
        protected = sorted(n for n in old_names if n.endswith(('.png', '.ogg')) or '/models/' in n or '/armor/' in n or '/equipment/' in n)
        assert all(old.read(n) == new.read(n) for n in protected), 'Accepted models, images or audio changed'
        for locale in ('ru_ru', 'en_us'):
            name = f'assets/interstice/lang/{locale}.json'
            a, b = json.loads(old.read(name)), json.loads(new.read(name))
            assert all(b.get(k) == v or 'engineer' in k for k, v in a.items()), 'Unrelated translations changed'
        asset_counts = {ext: sum(n.endswith(ext) for n in protected) for ext in ('.png', '.ogg')}
        unchanged_classes = sum(n.endswith('.class') and n not in changed for n in old_names)
    source_changes = []
    if baseline:
        allowed = {
            'build.gradle', 'src/main/resources/interstice.mixins.json',
            *('src/main/java/pro/erez/interstice/' + n + '.java' for n in allowed_classes),
            'src/main/java/pro/erez/interstice/gear/EngineerArmor.java',
            *('src/main/resources/assets/interstice/lang/' + n + '.json' for n in ('ru_ru', 'en_us')),
            *('src/main/resources/data/interstice/tags/item/enchantable/' + n + '.json' for n in ('rift_chest', 'rift_boots')),
            'src/main/resources/data/minecraft/tags/item/enchantable/leg_armor.json',
            *('src/main/resources/data/minecraft/tags/enchantment/' + n + '.json' for n in ('in_enchanting_table', 'non_treasure', 'on_random_loot', 'on_traded_equipment', 'tradeable')),
        }
        for name, original in load(baseline).items():
            path = ROOT / name
            assert path.is_file(), f'Source deleted: {name}'
            if digest(path.read_bytes()) != original:
                source_changes.append(name)
                assert name in allowed, f'Unexpected existing source edit: {name}'
    return {
        'passed': True, 'candidate_version': VERSION,
        'candidate_jar': str(candidate), 'candidate_sha256': digest(candidate.read_bytes()),
        'reference_jar': str(reference), 'reference_sha256': digest(reference.read_bytes()),
        'recipes_checked': len(seen), 'no_new_acquisition_cycle': True,
        'generator_idempotent': True, 'changed_existing_classes': changed_classes,
        'new_classes': added_classes, 'unchanged_previous_classes': unchanged_classes,
        'preserved_assets': asset_counts, 'preserved_models_equipment_and_audio': True,
        'changed_existing_source_files': source_changes,
    }

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--reference', type=Path, default=ROOT / 'build/libs/interstice-0.7.0-preview.38-clock-grip.jar')
    parser.add_argument('--candidate', type=Path, default=ROOT / ('build/libs/interstice-' + VERSION + '.jar'))
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.reference, args.candidate, args.baseline)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('passed', 'candidate_sha256', 'recipes_checked', 'unchanged_previous_classes', 'preserved_assets')}))
