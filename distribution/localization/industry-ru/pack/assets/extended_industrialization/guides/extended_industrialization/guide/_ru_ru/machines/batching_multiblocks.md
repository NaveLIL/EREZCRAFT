---
navigation:
  title: "Мультиблоки пакетной обработки"
  icon: "processing_array"
  parent: extended_industrialization:machines.md
categories:
  - machines
item_ids:
  - extended_industrialization:large_steam_furnace
  - extended_industrialization:large_steam_macerator
  - extended_industrialization:large_electric_furnace
  - extended_industrialization:large_electric_macerator
  - extended_industrialization:processing_array
---
<a name="batching-multiblocks"></a>
# Мультиблоки пакетной обработки

Некоторые мультиблоки работают как обычные машины своего типа, но обрабатывают несколько одинаковых наборов ингредиентов за раз. Если мультиблок поддерживает пакеты размером Y, он может одновременно потребить от 1x до Yx обычного количества ингредиентов и выдать соответствующее количество результатов. Потребление EU/t также умножается на число одновременно обрабатываемых наборов.

Допустимый размер пакета зависит от мультиблока. Точные значения указаны в подсказке его предмета.

Как и другие машины, такой мультиблок не может выполнять более одного разных рецептов одновременно.

<a name="large-furnace"></a>
## Большая печь

Размер пакета большой электрической печи зависит от установленных нагревательных катушек — аналогично электрической доменной печи. Число наборов, поддерживаемое каждой катушкой, указано в её подсказке.

<Row>
	<RecipeFor id="extended_industrialization:large_steam_furnace" />
	<RecipeFor id="extended_industrialization:large_electric_furnace" />
</Row>

<GameScene zoom="2" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:large_steam_furnace" />
    <MultiblockShape controller="extended_industrialization:large_electric_furnace" x="-6" y="-1" z="-6" />
</GameScene>

<a name="large-macerator"></a>
## Большой измельчитель

<Row>
	<RecipeFor id="extended_industrialization:large_steam_macerator" />
	<RecipeFor id="extended_industrialization:large_electric_macerator" />
</Row>

<GameScene zoom="2" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:large_steam_macerator" />
    <MultiblockShape controller="extended_industrialization:large_electric_macerator" x="-6" z="-6" />
</GameScene>

<a name="processing-array"></a>
## Обрабатывающий массив

Обрабатывающий массив выполняет пакетами рецепты электрической машины, помещённой в его интерфейс, если сама эта машина не является мультиблоком. Размер пакета ограничен размером массива и количеством установленных в него машин.

<RecipeFor id="extended_industrialization:processing_array" />

<GameScene zoom="2" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:processing_array" />
    <MultiblockShape controller="extended_industrialization:processing_array" useBigShape={true} x="-6" z="-8" />
</GameScene>