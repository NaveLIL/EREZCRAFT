from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'tools/render_aegis.py').read_text(encoding='utf-8').replace('armor_design_A03.json','armor_design_A04.json').replace('Aegis A03','Magen A04').replace('aegis_armor_A03.glb','magen_armor_A04.glb').replace('mezhdumorye_aegis_A03.blend','mezhdumorye_magen_A04.blend')
exec(compile(source,str(ROOT/'tools/render_aegis.py'),'exec'),globals())
