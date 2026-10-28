"""Deliver native screenshots and the cosmetic A04 patch with bounded verification evidence."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile,datetime
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');CANDIDATE=GAME/'.verification/ag03'
parser=argparse.ArgumentParser();parser.add_argument('--run-id',required=True);parser.add_argument('--component-evidence',required=True);args=parser.parse_args()
run=ROOT/'reports/player_gallery'/('run_'+args.run_id);gallery=json.loads((run/'run.json').read_text())
assert gallery['passed'] and gallery['released_runtime_classes_identical']>0
proof=gallery['proof'];assert len(proof['players'])==3 and {p['skin_model'] for p in proof['players']}=={'default','slim'}
for key in ['crouching_checked','walking_checked','bow_pose_checked','trim_dye_glint_checked','backpack_visual_checked','swimming_checked']:
    assert proof[key],key
assert proof['mod_version']=='0.7.0-preview.18-armor' and len(proof['screenshots'])==12
component=CANDIDATE/args.component_evidence;summary=json.loads((component/'summary.json').read_text());assert summary['passed']
log=(component/'runProgressionGameTestServer.log').read_text(encoding='utf-8');assert 'All 23 required tests passed' in log
sha=lambda b:hashlib.sha256(b).hexdigest()
jar=CANDIDATE/'build/libs/interstice-0.7.0-preview.18-armor.jar';old=ROOT/'exports/interstice-0.7.0-preview.17-armor.jar'
with zipfile.ZipFile(old) as before,zipfile.ZipFile(jar) as after:
    a={n:before.read(n) for n in before.namelist() if not n.endswith('/')};b={n:after.read(n) for n in after.namelist() if not n.endswith('/')}
    assert a.keys()==b.keys(),'Unexpected artifact entries'
    differences=[n for n in a if a[n]!=b[n]]
    for name in differences:
        if name in ['assets/interstice/armor/armor_design.json','META-INF/neoforge.mods.toml']:continue
        assert a[name].replace(b'\r\n',b'\n')==b[name].replace(b'\r\n',b'\n'),'Unexpected payload change: '+name
    old_design=json.loads(a['assets/interstice/armor/armor_design.json']);new_design=json.loads(b['assets/interstice/armor/armor_design.json'])
    assert new_design['revision']=='ARMOR-A04' and len(new_design['sets']['aegis']['cubes'])==124
    assert old_design['sets']['aegis']['cubes']==new_design['sets']['aegis']['cubes'][:-2]
    assert all(old_design['sets'][t]==new_design['sets'][t] for t in ['riftsilver','riftsteel'])
    assert sha(b['assets/interstice/armor/armor_design.json'])==gallery['geometry_sha256']
for dest in [ROOT/'exports'/jar.name,GAME/'build/libs'/jar.name]:shutil.copy2(jar,dest)
shutil.copy2(jar.with_name(jar.stem+'-sources.jar'),ROOT/'exports'/(jar.stem+'-sources.jar'))
record={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':proof['mod_version'],'geometry':'ARMOR-A04',
 'gallery_evidence':str(run),'component_evidence':str(component),'component_game_tests':23,'gallery':gallery,
 'jar_sha256':sha(jar.read_bytes()),'jar_bytes':jar.stat().st_size,'changed_jar_entries':differences,
 'runtime_code_identical_to_previous_tested_release':True,'cosmetic_changes':['Closed Magen neck skin gap','Closed Magen waist skin gap'],
 'other_two_armor_geometries_unchanged':True,'published':False,'owner_profile_used':False,
 'limits':['Controlled player/pose test, not a long natural Survival balance playthrough','One real client and three server-connected mock test players; not a capacity or real multi-client network acceptance test','Vanilla local skin fixtures; external skin services are outside this check','Not every third-party shader or trim combination was exhaustively tested']}
for p in [ROOT/'reports/player_gallery/accepted.json',GAME/'docs/verification/ARMOR_PLAYER_GALLERY.json']:
    p.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
doc=GAME/'docs/ARMOR.md';s=doc.read_text(encoding='utf-8').replace('0.7.0-preview.17-armor','0.7.0-preview.18-armor').replace('armor_design_A03.json','armor_design_A04.json')
s+='\n\n## Проверка на игровых персонажах A04\n\nНа трёх серверных тестовых игроках в полной сборке с DH проверены обычные и узкие руки, приседание, ходьба, поза лука, плавание, окраска, орнаменты, блеск зачарований и рюкзаки. Сняты 12 исходных кадров 1920×1080 без интерфейса. У Магена закрыты два найденных просвета скина на шее и поясе. Исправление косметическое: программный код и характеристики прежние. 23 GameTests и сборка версии 18 прошли повторно. Подробности — `docs/verification/ARMOR_PLAYER_GALLERY.json`; фото — Blender-проект `creations/13_minecraft_armor/preview/player_gallery`.\n'
doc.write_text(s,encoding='utf-8')
readme=ROOT/'README.md';s=readme.read_text(encoding='utf-8').replace('0.7.0-preview.17-armor','0.7.0-preview.18-armor').replace('armor_design_A03.json','armor_design_A04.json').replace('assembly/mezhdumorye_aegis_A03.blend','assembly/mezhdumorye_magen_A04.blend')
s+='\n\nИгровая проверка A04: `preview/player_gallery/README.md`, 12 кадров без обработки; `reports/player_gallery/accepted.json`. У Магена закрыты просветы под шлемом и на поясе; остальные комплекты и игровой код сохранены. Актуальную форму A04 воспроизводит `tools/build_magen_seals.py`, Blender-сцену — `tools/render_magen_A04.py`.\n'
readme.write_text(s,encoding='utf-8')
handoff=GAME/'docs/HANDOFF.md';s=handoff.read_text(encoding='utf-8');first,rest=s.split('\n',1)
entry='\nЛокально 27.10.2026: проверка брони на игровых персонажах **A04**, JAR **0.7.0-preview.18-armor**. Сняты 12 исходных кадров 1920×1080 трёх тестовых игроков; проверены wide/slim, приседание, ходьба, поза лука, плавание, рюкзаки, окраска/орнаменты/блеск. Закрыты два косметических просвета скина Магена на шее и поясе; основной программный код идентичен проверенной версии 17. На версии 18 повторно прошли 23 GameTests и assemble. [Доказательства](verification/ARMOR_PLAYER_GALLERY.json), [описание](ARMOR.md). Кандидат — `.verification/ag03`, авторитетная геометрия — Blender `creations/13_minecraft_armor/source/armor_design_A04.json`. На сервер и в пользовательские профили не установлено; незавершённая фауна не включена.\n'
handoff.write_text(first+'\n'+entry+rest,encoding='utf-8')
target=ROOT/'preview/player_gallery';archive=ROOT/'exports/erez_armor_ingame_screenshots_A04.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in proof['screenshots']:z.write(target/name,name)
    if (target/'README.md').exists():z.write(target/'README.md','README.md')
    z.write(ROOT/'reports/player_gallery/accepted.json','verification.json')
print(json.dumps({'version':proof['mod_version'],'screenshots':len(proof['screenshots']),'jar_sha256':record['jar_sha256'],'archive':str(archive)},ensure_ascii=False))
