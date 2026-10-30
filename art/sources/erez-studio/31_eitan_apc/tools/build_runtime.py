"""Rebuild authored Current and execute dependent checks, in a background Blender only."""
import runpy,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.argv=['build_scene.py','--','--views','driver,cutaway','--resolution','1200']
for name in ['build_scene','verify_model','verify_roof_fit','verify_hatch_seal','verify_footwell','export_vehicle']:
    print('EITAN_STAGE',name,flush=True)
    runpy.run_path(str(ROOT/'tools'/f'{name}.py'),run_name='__main__')
