---
navigation:
  title: "Объединитель машин"
  icon: "machine_chainer"
  parent: extended_industrialization:machines.md
categories:
  - machines
item_ids:
  - extended_industrialization:machine_chainer
  - extended_industrialization:machine_chainer_relay
---
<a name="machine-chainer"></a>
# Объединитель машин

<GameScene zoom="2" interactive={true} fullWidth={true}>
	<ImportStructure src="machine_chainer_example.nbt" />
	<IsometricCamera yaw="180" pitch="30" />
</GameScene>

<RecipeFor id="extended_industrialization:machine_chainer" />

Объединитель машин связывает машины, бочки и другие инвентарные блоки с тегом `#extended_industrialization:machine_chainer/linkable` по прямой на расстоянии до 64 блоков. Их инвентари становятся общим инвентарём объединителя. Его можно направить в любую сторону, в том числе вверх или вниз.

Объединитель передаёт предметы, жидкости и энергию! Однако передача энергии ограничена утроенной пропускной способностью одного кабеля соответствующего уровня. Энергия связанных машин с другим напряжением недоступна.

<a name="machine-chainer-relay"></a>
## Ретранслятор объединителя машин

Ретранслятор — блок, который объединитель может включить в цепь, но собственного инвентаря у него нет. Он заполняет промежутки между связанными машинами, избавляя от необходимости ставить туда лишнюю машину.

<RecipeFor id="extended_industrialization:machine_chainer_relay" />