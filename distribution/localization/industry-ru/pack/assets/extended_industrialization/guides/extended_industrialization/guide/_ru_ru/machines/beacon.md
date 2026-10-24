---
navigation:
  title: "Электрический маяк"
  icon: "electric_beacon"
  parent: extended_industrialization:machines.md
categories:
  - machines
item_ids:
  - extended_industrialization:electric_beacon
---
<a name="electric-beacon"></a>
# Электрический маяк

<RecipeFor id="extended_industrialization:electric_beacon" />

Электрический маяк расходует зелья и наделяет их эффектами всех игроков поблизости, потребляя 256 EU/t за каждый эффект. Время работы определяется самым коротким эффектом зелья и уменьшается вдвое. Обычно у зелья только один эффект: например, зелье скорости на 8 минут даст маяку 4 минуты работы с потреблением 256 EU/t. Зелье черепашьей мощи на 40 секунд даст лишь 20 секунд и потребует 512 EU/t, поскольку содержит два эффекта — замедление и сопротивление. Для непрерывной работы рекомендуется автоматизировать снабжение зельями через [зельеварню](brewery.md).

Радиус электрического маяка больше ванильного и зависит от числа слоёв под ним. Стальной корпус при подсчёте слоёв не учитывается.

| Слои | Радиус |
|------|--------|
| 1    | 30     |
| 2    | 50     |
| 3    | 70     |
| 4    | 90     |

<GameScene zoom="1.5" interactive={true} fullWidth={true}>
    <MultiblockShape controller="extended_industrialization:electric_beacon" />
    <MultiblockShape controller="extended_industrialization:electric_beacon" useBigShape={true} x="-9" y="2" z="-9" />
</GameScene>