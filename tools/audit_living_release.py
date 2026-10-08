#!/usr/bin/env python3
"""Inspect packaged resources and local model references, without modifying images or worlds."""
import hashlib
import json
from pathlib import Path
import zipfile
import argparse

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/interstice'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version',default='0.3.1')
    args=parser.parse_args()
    if not __import__('re').fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',args.version):parser.error('Use a semantic version')
    errors = []
    models = list((ASSETS / 'models').rglob('*.json'))
    for path in models:
        model = json.loads(path.read_text(encoding='utf-8-sig'))
        parent = model.get('parent', '')
        if parent.startswith('interstice:') and not (ASSETS / 'models' / (parent.split(':', 1)[1] + '.json')).is_file():
            errors.append(f'{path.relative_to(ROOT)}: missing parent {parent}')
        for texture in model.get('textures', {}).values():
            if texture.startswith('interstice:') and not (ASSETS / 'textures' / (texture.split(':', 1)[1] + '.png')).is_file():
                errors.append(f'{path.relative_to(ROOT)}: missing texture {texture}')
    jar_path = ROOT / ('build/libs/interstice-'+args.version+'.jar')
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        files = [p for p in RES.rglob('*') if p.is_file() and p.name != 'neoforge.mods.toml']
        for path in files:
            name = path.relative_to(RES).as_posix()
            if name not in names or jar.read(name) != path.read_bytes():
                errors.append(f'JAR resource differs: {name}')
        for name in names:
            if any(word in name for word in ('VisualSmoke', 'GameTests', 'NativeChunkSettler', 'geometry_fixture')):
                errors.append(f'Test artifact packaged: {name}')
        for required in ('assets/interstice/provenance/clingweed-original.json',
                         'assets/interstice/provenance/minerals.json',
                         'META-INF/licenses/freeterraforged-terrain.txt',
                         'data/interstice/dimension/islands_v4.json'):
            if required not in names:
                errors.append(f'Missing release resource: {required}')
    report = {'passed': not errors, 'jar': str(jar_path), 'jar_sha256': sha(jar_path.read_bytes()),
              'models_checked': len(models), 'resources_compared': len(files), 'errors': errors}
    target = ROOT / ('.verification/living-release-artifact.json' if args.version=='0.3.1' else '.verification/release-artifact-'+args.version+'.json')
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
    return 0 if not errors else 1


if __name__ == '__main__':
    raise SystemExit(main())
