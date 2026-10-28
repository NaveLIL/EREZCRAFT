"""Owner-approved slow harvesting and owner-requested closed-loop breathing design.
Only the design records change; the accepted visual JAR remains unchanged.
"""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'source/engineer_gameplay_proposal.json';d=json.loads(p.read_text(encoding='utf-8'))
d['design_revision']='D02_closed_breathing_and_realm_harvesting'
d['owner_decisions']={'slow_realm_harvesting':'Approved concept, initial numeric tuning below','closed_circuit_breathing':'Owner requested; adopted as the primary mask function','runtime_implementation':'Pending; no gameplay classes changed'}
h=d['hazards'];h.pop('filter',None)
h['breathing_loop']={
 'name':'Контур замкнутого дыхания','requires':['intact engineer helmet','intact engineer chestplate','loaded usable regenerative breathing cassette'],
 'reserve_seconds':600,'battery_independent':True,'mechanical_reserve_indicator_without_visor_power':True,
 'media':['underwater','explicit future nonrespirable atmosphere','future supported inhalation hazard'],
 'one_reserve_second_per_real_hazard_second':True,'no_drain_in_clean_breathable_air':True,
 'resource_meaning':'Stored breathing mixture and CO2 regeneration capacity; not a passive filter that creates air',
 'lava':'Breathing support does not cancel fire or lava damage. Heat protection requires a separate valid source such as Fire Resistance or Magen.',
 'future_atmosphere_integration':'Explicit nonrespirable-medium hook or adapter; never infer support for arbitrary third-party vacuum or gas systems',
 'no_global_poison_immunity':True,'no_thermal_or_contact_toxin_immunity':True,
 'no_free_air_from_equipping':'Preserve vanilla air budget across equip/unequip; reserve and clean-air recovery must not reset from item swaps',
 'regenerative_cassette_refill':'Recharge existing cassette in camp, return the same cassette and preserve unrelated data; exact replenishment chemistry remains design work'}
h['lower_sea_emergency_reduction']['requires']='four intact pieces with serviceable chemical lining; separate from breathing reserve'
h['lower_sea_emergency_reduction']['source']='chemical lining, never oxygen supply'
h['unlimited_underwater_air']=False
d['proposed_balance']['helmet_oxygen_bonus']=1
n=d['neurointerface'];n['hud']=[s.replace('cartridge and charge','breathing reserve, breathing seal and charge') for s in n['hud']]
power=n['power'];power['finite_in_every_dimension']=True;power['automatic_realm_power']=True
power['finite_means']='Finite cell capacity and finite harvesting rate, rather than Magen-style unconditional powered state inside the realm'
power['realm_harvesting']={
 'realm_predicate':'same intended realm family as existing AegisPower.realm; no change to Magen',
 'ambient_units_per_second':0.5,'exposed_surge_bonus_max_units_per_second':2.5,
 'surge_bonus_formula':'2.5 * clamp(native_buoyancy_intensity,0,1) only during eligible exposed SURGE',
 'maximum_total_units_per_second':3.0,
 'bonus_exclusions':['shelter','water or other fluid immersion','climbing','passenger','elytra flight'],
 'ambient_can_charge_with_visor_off':True,'outside_realm_units_per_second':0,
 'accounting':'Server tick accounting in fixed-point units, bounded by cell capacity; no offline or dimension-swap windfall',
 'initial_tuning_not_survival_verified':True}
power['example_continuous_rates']={'outside_hud_minutes':30,'outside_hud_nv_minutes':15,'calm_hud_minutes':60,'calm_hud_nv_minutes':20,'calm_visor_off_empty_to_full_minutes':60,'constant_max_surge_hud_net_units_per_second':2,'constant_max_surge_hud_nv_net_units_per_second':1,'note':'Examples exclude pulses and assume stated conditions stay constant; a whole tide phase is not guaranteed to last long enough for a full recharge'}
recipes=d['proposed_recipes'];recipes.pop('interstice:engineer_filter_cartridge',None)
recipes['interstice:engineer_breathing_cassette']={'count':1,'ingredients':{'minecraft:copper_ingot':2,'interstice:pure_vitriolite_lining':1,'minecraft:charcoal':1,'interstice:chemotrophic_fabric':1,'interstice:phosphorite_paste':1},'lore':'Regenerative breathing chemistry using the fictional materials of Interstice; not a real respirator recipe'}
d['enchantments']['mending_repairs_physical_armor_only']='Breathing mixture/regeneration reserve and battery charge are independent resources; Mending does not refill either'
d['progression']['ruin_role']=d['progression']['ruin_role'].replace('cartridges','breathing cassettes')
d['acceptance_before_gameplay_release']=[s for s in d['acceptance_before_gameplay_release'] if not s.startswith(('Filter only','At most one gas second','Finite power in every dimension'))]
d['acceptance_before_gameplay_release'] += [
 'Closed breathing works underwater only with valid helmet, chestplate and reserve; helmet visor remains independently usable',
 'Overlapping nonrespirable media consume at most one reserve second per second; clean breathable air consumes none',
 'Battery depletion does not disable mechanical breathing; mechanical reserve indication remains readable',
 'Equip swaps, reconnect, cassette swaps and changing medium never create free air or restore spent reserve',
 'Breathing cancels only supported suffocation/inhalation hazards; lava heat, contact toxin and injected poison remain distinct',
 'Finite-rate realm harvesting uses native phase/intensity and all eligibility exclusions; no charge bonus from standing in lava',
 'Fractional 0.5-unit charging survives save/load exactly; no overflow, negative charge, offline grant or dimension-swap grant',
 'Cassette and cell refills conserve actual items and preserve unrelated metadata; no duplication or free refills']
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

p=ROOT/'reports/GAMEPLAY_PROPOSAL.md';s=p.read_text(encoding='utf-8')
start=s.index('## Что даёт фильтр и защита');end=s.index('## Нейроинтерфейс',start)
s=s[:start]+'''## Контур замкнутого дыхания и защита

Главная функция маски — автономное дыхание, полезное уже под водой. Газозащита остаётся заделом под явно поддерживаемые будущие среды; наличие газовых обработчиков в коде не считается доказательством принятой опасности в игровом мире.

Контур требует исправных маски и пальто, где находится дыхательный блок, и установленной регенеративной кассеты. Кассета содержит запас дыхательной смеси и ресурс поглощения CO₂. Начальный запас —600 секунд в непригодной для дыхания среде. Под водой и в будущей явно помеченной безвоздушной атмосфере расходуется одна секунда ресурса в секунду; перекрывающиеся среды расход не умножают. В чистом пригодном воздухе контур не расходует кассету.

Это механическая система: разрядка визора не отключает дыхание. Остаток смеси и состояние герметизации остаются видимыми без питания нейроинтерфейса. Сама маска без пальто сохраняет визор и обычный бонус кислорода, но полного замкнутого дыхания не даёт. Снятие частей, смена кассеты или повторный вход не создают бесплатный воздух и не восстанавливают запас. Без кассеты или при разгерметизации действуют обычные правила среды.

Дыхание, нагрев и химическое повреждение разделены. Контур может поддерживать дыхание при погружении в лаву, но не отменяет урон лавы и огня. Для пребывания в лаве нужна отдельная защита от жара, например действующая Огнестойкость. Костюм инженера сам такой иммунитет не предоставляет. Верхнее токсичное море, укусы, введённый яд и ведьмины зелья не нейтрализуются наличием воздуха в маске. Совместимость с произвольными чужими газами и вакуумными измерениями потребует явного адаптера.

От укусов и плевков местных пауков предлагается6% снижения за часть,24% за комплект: слабее рифтстальных32%. Полный исправный костюм снижает контактный урон нижнего моря на60% за счёт химической подкладки, отдельно от дыхательного ресурса. Контакт продолжает наносить урон. Общий предел60% с местными зачарованиями сохраняется.

От прилива костюм бесплатно не освобождает. Полезная инженерная особенность — крепление существующего балластного пояса внутри пальто, освобождающее руки. Используется настоящий пояс с его износом, действующий коэффициент0,62 и существующие исключения для укрытия, жидкости, транспорта и элитр. Пояс в руке и в креплении не умножают защиту друг друга. При непрерывной активной нагрузке прежних240 единиц хватает на240 секунд; вне воздействия он не изнашивается.

У Магена остаются энергетическая герметизация, постоянный сканер сквозь стены на24 блока и неограниченная работа силовой системы в Междуморье.

'''+s[end:]
power_text='''Полевой аккумулятор —1800 единиц. В других мирах это30 минут базового HUD либо15 минут HUD с ночным зрением без импульсов. Импульсы дополнительно тратят заряд. Смена аккумулятора возвращает старый элемент; в лагере его можно перезарядить фосфоритной пастой и редстоуном.

Слабая самозарядка в Междуморье согласована владельцем. Предварительные числа: фон0,5 единицы/с; во время открытого активного прилива добавляется до2,5 единицы/с пропорционально штатной интенсивности. Максимальный сбор —3 единицы/с. Усиление не работает в укрытии, жидкости, на лестнице, в транспорте или при полёте на элитрах. Погружение в лаву не даёт усиления зарядки.

При выключенном визоре спокойное Междуморье зарядит пустую батарею за60 минут; при включённом HUD запас расходуется за60 минут, с ночным зрением —за20 минут. При постоянно максимальном открытом приливе HUD оставляет чистый прирост+2 единицы/с, HUD с ночным зрением —+1. Это расчёт при постоянных условиях, не обещание полной зарядки за одну фазу. За пределами Междуморья самозарядки нет. Ёмкость и скорость сбора конечны; режима безусловной запитанности Магена у инженера нет.

Числа являются начальными настройками для испытаний. Начисление идёт серверными игровыми тиками, без офлайн-зарядки, переполнения и подарка за переход между мирами. Разрядка отключает электронику, но сохраняет физическую броню и механическое дыхание при наличии смеси.

'''
s,n=re.subn(r'Полевой аккумулятор[^\n]*\n\n',power_text,s,count=1);assert n==1
s=s.replace('картриджа, заряда','дыхательной смеси, заряда').replace('фильтрация, приборы','автономное дыхание, приборы')
s,n=re.subn(r'^Аккумулятор:.*$', 'Аккумулятор:4 меди +2 редстоуна +2 фосфоритные пасты +1 серебряная проволока. Регенеративная дыхательная кассета:2 меди +1 чистая подкладка +1 древесный уголь +1 хемотрофная ткань +1 фосфоритная паста. Это игровая регенеративная химия вымышленных материалов Междуморья. Маска изготавливается без бесплатного запаса смеси; первую кассету и аккумулятор делают отдельно. Повторная заправка сохраняет саму кассету; точный рецепт её восстановления ещё предстоит выбрать.',s,count=1,flags=re.M);assert n==1
s=s.replace('с одним аккумулятором и одним картриджем','с одним аккумулятором и одной дыхательной кассетой').replace('| Медь | 4 |','| Медь | 6 |').replace('| Фосфоритная паста | 2 |','| Фосфоритная паста | 3 |')
s=s.replace('не заправляет картриджи и аккумуляторы','не восстанавливает дыхательную смесь и заряд аккумуляторов')
s=s.replace('серверные проверки источников газов и яда, расхода по секундам','серверные проверки дыхания и типов повреждения, расхода смеси и самозарядки по игровым тикам')
p.write_text(s,encoding='utf-8')
print('Design D02 saved; runtime and visual assets unchanged')
