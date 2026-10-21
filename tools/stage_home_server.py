"""Stage the exact signed alpha.13 pack plus current Interstice/Lootr for home deployment.

No user saves, credentials or launcher settings are copied. Server membership comes
from the previous real dedicated acceptance, rather than guessing JAR side support.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import zipfile

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--launcher', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    launcher = args.launcher.resolve()
    out = args.output.resolve()
    assert not out.exists(), 'Use a fresh staging directory'
    config = json.loads((launcher / 'config/launcher.downloads.json').read_text())
    key = Ed25519PublicKey.from_public_bytes(base64.b64decode(config['publicKey']))
    envelope = json.loads((launcher / '.local/files-release/public/releases/archipelago/techmagic-0-1-0-alpha-13-entry/manifest.json').read_text())
    payload = base64.b64decode(envelope['payload'])
    key.verify(base64.b64decode(envelope['signature']), payload)
    old = json.loads(payload)
    assert old['sequence'] == 13 and len(old['files']) == 72
    source = launcher / '.local/fishing-entry-final/source'
    for row in old['files']:
        assert digest(source / row['path']) == row['sha256'], row['path']
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    own = ROOT / f'build/libs/interstice-{version}.jar'
    assert version == '0.7.0-preview.12' and own.is_file()
    with zipfile.ZipFile(own) as jar:
        assert 'data/interstice/dimension/islands_v6.json' in jar.namelist()
        assert not any('/smoke/' in name or '/test/' in name for name in jar.namelist())
    shutil.copytree(source, out / 'client')
    for path in (out / 'client/mods').glob('interstice-*.jar'):
        path.unlink()  # Fresh, owned staging only.
    shutil.copyfile(own, out / 'client/mods' / own.name)
    lootr = json.loads((ROOT / 'distribution/structure-loot.lock.json').read_text())
    target = out / 'client/mods' / lootr['filename']
    with urllib.request.urlopen(lootr['download_url'], timeout=45) as response:
        target.write_bytes(response.read())
    assert target.stat().st_size == lootr['bytes'] and digest(target) == lootr['sha256']
    pack = json.loads((out / 'client/config/erezcraft-pack.json').read_text())
    pack.update(packVersion='0.1.0-alpha.14', deliveryRelease=14, interstice=version)
    (out / 'client/config/erezcraft-pack.json').write_text(json.dumps(pack, indent=2) + '\n')
    accepted = json.loads((ROOT / '.verification/client-comfort-dedicated-20261010/acceptance.json').read_text())
    assert accepted['passed'] and accepted['server_jar_count'] == 49
    server_names = {row['filename'] for row in accepted['copied_artifacts'] if not row['filename'].startswith('interstice-')}
    (out / 'server/mods').mkdir(parents=True)
    for name in sorted(server_names):
        path = out / 'client/mods' / name
        assert path.is_file(), name
        old_row = next(row for row in accepted['copied_artifacts'] if row['filename'] == name)
        assert digest(path) == old_row['sha256'], name
        shutil.copyfile(path, out / 'server/mods' / name)
    for name in [own.name, lootr['filename']]:
        shutil.copyfile(out / 'client/mods' / name, out / 'server/mods' / name)
    (out / 'server/config').mkdir()
    for name in ['DistantHorizons.toml', 'erezcraft-pack.json']:
        shutil.copyfile(out / 'client/config' / name, out / 'server/config' / name)
    agent = launcher / 'vendor/authlib-injector/authlib-injector-1.2.8.jar'
    assert digest(agent) == '9c7f4343e6c82034958ffb48c14a2cb0c85928be7283103ce17da00c6d5a7b10'
    (out / 'server/erezcraft').mkdir()
    shutil.copyfile(agent, out / 'server/erezcraft' / agent.name)
    external = json.loads((launcher / '.local/fishing-entry-final/descriptor.json').read_text())['externalDownloads']
    external['mods/' + lootr['filename']] = lootr['download_url']
    inventory = {}
    for side in ['client', 'server']:
        inventory[side] = [{'path': p.relative_to(out / side).as_posix(), 'size': p.stat().st_size, 'sha256': digest(p),
                            'url': external.get(p.relative_to(out / side).as_posix())}
                           for p in sorted((out / side).rglob('*')) if p.is_file()]
    assert len(list((out / 'client/mods').glob('*.jar'))) == 63
    assert len(list((out / 'server/mods').glob('*.jar'))) == 50
    (out / 'inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')
    descriptor = json.loads((launcher / '.local/fishing-entry-final/descriptor.json').read_text(encoding='utf-8'))
    descriptor.update(releaseId='techmagic-0-1-0-alpha-14-server', sequence=14, externalDownloads=external)
    (out / 'descriptor.json').write_text(json.dumps(descriptor, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'stage': str(out), 'client_jars': 63, 'server_jars': 50, 'interstice': version, 'sha256': digest(own)}))


if __name__ == '__main__':
    main()
