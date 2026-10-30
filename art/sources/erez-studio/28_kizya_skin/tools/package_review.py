from pathlib import Path
import json,hashlib,shutil,zipfile,datetime
ROOT=Path(__file__).resolve().parents[1]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
old=ROOT/'source/ASSET_PROVENANCE.json'
archive=ROOT/'archive/PK01_block_texture_trial/ASSET_PROVENANCE.json'
if old.exists() and not archive.exists():shutil.copy2(old,archive)
provenance={
 'revision':'PK02','active':True,'art_mode':'built-in image_gen',
 'owner_reference':'references/owner_reference.png','raw_generated_art':'source/painted_sheet_PK02_original.png',
 'raw_generated_art_sha256':sha(ROOT/'source/painted_sheet_PK02_original.png'),
 'prompt':'source/painted_sheet_prompt.txt',
 'format_operations':'Native UV region selection, resolution conversion, packing into CPM 128x128 and standard Java slim 64x64 layouts.',
 'borrowed_block_textures_used':False,
 'layout_reference_only':'references/alex_uv_layout.png; vanilla Alex UV dimensions only, its artwork is not sampled by PK02.',
 'retired_trial':'archive/PK01_block_texture_trial; rejected by owner, not a delivery candidate',
 'geometry_authority':'source/avatar_PK02.json; Blender scenes and CPM files are exports from this source',
 'units':'Minecraft player-model pixels, 16 pixels = 1 block',
 'game_dependency':{'name':'Custom Player Models','version':'0.6.27a','minecraft':'1.21.1 NeoForge',
  'source':'https://github.com/tom5454/CustomPlayerModels',
  'sha256':'b5d3a473998648eefdf646e10a48af95dac57add0df681ca4407e83c716d6c84'}
}
old.write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
account={'name':'kizya','canonical_uuid':'f644c73e-3c3f-4bbd-83e5-074e8c552b31',
 'owner_confirmed':True,'public_profile_lookup_confirmed':True,
 'account_skin_changed':False,'live_client_changed':False,'live_server_changed':False}
(ROOT/'reports/ACCOUNT_TARGET.json').write_text(json.dumps(account,indent=2)+'\n')
readme='''# Kizya / Pink PK02

Новый нарисованный скин по референсу владельца: длинные розовые волосы,
белый свитер, розовый комбинезон, полосатые гольфы и обувь на платформе.
Рисунок создан встроенным image_gen; исходник и точный prompt сохранены.
PK01 с блочными текстурами отвергнут, сохранён в archive/ и не входит в комплект PK02.

## Файлы

- exports/Kizya_Pink_PK02_slim_64x64.png — обычный Java-скин, тип **Slim/Alex**.
  Подходит формату EREZ ID, не содержит объёмной геометрии.
- exports/Kizya_Pink_PK02.cpmmodel — объёмная модель CPM, 139 деталей.
- exports/Kizya_Pink_PK02.cpmproject — редактируемый проект CPM.
- assembly/Kizya_Pink_PK02.blend — исходная сцена Blender с упакованным атласом.
- source/avatar_PK02.json — авторитетный источник геометрии, UV и привязок.
- source/painted_sheet_PK02_original.png — неизменённый рисунок image_gen.
- source/painted_sheet_prompt.txt — полный запрос к image_gen.
- preview/native/ — фотографии настоящего Minecraft из отдельного тестового профиля.

## Проверки и ограничения

EREZ ID: настоящий валидатор gateway принимает PNG 64×64/Slim без изменения
байтов. CPM: официальный редактор экспортирует проект, официальный ModelFile
читает модель обратно. Актуальные игровые результаты: reports/native/run_*/run.json.
Код возврата Gradle 0 сам по себе не подтверждает игровую проверку: нужен proof.passed=true.
Итоговая проверка run_06 прошла: 108 модов, фактически загруженные DH/CPM,
передача модели сервер → клиент, ходьба/приседание/плавание, броня по слотам,
элитры/рюкзак, перезагрузка ресурсов и пустая рука от первого лица.

Клиентские проверки используют изолированный снимок сборки со старым
Interstice preview.21 и обязательными DH/Create/Sable, дополненный CPM.
Это не проверка нынешнего публичного alpha.22/preview.38, отдельного
dedicated server, нескольких настоящих клиентов или Survival.
Сцену и mock-игроков нельзя выдавать за естественное прохождение.

Для режима .cpmmodel CPM требуется на сервере и у наблюдающих клиентов.
В текущем публичном клиенте и на живом сервере CPM не найден; этот проект
их не меняет и не перезапускает. PNG можно загрузить через EREZ ID из
собственного аккаунта kizya; аккаунт и прежний скин здесь не изменены.
Объёмный вид — отдельная интеграция после визуальной оценки модели.

Новый собственный erez-cpm-guard 0.2.0 в exports — только кандидат для
изолированного теста конкурентного доступа CPM к конфигурации. Старый
erez-avatar-compat 0.1.0 не устанавливался. Сторонний CPM JAR не изменялся.

## Повторная сборка

Последовательно: tools/build_model_PK02.py → Blender --background --python
tools/prepare_painted_export.py → tools/compile_cpm.py → Blender --background
--python tools/build_blender_PK02.py. UV-упаковка выполняется только после
восстановления исходных UV первым скриптом. tools/validate_erez_id.mjs
проверяет итоговый PNG. tools/run_native.py создаёт новый одноразовый профиль
и отказывается запускаться при уже работающей тяжёлой Java JVM.
'''
(ROOT/'README.md').write_text(readme,encoding='utf8')
selection={'selected_revision':'PK02','owner_visual_approval':False,'account':'kizya',
 'reference':'references/owner_reference.png','requirements':[
 'Newly painted texture, no block texture reuse','Slim arms','Long layered pastel pink hair',
 'White sweater and dusty-pink pinafore shorts','Pink striped socks and platform shoes',
 'Vanilla pivots and equipment clearance','Preserve owner profiles and live server'],
 'not_delivered_to_account':True,'retired_revision':'PK01'}
(ROOT/'source/selection.json').write_text(json.dumps(selection,indent=2)+'\n')
files=[ROOT/'README.md',ROOT/'source/painted_sheet_prompt.txt',ROOT/'source/ASSET_PROVENANCE.json']
files += [ROOT/'exports'/n for n in ['Kizya_Pink_PK02_slim_64x64.png','Kizya_Pink_PK02.cpmmodel','Kizya_Pink_PK02.cpmproject']]
files += [ROOT/'preview'/n for n in ['PK02_front.png','PK02_quarter.png','PK02_back.png']]
native=ROOT/'reports/native/run_06/run.json'
if native.exists() and json.loads(native.read_text()).get('passed'):
 files += [native,ROOT/'preview/native/kizya-bare-quarter.png',ROOT/'preview/native/kizya-after-reload.png']
with zipfile.ZipFile(ROOT/'exports/Kizya_Pink_PK02_review.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in files:z.write(p,p.relative_to(ROOT))
manifest={p.relative_to(ROOT).as_posix():{'bytes':p.stat().st_size,'sha256':sha(p)} for p in files}
(ROOT/'reports/DELIVERY_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'review_package':str(ROOT/'exports/Kizya_Pink_PK02_review.zip'),'account_changed':False}))
