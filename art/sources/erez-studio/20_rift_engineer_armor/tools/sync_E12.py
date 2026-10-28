from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
dest=ROOT/'exports/intermediate/E11';dest.mkdir(parents=True,exist_ok=True)
for p in (CAND/'build/libs').glob('*24-engineer*.jar'):shutil.copy2(p,dest/p.name)
p=CAND/'gradle.properties';s=p.read_text(encoding='utf-8');assert 'mod_version=0.7.0-preview.24-engineer' in s;s=s.replace('mod_version=0.7.0-preview.24-engineer','mod_version=0.7.0-preview.25-engineer-belt');p.write_text(s,encoding='utf-8')
for filename in ('build_game_resources.py','prepare_native.py','native_tail.java.txt','run_native.py','verify_review.py'):
    p=ROOT/'tools'/filename;s=p.read_text(encoding='utf-8').replace('E11','E12').replace('0.7.0-preview.24-engineer','0.7.0-preview.25-engineer-belt');p.write_text(s,encoding='utf-8')
s=(ROOT/'tools/finish_E11.py').read_text(encoding='utf-8').replace('E11','E12').replace('0.7.0-preview.24-engineer','0.7.0-preview.25-engineer-belt').replace('native/run_07','native/run_08').replace("('E04','E07','E08','E09','E10')","('E04','E07','E08','E09','E10','E11')")
(ROOT/'tools/finish_E12.py').write_text(s,encoding='utf-8')
for filename in ('review_README.md','review_template.html'):
    p=ROOT/'tools'/filename;s=p.read_text(encoding='utf-8').replace('E11','E12').replace('0.7.0-preview.24-engineer','0.7.0-preview.25-engineer-belt').replace('native/run_07','native/run_08')
    if filename.endswith('.html'):
        s=s.replace('<button onclick="show(\'E12\')">Сейчас: E12</button>','<button onclick="show(\'E11\')">E11: пояс скрыт тканью</button><button onclick="show(\'E12\')">Сейчас: E12</button>')
        s=s.replace('<h2>Та же модель в Minecraft</h2>','<p>E12: кожаный пояс выведен поверх тканевых перемычек пальто. Латунная пряжка, подсумки и задние крепления подогнаны к новой поверхности. Геометрия и физика пол пальто сохранены.</p><h2>Та же модель в Minecraft</h2>')
    else:s+='\nE12: кожаный пояс выведен наружу относительно тканевых поясных перемычек; крепления подогнаны. Физика пол и проект свойств D02 сохранены. Предыдущий E11/JAR24 остаётся в истории; актуальный визуальный кандидат —preview.25-engineer-belt.\n'
    p.write_text(s,encoding='utf-8')
print('E12 resources use a new preview.25 artifact; exported E11/JAR24 preserved')
