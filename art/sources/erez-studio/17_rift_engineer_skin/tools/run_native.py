from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
base=(ROOT.parent/'15_armor_inventory_icons/tools/run_native.py').read_text(encoding='utf-8')
base=base.replace("GAME/'.verification/ai01'","GAME/'.verification/sk01'").replace("('icons'+args.run_id)","('skin'+args.run_id)")
base=base.replace('ArmorIconsSmoke.java','AvatarNative.java').replace('armor-icons.json','avatar-native.json').replace('interstice-0.7.0-preview.20-icons.jar','interstice-0.7.0-preview.21-backpacks.jar')
base=base.replace("ROOT/'source/icon_manifest.json'","ROOT.parent/'15_armor_inventory_icons/source/icon_manifest.json'")
base=base.replace("shutil.copy2(file,ROOT/'preview'/name)","shutil.copy2(file,ROOT/'preview/native'/name)")
# Compare the unchanged accepted jar, not a newly packaged cosmetic-only mod.
base=base.replace("CAND/'build/libs/interstice-0.7.0-preview.21-backpacks.jar'","ROOT.parent/'16_expedition_backpacks/exports/interstice-0.7.0-preview.21-backpacks.jar'")
exec(compile(base,str(ROOT/'tools/run_native.py'),'exec'),globals())
