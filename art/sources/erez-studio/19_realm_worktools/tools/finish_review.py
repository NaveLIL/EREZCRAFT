from pathlib import Path
import json,hashlib,zipfile,shutil,datetime,difflib
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/wt01';BASE=GAME/'.verification/et01'
sha=lambda b:hashlib.sha256(b).hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
design=read(ROOT/'source/worktools_WT03.json');review=read(ROOT/'reports/design_verification.json');blender=read(ROOT/'reports/blender.json')
assert review['passed'] and review['source_sha256']==sha((ROOT/'source/worktools_WT03.json').read_bytes())==blender['source_sha256']
native=read(ROOT/'reports/native/run_02/run.json');assert native['passed'] and native['proof']['native_full_pack_mode_and_excavation_checked']
testroot=CAND/'.verification/20261027T104024.869796Z-worktools-WT02-functional';tests=read(testroot/'runProgressionGameTestServer.json');assert tests['accepted'] and tests['required_tests_passed']==43
tested=read(testroot/'source-sha256.json')
for n in ('RealmArmaments.java','RealmWorktools.java'):
    matches=[(p,h) for p,h in tested.items() if p.replace('\\','/').endswith('/'+n)];assert len(matches)==1
    p,h=matches[0];assert sha((CAND/p.replace('\\','/')).read_bytes())==h,('Gameplay changed since tests',n)
for name in ('excavation_stone','excavation_soil','felling_logs','area_tillable','riftsilver_resonant_materials'):
    file='src/main/resources/data/interstice/tags/block/'+name+'.json'
    key=next(k for k in tested if k.replace('\\','/')==file);assert sha((CAND/file).read_bytes())==tested[key],file
jar=CAND/'build/libs/interstice-0.7.0-preview.23-worktools.jar';old=ROOT.parent/'18_expedition_tools/exports/interstice-0.7.0-preview.22-tools.jar'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(jar) as b:
    removed=sorted(set(a.namelist())-set(b.namelist()));assert not removed
    changed=sorted(n for n in a.namelist() if a.read(n)!=b.read(n));added=sorted(set(b.namelist())-set(a.namelist()))
    changed_classes=[n for n in changed if n.endswith('.class')]
    assert all(n.startswith('pro/erez/interstice/gear/RealmArmaments') for n in changed_classes),changed_classes
    assert all(n.startswith('pro/erez/interstice/gear/RealmWorktools') for n in added if n.endswith('.class'))
    unchanged_classes=sum(n.endswith('.class') and n not in changed for n in a.namelist())
    for name,row in design['models'].items():
        entry='assets/interstice/models/item/'+name+'.json';assert json.loads(b.read(entry))==row['model']
    for file in (ROOT/'exports/assets').rglob('*'):
        if file.is_file():assert b.read(file.relative_to(ROOT/'exports').as_posix())==file.read_bytes()

# A narrow source snapshot relative to accepted preview.22, not the unrelated primary WIP diff.
baseline=read(ROOT/'reports/baseline.json')['files'];snapshot={};patch=[]
for file in (CAND/'src/main').rglob('*'):
    if not file.is_file():continue
    rel=file.relative_to(CAND).as_posix();data=file.read_bytes()
    if baseline.get(rel)==sha(data):continue
    out=ROOT/'source/code'/rel;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data);snapshot[rel]=sha(data)
    if file.suffix in ('.java','.json'):
        prior=(BASE/rel).read_text(encoding='utf-8').splitlines(True) if (BASE/rel).exists() else []
        patch.extend(difflib.unified_diff(prior,data.decode('utf-8').splitlines(True),fromfile='a/'+rel,tofile='b/'+rel))
for rel in ('src/smoke/java/pro/erez/interstice/test/WorktoolsGameTests.java','src/smoke/java/pro/erez/interstice/client/WorktoolsNative.java','tools/build_expedition_progression.py'):
    src=CAND/rel;dest=ROOT/'source/code'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest);snapshot[rel]=sha(src.read_bytes())
(ROOT/'source/gameplay_changes.patch').write_text(''.join(patch),encoding='utf-8')
for suffix in ('.jar','-sources.jar'):
    src=CAND/'build/libs'/('interstice-0.7.0-preview.23-worktools'+suffix);shutil.copy2(src,ROOT/'exports'/src.name)
for file in (testroot/'runProgressionGameTestServer.json',testroot/'runProgressionGameTestServer.log',testroot/'source-sha256.json'):
    dest=ROOT/'reports/gametests';dest.mkdir(exist_ok=True);shutil.copy2(file,dest/file.name)
pose={'revision':'WT03','cause':'Upright native 3D haft was using transforms intended for a diagonal vanilla sprite; wrong left first-person yaw also corrected',
    'pickaxe_head_width':{'previous':16,'current':12,'reduction_percent':25},'curve_step':.5,'model_units_per_block':16,
    'display':{n:r['model']['display'] for n,r in design['models'].items()},'native_run':'reports/native/run_02/run.json',
    'owner_final_visual_approval':False}
(ROOT/'reports/hand_pose_review.json').write_text(json.dumps(pose,indent=2)+'\n',encoding='utf-8')
record={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'revision':'WT03','version':'0.7.0-preview.23-worktools',
    'candidate':str(CAND),'source_sha256':review['source_sha256'],'jar_sha256':sha(jar.read_bytes()),'jar_bytes':jar.stat().st_size,
    'gameplay_game_tests':{'required_passed':43,'new_tool_tests':15,'java_and_allowlists_match_tested_sources':True,'evidence':'reports/gametests/runProgressionGameTestServer.json'},
    'native':{'passed':True,'mod_count':native['proof']['mod_count'],'distant_horizons_loaded':True,'all_eight_both_hands_checked':True,'resource_reload_checked':True,'mode_and_nine_block_excavation_checked':True,'screenshot_count':len(native['proof']['screenshots']),'evidence':'reports/native/run_02/run.json'},
    'blender_version':blender['blender_version'],'exact_native_geometry_and_palette_images':True,
    'jar_comparison':{'base_version':'preview.22','changed':changed,'added':added,'removed':removed,'unchanged_prior_runtime_classes':unchanged_classes,'changed_prior_runtime_classes':changed_classes},
    'source_snapshot':snapshot,'owner_final_visual_approval':False,'installed_to_server_or_owner_profile':False,
    'limits':['Controlled fixtures, not long Survival acceptance','Protection hooks tested with per-block event cancellation; not every third-party protection mod','WT01 rejected, WT02 superseded; WT03 awaits final visual review','No renderer or server-capacity benchmark claimed']}
(ROOT/'reports/game_review.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
backlog=ROOT.parent/'16_expedition_backpacks/source/item_model_backlog.json';data=read(backlog)
for row in data['queue']:
    if row['group']=='Silver and steel tools':row.update(status='WT03 Blender and native checked; switchable steel modes and silver resonance; local preview.23, awaiting final visual review, not installed',project='../19_realm_worktools')
backlog.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
html='''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>EREZ — инструменты WT03</title><style>body{background:#14202a;color:#e0edf2;font:17px/1.6 system-ui;max-width:1200px;margin:32px auto;padding:0 20px}img{max-width:100%;display:block;border-radius:12px;margin:16px 0}a{color:#88d7da}button{padding:10px 20px;background:#2d5662;color:white;border:0;border-radius:8px;margin-right:10px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #456;padding:12px;text-align:left}small{color:#a7c1cd}</style><h1>EREZ / рабочие инструменты / WT03</h1><p>Восемь инструментов: связные более узкие и плавные головки, исправленный хват. Изображения получены из настоящих Blender и Minecraft. Ожидает итоговой визуальной приёмки.</p><h2>Было / стало — Blender</h2><button onclick="document.getElementById('comparison').src='../preview/worktools_previous.png'">Исходные модели</button><button onclick="document.getElementById('comparison').src='../preview/worktools_WT03.png'">WT03</button><img id="comparison" src="../preview/worktools_WT03.png"><p><a href="../assembly/realm_worktools_WT03.blend">Сцена Blender</a> · <a href="../exports/interstice-0.7.0-preview.23-worktools.jar">Локальный JAR23</a> · <a href="../README.md">Управление и ограничения</a></p><h2>Фактический Minecraft</h2><img src="../preview/native/worktools-after-reload.png"><h2>Обе руки, вид от первого лица</h2><img src="../preview/native/worktools-first-person-riftsteel_pickaxe.png"><p>Обычный FOV70. Модели и текстуры совпадают с Blender; освещение и ракурс игровые.</p><h2>Свойства</h2><table><tr><th>Инструмент</th><th>Работа</th></tr><tr><td>Серебро, все четыре</td><td>+25% базовой скорости по подходящим местным природным материалам.</td></tr><tr><td>Рифтстальная кирка</td><td>Камень3×3; дополнительные руды и блоки с содержимым пропускает.</td></tr><tr><td>Рифтстальный топор</td><td>Связанные брёвна одной породы от сруба вверх, до16.</td></tr><tr><td>Рифтстальная лопата</td><td>Грунт3×3 по выбранной грани.</td></tr><tr><td>Рифтстальная мотыга</td><td>Грядка3×3, обычная и местная почва.</td></tr></table><p>Shift+ПКМ по воздуху переключает режим рифтстали. Приседание временно делает работу одиночной. Расход прочности — за каждый блок.</p><p>43 серверных теста; полный клиент106модов с Distant Horizons,22 необработанных скриншота, все восемь моделей в обеих руках, ресурсная перезагрузка и фактическая выработка3×3. Это подготовленные тестовые условия, не длительная Survival-приёмка.</p><p><a href="game_review.json">Хеши и доказательства</a> · <a href="../exports/realm_worktools_WT03_review.zip">Все исходники и материалы проверки</a></p><small>На сервер, в подписанный каталог лаунчера и рабочий профиль пользователя не устанавливалось.</small></html>'''
(ROOT/'reports/review.html').write_text(html,encoding='utf-8')
archive=ROOT/'exports/realm_worktools_WT03_review.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for directory in ('source','assembly','preview','reports','tools','exports/assets'):
        for file in (ROOT/directory).rglob('*'):
            if file.is_file() and '__pycache__' not in file.parts and file.suffix not in ('.blend1','.pyc','.log'):z.write(file,file.relative_to(ROOT))
    for file in (ROOT/'exports').glob('*WT03.glb'):z.write(file,file.relative_to(ROOT))
    z.write(ROOT/'README.md','README.md')
(ROOT/'reports/package.json').write_text(json.dumps({'path':str(archive),'sha256':sha(archive.read_bytes()),'bytes':archive.stat().st_size},indent=2),encoding='utf-8')
print(json.dumps({'version':record['version'],'jar_sha256':record['jar_sha256'],'native_passed':True,'unchanged_prior_runtime_classes':unchanged_classes,'review_archive_bytes':archive.stat().st_size}))
