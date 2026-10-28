from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
dest=ROOT/'exports/intermediate/E10';dest.mkdir(parents=True,exist_ok=True)
for p in (CAND/'build/libs').glob('*engineer*.jar'):shutil.copy2(p,dest/p.name)
for filename in ('build_game_resources.py','prepare_native.py','native_tail.java.txt','run_native.py','verify_review.py'):
    p=ROOT/'tools'/filename;s=p.read_text(encoding='utf-8').replace('E10','E11')
    if filename=='verify_review.py':s=s.replace("if p['name'].startswith('chest_star_')])==6","if p['name'].startswith('chest_star_')])==0")
    p.write_text(s,encoding='utf-8')
s=(ROOT/'tools/finish_E10.py').read_text(encoding='utf-8').replace('E10','E11').replace('native/run_06','native/run_07').replace("('E04','E07','E08','E09')","('E04','E07','E08','E09','E10')")
s=s.replace("ROOT/'source/requirements.json',mesh", "ROOT/'source/requirements.json',ROOT/'source/engineer_gameplay_proposal.json',ROOT/'reports/GAMEPLAY_PROPOSAL.md',mesh")
(ROOT/'tools/finish_E11.py').write_text(s,encoding='utf-8')
for filename in ('review_README.md','review_template.html'):
    p=ROOT/'tools'/filename;s=p.read_text(encoding='utf-8').replace('E10','E11').replace('native/run_06','native/run_07')
    for a,b in {'#7d8892':'#66727d','#596672':'#414e5a','#8f9aa4':'#6d7984','#6c7883':'#52606c','#4b5863':'#384550','#343f49':'#252f39'}.items():s=s.replace(a,b)
    if filename.endswith('.html'):
        s=s.replace('<h2>Движение пальто</h2>','<p>Передняя звезда и её подложка удалены; задняя эмблема сохранена. <a href="GAMEPLAY_PROPOSAL.md">Проект свойств, нейроинтерфейса и крафта</a> — предложение для следующего этапа, ещё не игровые свойства.</p><h2>Движение пальто</h2>')
    else:s+='\n## Следующий этап — свойства\n\nПередняя звезда и квадратная подложка удалены, задняя эмблема сохранена. См. `reports/GAMEPLAY_PROPOSAL.md` и `source/engineer_gameplay_proposal.json`: это проект игровых свойств и рецептов, не реализованные механики. Текущий JAR по-прежнему использует железные характеристики для визуальной приёмки.\n'
    p.write_text(s,encoding='utf-8')
print('E11 revision synchronized; gameplay proposal remains separate from runtime')
