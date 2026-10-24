---
navigation:
  title: "Ферма"
  icon: "steam_farmer"
  parent: extended_industrialization:machines.md
categories:
  - machines
item_ids:
  - extended_industrialization:steam_farmer
  - extended_industrialization:electric_farmer
---
<a name="farmer"></a>
# Ферма

<Row>
	<RecipeFor id="extended_industrialization:steam_farmer" />
	<RecipeFor id="extended_industrialization:electric_farmer" />
</Row>

Ферма не выполняет обычные рецепты машин. Вместо этого она вспахивает и увлажняет почву, сажает, удобряет и собирает культуры, растения и деревья. Необязательно использовать все задачи: некоторые лучше отключать. Например, при выращивании деревьев вспашка и увлажнение почвы не нужны.

Без достаточного питания ферма не работает. Паровой ферме нужен пар с энергетическим эквивалентом 32 EU/t, а электрической — 64 EU/t.

Землю в конструкции фермы можно заменить любым блоком с тегом `#extended_industrialization:farmer_dirt`.

<a name="tasks"></a>
## Задачи

<a name="tilling-soil"></a>
### Вспашка почвы

Если эта задача включена в настройках формы мультиблока, блоки земли в его составе превращаются в грядки, как при использовании мотыги.

<a name="hydrating-soil"></a>
### Увлажнение почвы

Если через входной люк подаётся вода, ферма поддерживает все грядки увлажнёнными без размещения воды рядом с ними.

<a name="planting"></a>
### Посадка

Получая посадочный материал через входной люк, ферма высаживает его на подходящие блоки в рабочей области. Высаживаются только предметы с тегом `#extended_industrialization:farmer_plantable`.

<a name="fertilizing"></a>
### Удобрение

Удобрение доступно только электрической ферме. При подаче подходящего жидкого удобрения через входной люк (варианты показаны в EMI) она воздействует на растения в рабочей области подобно костной муке. Эффект работает и на растениях, к которым обычная костная мука неприменима, например кактусах и сахарном тростнике.

<a name="harvesting"></a>
### Сбор урожая

Когда растение полностью созрело — например, выросла пшеница или саженец превратился в дерево — и в выходном люке достаточно места, ферма разрушает его и забирает добычу.

Предметы с тегом `#extended_industrialization:farmer_voidable` имеют низкий приоритет хранения. Если для них нет места, при сборе они уничтожаются. Например, это <ItemLink id="minecraft:stick" /> и <ItemLink id="minecraft:apple" />: можно закрепить выходные слоты только за брёвнами и саженцами, не заботясь о хранении побочных предметов.

<a name="steam-farmer"></a>
## Паровая ферма

<GameScene zoom="1" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:steam_farmer" />
    <MultiblockShape controller="extended_industrialization:steam_farmer" useBigShape={true} x="-10" z="-10" />
</GameScene>

<a name="electric-farmer"></a>
## Электрическая ферма

<GameScene zoom="1" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:electric_farmer" />
    <MultiblockShape controller="extended_industrialization:electric_farmer" useBigShape={true} x="-12" z="-12" />
</GameScene>