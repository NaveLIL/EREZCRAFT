from pathlib import Path
import json,zipfile,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
check=json.loads((ROOT/'reports/MODEL_CHECK.json').read_text());assert check['passed']
assert check['assembly_sha256']==hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest()
assert check['geometry_sha256']==hashlib.sha256((ROOT/'source/eitan_E01.json').read_bytes()).hexdigest()
motion=json.loads((ROOT/'reports/MOTION_RENDER.json').read_text());assert motion['source_blend_sha256']==hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest()
frames=[Image.open(ROOT/f'preview/motion/{frame:03}.png').convert('RGB') for frame in motion['frames']]
durations=[650 if frame in [1,41,61,81,101] else 130 for frame in motion['frames']]
frames[0].save(ROOT/'preview/eitan_E01_opening.gif',save_all=True,append_images=frames[1:],duration=durations,loop=0,optimize=False)
README='''# Eitan E01-R3 — размерная модель для Minecraft

Восьмиколёсный БТР с проработанным отсеком экипажа и десанта.
Рабочий масштаб: 1 м = 1 блок. Номинальная основа — 8 × 3 × 3 м.
Измеренный закрытый габарит без гибких антенн — около 8,01 × 3,00 × 3,00 м.
Точные заводские чертежи не использовались: подтверждённые сведения, оценки
по фото и игровые адаптации разделены в docs/ASSUMPTIONS.md и source/sources.json.

## Посмотреть

- preview/eitan_E01_hero.png — внешний вид.
- preview/eitan_E01_rear_detail.png — выступающие кормовые борта, ниша аппарели и нижние узлы крепления.
- preview/eitan_E01_boarding.png — открытые аппарель и люки.
- preview/eitan_E01_interior.png — десантный отсек с кормы.
- preview/eitan_E01_cutaway.png — места экипажа и внутреннее устройство.
- preview/eitan_E01_door.png — дверь, открываемая отдельно от аппарели.
- preview/eitan_E01_opening.gif — движение той же модели Blender.
- assembly/eitan_E01.blend — редактируемая сцена и временная шкала механизмов.
- exports/eitan_E01_closed.glb и eitan_E01_boarding.glb — переносимые модели.

## Что разработано

Восемь колёс с раздельными опорами, протектором и видимыми элементами подвески.
Скошенный нос и навесные бортовые панели. Кормовая аппарель на нижней оси,
отдельная дверь внутри неё, два следящих телескопических цилиндра. Четыре
крышки над тремя проёмами крыши. Девять складных сидений десанта, три места
экипажа: водитель, командир, оператор. Есть панели приборов, экраны,
вентиляционные каналы, поручни и оборудование отсека.

Корма переработана по замечаниям владельца: выступающие высокие боковины,
утопленная примерно на 0,59 м рамка аппарели, цельные нижние секции и буксировочные
серьги с основаниями, вилками и пальцами. При закрытии остаются тонкие стыки;
чёрные прямоугольники от перекрывавшихся плоскостей прежней рамки устранены.
Глубина ниши и детали креплений оценены по фото, а не заводскому чертежу.

Задний выход и люки сделаны с реальными отверстиями в геометрии. Аппарель
не наклеена поверх закрытой стены. Кинематика — аналитические оси и нативные
драйверы Blender. Углы открытия и геометрия приводов относятся к игровой
адаптации; они не объявляются точной копией неизвестных заводских механизмов.

В tools/mechanism_state.py есть исполняемый контракт команд: дверь и аппарель
исключают открытие друг друга, задний выход открывается только на остановке.
Это проектное правило будущего игрового адаптера, не модификация Minecraft.

## Проверки и готовность

Проверены оценённые сетки, габариты, 12 мест/8 колёс, настоящие проёмы,
подъём девяти складных подушек и обратное чтение обоих GLB.
Для аппарели рассчитан непрерывный нижний габарит при вращении над плоским
грунтом. Отдельно проверены поверхности рамки/двери относительно кормовой ниши
в перечисленных положениях. Штоки проверены в перечисленных в отчёте положениях; это не расчёт
гидравлических усилий и не полное доказательство отсутствия всех столкновений.
Подробности: reports/MODEL_CHECK.json и отчёт toolkit уровня concept.

## Что ещё нужно для ездящего броневика

E01 **не зарегистрирован в Minecraft и ещё не ездит**. Требуются адаптер
дробных центров колёс и радиуса шины для Offroad, привод/руление, настройка
игровой массы и подвески, взаимодействия и коллизии створок, пассажиры,
сетевое сохранение и проверка на отдельном клиенте/сервере с DH.
План интерфейсов: source/game_integration.json.

Внутри принят тесный объём: примерно 1,60 м свободной высоты в проходе.
Для игрока планируются приседание и посадка в кресло. Полный рост 1,8 м
не помещается; кузов ради этого не увеличивался.
Бронезащита, вооружение, APS, силовые расчёты и изготовление не реализованы.
Профиль FDM в project.json — неиспользуемый шаблон toolkit, не план производства.
Игровые миры, действующая игра и сервер этой разработкой не менялись.

Внешние фотографии служат только референсами; их текстуры в модели не используются.
Ссылки и авторство сохранены в источниках, внешние фотографии в архив не включены.
'''
(ROOT/'README.md').write_text(README,encoding='utf8')
files=[ROOT/'README.md',ROOT/'project.json']
for directory,patterns in [('source',['*.json']),('tools',['*.py']),('docs',['*.md','*.csv']),('assembly',['*.blend']),('exports',['*.glb']),('preview',['eitan_E01_*.png','eitan_E01_opening.gif']),('reports',['MODEL_CHECK.json','BLENDER_BUILD.json','MOTION_RENDER.json','concept.json','concept.html'])]:
    for pattern in patterns:files.extend(sorted((ROOT/directory).glob(pattern)))
files.extend(sorted((ROOT/'verification/evidence').glob('*.json')) if (ROOT/'verification/evidence').exists() else [])
manifest={p.relative_to(ROOT).as_posix():{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(ROOT/'reports/DELIVERY_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
files.append(ROOT/'reports/DELIVERY_MANIFEST.json')
out=ROOT/'exports/Eitan_E01_review.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for p in files:z.write(p,p.relative_to(ROOT))
with zipfile.ZipFile(out) as z:assert z.testzip() is None
print(json.dumps({'package':str(out),'files':len(files),'bytes':out.stat().st_size,'runtime_registered':False}))
