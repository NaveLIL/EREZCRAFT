from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
benchmark_only='--benchmark-only' in sys.argv
if benchmark_only:sys.argv.remove('--benchmark-only')
if benchmark_only:
    config=Path('D:/repos/Minecraft/EREZCRAFT/.verification/bp01/build.gradle')
    text=config.read_text(encoding='utf-8');marker='// Owned backpack benchmark mode'
    if marker not in text:
        config.write_text(text+'\n'+marker+'\nneoForge.runs.progressionCreate.systemProperty("interstice.backpackBenchOnly", providers.gradleProperty("backpackBenchOnly").getOrElse("false"))\n',encoding='utf-8')
base=(ROOT.parent/'15_armor_inventory_icons/tools/run_native.py').read_text(encoding='utf-8')
base=base.replace("GAME/'.verification/ai01'","GAME/'.verification/bp01'").replace("('icons'+args.run_id)","('packs'+args.run_id)")
base=base.replace('ArmorIconsSmoke.java','BackpackNative.java').replace('armor-icons.json','backpacks-native.json').replace('interstice-0.7.0-preview.20-icons.jar','interstice-0.7.0-preview.21-backpacks.jar')
base=base.replace("ROOT/'source/icon_manifest.json'","ROOT.parent/'15_armor_inventory_icons/source/icon_manifest.json'")
base=base.replace("shutil.copy2(file,ROOT/'preview'/name)","(ROOT/'preview/native').mkdir(exist_ok=True);shutil.copy2(file,ROOT/'preview/native'/name)")
if benchmark_only:base=base.replace('env=dict(os.environ,',"command.insert(-1,'-PbackpackBenchOnly=true')\nenv=dict(os.environ,")
exec(compile(base,str(ROOT/'tools/run_native.py'),'exec'),globals())
