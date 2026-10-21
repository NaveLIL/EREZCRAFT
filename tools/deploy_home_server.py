"""Run on the owned home VM after transferring a fresh, hash-pinned stage.

Uses the existing pinned Java21 image and FRP transport, but an independent data
directory and online-mode EREZ ID. Does not archive or delete the old pack/world.
The public tunnel is started separately, after acceptance and pregeneration.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path('/opt/minecraft')
TARGET = ROOT / 'erezcraft'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('incoming', type=Path)
    args = parser.parse_args()
    incoming = args.incoming.resolve(strict=True)
    assert incoming.is_relative_to(ROOT / 'incoming')
    inventory = json.loads((incoming / 'inventory.json').read_text())['server']
    assert len([r for r in inventory if r['path'].startswith('mods/')]) == 50
    old = json.loads(subprocess.check_output(['docker', 'compose', '-f', str(ROOT / 'compose.yaml'), 'config', '--format', 'json']))
    assert old['name'] == 'homestead'
    state = json.loads(subprocess.check_output(['docker', 'inspect', 'homestead-minecraft-1']))[0]
    assert not state['State']['Running'], 'Stop the old server normally before switching'
    assert not TARGET.exists(), 'A deployment already exists; inspect it instead of replacing data'
    data = TARGET / 'data'
    data.mkdir(parents=True, mode=0o750)

    def install(row):
        rel = Path(row['path'])
        assert not rel.is_absolute() and '..' not in rel.parts
        assert rel.parts[0] in ['mods', 'config', 'erezcraft']
        path = data / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        local = incoming / 'server' / rel
        if row.get('url'):
            assert row['url'].startswith('https://cdn.modrinth.com/data/')
            with urllib.request.urlopen(row['url'], timeout=90) as response:
                payload = response.read(row['size'] + 1)
        else:
            payload = local.read_bytes()
        assert len(payload) == row['size'] and hashlib.sha256(payload).hexdigest() == row['sha256'], row['path']
        path.write_bytes(payload)
        return row['path']

    with ThreadPoolExecutor(max_workers=4) as pool:
        for name in pool.map(install, inventory):
            print('VERIFIED', name, flush=True)
    (TARGET / 'inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')
    minecraft = old['services']['minecraft']
    assert minecraft['image'] == 'itzg/minecraft-server@sha256:3da0b5e26a3adccc4ed8708f90058e84ebc09fc6f8ced0abc02a1156be656bf7'
    env = minecraft['environment']
    env.pop('FABRIC_LOADER_VERSION', None)
    env.pop('FABRIC_INSTALLER_VERSION', None)
    env.update(TYPE='NEOFORGE', VERSION='1.21.1', NEOFORGE_VERSION='21.1.252', INIT_MEMORY='2G', MAX_MEMORY='12G',
               ONLINE_MODE='TRUE', ENABLE_WHITELIST='TRUE', ENFORCE_WHITELIST='TRUE', ENFORCE_SECURE_PROFILE='TRUE',
               LEVEL='erezcraft', SEED='20261006', GENERATE_STRUCTURES='true', ALLOW_FLIGHT='TRUE',
               MOTD='EREZCRAFT | Techmagic + Interstice V6', ENABLE_COMMAND_BLOCK='FALSE',
               ENABLE_QUERY='FALSE', DIFFICULTY='normal', GAMEMODE='survival',
               JVM_OPTS=' '.join(['-XX:+UseZGC', '-XX:+ZGenerational', '-XX:+ExitOnOutOfMemoryError',
                    '-XX:-CreateCoredumpOnCrash', '-XX:-HeapDumpOnOutOfMemoryError', '-XX:-DumpReplayDataOnError', '-XX:ErrorFile=/dev/null',
                    '-javaagent:/data/erezcraft/authlib-injector-1.2.8.jar=https://auth.erezcraft.com/',
                    '-Dauthlibinjector.disableHttpd', '-Dauthlibinjector.noShowServerName',
                    '-Dauthlibinjector.mojangNamespace=disabled', '-Dauthlibinjector.legacySkinPolyfill=disabled',
                    '-Dauthlibinjector.usernameCheck=enabled', '-Dauthlibinjector.profileKey=enabled',
                    '-Dauthlibinjector.mojangAntiFeatures=enabled',
                    '-Dminecraft.api.env=custom', '-Dminecraft.api.auth.host=https://auth.erezcraft.com/auth',
                    '-Dminecraft.api.account.host=https://auth.erezcraft.com/account', '-Dminecraft.api.profiles.host=https://auth.erezcraft.com/account',
                    '-Dminecraft.api.session.host=https://auth.erezcraft.com/session', '-Dminecraft.api.services.host=https://auth.erezcraft.com/services',
                    '-Dminecraft.api.discovery.host=https://auth.erezcraft.com/discovery/minecraft/client']))
    # Preserve the owner's actual 8/6 view/simulation settings from the existing host.
    assert env['VIEW_DISTANCE'] == '8' and env['SIMULATION_DISTANCE'] == '6'
    minecraft.update(mem_limit='16g', cpus=4, user='1000:1000', cap_drop=['ALL'], security_opt=['no-new-privileges:true'])
    minecraft['ports'] = [p for p in minecraft['ports'] if p.get('protocol') == 'tcp']
    minecraft['volumes'] = [{'type': 'bind', 'source': str(data), 'target': '/data'}]
    services = {'minecraft': minecraft, 'tunnel': old['services']['tunnel']}
    for name in ['backups', 'maintenance']:
        service = old['services'][name]
        for volume in service.get('volumes', []):
            if volume['source'].startswith(str(ROOT / 'data')):
                volume['source'] = str(data) + volume['source'][len(str(ROOT / 'data')):]
        if name == 'backups':
            service['volumes'] = [v for v in service['volumes'] if v['target'] in ['/data', '/backups']]
            service['volumes'].append({'type': 'bind', 'source': str(TARGET / 'backups'), 'target': '/backups'})
            service['volumes'] = [v for i, v in enumerate(service['volumes']) if v['target'] != '/backups' or v['source'] == str(TARGET / 'backups')]
            service['environment'].pop('PRE_BACKUP_SCRIPT_FILE', None)
            service['environment']['BACKUP_NAME'] = 'erezcraft-1.21.1'
            service.pop('build', None)
            (TARGET / 'backups').mkdir(mode=0o750)
        services[name] = service
    for path in [data / 'logs', data / 'crash-reports']:
        path.mkdir(exist_ok=True)
    compose = {'name': 'erezcraft', 'services': services, 'secrets': old['secrets']}
    compose_path = TARGET / 'compose.json'
    compose_path.write_text(json.dumps(compose, indent=2) + '\n')
    os.chmod(compose_path, 0o600)
    subprocess.run(['docker', 'compose', '-f', str(compose_path), 'config', '--quiet'], check=True)
    subprocess.run(['docker', 'compose', '-f', str(compose_path), 'up', '-d', 'minecraft'], check=True)
    print('EREZCRAFT_STARTING_WITH_WHITELIST_AND_NO_PUBLIC_TUNNEL')


if __name__ == '__main__':
    main()
