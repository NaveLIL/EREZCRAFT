from pathlib import Path
import json,hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
report=json.loads((ROOT/'reports/GEOMETRY_CHECK.json').read_text())
assert report['native_blender_art_verified']
readme='''# Микан / MC01 — кораблик по рисунку приёмщицы

Белый корпус с персиковыми и серыми пятнами, сонные закрытые глаза,
розовый нос, два ушка и один круглый кормовой винт. Морда — отдельная
объёмная геометрия: две выступающие щёчки, треугольный нос и рот.
Это модель Blender; рендеры сняты с той же модели, которая лежит в GLB.

## Файлы

- assembly/calico_MC01.blend — редактируемая сцена с материалами и анимацией винта.
- exports/calico_MC01.glb — переносимая статическая модель без студийного фона.
- source/calico_MC01.json — авторитетный источник геометрии и палитры.
- preview/calico_MC01_hero.png — основной ракурс.
- preview/ — также фасад, корма, профиль, палуба и морда крупнее.
- references/owner_calico_sketch.jpg — предоставленный владельцем рисунок.
- reports/GEOMETRY_CHECK.json — проверка сетки, обратного чтения GLB и сохранности Ската.

## Размер и устройство

Около 14,26 × 8,35 × 7,50 блока по габариту с мордочкой, ушками и винтом.
Условные единицы: 1 единица Blender = 1 блок Minecraft.
168 деталей, 17 групп. Четыре кресла: два на приподнятом мостике за лбом,
два на кормовой палубе. Шесть ящиков по бортам и центральный проход.
Проекция носика перед основной плоскостью головы — 0,95 блока.

Пятна, глазки и мордочка сделаны геометрией. Материалы — собственная
однотонная палитра; растровые текстуры и imagegen для этой модели не использовались.
Небольшие фаски и грани сохраняют блочную стилистику.

## Что проверено

Все 168 исходных сеток замкнуты, нет граней нулевой площади и неверных
числовых координат. GLB прочитан в чистой сцене Blender: 168 сеток,
отклонение общих габаритов менее 0,05 блока из-за применения фасок.
Проверены хеши 12 прежних файлов SK01/SK02/SK03 — они сохранены.

## Граница готовности

Это законченный вариант модели для визуальной оценки, а не установленный
в игру транспорт. Кресла, ящики, двигатель и приборы пока являются деталями
модели. Плавучесть, управление, коллизии, прочность, инвентари и взаимодействия
с Sable в MC01 ещё не реализованы и не проверялись. Анимация винта в Blender
показывает предполагаемое движение и не доказывает работу двигателя.
Присланный владельцем кадр плавающего большого Ската относится к другому судну.
Пользовательские миры, игра и сервер этой работой не менялись.

## Воспроизведение

tools/build_design.py создаёт JSON. Затем Blender --background --python
tools/build_scene.py строит сцену и рендерит шесть ракурсов.
tools/verify_model.py проверяет сохранённую сцену и GLB.
tools/package_review.py собирает этот комплект.
'''
readme+='\n## Утверждённый гудок\n\nГудок «Микана» — **мяу**. Владелец закрепил это как обязательное требование\nдля переноса корабля в игру. Спецификация: source/sound_design.json.\nЗвуковой файл и игровая регистрация пока не реализованы.\n'
(ROOT/'README.md').write_text(readme,encoding='utf8')
selection={'project':'Mikan','revision':'MC01','owner_visual_approval':False,
 'authoritative_geometry':'source/calico_MC01.json','owner_request':'Calico ship from supplied sketch with a volumetric muzzle',
 'artwork':'Native Blender mesh and solid color materials; no generated illustration',
 'horn':{'sound':'meow','owner_approved':True,'required_for_game_integration':True},
 'runtime_registered':False,'physics_verified':False}
(ROOT/'source/selection.json').write_text(json.dumps(selection,indent=2)+'\n')
files=[ROOT/'README.md',ROOT/'assembly/calico_MC01.blend',ROOT/'exports/calico_MC01.glb',ROOT/'source/calico_MC01.json',ROOT/'source/selection.json',ROOT/'references/owner_calico_sketch.jpg']
files+=[ROOT/'source/sound_design.json']
files+=sorted((ROOT/'preview').glob('*.png'))
files += [ROOT/'reports/BLENDER_BUILD.json',ROOT/'reports/GEOMETRY_CHECK.json']
files += sorted((ROOT/'tools').glob('*.py'))
manifest={p.relative_to(ROOT).as_posix():{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(ROOT/'reports/DELIVERY_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
files += [ROOT/'reports/DELIVERY_MANIFEST.json']
path=ROOT/'exports/Mikan_MC01.zip'
with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as archive:
    for p in files:archive.write(p,p.relative_to(ROOT))
with zipfile.ZipFile(path) as archive:assert archive.testzip() is None
print(json.dumps({'package':str(path),'bytes':path.stat().st_size,'files':len(files),'runtime_installed':False}))
