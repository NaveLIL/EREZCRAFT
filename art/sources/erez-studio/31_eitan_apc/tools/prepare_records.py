from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
sources=[
 {'id':'OWNER-PHOTOS','kind':'owner supplied reference photographs','locator':'references/owner_front.jpg; references/owner_rear.jpg','supports':['visible silhouette','8x8 layout','rear ramp and corner pods'],'limits':'Perspective images do not give precise dimensions or all hinge axes.'},
 {'id':'NOMINAL-OVERVIEW','kind':'owner-linked secondary overview used as working constraint','url':'https://en.wikipedia.org/wiki/Eitan_AFV','locator':'Specifications: Length/Width/Height','supports':['8.0m nominal length','3.0m nominal width','3.0m nominal height'],'limits':'Rounded public values; height datum and variant boundaries unspecified; not manufacturer dimensional drawings.'},
 {'id':'IMOD-2023','kind':'primary developer announcement','url':'https://mod.gov.il/en/press-releases/press-room/delivery-of-first-eitan-armored-personnel-carriers-apc-to-the-israel-defense-forces-nahal-infantry-brigade','date':'2023-05-28','locator':'Eitan APC - Background Information','supports':['eight wheels','twelve personnel','camera/monitor based awareness'],'limits':'No engineering section drawing or detailed dimensions.'},
 {'id':'FIRSTHAND-TOUR','kind':'firsthand vehicle tour and trainer interviews','url':'https://breakingdefense.com/2023/10/inside-israel-defense-forces-training-on-more-versatile-eitan-apc/','date':'2023-10-04','locator':'Interior tour and twelve-person seating paragraphs','supports':['driver, commander, gunner and nine troops','rear exit ramp','crew displays and protected roof viewing'],'limits':'Does not establish exact chair/hatch coordinates.'},
 {'id':'INTERIOR-PHOTO','kind':'firsthand press photograph','url':'https://www.ynetnews.com/magazine/article/rkqtpjznle','locator':'The inside of Eitan, Photo: Nir Ben Zaken; references/interior_driver.jpg','supports':['perforated folding troop seat shells','inward facing side rows','central aisle','service channels','front crew consoles'],'limits':'Image-based interpretation only; not every one of the twelve seats is visible.'},
 {'id':'EARLY-INTERIOR','kind':'secondary-hosted early video still used for visual corroboration only','url':'https://armor-il.blogspot.com/2018/01/eitan-and-new-years-update.html','locator':'references/early_troop_reference.png','supports':['seat shell visual style','extinguishers and front service equipment'],'limits':'Early configuration; no exact numerical claim taken from this source.'},
 {'id':'OFFROAD-BINARY','kind':'installed primary software artifact','locator':'D:/repos/Minecraft/EREZCRAFT/docs/CREATE_OFFROAD_AUDIT.md','supports':['wheel physics backend exists','radius is a TireLike component','redstone steering and braking','Sable force application'],'limits':'No eight-wheel chassis test in this project; block-grid physical contacts require integration work.'}]
(ROOT/'source/sources.json').write_text(json.dumps({'access_date':'2026-10-29','sources':sources},indent=2)+'\n')
parameters=json.loads((ROOT/'source/parameters.json').read_text())
integration={'status':'R4_stationary_native_preview_adapter;_Offroad_gameplay_pending','preview_registry_flag':'interstice.eitanPreview','minecraft_source':'D:/repos/Minecraft/EREZCRAFT/src/main/java/pro/erez/interstice/vehicle','scale':'1 m = 1 block','body_dimensions_m':[8,3,3],
 'backend':{'create':'6.0.10','offroad':'1.3.2','sable':'2.0.6'},
 'wheel_radius_m':.68,'wheel_centres_m':[[side*1.26,y,.68] for side in [-1,1] for y in [-2.68,-.92,.84,2.60]],
 'mechanisms':{'ramp':{'group':'RAMP','axis':'X','pivot_m':parameters['ramp_hinge_m'],'open_deg':-parameters['ramp_open_deg']},'door':'RAMP_DOOR; Z axis; opens +95 deg only with ramp up','roof':['DRIVER_HATCH','COMMANDER_HATCH','TROOP_HATCH_L','TROOP_HATCH_R'],'seat_pans':9},
 'state_contract':'tools/mechanism_state.py','occupancy':{'driver':1,'commander':1,'station_operator':1,'troops':9},
 'access':{'standing_player_height_m':1.8,'crouched_player_height_m':1.5,'interior_nominal_clear_height_m':1.62,'policy':'Crouch through aisle/rear opening, then interact to occupy seats; no body enlargement.'},
 'required_work':['Fractional wheel-centre adapter: stock Offroad contact points are block-centred; visual tyre offset alone is not proven to move physics.', 'Custom .68m radius tyre using TireLike and matching render geometry.', 'Create torque path and two-front-axle steering controls; tune mass/suspension in a separate owned world.', 'Mechanism state synchronization, changing collision shapes, seat passengers and dismount locations.', 'Cold save/reload and dedicated multiplayer checks with DH.'],
 'not_implemented':['production vehicle registration','driving','independent animated gameplay mechanisms','seat passengers','weapons','armour simulation','APS']}
# Preserve the newer runtime record when regenerating historical concept paperwork.
existing=ROOT/'source/game_integration.json'
if existing.exists():
 current=json.loads(existing.read_text())
 if current.get('revision','').startswith('E01-R') and int(current['revision'].split('-R')[-1])>=7:integration=current
existing.write_text(json.dumps(integration,indent=2)+'\n')
assumptions='''# Основания и адаптации E01

Модель для Minecraft, единицы Blender — метры. Это не конструкторская документация
на настоящую бронемашину. Авторитетная геометрия — source/eitan_E01.json.

## Что взято из открытых данных

Восемь колёс и вместимость 12 человек подтверждены разработчиком.
Состав 3 члена экипажа + 9 десантников и кормовой выход через аппарель описаны
в репортаже с личным осмотром машины. Белые перфорированные складные кресла,
боковые ряды, центральный проход и служебные каналы видны на фотографии интерьера.
Полный реестр ссылок и ограничений: source/sources.json.

## Рабочие размеры, не выдаваемые за заводской чертёж

- Номинал 8 × 3 × 3 м — округлённые публичные габариты из присланной страницы.
  Для модели принят габарит с крышей/компактным дистанционным модулем, без гибких антенн.
- Радиус шин 0,68 м, центры осей, углы бронепанелей и толщина графической оболочки
  восстановлены по фотографиям/скомпонованы для игры. Толщины не означают реальную бронезащиту.
- Размеры внутреннего прохода, кресел, органов управления, проёмов и створок — адаптации.
  Девять мест десанта расположены 5+4; точная схема серийной машины не объявляется известной.
- Аппарель вращается на нижней поперечной оси, имеет отдельную дверь и условные
  телескопические приводы. Угол до земли рассчитан по выбранной геометрии модели;
  это не опубликованное значение оригинального привода.
- Направления/углы четырёх крышек люков и складных подушек выбраны для понятной
  кинематической модели. Все исходные шарниры, замки и заводские приводы не установлены.
- Передний правый отсек двигателя, места экипажа и экраны представлены компоновочно.
  Монтажные размеры реального силового блока неизвестны.

## Игровые решения

Внутри сохранён тесный объём машины 1:1. Игрок 1,8 м не получает обещания ходить
в полный рост: предусмотрены приседание и посадка в кресла. Порог/подвеску и контакт
шин с грунтом потребуется настроить на настоящем шасси Offroad.
Взаимная блокировка двери и аппарели, запрет открывать задний выход на ходу —
проектное игровое правило, не заявленная копия штатной системы Eitan.

Физические нагрузки, прочность, ресурс, гидравлические усилия, бронезащита и работа
вооружения не рассчитывались. Анимация показывает геометрию, а не динамические испытания.
'''
assumptions+='''\n## Коррекция кормы E01-R3 по замечаниям владельца\n\nВысокие гранёные объёмы продолжают борта и выступают назад по сторонам аппарели.\nОсь аппарели утоплена на 0,70 м от задних концов этих объёмов; внешняя плоскость\nеё закрытой рамки — примерно на 0,59 м. Значения выбраны по перспективному фото,\nа не взяты с заводского чертежа. Нижние секции корпуса, крепления буксировочных\nсерёг, неподвижные стойки проёма и тонкие боковые уплотнения — адаптации.\n\nРамка аппарели является одной сеткой вокруг отдельной двери. Перекрывающиеся\nплоские поверхности прежней рамки, создававшие чёрные углы на рендере, удалены.\nПосле переноса оси изменён край пола, чтобы аппарель не задевала порог.\nПроверки пересечений с кормовой нишей выполняются в перечисленных положениях;\nэто не доказательство непрерывного отсутствия всех столкновений.\n'''
assumptions+='''\n## Исправление корпуса и люков E01-R4\n\nПередние боковины используют поперечный наклон основных бортов и сужаются к носу.\nИх поверхности состыкованы без прежнего наложения в одной плоскости. Верхняя\nплощадка продлена вперёд под командирский люк, водительский люк перемещён целиком\nс проёмом, петлёй, уплотнением и перископами. Десантные крышки подняты над крышей\nдля свободного начала хода; под центральным швом есть перекрытие на левой створке.\nЭто игровые геометрические решения, не восстановленные заводские размеры.\n\nИгровой экспорт сохраняет рисунок сетки и авторскую палитру. Коллизии приближены\nконсервативными ячейками 1/16 блока; точность отображения и свободный проход\nпроверяются отдельно. Вершины ближе 0,05 мм к целой плоскости блока привязываются\nк этой плоскости; фактическая максимальная правка 0,0265 мм. Четыре проверочные\nпозы не являются контроллером игровой анимации или доказательством работы Offroad.\n'''
(ROOT/'docs/ASSUMPTIONS.md').write_text(assumptions,encoding='utf8')
instructions='''# Просмотр и управление моделью

Открыть assembly/eitan_E01.blend. Кадры: 1 — закрыта; 41 — аппарель и люки открыты;
61 — закрыта; 81 — отдельная дверь; 101 — закрыта; 121 — сложенные сиденья и поворот
передних колёс; 161 — закрыта. Гидроцилиндры следуют за аппарелью нативными драйверами.
Скрыть ROOF и ANTENNAS для осмотра экипажа/десанта. Это графическая разборка.

exports/eitan_E01_closed.glb и eitan_E01_boarding.glb — статические варианты для переноса.
Все детали подвижных узлов сгруппированы относительно осей шарниров.
Рендеры preview являются изображениями этой сцены Blender. Отдельный игровой\n+адаптер EitanPreview в репозитории Minecraft включается только специальным флагом;\n+это неподвижная сцена осмотра, не управляемая машина.

Порядок воспроизведения: tools/build_design.py; Blender --background --python
tools/build_scene.py; tools/verify_roof_fit.py и tools/verify_model.py в Blender;\n+tools/export_minecraft.py в Blender; tools/mechanism_state.py. Перенос в Minecraft:\n+tools/ImportEitanModel.py и tools/VerifyEitan.ps1 в репозитории EREZCRAFT.
Затем инженерный record/report с входом verification/model-inspection.json.
Производство и испытания настоящей бронемашины к этому проекту не относятся.
'''
(ROOT/'docs/assembly.md').write_text(instructions,encoding='utf8')
m=json.loads((ROOT/'project.json').read_text());m['revision']=json.loads((ROOT/'source/eitan_E01.json').read_text())['revision'];m['purpose']='1:1 nominal Eitan game model, documented estimates, interior and prescribed opening kinematics. No real manufacturing or operational vehicle certification.'
m['manufacturing'].update(applicable=False,rationale='Virtual game asset only. Default unqualified template profile is not used to assert any manufacturing process.')
req=[]
def requirement(id,text,metric,unit,operator,value=None,lower=None,upper=None):
    acceptance={'metric':metric,'unit':unit,'operator':operator}
    if value is not None:acceptance['value']=value
    if lower is not None:acceptance.update(lower=lower,upper=upper)
    req.append({'id':id,'text':text,'method':'inspection','required_for':['concept'],'acceptance':acceptance})
requirement('DIM-L','Nominal model length 8000mm, excluding deployed mechanisms','length_mm','mm','between',lower=7975,upper=8025)
requirement('DIM-W','Nominal model width 3000mm','width_mm','mm','between',lower=2975,upper=3025)
requirement('DIM-H','Nominal height 3000mm excluding flexible antennas','height_mm','mm','between',lower=2975,upper=3025)
requirement('SEATS','Twelve distinct seating positions','seats','count','equals',12)
requirement('WHEELS','Eight wheel hub pivots','wheels','count','equals',8)
requirement('RAMP-GROUND','Ramp shell remains above ground over the prescribed continuous rotation','ramp_continuous_min_z_mm','mm','min',5)
requirement('SEAT-FOLD','All nine folding pans rise in stowed pose','folded_pans_rising','count','equals',9)
requirement('STATE','Interaction contract passes mutual-exclusion and stationary-opening cases','state_contract_passed','bool','equals',True)
requirement('EXPORT','Both GLB states reopen as geometry','glb_round_trip_passed','bool','equals',True)
requirement('REAR-RECESS','Photo-informed projecting aft shoulders: ramp outer face recessed at least 550mm; design estimate, not manufacturer dimension','rear_recess_mm','mm','min',550)
requirement('REAR-RISE','Raised faceted aft shoulders above nominal 2600mm roof; photo-informed design estimate','rear_shoulder_rise_mm','mm','min',140)
requirement('REAR-MOTION','No triangle-surface intersections with the changed rear bay at prescribed ramp/door sample poses; no continuous collision claim','rear_sampled_surface_intersections','count','equals',0)
requirement('ROOF-FIT','Supported hatch rims, unobstructed apertures, sealed centre seam and sampled lid-to-roof clearance','roof_fit_passed','bool','equals',True)
requirement('BOW-JOINT','Adjacent bow/main patches agree in transverse outer plane within 1mm virtual mesh comparison','bow_main_side_offset_mm','mm','max',1)
req.extend(r for r in m.get('requirements',[]) if r['id'] not in {x['id'] for x in req})
m['requirements']=req
m['assumptions']=[{'id':'PUBLIC-GEOMETRY','text':'Nominal dimensions and image-based interior assumptions are bounded in docs/ASSUMPTIONS.md.','state':'accepted','rationale':'Owner explicitly authorized adapting unknown information; numerical estimates are not labelled manufacturer dimensions.','required_for':['concept']}]
m['risks']=[{'id':'GAME-ADAPTER','description':'Runtime acceptance is recorded separately from the geometric concept gate.','state':'accepted','rationale':'See source/game_integration.json and the linked native driving report; a concept gate does not certify production runtime or physical vehicle safety.','required_for':['concept']}]
artifact_specs=[('PARAMETERS','source/parameters.json','source'),('GEOMETRY','source/eitan_E01.json','source'),('SOURCES','source/sources.json','source'),('ADAPTER','source/game_integration.json','source'),('ASSUMPTIONS','docs/ASSUMPTIONS.md','source'),('DESIGN','tools/build_design.py','source'),('BUILD','tools/build_scene.py','source'),('VERIFY','tools/verify_model.py','source'),('STATE-CONTRACT','tools/mechanism_state.py','source'),('ASSEMBLY','assembly/eitan_E01.blend','assembly'),('BOM','docs/bom.csv','bom'),('INSTRUCTIONS','docs/assembly.md','instructions'),('GLB-CLOSED','exports/eitan_E01_closed.glb','export'),('GLB-BOARDING','exports/eitan_E01_boarding.glb','export'),('INSPECTION','reports/MODEL_CHECK.json','report')]
artifact_specs.extend([('ROOF-VERIFY','tools/verify_roof_fit.py','source'),('MC-EXPORTER','tools/export_minecraft.py','source'),('RECORDS','tools/prepare_records.py','source'),('ROOF-CHECK','reports/ROOF_FIT.json','report'),('MC-EXPORT','reports/MINECRAFT_EXPORT.json','report')])
extra_artifacts=[a for a in m.get('artifacts',[]) if a['id'] not in {x[0] for x in artifact_specs}]
m['artifacts']=[{'id':id,'path':path,'role':role,'required_for':['concept']} for id,path,role in artifact_specs]+extra_artifacts;m['calculations']=[]
(ROOT/'project.json').write_text(json.dumps(m,indent=2)+'\n')
print(json.dumps({'records_prepared':True,'requirements':len(req),'artifacts':len(m['artifacts'])}))
