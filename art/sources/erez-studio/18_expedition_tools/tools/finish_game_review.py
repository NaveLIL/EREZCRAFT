from pathlib import Path
import argparse,json,hashlib,zipfile,shutil,datetime
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT/.verification/et01')
p=argparse.ArgumentParser();p.add_argument('--run-id',required=True);p.add_argument('--reviewed-scenes',action='store_true');a=p.parse_args();assert a.reviewed_scenes
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
record=json.loads((ROOT/'reports/native'/('run_'+a.run_id)/'run.json').read_text(encoding='utf-8'));assert record['passed'];proof=record['proof']
for flag in ('distant_horizons_loaded','third_person_both_hands_checked','first_person_both_hands_captured','resource_reload_checked','glint_checked'):assert proof[flag],flag
assert len(proof['screenshots'])==10 and proof['mod_version']=='0.7.0-preview.22-tools'
assert proof['first_person_fov']==70
d=json.loads((ROOT/'source/tools_T01.json').read_text(encoding='utf-8'));b=json.loads((ROOT/'reports/blender.json').read_text());assert b['source_sha256']==sha(ROOT/'source/tools_T01.json')
jar=GAME/'build/libs/interstice-0.7.0-preview.22-tools.jar';base=ROOT.parent/'16_expedition_backpacks/exports/interstice-0.7.0-preview.21-backpacks.jar'
allowed={'assets/interstice/models/item/'+name+'.json' for name in d['models']}|{'META-INF/neoforge.mods.toml'}
with zipfile.ZipFile(base) as old,zipfile.ZipFile(jar) as new:
    before={n:old.read(n) for n in old.namelist() if not n.endswith('/')};after={n:new.read(n) for n in new.namelist() if not n.endswith('/')}
    assert before.keys()<=after.keys()
    changed=[n for n in before if before[n]!=after[n]];assert set(changed)<=allowed,changed
    added=sorted(after.keys()-before.keys());assert all(n.startswith('assets/interstice/textures/item/expedition_tools/') and n.endswith('.png') for n in added),added
    classes=[n for n in before if n.endswith('.class')];assert all(before[n]==after[n] for n in classes)
    for name,item in d['models'].items():assert json.loads(after['assets/interstice/models/item/'+name+'.json'])==item['model']
    for f in (ROOT/'exports/assets').rglob('*.png'):assert after[f.relative_to(ROOT/'exports').as_posix()]==f.read_bytes()
for name,h in record['screenshot_sha256'].items():assert sha(ROOT/'preview/native'/name)==h
shutil.copy2(jar,ROOT/'exports'/jar.name);shutil.copy2(jar.with_name(jar.stem+'-sources.jar'),ROOT/'exports'/(jar.stem+'-sources.jar'))
result={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'revision':'EXPEDITION-TOOLS-T01','source_sha256':sha(ROOT/'source/tools_T01.json'),
 'blender':b,'native':record,'jar_sha256':sha(jar),'changed_previous_entries':changed,'added_entries':added,'all_previous_runtime_classes_byte_identical':len(classes),
 'visual_direction':'User saw Blender prototypes and authorized continued in-game comparison; final review still available',
 'handheld_scale':{n:{ctx:m['model']['display'][ctx]['scale'] for ctx in ('gui','thirdperson_righthand','firstperson_righthand')} for n,m in d['models'].items()},'first_person_fixture_fov':70,
 'installed':False,'published':False,'primary_game_sources_changed':False,'launcher_changed':False,
 'limits':['Native screenshots come from one real client and supported server test players in an isolated profile',
  'Studio Blender lighting and native game lighting differ; geometry and texture files are exact matches',
  'The visible symbols are static decorations; no new controls, status UI or gameplay behavior were added',
  'No dedicated-server or multiplayer capacity claim and no numeric FPS guarantee']}
(ROOT/'reports/game_review.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
backlog=ROOT.parent/'16_expedition_backpacks/source/item_model_backlog.json';q=json.loads(backlog.read_text(encoding='utf-8'));q['queue'][1]['status']='T01 built in Blender and full native client; local preview.22 review candidate, not installed';backlog.write_text(json.dumps(q,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
req=ROOT/'source/requirements.json';r=json.loads(req.read_text(encoding='utf-8'));r['stage']='Blender and native review completed';r['approval']='Candidate shown for final visual assessment';r['unknowns']=['Final owner visual assessment','Behavior under untested third-party shaders or item-rendering replacements'];req.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
readme=ROOT/'README.md';s=readme.read_text(encoding='utf-8');s+='\n## Проверка в Minecraft\n\nПодготовлен отдельный кандидат preview.22-tools. Настоящий клиент с DH проверил точное число граней, текстуры, миниатюры, инвентарь, обе руки от первого и третьего лица, перезагрузку ресурсов и блеск. Все прежние runtime-классы побайтно сохранены. Новый код механик не добавлялся. Итоговый [отчёт](reports/game_review.json), [игровые миниатюры](preview/native/tools-catalog.png), [вид от третьего лица](preview/native/tools-held-quarter.png). Сборка остаётся локальным кандидатом; рабочие профили, сервер и лаунчер не изменялись.\n';readme.write_text(s,encoding='utf-8')
s=readme.read_text(encoding='utf-8').replace('Положение в руках, левой руке, перезагрузка ресурсов и эффекты зачарования будут проверены после согласования внешнего вида.','Положение в обеих руках, перезагрузка ресурсов и блеск проверены в отдельном игровом профиле; доказательства приведены ниже.')
s+='\nМасштаб в руках уменьшен по замечанию владельца отдельно от GUI: катушка 0,42, пульт 0,33, искатель 0,38 в виде от третьего лица. От первого лица — 0,50/0,45/0,48, проверка при FOV 70. Размер инвентарных миниатюр сохранён. Старые кадры с чрезмерным масштабом находятся в preview/history и не представляют текущий результат.\n'
readme.write_text(s,encoding='utf-8')
html=ROOT/'reports/review.html';s=html.read_text(encoding='utf-8');s=s.replace('</main>','<h2>Реальный Minecraft</h2><a href="../preview/native/tools-catalog.png"><img src="../preview/native/tools-catalog.png"></a><p>Рисует настоящий ItemRenderer, крупно и в обычном размере иконки.</p><a href="../preview/native/tools-held-quarter.png"><img src="../preview/native/tools-held-quarter.png"></a><p><a href="game_review.json">Доказательства игровой проверки</a>. Кандидат не установлен в рабочий профиль.</p></main>');html.write_text(s,encoding='utf-8')
s=html.read_text(encoding='utf-8').replace('Положение в руках и работу в игровом клиенте проверим после согласования внешнего вида.','Положение в руках и работу моделей проверили в отдельном игровом клиенте; кадры приведены ниже.').replace('в игру не установлено','в рабочий профиль не установлено');html.write_text(s,encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/expedition_tools_T01_review.zip','w',zipfile.ZIP_DEFLATED) as z:
    for folder in ('source','assembly','preview','reports'):
        for f in (ROOT/folder).rglob('*'):
            if f.is_file() and f.suffix!='.blend1':z.write(f,f.relative_to(ROOT).as_posix())
    for f in (ROOT/'exports/assets').rglob('*'):
        if f.is_file():z.write(f,f.relative_to(ROOT).as_posix())
    for f in (ROOT/'exports').glob('*.glb'):z.write(f,f.relative_to(ROOT).as_posix())
    z.write(ROOT/'README.md','README.md')
print(json.dumps({'jar':jar.name,'jar_sha256':sha(jar),'unchanged_classes':len(classes),'native_passed':True,'installed':False}))
