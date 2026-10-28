from pathlib import Path

root = Path(__file__).resolve().parents[1]
f = root / 'tools/finish.py'
s = f.read_text(encoding='utf-8')
s = '\n'.join(
    " 'visual_direction_accepted':'User broadly accepted the B01 direction and explicitly authorized continuation'," if line.startswith(" 'visual_direction_accepted':") else
    "entry='\\nBackpacks 2026-10-27: all three B02 models implemented; local JAR 0.7.0-preview.21-backpacks. Capacities 54/72/84, storage, recipes and gameplay classes preserved. Native client with DH and 106 mods: wide/slim players, walking, crouching, swimming, Kotomyaf, armor, folded/flying elytra, all three item miniatures and the previous 16 armor icons checked; 16 screenshots. Old/new/old frame comparison with 10 actually rendered players: see [evidence](verification/BACKPACKS_REDESIGN.json); local rendering evidence only. Candidate .verification/bp01. Sources, Blender scenes and item-art backlog: Blender creations/16_expedition_backpacks. Only seven main-source paths integrated; unrelated files preserved. Server, owner profiles and primary gradle.properties unchanged.\\n'" if line.startswith('entry=') else line
    for line in s.splitlines()
) + '\n'
f.write_text(s, encoding='utf-8')

(root/'README.md').write_text('''# Рюкзаки Междуморья — B02

Три новые модели реализованы в Minecraft Java 1.21.1 / NeoForge. Сохранены прежние предметы, рецепты, содержимое, модули и вместимость.

| Рюкзак | Дизайн | Ячеек |
| --- | --- | --- |
| Полевой | Песочный roll-top, кожаные ремни, карман, фляга и скатка | 54 |
| Экспедиционный | Бирюзовый клапан, два передних кармана, карта и образцы | 72 |
| Разломный | Тёмный грузовой корпус в гранёной раме, боковые контейнеры и светящийся сердечник | 84 |

Единственный источник геометрии — [backpacks_B02.json](source/backpacks_B02.json). Из него собраны носимые модели, миниатюры предметов, Blender и GLB. Использованы существующие текстуры Minecraft; новых PNG нет. Наружное снаряжение и сердечник декоративные.

## Итоговые файлы

- [JAR preview.21](exports/interstice-0.7.0-preview.21-backpacks.jar)
- [Исходники JAR](exports/interstice-0.7.0-preview.21-backpacks-sources.jar)
- [Blender B02](assembly/backpacks_B02.blend)
- [Полевой GLB](exports/field_backpack_B02.glb), [экспедиционный GLB](exports/expedition_backpack_B02.glb), [разломный GLB](exports/rift_backpack_B02.glb)
- [Все 16 игровых скриншотов и отчёт](exports/backpacks_B02_ingame.zip)

## Проверка в игре

[Общий вид сзади](preview/native/backpacks-armor-back.png), [спереди](preview/native/backpacks-armor-front.png), [миниатюры](preview/native/backpacks-item-miniatures.png), [полёт с элитрами](preview/native/backpacks-elytra-flight.png).

Проверены обычная и тонкая модель игрока, броня, Котомяф, отсутствие брони, ходьба, приседание, плавание, сложенные и раскрытые элитры. Проверены три новые миниатюры и прежние 16 иконок брони. Сборка: 106 модов с Distant Horizons. Использован отдельный тестовый профиль; пользовательский профиль и сервер не менялись.

В коротком сравнении на RX 6800 XT, 1920×1080, без VSync и ограничения FPS, десять персонажей в Маген с разломными рюкзаками: старый рендер около 3,775 мс/кадр, новый 4,342 мс/кадр (+0,567 мс, около 15%). Это примерно 265 и 230 FPS соответственно. Метод: старый/новый/старый, по 480 кадров прогрева и 1200 измерений; каждый измеренный кадр действительно рисовал десять персонажей. Результат относится к этой локальной сцене и не гарантирует FPS в других сборках или производительность сервера.

Полное доказательство и хеши: [accepted.json](reports/accepted.json). Прогоны 01 и 02 содержат непригодные для сравнения производительности постановки и не используются в итоговом выводе; прогон 03 предшествовал кешированию геометрии; итог — прогон 04.

## История и очередь

[Старый вид](preview/backpacks_previous.png) и [согласованное направление B01](preview/backpacks_proposed_B01.png) сохранены. B02 улучшает читаемость карманов и переносит модели в игру; B01 остаётся историческим предложением.

[Очередь доработки остальных предметов](source/item_model_backlog.json): катушка, пульт и указатель; затем якорь, линза, индикатор прилива, инструменты и небольшие устройства. Отсутствующая модель яйца upper_sea_ghast вынесена отдельным техническим дефектом.

Генераторы: tools/build_runtime.py, tools/render_review.py. Blender-сцена — визуальное представление; актуальные игровые проверки хранятся отдельно в reports/accepted.json.
''', encoding='utf-8')
