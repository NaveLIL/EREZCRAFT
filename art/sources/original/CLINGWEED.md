# Оригинальная липучка

Создана встроенным инструментом ImageGen 08.10.2026 по разрешению владельца проекта разработать отдельную текстуру этого растения. Сторонние изображения не передавались. Принятый исходник: `clingweed-sprite-master.png`.

Первоначальные варианты сплошной стенки отклонены после игрового просмотра. Итоговый вариант — прозрачный пучок загнутых стеблей с янтарными каплями. `tools/PrepareClingweedTexture.java` только приводит исходник к размеру32×32 ближайшим соседом; рисунок не дорисовывается. Модели задают направление роста и три размера пучка.

## Восстановленная спецификация финального запроса

Это запись требований, восстановленная из рабочего контекста; не заявляется как дословный журнал вызова инструмента.

> Production ONE Minecraft alien cave plant sprite. Genuinely transparent background, chunky32 logical pixels. Dark plum and muted pale teal five to seven curved hooked stalks that catch objects, with three amber luminous resin beads. Grasslike crossed-plane3D plant, readable at small size, no opaque wall, cube, grid, grain, text, ground or pot. Limited palette, at most16 colors.

Параметр инструмента: `transparent_background=true`. В игре растение остаётся проходимым; срезание, коллизия, кража и газ определяются кодом, а не этим изображением. Небольшой свет задаётся состоянием блока.
