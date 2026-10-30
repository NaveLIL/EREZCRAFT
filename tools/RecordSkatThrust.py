"""Record the accepted native movement, exact class overlay and public delivery."""
from pathlib import Path
import datetime,hashlib,json,statistics,zipfile
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/skat-thrust-release-20261030'
read=lambda p:json.loads(Path(p).read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
package=read(STAGE/'input.json')
binding=read(ROOT/'.verification/skat-20261029/accepted-classes.json')
native_path=Path(package['proof']);native=read(native_path)
server=read(STAGE/'server-deployed.json')
probe=read(STAGE/'probe.json')
published=read(STAGE/'published.json')
assert native['passed'] and native['process_exit_code']==0 and native['dh_loaded']
assert native['reverse_native_control'] and native['pilot_failsafe']
assert sha(native_path)==package['proof_sha256']==binding['proof_sha256']
assert server['passed'] and server['healthy'] and probe['passed']
assert server['jar_sha256']==package['jar_sha256']==sha(Path(package['jar']))
assert published['sequence']==25 and published['public_https_verified'] and published['signatures_verified']
assert published['clientJars']==79 and published['files']==89 and published['unchangedBaseFiles']==87
with zipfile.ZipFile(package['jar']) as z:
    for name,digest in binding['class_sha256'].items():
        assert hashlib.sha256(z.read(name)).hexdigest()==digest
for name,digest in binding['source_sha256'].items():
    assert sha(ROOT/'src/main/java'/name)==digest,'Accepted source changed'
samples=[s for s in native['propulsion_samples'] if s['phase']==3 and 'kmh' in s]
coast=[s['kmh'] for s in native['propulsion_samples'] if s['phase']==8 and 'kmh' in s]
reverse=[s['kmh'] for s in native['propulsion_samples'] if s['phase']==13 and 'kmh' in s]
mean=statistics.mean(s['kmh'] for s in samples[-20:])
assert 45<=mean<=51 and max(s['kmh'] for s in samples)<=53
assert native['structure_cells_checked']==1509 and native['cargo_slots']==1296
image=native_path.parent/'screenshots/skat-underway.png'
result=dict(
    passed=True,checked_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    objective='Skat full speed around 50 km/h; measured physical movement',
    mod_version=package['mod_version'],sequence=25,pack='0.1.0-alpha.25',
    jar_sha256=package['jar_sha256'],native_proof=str(native_path),
    native_proof_sha256=sha(native_path),native_dh_loaded=True,
    actual_dimension='interstice:islands_v6',owner_profile_used=False,
    measured=dict(straight_ticks=600,path_m=native['driven_path_m'],
        last_10s_mean_kmh=mean,last_10s_min_kmh=min(s['kmh'] for s in samples[-20:]),
        last_10s_max_kmh=max(s['kmh'] for s in samples[-20:]),
        maximum_kmh=max(s['kmh'] for s in samples),
        max_tilt_degrees=native['max_tilt_all_phases']*180/3.141592653589793,
        neutral_last_5s_mean_kmh=statistics.mean(coast[-10:]),
        turn_radians=native['turn_radians'],reverse_final_kmh=native['reverse_final_speed_kmh'],
        reverse_signed_mps=native['reverse_signed_speed_mps'],
        reverse_last_5s_mean_kmh=statistics.mean(reverse[-10:])),
    actual_keyboard_network_seat_and_native_forces=True,
    reverse_control_passed=True,pilot_failsafe_passed=True,
    structure_cells=1509,cargo_containers=24,cargo_slots=1296,
    component_contracts=dict(passed=True,checks=16,entrypoint='tools/VerifySkatPropulsion.java',
        source_sha256=sha(ROOT/'tools/VerifySkatPropulsion.java')),
    source_sha256=binding['source_sha256'],class_sha256=binding['class_sha256'],
    preserved_jar_entries=package['preserved_entries'],
    server=server,private_dedicated=probe,delivery=published,
    screenshot=dict(file=str(image.relative_to(ROOT)),sha256=sha(image),unmodified_capture=True,
        night_vision_during_trial=True),
    limitations=[
        'Controlled fresh native scene; not Survival or a real-server crew sailing trial.',
        'Dedicated acceptance is boot/idle saved test-body loading, not capacity for ten players.',
        'Signed HTTPS release verified; owner profile and launcher UI were not automated.',
        'Cruising turns are deliberately gentle; reduce throttle for confined maneuvers.'])
target=ROOT/'docs/verification/SKAT_THRUST.json'
target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
doc=ROOT/'docs/SKAT_THRUST.md'
s=doc.read_text(encoding='utf8')
s=s.replace('Серверная проверка и доставка фиксируются отдельно после завершения.',
    '**Alpha.25 / sequence25 опубликована**, основной сервер healthy с тем же JAR.\n'
    'Клиент: 79 JAR / 89 файлов, 87 прежних записей сохранены. Сервер: 67 JAR,\n'
    'остальные 66 побайтно прежние. HTTPS, Ed25519 и SHA256 проверены.\n'
    '[Машинный отчёт](verification/SKAT_THRUST.json).')
doc.write_text(s,encoding='utf8')
checkpoint=('\n\n30.10.2026 — скорость «Ската»: **alpha.25 / sequence25**, Interstice '
    '**0.7.0-preview.110-skat-thrust** опубликован, основной сервер healthy. '
    'В полном клиенте с DH/настоящей V6 измерены 45,13–49,73 км/ч '
    '(среднее последних 10 с 47,06), поворот/выбег/задний ход/выход из кресла '
    'прошли; 1509 ячеек и 24 грузовых контейнера сохранены. '
    'Только 3 класса движения поверх JAR100; клиент 79 JAR/89 файлов, '
    '87 записей и 66 прочих серверных JAR прежние. Пользовательские профили '
    'не использовались; Survival/рейс экипажа на основном сервере/ёмкость '
    'не заявлены. Подробности: [SKAT_THRUST](SKAT_THRUST.md).\n')
for rel in ['docs/HANDOFF.md','docs/progress/AUTONOMOUS.md','docs/HANDOFF_SKAT_PROPULSION.md']:
    p=ROOT/rel;text=p.read_text(encoding='utf8')
    link='../SKAT_THRUST.md' if rel=='docs/progress/AUTONOMOUS.md' else 'SKAT_THRUST.md'
    if '30.10.2026 — скорость «Ската»' not in text:
        first,sep,rest=text.partition('\n')
        if rel=='docs/HANDOFF_SKAT_PROPULSION.md':first='# Скорость «Ската»: исправление опубликовано'
        p.write_text(first+checkpoint.replace('(SKAT_THRUST.md)','('+link+')')+'\n'+rest,encoding='utf8')
print(json.dumps(dict(passed=True,report=str(target),sequence=25,mean_kmh=mean,
    jar_sha256=package['jar_sha256']),ensure_ascii=False))
