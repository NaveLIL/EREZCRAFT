"""Stage pinned client/server files for the EREZCRAFT launcher's existing publisher.

This creates a private local staging directory, never publishes a catalog or admits
players. Public redistribution review and actual host acceptance remain separate.
"""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASE = 'techmagic-0.1.0-alpha.2-foundation'


def sha(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def stage():
    spine = json.loads((ROOT / 'distribution/techmagic-spine.lock.json').read_text(encoding='utf-8'))
    foundation = json.loads((ROOT / 'distribution/server-foundation.lock.json').read_text(encoding='utf-8'))
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    own = ROOT / f'build/libs/interstice-{version}.jar'
    assert own.is_file(), 'First assemble and audit the current Interstice artifact'
    with zipfile.ZipFile(own) as jar:
        assert 'data/interstice/dimension/islands_v6.json' in jar.namelist()
        assert 'DhChunkyInitializationMixin' in jar.read('interstice.mixins.json').decode()
        assert not any('/test/' in name or '/smoke/' in name for name in jar.namelist())
    mods = spine['required_spine'] + foundation['mods']
    assert len(mods) == 35
    client = {f'mods/{own.name}': own}
    server = dict(client)
    upstream = []
    for mod in mods:
        filename = mod.get('filename', mod.get('file'))
        local = ROOT / 'research/dependencies' / filename
        data = local.read_bytes()
        assert len(data) == mod['bytes'] and sha(data, 'sha512') == mod['sha512'], filename
        assert mod['download_url'].startswith('https://cdn.modrinth.com/'), filename
        # Our launcher's path contract intentionally permits a narrow ASCII alphabet.
        # A filename change preserves the original JAR bytes and internal mod identity.
        relative = 'mods/' + filename.replace('+', '-')
        assert relative not in client, relative
        client[relative] = local
        client_only = mod.get('role') in ('minimap', 'worldmap')
        if not client_only:
            server[relative] = local
        upstream.append({'path': relative, 'original_filename': filename, 'size': len(data), 'sha256': sha(data),
                         'download_url': mod['download_url'], 'client_only': client_only,
                         'license': mod.get('license'), 'public_redistribution_review_complete': False})
    for files in (client, server):
        files['config/DistantHorizons.toml'] = ROOT / 'distribution/DistantHorizons.toml'
    target = ROOT / 'build/distributions' / ('erezcraft-' + RELEASE + '-launcher')
    target.mkdir(parents=True, exist_ok=True)
    inventory = {}
    for side, files in (('client', client), ('server', server)):
        directory = target / side
        if directory.exists():
            existing = {p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}
            assert existing <= set(files), f'Unexpected file in private {side} stage; refusing to alter it'
        inventory[side] = []
        for relative, source in sorted(files.items()):
            output = directory / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, output)
            assert sha(output.read_bytes()) == sha(source.read_bytes()), relative
            inventory[side].append({'path': relative, 'size': output.stat().st_size, 'sha256': sha(output.read_bytes())})
    # These are publisher inputs, not signed release metadata; preserve playable=false.
    descriptor = {'serverId': 'archipelago', 'releaseId': RELEASE.replace('.', '-'), 'sequence': 1,
                  'expiresAt': 1794009600, 'minecraft': '1.21.1', 'loader': 'neoforge',
                  'loaderVersion': '21.1.252', 'name': 'EREZCRAFT', 'tag': 'Тестовая основа',
                  'description': 'Техномагическая основа с Create, Aeronautics, AE2 и авторским V6. Игровая приёмка продолжается.',
                  'address': 'play.erezcraft.example:25565', 'playable': False, 'managedConfig': []}
    for name, value in (('publisher-descriptor.example.json', descriptor), ('inventory.json', inventory), ('upstream-files.json', upstream)):
        (target / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    shutil.copyfile(ROOT / 'distribution/client-jvm-args.txt', target / 'client-jvm-args.txt')
    (target / 'README.txt').write_text(
        'Private EREZCRAFT launcher staging, not a live release.\n'
        'client/ matches the allowed roots of D:/repos/erezcraft/tools/publish-pack.mjs.\n'
        'server/ excludes client minimap/worldmap. DH remains on both sides.\n'
        'Do not copy this into existing player instances or saves. Use the launcher installation flow.\n'
        'Publisher descriptor is an example: actual host, monotonic sequence and expiry require real deployment values.\n'
        'Client RAM/GC are configured by our launcher, not by synchronizing options.txt.\n'
        'Public redistribution permissions for upstream custom/ARR licenses still require review; upstream-files.json preserves original URLs.\n'
        'Production capacity, final pregeneration, recipes, ships, graves and shaders remain acceptance gates.\n', encoding='utf-8')
    receipt = {'passed': True, 'release': RELEASE, 'own_mod_version': version,
               'own_mod_sha256': sha(own.read_bytes()), 'directory': str(target),
               'client_mod_jars': len(client)-1, 'server_mod_jars': len(server)-1,
               'worlds_included': False, 'published': False, 'players_admitted': False,
               'inventory_sha256': sha((target / 'inventory.json').read_bytes())}
    (ROOT / '.verification/techmagic-foundation-package.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(receipt))


if __name__ == '__main__':
    stage()
