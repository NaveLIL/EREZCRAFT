# Свободные генераторы рельефа: отбор для Междуморья

Проверка08.10.2026 для Minecraft1.21.1, NeoForge21.1.252, Java21. Результат — выбор источника алгоритмов и схема прототипа; интеграция/сборка кандидатов ещё не выполнены. Исходники скачиваются только в игнорируемый `research`, пользовательские миры для сравнения не используются.

## Основной кандидат — FreeTerraForged

Проверенная ветка `1.21.1`, commit `c5d322f5e44539ca6d3a6059c5de440af9abcfe6` от03.10.2026. Фактический checkout: MC1.21.1, NeoForge21.1.219, Java21, версия1.0.0002. Близкая версия загрузчика не означает проверенную совместимость с нашим21.1.252. [Параметры](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/gradle.properties).

Это развитие TerraForged/ReTerraForged с настраиваемыми наземными формами. [Исходники](https://github.com/ETcodehome/FreeTerraForged/tree/c5d322f5e44539ca6d3a6059c5de440af9abcfe6), [MIT с обязательным сохранением уведомления](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/LICENSE). При фактической адаптации сохранять copyright и текст лицензии в исходниках/JAR, фиксировать список файлов и изменения. Сейчас чужой код в мод не добавлен.

| Компонент | Роль в нашем прототипе | Проверенный исходник |
|---|---|---|
| RegionModule | Искривлённые региональные области и непрерывное поле края | [RegionModule.java](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/world/worldgen/cell/terrain/region/RegionModule.java) |
| Populators / TerrainPopulator | Композиции равнин, долин, плато и горных цепей | [Populators.java](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/world/worldgen/cell/terrain/Populators.java), [TerrainPopulator.java](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/world/worldgen/cell/terrain/populator/TerrainPopulator.java) |
| Численные noise/domain зависимости | Готовые шумы и композиции, нужные выбранным модулям | Ограниченный dependency closure из проверенного checkout, с аудитом заголовков лицензий каждого переносимого файла |
| River/river carvers | Позже: геометрия связанных долин и низких проток | Пока не включать полный водный/климатический конвейер |

**Установить целиком и ожидать готовое Междуморье нельзя.** [MixinChunkMap](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/mixin/MixinChunkMap.java) ограничивает контекст Overworld; [MixinRandomState](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/mixin/MixinRandomState.java) обнуляет cell markers вне него. [PresetNoiseGeneratorSettings](https://github.com/ETcodehome/FreeTerraForged/blob/c5d322f5e44539ca6d3a6059c5de440af9abcfe6/common/src/main/java/etcodehome/freeterraforged/data/worldgen/preset/PresetNoiseGeneratorSettings.java) настраивает `minecraft:overworld` и обычные STONE/WATER. Нужен изолированный адаптер численной части в наше измерение, без глобальных mixin-подмен чужих миров.

Простая point-ветка популяторов — первый проверяемый объём. Полный Heightmap/GeneratorContext включает registry, гидрологию, climate, тайлы, фильтры и потоки. WorldLookup может читать фильтрованный тайл либо сырой point-result; при переносе необходимо выбрать один канонический путь для всех потребителей. Это риск адаптации, не утверждение о дефекте штатного FreeTerraForged. Тайловая эрозия требует отдельного профиля производительности и тестов границ.

Проверочный клон: `research/FreeTerraForged-1.21.1`. Он не входит в Git/JAR проекта.

Точные SHA десяти просмотренных файлов, параметры ревизии и результаты проверки локальных ссылок/неизменности текущего JAR: [terrain-sources-20261008.json](research/terrain-sources-20261008.json). Этот реестр фиксирует исследование; он не заявляет интеграцию чужого кода.

## Другие проверенные варианты

| Решение | Что подтверждено | Вывод для проекта |
|---|---|---|
| [Terra](https://github.com/PolyhedralDev/Terra) | Официальные платформы Fabric/Bukkit. API и core addons — MIT, платформенные реализации — GPLv3 | Хороший пример конфигурируемых биомов/форм, но официальный NeoForge backend в README не заявлен. Полный перенос платформы не нужен для первого прототипа |
| [Terra Overworld Config](https://github.com/PolyhedralDev/TerraOverworldConfig) | Готовая библиотека биомов и математических конфигов для Terra; у самого пака **CC-BY-4.0**, а не лицензия движка | Можно изучать организацию конфигов и отдельные формы; нельзя считать все готовые конфиги MIT/CC0. Включения в мод сейчас нет |
| [Tectonic](https://github.com/Apollounknowndev/tectonic) | Готовая генерация крупных гор, континентов и подземных рек; авторская [страница](https://www.curseforge.com/minecraft/mc-mods/tectonic) указывает поддержку1.21.1 и NeoForge. Ранее отобранная версия3.0 описана в [WORLDGEN_TOOLS.md](WORLDGEN_TOOLS.md) | Второй источник для сравнения форм/настроек. Полная установка не обеспечивает интеграцию с нашей плотностью и морями. Версию и условия конкретных переносимых файлов проверять отдельно; текущая ветка источников не закреплена для адаптации |
| [FastNoiseLite](https://github.com/Auburn/FastNoiseLite) | MIT, Java,2D/3D, ridge/fractal, domain warp, cellular noise | Лёгкая численная библиотека, **не готовый генератор биомов**. Резерв для ограниченных полей; не подменять ею обещание использовать готовые наземные формы |
| [OpenSimplex2](https://github.com/KdotJPG/OpenSimplex2) | CC0, Java,2D/3D/4D | Альтернативный шумовой kernel. Сам не создаёт реки, горные цепи или экосистему; нет причины включать сразу две шумовые библиотеки |
| [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion-mc) | Исходники MIT; официальный [v2.3.0-preview](https://github.com/xandergos/terrain-diffusion-mc/releases/tag/2.3.0-preview) добавляет NeoForge1.21.1. README описывает модели около2.5GB, нативный inference и дополнительные требования памяти/GPU | Реальный кандидат для реалистичного сравнительного эксперимента. Для первого игрового прототипа отложен: нам нужны контролируемые3D-острова, своды и два моря, а не новый тяжёлый путь генерации. Исходная высотная модель сама по себе не решает эту задачу |
| [TerraForge с земными данными](https://github.com/denfry/TerraForge) | MIT-код, подготовленные реальные высоты/берега; официальный target Paper1.21.8. Геоданные имеют [отдельные условия](https://github.com/denfry/TerraForge/blob/main/DATA_SOURCES.md) | Подходит для карт по настоящей географии, но не готовая NeoForge1.21.1-интеграция. Для чужеродного бесконечного мира реальные координаты Земли не нужны; инструмент остаётся вариантом офлайн-референса |

Здесь «свободный» не означает одинаковую лицензию движка, конфигов, моделей и геоданных. Эти части проверяются отдельно при включении. Никаких новых модов в пользовательскую сборку не установлено; сравнение описаний и исходников не заменяет сборку/нативную проверку.

## Выбранный порядок

1. Адаптировать ограниченную point-часть FreeTerraForged на фиксированной ревизии; подтвердить минимальный набор зависимостей и лицензии. Штатные популяторы имеют `Noises.cache2d`/ThreadLocal, поэтому для собственного адаптера удалить необязательный кэш либо закрепить sampler с неизменным seed за каждым миром. Проверить два мира и точность float x/z далеко от начала координат.
2. Прототип карт/разрезов с нашими масштабами, lowersea и мягким верхним пределом; сопоставить крупные горы и низкие долины. Парящие острова брать из прежней3D-модели.
3. Встроить только в новую ревизию Междуморья, сохранив Overworld и старые настройки. Проверить determinism/base-column/соседей до добавления декораций.
4. Если point-формы недостаточно убедительны, сравнить ограниченный bordered-tile путь эрозии с бюджетом памяти/времени; не заменять sampler в середине ревизии уже сгенерированного мира.

Полный дизайн биомов, переходов и проверки: [TERRAIN_V2_DESIGN.md](TERRAIN_V2_DESIGN.md).
