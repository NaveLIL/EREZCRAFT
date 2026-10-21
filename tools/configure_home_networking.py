"""Apply the pinned voice JAR and actual Sable UDP route on the owned home VM."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import urllib.request

ROOT = Path('/opt/minecraft')
BASE = ROOT / 'erezcraft'


def main():
    lock = json.loads((BASE / 'voice-network.lock.json').read_text())
    compose_path = BASE / 'compose.json'
    compose = json.loads(compose_path.read_text())
    status = subprocess.check_output(['docker', 'exec', 'erezcraft-minecraft-1', 'rcon-cli', 'list'], text=True)
    assert 'There are 0 of a max of 10 players online' in status, 'Never interrupt a player for this setup'
    subprocess.run(['docker', 'compose', '-f', str(compose_path), 'stop', 'backups', 'minecraft'], check=True)
    with urllib.request.urlopen(lock['download_url'], timeout=60) as response:
        payload = response.read()
    assert len(payload) == lock['bytes'] and hashlib.sha256(payload).hexdigest() == lock['sha256']
    (BASE / 'data/mods' / lock['filename']).write_bytes(payload)
    marker = BASE / 'data/config/erezcraft-pack.json'
    pack = json.loads(marker.read_text())
    pack.update(packVersion='0.1.0-alpha.15', deliveryRelease=15)
    marker.write_text(json.dumps(pack, indent=2) + '\n')
    voice = BASE / 'data/config/voicechat/voicechat-server.properties'
    voice.parent.mkdir(exist_ok=True)
    voice.write_text('port=24454\nbind_address=\nvoice_host=mc.erezcraft.com:24454\nforce_voice_chat=false\nallow_pings=true\nmax_voice_distance=48.0\nwhisper_distance=24.0\ncodec=VOIP\nmtu_size=1275\n')
    for port in [25565, 24454]:
        rows = compose['services']['minecraft']['ports']
        if not any(p['target'] == port and p.get('protocol') == 'udp' for p in rows):
            rows.append({'target': port, 'published': str(port), 'protocol': 'udp', 'host_ip': '192.168.10.152'})
    client_path = ROOT / 'config/frpc-erezcraft.toml'
    source = (ROOT / 'config/frpc.toml').read_text()
    assert 'name = "sable"' not in source
    assert 'udpPacketSize' not in source
    client_path.write_text('udpPacketSize = 65535\n' + source + '\n[[proxies]]\nname = "sable"\ntype = "udp"\nlocalIP = "minecraft"\nlocalPort = 25565\nremotePort = 25565\n')
    os.chmod(client_path, 0o600)
    for volume in compose['services']['tunnel']['volumes']:
        if volume['target'] == '/etc/frp/frpc.toml':
            volume['source'] = str(client_path)
    compose_path.write_text(json.dumps(compose, indent=2) + '\n')
    subprocess.run(['docker', 'compose', '-f', str(compose_path), 'config', '--quiet'], check=True)
    subprocess.run(['docker', 'compose', '-f', str(compose_path), 'up', '-d', 'minecraft', 'tunnel', 'backups'], check=True)
    print('HOME_EREZCRAFT_VOICE_AND_SABLE_STARTING')


if __name__ == '__main__':
    main()
