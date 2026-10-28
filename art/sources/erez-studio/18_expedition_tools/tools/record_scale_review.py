from pathlib import Path
import json,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1];old=Path('D:/repos/Minecraft/EREZCRAFT/.verification/et01/.verification/tools01/progressionNative/screenshots')
history=ROOT/'preview/history';history.mkdir(exist_ok=True);copies={}
for name in ('tools-held-front.png','tools-first-person-lift_controller.png'):
    dest=history/('oversized_'+name);shutil.copy2(old/name,dest);copies[dest.name]={'source':str(old/name),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'edited':False}
d=json.loads((ROOT/'source/tools_T01.json').read_text(encoding='utf-8'))
record={'trigger':'Owner rejected oversized handheld presentation','geometry_changed':False,'inventory_scale_changed':False,
 'previous_third_person_scale':.75,'previous_first_person_scale':.7,'previous_first_person_fixture_fov':42,'current_first_person_fixture_fov':70,
 'current':{n:{ctx:m['model']['display'][ctx] for ctx in ('gui','thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand')} for n,m in d['models'].items()},'history':copies,
 'comparison_note':'First-person before/after changes both display scale and fixture FOV; not a controlled scale-only or FPS comparison. No live user options changed.'}
(ROOT/'reports/handheld_scale_review.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
