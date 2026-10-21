"""Extend the verified home-server pack with the pinned original voice mod only."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    assert not args.output.exists()
    prior = json.loads((args.base / 'inventory.json').read_text())
    for side in ['client', 'server']:
        for row in prior[side]:
            assert digest(args.base / side / row['path']) == row['sha256']
        shutil.copytree(args.base / side, args.output / side)
    lock = json.loads((ROOT / 'distribution/voice-network.lock.json').read_text())
    with urllib.request.urlopen(lock['download_url'], timeout=45) as response:
        payload = response.read()
    assert len(payload) == lock['bytes'] and hashlib.sha512(payload).hexdigest() == lock['sha512']
    for side in ['client', 'server']:
        p = args.output / side / 'mods' / lock['filename']
        p.write_bytes(payload)
        assert digest(p) == lock['sha256']
        pack = args.output / side / 'config/erezcraft-pack.json'
        data = json.loads(pack.read_text())
        data.update(packVersion='0.1.0-alpha.15', deliveryRelease=15)
        pack.write_text(json.dumps(data, indent=2) + '\n')
    with zipfile.ZipFile(args.output / 'server/mods' / lock['filename']) as jar:
        metadata = jar.read('META-INF/neoforge.mods.toml').decode()
        assert 'version = "1.21.1-2.6.22"' in metadata and 'versionRange = "[1.21,1.21.1]"' in metadata
    voice = args.output / 'server/config/voicechat/voicechat-server.properties'
    voice.parent.mkdir()
    voice.write_text('port=24454\nbind_address=\nvoice_host=mc.erezcraft.com:24454\nforce_voice_chat=false\nallow_pings=true\nmax_voice_distance=48.0\nwhisper_distance=24.0\ncodec=VOIP\nmtu_size=1275\n')
    descriptor = json.loads((args.base / 'descriptor.json').read_text(encoding='utf-8'))
    descriptor.update(sequence=15, releaseId='techmagic-0-1-0-alpha-15-network')
    descriptor['externalDownloads']['mods/' + lock['filename']] = lock['download_url']
    (args.output / 'descriptor.json').write_text(json.dumps(descriptor, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    inventory = {}
    for side, expected in [('client', 64), ('server', 51)]:
        assert len(list((args.output / side / 'mods').glob('*.jar'))) == expected
        inventory[side] = [{'path': p.relative_to(args.output / side).as_posix(), 'size': p.stat().st_size, 'sha256': digest(p),
                           'url': descriptor['externalDownloads'].get(p.relative_to(args.output / side).as_posix())}
                          for p in sorted((args.output / side).rglob('*')) if p.is_file()]
    (args.output / 'inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')
    print('VOICE_NETWORKING_STAGE_READY client=64 server=51')


if __name__ == '__main__':
    main()
