from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
base=(ROOT.parent/'15_armor_inventory_icons/tools/run_native.py').read_text(encoding='utf-8')
base=base.replace("GAME/'.verification/ai01'","GAME/'.verification/wt01'").replace("('icons'+args.run_id)","('worktools'+args.run_id)")
base=base.replace('ArmorIconsSmoke.java','WorktoolsNative.java').replace('armor-icons.json','worktools-native.json').replace('interstice-0.7.0-preview.20-icons.jar','interstice-0.7.0-preview.23-worktools.jar')
base=base.replace("ROOT/'source/icon_manifest.json'","ROOT.parent/'15_armor_inventory_icons/source/icon_manifest.json'")
base=base.replace("shutil.copy2(file,ROOT/'preview'/name)","(ROOT/'preview/native').mkdir(exist_ok=True);shutil.copy2(file,ROOT/'preview/native'/name)")
exec(compile(base,str(ROOT/'tools/run_native.py'),'exec'),globals())
