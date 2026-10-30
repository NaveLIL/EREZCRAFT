"""Capture authored model and completed isolated AE2/Sable evidence."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
MODEL = Path('D:/repos/Blender/creations/32_nordhavn_expedition')
PROFILE = ROOT / '.verification/ne01me03/studioMarineAe2'
DEST = ROOT / 'docs/verification/ne01'
DEST.mkdir(parents=True, exist_ok=True)

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

data = read(MODEL / 'source/ne01.json')
check = read(MODEL / 'reports/MODEL_INSPECTION.json')
export = read(MODEL / 'reports/RENDER_EXPORT.json')
assert check['geometry_sha256'] == sha(MODEL / 'source/ne01.json')
assert check['assembly_sha256'] == sha(MODEL / 'assembly/ne01.blend')
assert check['passed_geometry'] and check['passed_sampled_clearance']
assert export['source_meshes'] == len(data['parts']) and not export['boundary_snap_violations']
assert export['geometry_sha256'] == check['geometry_sha256']
assert export['assembly_sha256'] == check['assembly_sha256']

proofs = {}
files = [ROOT / 'src/smoke/java/pro/erez/interstice/client/MarineAe2Native.java',
         ROOT / 'tools/VerifyMarineAe2.ps1']
for mode in ['create', 'reload']:
    source = PROFILE / f'marine-{mode}.json'
    proof = read(source)
    assert proof['passed'] and proof['process_exit_code'] == 0 and proof['dh_loaded']
    assert proof['diamond_count'] == 336 and proof['network_powered']
    assert proof['two_terminals_one_grid'] and proof['controlled_pose_distance'] > 30
    assert not proof['owner_profile_used']
    proofs[mode] = proof
    shutil.copy2(source, DEST / source.name)
    files.append(source)
    for index in [1, 2]:
        screenshot = PROFILE / 'screenshots' / f'marine-{mode}-terminal-{index}.png'
        shutil.copy2(screenshot, DEST / screenshot.name)
        files.append(screenshot)
assert proofs['create']['ship_uuid'] == proofs['reload']['ship_uuid']
settlement = proofs['reload']['final_chunk_settlement']
assert settlement['stable_actual_server_ticks'] >= 20
assert settlement['generation_ref_count'] == settlement['save_futures_pending'] == 0
assert settlement['holders_not_ready_for_saving'] == settlement['save_futures_failed'] == 0

attempts = [
    {'profile': 'ne01me01', 'result': 'Native memory exhaustion during startup; no AE2 result.'},
    {'profile': 'ne01me02', 'result': 'DH loaded, fixture preflight failed on terrain; no AE2 result.'},
    {'profile': 'ne01me03 create', 'result': 'Passed, clean exit 0. Long chunk-unload wait was investigated with two thread dumps.'},
    {'profile': 'ne01me03 initial reload', 'result': 'Fixture looked for the body before asynchronous loading completed; failed, clean exit 0.'},
    {'profile': 'ne01me03 corrected reload', 'result': 'Same saved world, no recreation. Loaded original UUID, 336 items, both native menus, another 40m controlled displacement; clean exit 0.'},
]
result = dict(
    status='model_inspected_and_native_ae2_fixture_passed_full_vessel_integration_pending',
    revision=data['revision'], model_project=str(MODEL),
    source_parts=len(data['parts']), berths_model_only=len(data['beds']),
    terminal_positions_only=len(data['terminals']), original_textures=len(data['materials']),
    source_sha256=sha(MODEL / 'source/ne01.json'),
    assembly_sha256=sha(MODEL / 'assembly/ne01.blend'),
    mesh_inspection=check, render_export=export,
    me_probe=dict(passed=True, installed_versions=dict(ae2='19.2.18', sable='2.0.6'),
                  proofs=proofs, attempts=attempts,
                  scope='Small real Sable raft, creative energy cell, one 64k item cell, two actual native terminals. Controlled body teleport, not propulsion. Part API menu activation, not player raytrace.'),
    artifact_hashes={str(p.relative_to(ROOT)): sha(p) for p in files},
    owner_decision='Майн я закрыл', owner_profiles_modified=False, production_changed=False,
    ne01_native_accepted=False,
    limitations=[
        'No Minecraft registration/collision or walking acceptance for the complete NE01 yet.',
        'Small raft test is not six-terminal full-vessel acceptance.',
        'Creative power source is a compatibility fixture, not the final onboard generator.',
        'No power-loss, propulsion, sleeping/respawn or two-client acceptance yet.',
        'Static sampled geometry checks are not a continuous swept collision proof.',
    ])
(ROOT / 'docs/verification/NE01_MODEL.json').write_text(
    json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')

text = f'''# NE01 — модель и проверка бортовой МЕ-сети

29.10.2026, {data['revision']}. Основа — Nordhavn 96. Корпус и интерьер созданы
в Blender; самостоятельная проверка AE2/Sable прошла в настоящем клиенте с DH.
Полное судно ещё не зарегистрировано в Minecraft.

Проект: `{MODEL.as_posix()}`. Полная компоновка —
[MARINE_EXPEDITION_VESSEL.md](MARINE_EXPEDITION_VESSEL.md).

## Модель

Корпус 29,44 × 7,32 м, четыре связанных уровня, 10 смоделированных спальных мест,
6 мест под настоящие терминалы, {len(data['parts'])} деталей и 19 собственных текстур.
Внутри есть каюты, салон, камбуз, техническое помещение, мостик и лестницы.
Посадка предусмотрена с кормовой платформы и бортовых трапов. Механизмы дверей
и трапов пока демонстрируются анимацией Blender.

Непрерывная оболочка корпуса, цельные рамы и нахлёст стекла проверены
{check['window_rays']} лучами по окнам; просветов в этих пробах нет.
На {len(check['clearance_samples'])} статических пробах проверены проходы тела шириной 0,6 м
и высотой 2,2 м с оговорённым допуском подъёма по ступеням. Это проверка геометрии,
игровая ходьба требует отдельного подтверждения.

У {check['mesh_objects']} мешей нет открытых рёбер или граней нулевой площади.
В экспорте {export['cells']} ячеек, стекло отделено от непрозрачных поверхностей.
Привязка граничных вершин к 0/1 сохраняется после float32; максимальная ошибка
восстановления координат {export['max_float32_reconstruction_error_m']:.9f} м.

## Настоящая AE2 на Sable

В изолированном мире собран небольшой корпус Sable с накопителем AE2 64k,
контроллером, Creative-источником питания и двумя настоящими терминалами.
Оба терминала принадлежат одной работающей сети. Вставлено 321 + 79 предметов,
извлечено 64; остаток 336 проверен до выхода и после холодного запуска.

Первый процесс завершился с кодом 0. После повторного запуска загрузился прежний
UUID корпуса, сохранились все 336 предметов и открылись штатные MEStorageScreen.
На каждом запуске выполнено контролируемое перемещение корпуса примерно на 40 м;
после него второй терминал продолжал видеть ту же сеть. Второй процесс также
завершился с кодом 0. Перед выходом его очередь генерации и сохранения была пуста.

Начальные неудачные попытки сохранены в [полном отчёте](verification/NE01_MODEL.json).
В частности, первая проверка reload обращалась к корпусу слишком рано; исправленная
проверка дождалась асинхронной загрузки и использовала тот же мир без пересоздания.

Прямой вызов API части AE2 проверяет штатное меню. Попадание луча игрока по
терминалу на движущемся судне, шесть терминалов готового корабля, обесточивание,
два одновременных игрока и конечный бортовой генератор ещё не проверены.
Перенос корпуса через physics API не является проверкой морской тяги.

## Артефакты

- `assembly/ne01.blend` — модель с упакованными текстурами.
- `exports/ne01.glb` — модель и демонстрация открывания.
- `exports/NE01_A02_model_review.zip` — исходники, модель, рендеры и проверки.
- `preview/ne01_afloat.png`, `preview/ne01_main_interior.png`, `preview/ne01_bridge.png`.
- [Терминал после холодного запуска и перемещения](verification/ne01/marine-reload-terminal-2.png).
- [Создание](verification/ne01/marine-create.json), [холодный запуск](verification/ne01/marine-reload.json).

Следующий этап — игровые ячейки и коллизии, механизмы, настоящие места шести
терминалов, морская тяга и проверка полного корпуса. Пользовательские миры,
профили, живой сервер и публичная доставка этой работой не изменены.
'''
(ROOT / 'docs/NE01_MODEL.md').write_text(text, encoding='utf8')
print(json.dumps(dict(revision=data['revision'], native_ae2_fixture_passed=True,
                      full_vessel_accepted=False)))
