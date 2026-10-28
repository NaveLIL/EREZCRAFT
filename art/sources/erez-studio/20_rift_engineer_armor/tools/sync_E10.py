from pathlib import Path
import json,shutil,hashlib
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
archive=ROOT/'exports/intermediate/E09';archive.mkdir(parents=True,exist_ok=True)
for file in (CAND/'build/libs').glob('*engineer*.jar'):shutil.copy2(file,archive/file.name)
for filename in ('build_game_resources.py','prepare_native.py','native_tail.java.txt','run_native.py','verify_review.py'):
    p=ROOT/'tools'/filename;p.write_text(p.read_text(encoding='utf-8').replace('E09','E10'),encoding='utf-8')
s=(ROOT/'tools/finish_E09.py').read_text(encoding='utf-8').replace('E09','E10').replace('native/run_05','native/run_06').replace("'reports/native/run_05'","'reports/native/run_06'")
s=s.replace("('E04','E07','E08')","('E04','E07','E08','E09')")
(ROOT/'tools/finish_E10.py').write_text(s,encoding='utf-8')
for filename in ('review_template.html','review_README.md'):
    p=ROOT/'tools'/filename;s=p.read_text(encoding='utf-8').replace('E09','E10').replace('native/run_04','native/run_06').replace('трубок, соединителей','боковых фильтров, соединителей').replace('светло-серыми','приглушёнными серыми').replace('Светло-серые','Серые')
    for a,b in {'#b8bec2':'#7d8892','#939ca3':'#596672','#c9ced1':'#8f9aa4','#a5aeb4':'#6c7883','#d3d7d9':'#4b5863','#8c969e':'#343f49','Боковые трубки':'Рамки фильтров','Соединители</span>':'Подложка</span>'}.items():s=s.replace(a,b)
    if filename.endswith('.html'):
        s=s.replace('<button onclick="show(\'E10\')">Сейчас: E10</button>','<button onclick="show(\'E09\')">E09: слишком светлая</button><button onclick="show(\'E10\')">Сейчас: E10</button>')
    p.write_text(s,encoding='utf-8')
print('E10 native and review tools synchronized')
