"""Data for the explicit island surface pipeline. Does not enable vanilla decoration."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/interstice'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def source():
    points = []
    for name, interval in [('ash_islands', [-2, .08]), ('stone_vaults', [.08, 2])]:
        parameters = {name: [-2, 2] for name in ('temperature', 'humidity', 'continentalness', 'erosion', 'depth', 'weirdness')}
        parameters.update(continentalness=interval, offset=0)
        points.append({'biome': 'interstice:' + name, 'parameters': parameters})
    return {'type': 'minecraft:multi_noise', 'biomes': points}

for name in ('ash_islands', 'stone_vaults'):
    biome = {'has_precipitation': False, 'temperature': .5, 'downfall': 0,
             'effects': {'fog_color': 0, 'sky_color': 0, 'water_color': 4159204, 'water_fog_color': 329011,
                         'grass_color': 7102055, 'foliage_color': 6179159,
                         'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000,
                                        'block_search_extent': 8, 'offset': 2.0}},
             'carvers': {}, 'features': [[] for _ in range(11)], 'spawn_costs': {},
             'spawners': {category: [] for category in ('ambient', 'axolotls', 'creature', 'misc', 'monster',
                                                       'underground_water_creature', 'water_ambient', 'water_creature')}}
    write(DATA / 'worldgen/biome' / (name + '.json'), biome)

write(DATA / 'worldgen/noise/realm_geology.json', {'firstOctave': -5, 'amplitudes': [1, .35, .15]})
for name in ('islands', 'islands_tall'):
    path = DATA / 'dimension' / (name + '.json')
    dimension = json.loads(path.read_text(encoding='utf-8'))
    dimension['generator']['biome_source'] = source()
    write(path, dimension)
    path = DATA / 'worldgen/noise_settings' / (name + '.json')
    settings = json.loads(path.read_text(encoding='utf-8'))
    # Seeded, Y-invariant geology. Independent from final_density and the sea surface.
    settings['noise_router']['continents'] = {'type': 'minecraft:noise', 'noise': 'interstice:realm_geology',
                                            'xz_scale': .45, 'y_scale': 0}
    write(path, settings)

fixture = ROOT / 'src/geometryFixture/resources/data/minecraft/worldgen/world_preset/flat.json'
value = json.loads(fixture.read_text(encoding='utf-8'))
for name in ('islands', 'islands_tall'):
    value['dimensions']['interstice:' + name]['generator']['biome_source'] = source()
write(fixture, value)

for locale, names in {'en_us': ('Ash Islands', 'Stone Vaults'),
                      'ru_ru': ('Пепельные острова', 'Каменные своды')}.items():
    path = ROOT / 'src/main/resources/assets/interstice/lang' / (locale + '.json')
    value = json.loads(path.read_text(encoding='utf-8'))
    value.update(dict(zip(('biome.interstice.ash_islands', 'biome.interstice.stone_vaults'), names)))
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

write(DATA / 'tags/worldgen/biome/is_interstice.json',
      {'replace': False, 'values': ['interstice:ash_islands', 'interstice:stone_vaults']})
