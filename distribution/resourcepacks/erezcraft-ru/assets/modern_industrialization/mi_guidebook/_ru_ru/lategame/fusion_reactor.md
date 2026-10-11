---
navigation:
  title: "ТЕРМОЯДЕРНЫЙ СИНТЕЗ!"
  icon: "modern_industrialization:fusion_reactor"
  position: 302
  parent: modern_industrialization:endgame.md
item_ids:
  - modern_industrialization:fusion_reactor
  - modern_industrialization:plasma_turbine
---

<a name="fuuuuusion"></a>

# ТЕРМОЯДЕРНЫЙ СИНТЕЗ!

<a name="fusion-reactor"></a>

## Термоядерный реактор

<GameScene zoom="1" interactive={true} fullWidth={true}>
    <MultiblockShape controller="fusion_reactor" />
</GameScene>

Термоядерный реактор — вершина энергетики! Он объединяет дейтерий, тритий и/или гелий-3, получая гелиевую плазму — самое мощное топливо игры. Однако для запуска реакции требуется большое количество энергии.

<Recipe id="modern_industrialization:electric_age/machine/fusion_reactor_asbl" />

<a name="plasma-turbine"></a>

## Плазменная турбина

<GameScene zoom="2" interactive={true} fullWidth={true}>
    <MultiblockShape controller="plasma_turbine" />
</GameScene>

Плазменная турбина превращает гелиевую плазму в энергию: 100 kEU на милливедро. Максимальная мощность — примерно 1 MEU/тик.

<Recipe id="modern_industrialization:electric_age/machine/plasma_turbine_asbl" />
