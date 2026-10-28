from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CAND=GAME/'.verification/ai01';BASE=GAME/'.verification/kt01'
p=argparse.ArgumentParser();p.add_argument('--assembly',required=True);p.add_argument('--native-run',required=True);args=p.parse_args()
sha=lambda b:hashlib.sha256(b).hexdigest()
assembly=CAND/args.assembly;assert json.loads((assembly/'summary.json').read_text())['passed']
for name,h in json.loads((assembly/'source-sha256.json').read_text()).items():
    if name.replace('\\','/').startswith('src/main/'):assert sha((CAND/name).read_bytes())==h,name
native=ROOT/'reports/native'/('run_'+args.native_run);record=json.loads((native/'run.json').read_text(encoding='utf-8'));assert record['passed']
proof=record['proof']
for key in ['all_16_native_models_checked','actual_inventory_screenshot','resource_reload_checked','dye_and_glint_checked','distant_horizons_loaded']:assert proof[key],key
assert len(proof['items'])==16 and proof['version']=='0.7.0-preview.20-icons'
jar=CAND/'build/libs/interstice-0.7.0-preview.20-icons.jar';old=ROOT.parent/'14_kotomyaf_armor/exports/interstice-0.7.0-preview.19-kotomyaf.jar'
manifest=json.loads((ROOT/'source/icon_manifest.json').read_text(encoding='utf-8'));model_names={r['model_path'].removeprefix('src/main/resources/') for r in manifest['icons']}
with zipfile.ZipFile(old) as a,zipfile.ZipFile(jar) as b:
    before={n:a.read(n) for n in a.namelist() if not n.endswith('/')};after={n:b.read(n) for n in b.namelist() if not n.endswith('/')}
    assert before.keys()<=after.keys()
    changed_old=[n for n in before if before[n]!=after[n]]
    assert set(changed_old)<=model_names|{'META-INF/neoforge.mods.toml'},changed_old
    old_classes=[n for n in before if n.endswith('.class')];assert all(before[n]==after[n] for n in old_classes)
    for file in (CAND/'build/classes/java/main').rglob('*.class'):assert after[file.relative_to(CAND/'build/classes/java/main').as_posix()]==file.read_bytes()
    for row in manifest['icons']:assert sha(after[row['model_path'].removeprefix('src/main/resources/')])==row['model_sha256']
baseline=json.loads((ROOT/'reports/baseline.json').read_text())['files']
current={p.relative_to(CAND).as_posix():sha(p.read_bytes()) for p in (CAND/'src/main').rglob('*') if p.is_file()}
delta=[name for name,h in current.items() if baseline.get(name)!=h]
expected={r['model_path'] for r in manifest['icons']}|{'src/main/java/pro/erez/interstice/client/ArmorIconModels.java','src/main/resources/assets/minecraft/atlases/blocks.json'}
assert set(delta)==expected,delta
writes={}
for name in delta:
    target=GAME/name;value=(CAND/name).read_bytes()
    if name in baseline:
        assert target.exists()
        if target.read_bytes()!= (BASE/name).read_bytes():assert json.loads(target.read_bytes())==json.loads((BASE/name).read_bytes()),'Independent model change: '+name
    elif target.exists():
        if name.endswith('assets/minecraft/atlases/blocks.json'):
            data=json.loads(target.read_bytes());new=json.loads(value)
            for source in new['sources']:
                if source not in data['sources']:data['sources'].append(source)
            value=(json.dumps(data,indent=2)+'\n').encode()
        else:assert target.read_bytes()==value,'New source collision: '+name
    writes[name]=value
# Prepare narrow predecessor-generator guards without copying unrelated tool edits.
tool_writes={}
for name,param in [('tools/build_expedition_progression.py','value'),('tools/build_aegis_resources.py','data')]:
    target=GAME/name;source=target.read_text(encoding='utf-8');lines=source.splitlines(True)
    if 'Derived armor miniatures are authored' not in source:
        at=next(i for i,line in enumerate(lines) if line.startswith('def write('))
        lines.insert(at+1,f'''    # Derived armor miniatures are authored by creations/15_armor_inventory_icons.
    if path.exists() and {param}.get("loader") != "interstice:armor_icon":
        previous = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(previous, dict) and previous.get("loader") == "interstice:armor_icon":
            return
''')
    tool_writes[target]=''.join(lines)
koto=ROOT.parent/'14_kotomyaf_armor/tools/build_resources.py';source=koto.read_text(encoding='utf-8')
if 'Preserve final derived inventory miniatures' not in source:
    source=source.replace('    p=RES/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(','''    p=RES/name
    # Preserve final derived inventory miniatures from project 15.
    if p.exists() and data.get("loader") != "interstice:armor_icon":
        previous=json.loads(p.read_text(encoding='utf-8'))
        if isinstance(previous,dict) and previous.get('loader')=='interstice:armor_icon':return
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(''')
tool_writes[koto]=source
untouched={p.relative_to(GAME).as_posix():sha(p.read_bytes()) for p in (GAME/'src/main').rglob('*') if p.is_file() and p.relative_to(GAME).as_posix() not in writes}
for name,value in writes.items():target=GAME/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value)
for target,source in tool_writes.items():target.write_text(source,encoding='utf-8')
for name,h in untouched.items():assert sha((GAME/name).read_bytes())==h,name
for destination in [ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name]:shutil.copy2(jar,destination)
shutil.copy2(jar.with_name(jar.stem+'-sources.jar'),ROOT/'exports'/(jar.stem+'-sources.jar'))
result={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':proof['version'],'icons':16,'jar_sha256':sha(jar.read_bytes()),
    'previous_runtime_classes_identical':len(old_classes),'only_previous_jar_changes':changed_old,'main_source_delta':delta,'unrelated_primary_runtime_files_preserved':len(untouched),
    'assembly_evidence':str(assembly),'native':record,'source_manifest':str(ROOT/'source/icon_manifest.json'),'new_png_or_ogg_assets':False,
    'installed':False,'published':False,'owner_profile_used':False,'generator_guards':list(map(str,tool_writes)),
    'limits':['Prepared inventory and diagnostic catalog with native Minecraft item renderer, not natural Survival','Other resource packs can replace item models; not every third-party shader combination was checked']}
for target in [ROOT/'reports/accepted.json',GAME/'docs/verification/ARMOR_ICONS.json']:target.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
handoff=GAME/'docs/HANDOFF.md';s=handoff.read_text(encoding='utf-8');header,rest=s.split('\n',1)
entry='\nЛокально 27.10.2026: **16 объёмных миниатюр всей брони**, JAR **0.7.0-preview.20-icons**. Серебро, рифтсталь, Маген и Котомяф — по четыре предмета; точные грани, кости, углы и цвета производятся из прежней геометрии, у курток оба рукава, у поножей/обуви обе ноги. Новый загрузчик только клиентский; все прежние runtime-классы и механики побайтно сохранены. Полный клиент DH/106 модов проверил настоящий инвентарь, 16 запечённых моделей, цвета, отсутствие пропавших текстур, перезагрузку ресурсов, окраску и блеск. [Проверка](verification/ARMOR_ICONS.json). Артефакты: Blender `creations/15_armor_inventory_icons`; кандидат `.verification/ai01`. Старые генераторы ресурсов защищены от возврата плоских спрайтов. Источники основной рабочей копии обновлены узко; её gradle.properties, сервер и игровые профили не изменялись.\n'
handoff.write_text(header+'\n'+entry+rest,encoding='utf-8')
for name in ['ARMOR.md','KOTOMYAF.md']:
    target=GAME/'docs'/name;s=target.read_text(encoding='utf-8');s+='\n\nМиниатюры инвентаря обновлены для всех 16 частей в `0.7.0-preview.20-icons`. Производный источник: `D:/repos/Blender/creations/15_armor_inventory_icons`. [Подтверждение](verification/ARMOR_ICONS.json). Предыдущие версии выше сохранены как история функциональных проверок.\n';target.write_text(s,encoding='utf-8')
with zipfile.ZipFile(ROOT/'exports/armor_icons_A01_screenshots.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name,h in record['screenshot_sha256'].items():
        image=ROOT/'preview'/name;assert sha(image.read_bytes())==h;z.write(image,name)
    z.write(ROOT/'README.md','README.md');z.write(ROOT/'reports/accepted.json','verification.json')
print(json.dumps({'version':result['version'],'icons':16,'jar_sha256':result['jar_sha256'],'old_runtime_classes_identical':len(old_classes),'preserved_unrelated_files':len(untouched)}))
