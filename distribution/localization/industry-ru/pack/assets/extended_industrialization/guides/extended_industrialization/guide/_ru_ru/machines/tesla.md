---
navigation:
  title: "Технология Теслы"
  icon: "tesla_coil"
  parent: extended_industrialization:machines.md
categories:
  - machines
item_ids:
  - extended_industrialization:tesla_calibrator
  - extended_industrialization:tesla_handheld_receiver
  - extended_industrialization:tesla_interdimensional_upgrade
  - extended_industrialization:tesla_coil
  - extended_industrialization:tesla_receiver
  - extended_industrialization:lv_tesla_receiver_hatch
  - extended_industrialization:mv_tesla_receiver_hatch
  - extended_industrialization:hv_tesla_receiver_hatch
  - extended_industrialization:ev_tesla_receiver_hatch
  - extended_industrialization:superconductor_tesla_receiver_hatch
  - extended_industrialization:tesla_tower
  - extended_industrialization:aluminum_tesla_winding
  - extended_industrialization:annealed_copper_tesla_winding
  - extended_industrialization:copper_tesla_winding
  - extended_industrialization:electrum_tesla_winding
  - extended_industrialization:superconductor_tesla_winding
---
<a name="tesla"></a>
# Технология Теслы

Катушки и приёмники Теслы передают EU без проводов, но с дополнительными затратами. В сети Теслы может быть только один передатчик — катушка или башня, а встроенного ограничения числа приёмников нет. У каждого передатчика свои дальность и постоянный расход энергии.

<a name="tesla-calibrator"></a>
## Калибратор Теслы

Чтобы связать передатчик с приёмниками, возьмите калибратор Теслы и нажмите **<KeyBind id="key.sneak" />** + **<KeyBind id="key.use" />** на передатчике. Затем нажмите **<KeyBind id="key.use" />** калибратором по каждому нужному приёмнику.

<RecipeFor id="extended_industrialization:tesla_calibrator" />

<a name="tesla-transmitters"></a>
## Передатчики Теслы

Передатчик Теслы — источник энергии каждой сети Теслы.

Передатчик и приёмник должны работать с одним и тем же напряжением. Например, катушка Теслы с продвинутым корпусом машины не передаёт энергию приёмнику без корпуса, но передаёт приёмнику с таким же продвинутым корпусом.

Постоянный расход EU/t катушки Теслы зависит от напряжения установленного корпуса либо его отсутствия.

<RecipeFor id="extended_industrialization:tesla_coil" />

Напряжение башни Теслы определяется установленными люками ввода энергии.

Постоянный расход EU/t, предел передачи и дальность башни Теслы зависят от обмоток. Подробности указаны в подсказке каждой обмотки.

<Row>
	<GameScene zoom="0.75" interactive={true} fullWidth={false}>
		<MultiblockShape controller="extended_industrialization:tesla_tower" />
	</GameScene>
	<RecipeFor id="extended_industrialization:tesla_tower" />
</Row>

<a name="tesla-receivers"></a>
## Приёмники Теслы

Приёмники Теслы — конечные точки передачи энергии от передатчика.

Приёмник Теслы накапливает полученную энергию и выдаёт её через выходную сторону. Как и у других источников энергии, её можно забирать кабелями.

<RecipeFor id="extended_industrialization:tesla_receiver" />

Приёмный люк Теслы принимает энергию как обычный приёмник, но одновременно служит люком ввода энергии мультиблока. Вместо отдельного приёмника и люка достаточно одного блока.

<RecipeFor id="extended_industrialization:lv_tesla_receiver_hatch" />