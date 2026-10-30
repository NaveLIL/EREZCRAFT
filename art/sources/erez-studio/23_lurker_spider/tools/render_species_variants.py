from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[1]
BLENDER=Path('C:/Users/Dimon/AppData/Local/Programs/blender-5.2.2-windows-x64/blender.exe')
for rev,role,scale in [('SK01','skirmisher','.60'),('SP01','spitter','.55'),('SP02','spitter','.55')]:
    log=ROOT/f'reports/blender_{rev}_run.log'
    args=[str(BLENDER),'--background','--factory-startup','--python',str(ROOT/'tools/build_scene.py'),
          '--','--revision',rev,'--baseline','LS03','--role',role,'--variant-scale',scale,
          '--views','quarter,front','--resolution','1200']
    print('Rendering',rev,role,flush=True)
    with log.open('w',encoding='utf8') as f:subprocess.run(args,stdout=f,stderr=subprocess.STDOUT,check=True)
    assert f'LURKER_{rev}_SCENE_COMPLETE' in log.read_text(encoding='utf8'),log
    report=json.loads((ROOT/f'reports/blender_{rev}.json').read_text())
    assert report['role']==role and report['minecraft_scale']==float(scale)
    print('Complete',rev,flush=True)
