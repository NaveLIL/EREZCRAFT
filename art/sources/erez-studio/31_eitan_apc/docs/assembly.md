# Просмотр и управление моделью

Открыть assembly/eitan_E01.blend. Кадры: 1 — закрыта; 41 — аппарель и люки открыты;
61 — закрыта; 81 — отдельная дверь; 101 — закрыта; 121 — сложенные сиденья и поворот
передних колёс; 161 — закрыта. Гидроцилиндры следуют за аппарелью нативными драйверами.
Скрыть ROOF и ANTENNAS для осмотра экипажа/десанта. Это графическая разборка.

exports/eitan_E01_closed.glb и eitan_E01_boarding.glb — статические варианты для переноса.
Все детали подвижных узлов сгруппированы относительно осей шарниров.
Рендеры preview являются изображениями этой сцены Blender. Отдельный игровой
+адаптер EitanPreview в репозитории Minecraft включается только специальным флагом;
+это неподвижная сцена осмотра, не управляемая машина.

Порядок воспроизведения: tools/build_design.py; Blender --background --python
tools/build_scene.py; tools/verify_roof_fit.py и tools/verify_model.py в Blender;
+tools/export_minecraft.py в Blender; tools/mechanism_state.py. Перенос в Minecraft:
+tools/ImportEitanModel.py и tools/VerifyEitan.ps1 в репозитории EREZCRAFT.
Затем инженерный record/report с входом verification/model-inspection.json.
Производство и испытания настоящей бронемашины к этому проекту не относятся.
