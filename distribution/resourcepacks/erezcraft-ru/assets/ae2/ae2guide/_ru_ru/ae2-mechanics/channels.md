---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: Каналы
  icon: controller
---

<a name="channels"></a>

# Каналы

[Сети ME](me-network-connections.md) Applied Energistics 2 требуют
каналов для поддержки [устройств](devices.md), которые используют сетевое хранилище или другие сетевые
сервисы. Представьте себе каналы как USB-кабели ко всем вашим устройствам. Компьютер имеет ограниченное количество USB-портов и может поддерживать
ограниченное количество подключённых к нему устройств. Большинство машин, устройств полного блока и стандартных кабелей могут передавать
не более 8 каналов. Вы можете представить устройства полного блока и стандартные кабели как связку из 8 "проводов канала". Однако [плотные кабели](../items-blocks-machines/cables.md#dense-cable) могут поддерживать до
32 каналов. Единственные другие устройства, способные передавать 32 канала, это <ItemLink id="me_p2p_tunnel" />
и [Квантовый мост сети](../items-blocks-machines/quantum_bridge.md). Каждый раз, когда устройство использует канал, представьте, что вы отрываете один "провод USB" от
связки, что, очевидно, означает, что этот "провод" недоступен дальше по линии.

<GameScene zoom="7" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_demonstration_1.snbt" />

  <LineAnnotation color="#33ff33" from="1 .4 .7" to="2.4 .4 .7" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .7" to="2.4 .6 .7" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .6" to="2.6 .4 .6" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .6" to="2.6 .6 .6" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .6" to="2.6 .6 .6" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.4 .6 .7" to="2.4 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.4 .4 .7" to="2.4 .4 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .6 .6" to="2.6 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .4 .6" to="2.6 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.1 .6 1.5" to="2.4 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .4 1.5" to="2.9 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.6 .6 1.5" to="2.6 .9 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.4 .1 1.5" to="2.4 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1 .6 .4" to="3.5 .6 .4" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .4" to="3.5 .4 .4" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="3.5 .6 .4" to="3.5 .9 .4" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="3.5 .1 .4" to="3.5 .4 .4" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1 .6 .3" to="1.5 .6 .3" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .3" to="1.5 .4 .3" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1.5 .6 .3" to="1.5 .9 .3" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1.5 .1 .3" to="1.5 .4 .3" alwaysOnTop={true}/>

  <LineAnnotation color="#ff3333" from="3.5 .5 .5" to="5.5 .5 .5" alwaysOnTop={true}>
  Все 8 каналов в кабеле были использованы, поэтому накопитель не получает канал.\
  </LineAnnotation>

  <LineAnnotation color="#993333" from="1 .5 .5" to="1.25 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="1.5 .5 .5" to="1.75 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="2 .5 .5" to="2.25 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="2.5 .5 .5" to="2.75 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="3 .5 .5" to="3.25 .5 .5" alwaysOnTop={true}/>

  <DiamondAnnotation pos="3.6 0.5 0.5" color="#ff0000">
        Все 8 каналов в кабеле были использованы, поэтому накопитель не получает канал.
    </DiamondAnnotation>

  <IsometricCamera yaw="15" pitch="30" />
</GameScene>

Простой способ увидеть, как каналы используются и проходят через вашу сеть, — использовать [умные кабели](../items-blocks-machines/cables.md), которые будут отображать на себе пути и использование каналов.

Каналы будут потреблять 1⁄128 ae/t за каждый узел, который они проходят, это означает, что добавление <ItemLink id="controller" /> к
сети с 8 устройствами и более чем 96 узлами может фактически
уменьшить потребление энергии, потому что это изменяет способ распределения каналов.

Важно отметить, что **КАНАЛЫ НИКАК НЕ СВЯЗАНЫ С ЦВЕТОМ КАБЕЛЯ**, цвет кабеля только мешает кабелям соединяться.

<a name="channel-routing"></a>

## Маршрутизация каналов

При использовании <ItemLink id="controller" />,
каналы проходят через 3 этапа. Сначала они идут по кратчайшему пути через соседние машины к ближайшему [обычному кабелю](../items-blocks-machines/cables.md)
(стеклянному, покрытому или умному). Затем они идут по кратчайшему пути по этому обычному кабелю к ближайшему [плотному кабелю](../items-blocks-machines/cables.md)
(плотному или плотному умному). Затем они идут по кратчайшему пути по этому плотному кабелю к <ItemLink id="controller" />.
Если кратчайший путь уже заполнен, некоторые [устройства](devices.md) могут не получить необходимые каналы, используйте
цветные кабели, якоря кабелей и тоннели в своих интересах, чтобы убедиться, что ваши каналы идут по нужному пути.

Например, в этом случае некоторые накопители не получают каналы, потому что, хотя в кабелях достаточно ёмкости, каналы
стремятся идти по кратчайшему пути, перегружая некоторые кабели, в то время как другие остаются пустыми.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_path_length_issue.snbt" />

  <LineAnnotation color="#33ff33" from="3 .5 1.4" to="0.4 0.5 1.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 .5 1.4" to="0.4 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 0.5 3.6" to="1.4 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.4 0.5 3.6" to="1.4 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#33ff33" from="3 0.5 3.6" to="1.6 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.6 0.5 3.6" to="1.6 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#ff3333" from="3 .5 1.6" to="0.6 .5 1.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#ff3333" from="0.6 .5 1.6" to="0.6 .5 3.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#ff3333" from="0.6 .5 3.4" to="1.4 .5 3.4" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#ff3333" from="3 .5 3.4" to="1.6 .5 3.4" alwaysOnTop={true} thickness="0.05"/>

  <BoxAnnotation color="#dddddd" min="1.2 0.2 3.2" max="1.8 0.8 3.8" alwaysOnTop={true} thickness="0.05">
        Через это место пытаются пройти более 8 каналов, поэтому некоторые из них отсекаются.
  </BoxAnnotation>

  <IsometricCamera yaw="90" pitch="90" />

</GameScene>

Это можно исправить, более тщательно ограничив пути, по которым могут идти каналы. Сети должны быть древовидными (или кустистыми).
Петли и неоднозначные пути каналов должны быть сведены к минимуму.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_path_length_issue_fix.snbt" />

  <LineAnnotation color="#33ff33" from="3 .5 1.4" to="0.4 0.5 1.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 .5 1.4" to="0.4 0.5 5.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 0.5 5.6" to="1 0.5 5.6" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#33ff33" from="3 0.5 3.6" to="1.6 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.6 0.5 3.6" to="1.6 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <IsometricCamera yaw="90" pitch="90" />

</GameScene>

<a name="ad-hoc-networks"></a>

## Ад-хок сети

Сеть без <ItemLink id="controller" />
считается ад-хок, и может поддерживать до 8 устройств, использующих каналы.
Как только вы превысите 8 устройств, устройства в сети, использующие каналы, отключатся,
вы можете либо удалить устройства, либо добавить <ItemLink id="controller" />.

В отличие от сетей с контроллером, [умные кабели](../items-blocks-machines/cables.md) в ад-хок сетях будут показывать количество
используемых каналов по всей сети вместо количества каналов, проходящих через этот конкретный кабель.

При использовании ад-хок сетей каждое устройство будет
использовать 1 канал по всей сети, что очень отличается от того, как <ItemLink id="controller" /> распределяет каналы на основе
кратчайшего маршрута.

<a name="design"></a>

## Дизайн

Как упоминалось ранее в [маршрутизации каналов](channels.md#channel-routing), лучше всего проектировать вашу сеть в виде древовидной структуры, с плотными кабелями, ответвляющимися от контроллера, обычными кабелями,
ответвляющимися от плотных, и [устройствами](../ae2-mechanics/devices.md) в кластерах по 8 или меньше на обычных кабелях.

Вот пример того, что делать не следует:

Следуя путям каналов,

1. Сразу выходя из контроллера направо, мы сталкиваемся с узким местом из 8 каналов, потому что накопитель действует как обычный кабель.
Однако, поскольку мы не используем здесь умный кабель, мы не можем видеть, сколько каналов используется. Осталось 8 каналов.
2. Накопитель занимает канал.
Осталось 7 каналов.
3. 2 канала идут вверх к терминалам.
Осталось 5 каналов.
4. Продолжая направо, интерфейс занимает ещё один канал.
Осталось 4 канала.
5. 1 канал идёт вверх к поставщику шаблонов.
Осталось 3 канала.
6. Продолжая направо, 1 канал идёт вверх к шине импорта.
Осталось 2 канала.
7. Кластер поставщиков шаблонов, питающих сборщики, получает только 2 канала, поэтому 2 поставщика не получают каналов.

В конечном итоге ошибка заключается в узком месте каналов и необдуманном распределении каналов.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/bad_network_structure.snbt" />

<LineAnnotation color="#33ff33" from="6.5 .5 1.5" to="6 .5 1.5" alwaysOnTop={true} thickness="0.4">
  32 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="6 .5 1.5" to="5.5 .5 1.5" alwaysOnTop={true} thickness="0.2">
  8 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="5.5 1.5 1.5" alwaysOnTop={true} thickness="0.1">
  2 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="5.5 .3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 1.5 1.5" to="5.5 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 2.5 1.5" to="5.5 2.5 1.1" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="4.5 .5 1.5" alwaysOnTop={true} thickness="0.158">
  5 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="4.5 .3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="4.5 1.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="3.5 .5 1.5" alwaysOnTop={true} thickness="0.122">
  3 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 .5 1.5" to="3.5 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 2.5 1.5" to="3.7 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 .5 1.5" to="1.5 .5 1.5" alwaysOnTop={true} thickness="0.1">
  2 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="1.5 0.5 1.5" to="1.5 0.3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="1.5 0.5 1.5" to="0.5 0.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="0.5 0.5 1.5" to="0.5 0.5 0.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#ff3333" from="0.5 1.5 1.5" to="0.5 1.3 1.5" alwaysOnTop={true} thickness="0.071">
  нет каналов
</LineAnnotation>

<LineAnnotation color="#ff3333" from="1.5 1.5 0.5" to="1.5 1.3 0.5" alwaysOnTop={true} thickness="0.071">
  нет каналов
</LineAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

---

Пример правильной структуры:

<GameScene zoom="2.5" interactive={true}>
  <ImportStructure src="../assets/assemblies/treelike_network_structure.snbt" />

    <BoxAnnotation color="#dddddd" min="6.9 0 4.9" max="9.1 4 7.1" thickness="0.05">
        Поставщики шаблонов разделены на группы по 8.
    </BoxAnnotation>

    <BoxAnnotation color="#dddddd" min="5 4 4" max="8 5 5" thickness="0.05">
        Когда сходятся два обычных кабеля со всеми занятыми каналами, нужен плотный кабель.
    </BoxAnnotation>

    <BoxAnnotation color="#dddddd" min="5 0 13" max="8 1 14" thickness="0.05">
        Разные цвета кабелей не позволяют соседним кабелям соединяться.
    </BoxAnnotation>


  <IsometricCamera yaw="315" pitch="30" />
</GameScene>

<a name="channel-modes"></a>

## Режимы каналов

В AE2 10.0.0 для Minecraft 1.18 появились новые способы настройки каналов.
Режим задаётся параметром channels в общих настройках. Операторы могут изменить его командой `/ae2 channelmode <mode>`, а команда `/ae2 channelmode` показывает текущий режим. При изменении в игре все существующие сети перезапускаются и сразу переходят на новый режим.

Это возвращает и улучшает настройку из Minecraft 1.12, позволяя смягчить ограничения без полного удаления механики каналов.

Режимы, доступные в файле настроек и команде:

| Настройка    | Описание                                                                                                                                                                                                                               |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `default`  | Стандартный режим с описанными в руководстве лимитами кабелей и сетей без контроллера.                                                                                                                           |
| `x2`       | Все лимиты удвоены: 16 каналов у обычного кабеля, 64 у плотного, 16 у сети без контроллера.                                                                                                                           |
| `x3`       | Все лимиты утроены: 24 канала у обычного кабеля, 96 у плотного, 24 у сети без контроллера.                                                                                                                           |
| `x4`       | Все лимиты учетверены: 32 канала у обычного кабеля, 128 у плотного, 32 у сети без контроллера.                                                                                                                       |
| `infinite` | Все ограничения каналов сняты. Контроллеры всё равно *значительно* снижают энергопотребление сети. Умные кабели показывают только полное выключение (нет каналов) или полное включение (один канал и более). |
