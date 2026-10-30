"""Publish local verification evidence only; never installs or deploys a mod."""
import argparse, hashlib, json, shutil
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--drive-root',type=Path,required=True)
p.add_argument('--network-root',type=Path,required=True)
a=p.parse_args()
for root in [a.drive_root,a.network_root]:
    assert root.resolve().parent==ROOT/'.verification' and root.name.startswith('eitan-drive-')
profile=a.drive_root/'studioEitanDrive'
provenance=ROOT/'src/main/resources/assets/interstice/provenance/eitan_vehicle.json'
meta=json.loads(provenance.read_text(encoding='utf-8-sig'))
checks={}
for mode in ['full','reload','shader']:
    data=json.loads((profile/f'eitan-drive-{mode}.json').read_text(encoding='utf-8-sig'))
    assert data['passed'] and data['process_exit_code']==0 and not data['owner_profile_used'],mode
    assert data['geometry_sha256']==meta['geometry_sha256'] and data['dh_loaded'],mode
    checks[mode]=data
net=json.loads((a.network_root/'result.json').read_text(encoding='utf-8-sig'))
assert net['passed'] and all(net[k]['process_exit_code']==0 for k in ['server','client'])
assert net['client']['real_tcp_dedicated'] and net['client']['sable_udp_channel_active']
assert net['server']['survival_driver'] and net['server']['disconnect_brake_checked']
assert checks['reload']['cold_real_player_passenger_restored']
assert checks['shader']['shader_pack_in_use'] and checks['shader'].get('iris_settings_preserved') is True
assert checks['shader'].get('camera_day_sky_pixels',0)>1000
assert checks['shader'].get('camera_transparent_sky_pixels')==0
assert checks['shader'].get('physical_monitor_day_sky_pixels',0)>150
numerical=json.loads((ROOT/'.verification/eitan-drive/numerical.json').read_text())
assert numerical['passed']
checks.update(network=net,numerical=numerical)
source_paths=list((ROOT/'src/main/java/pro/erez/interstice/vehicle').glob('Eitan*.java'))+list((ROOT/'src/main/java/pro/erez/interstice/client').glob('Eitan*.java'))+list((ROOT/'src/main/java/pro/erez/interstice/mixin').glob('Eitan*.java'))+[ROOT/'src/main/java/pro/erez/interstice/ship/SkatPlayerLogin.java',ROOT/'src/main/resources/interstice.mixins.json']
source_hashes={str(path.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(source_paths)}
assert checks['reload'].get('source_changed_during_run') is False
assert checks['reload'].get('runtime_source_sha256')==source_hashes,'Runtime source changed after final native check'
dest=ROOT/'docs/verification/eitan-drive'
dest.mkdir(parents=True,exist_ok=True)
shots=[('full','ramp','Посадка через аппарель'),('full','cruise-instruments','Спидометр во время движения'),
       ('full','three-live-monitors','Три камеры и отдельный приборный экран'),
       ('full','driver-floor','Пол и внутренние колёсные ниши'),
       ('full','closed-driver-hatch','Закрытый водительский люк'),
       ('shader','three-live-monitors','Камеры с включённым Complementary'),
       ('shader','rear-camera','Рабочий задний вид с Complementary')]
cards=[]
for mode,name,title in shots:
    filename=f'eitan-drive-{mode}-{name}.png'
    shutil.copyfile(profile/'screenshots'/filename,dest/filename)
    cards.append(f'<figure><a href="{filename}"><img loading="lazy" src="{filename}" alt="{title}"></a><figcaption>{title}</figcaption></figure>')
html='''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Eitan E01-R7</title>
<style>body{background:#142029;color:#d0dad8;font:17px system-ui;margin:32px auto;max-width:1500px;padding:0 20px}h1{color:#e3c889}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(480px,1fr));gap:20px}figure{margin:0;background:#1d2c35;border:1px solid #9b8255;padding:10px}img{width:100%;display:block}figcaption{padding:10px 0 3px}a{color:#e3c889}@media(max-width:540px){main{display:block}figure{margin:18px 0}}</style>
<h1>Eitan E01-R7 — настоящий клиент</h1><p>Полная сборка с DH. Без ночного зрения и интерфейса игрока. Локальная тестовая сцена; сервер не обновлён.</p><main>'''+''.join(cards)+'</main></html>'
(dest/'gallery.html').write_text(html,encoding='utf-8')
meta['native_drive_accepted']=True
meta['verification']='docs/verification/EITAN_DRIVE.json'
report=dict(passed=True,utc=datetime.now(timezone.utc).isoformat(),revision=meta['revision'],
            source=meta,runtime_source_sha256=source_hashes,checks=checks,production_changed=False,owner_profile_used=False,
            backups_created=False,limits=['one real dedicated Survival driver','11 passenger dummies are not 11 players',
            'no production capacity claim','weapons, APS, armour simulation and recipes deferred'])
path=ROOT/'docs/verification/EITAN_DRIVE.json'
path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
meta['native_drive_accepted']=True
meta['verification']='docs/verification/EITAN_DRIVE.json'
provenance.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(passed=True,report=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest())))
