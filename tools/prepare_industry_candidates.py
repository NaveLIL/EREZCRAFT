"""Resolve owner-selected additions into an isolated, hash-checked candidate stage.

This does not install into a player's profile or publish a release. Existing production
artifacts remain pinned; optional dependencies never silently expand the pack.
"""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import subprocess
import tomllib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.verification/mod-candidates-20261024'
SELECTED = ['create-connected', 'slice-and-dice', 'create-enchantment-industry',
            'packagedauto', 'packageddraconic', 'pneumaticcraft-repressurized',
            'extended-industrialization', 'super-factory-manager']


def download(file):
    target = WORK / file['filename']
    assert target.parent == WORK and file['url'].startswith('https://cdn.modrinth.com/')
    if not target.exists() or hashlib.sha512(target.read_bytes()).hexdigest() != file['hashes']['sha512']:
        temporary = target.with_suffix(target.suffix + '.part')
        subprocess.run(['curl.exe', '--fail', '--location', '--retry', '4', '--retry-all-errors',
                        '--connect-timeout', '20', '--max-time', '150', '--silent', '--show-error',
                        '--output', str(temporary), file['url']], check=True)
        assert hashlib.sha512(temporary.read_bytes()).hexdigest() == file['hashes']['sha512'], target.name
        temporary.replace(target)
    print('VERIFIED', target.name, flush=True)
    return target


def inspect(slug, version):
    file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
    target = download(file)
    with zipfile.ZipFile(target) as archive:
        names = archive.namelist()
        meta = next(n for n in names if n in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'))
        metadata = tomllib.loads(archive.read(meta).decode('utf-8'))
        languages = {}
        for name in names:
            if name.startswith('assets/') and name.endswith('/lang/en_us.json'):
                english = json.loads(archive.read(name))
                russian_name = name.replace('/en_us.json', '/ru_ru.json')
                russian = json.loads(archive.read(russian_name)) if russian_name in names else {}
                languages[name.split('/')[1]] = {'english': len(english), 'russian': len(russian),
                                                'missing': len(set(english) - set(russian))}
    return slug, {'version': version, 'filename': target.name,
                  'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                  'metadata': metadata, 'languages': languages}


def main():
    versions = json.loads((WORK / 'versions.json').read_text('utf-8'))
    dependencies = json.loads((WORK / 'dependencies.json').read_text('utf-8'))
    chosen = {slug: versions[slug][0] for slug in SELECTED}
    for slug in ('create-dragons-plus', 'tesseract-api'):
        chosen[slug] = dependencies[slug]['versions'][0]
    # 1.12.19 requires MI 2.5.11; retain the installed, accepted MI 2.5.10.
    chosen['tesseract-api'] = next(v for v in dependencies['tesseract-api']['versions']
                                   if v['version_number'] == '1.12.18-1.21.1')
    chosen['patchouli'] = json.loads((WORK / 'patchouli-versions.json').read_text('utf-8'))[0]
    chosen['create-steam-n-rails-1.21.1'] = json.loads((WORK / 'railways-version.json').read_text('utf-8'))
    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        rows = dict(pool.map(lambda pair: inspect(*pair), chosen.items()))
    output = {'schema': 1, 'minecraft': '1.21.1', 'neoforge': '21.1.252',
              'status': 'candidate: runtime acceptance and localization pending', 'mods': rows}
    (WORK / 'candidate-lock.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    for slug, row in rows.items():
        print(slug, row['languages'], flush=True)


if __name__ == '__main__':
    main()
