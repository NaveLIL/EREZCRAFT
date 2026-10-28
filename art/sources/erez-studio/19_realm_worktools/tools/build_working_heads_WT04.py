"""WT04: double working-profile height; preserve head width/depth, hafts and both shovels."""
from pathlib import Path
import json,copy,hashlib
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'source/worktools_WT03.json'
design=json.loads(base.read_text(encoding='utf8'))
original=copy.deepcopy(design['models'])
design['revision']='WT04'
design['previous']={name:copy.deepcopy(row['model']) for name,row in original.items()}
design['stage']='Working-head height revision requested by owner; visual review candidate'
design['change']={'base_revision':'WT03','base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'profile_height_scale':2.0,'width_scale':1.0,'depth_scale':1.0,'pivot_y':12.2,'handle_and_shovels_unchanged':True}
checks=[]
for name,row in design['models'].items():
    old=original[name]['model'];model=row['model'];changed=[]
    if row['kind'] in ('pickaxe','axe','hoe'):
        for element in model['elements']:
            if element['name'].startswith('curved_'+row['kind']+'_'):
                element['from'][1]=round(12.2+(element['from'][1]-12.2)*2,4)
                element['to'][1]=round(12.2+(element['to'][1]-12.2)*2,4)
                w,h,d=[element['to'][i]-element['from'][i] for i in range(3)]
                dims={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}
                for side,face in element['faces'].items():face['uv']=[1,1,round(1+min(14,dims[side][0]),4),round(1+min(14,dims[side][1]),4)]
                changed.append(element['name'])
    for before,after in zip(old['elements'],model['elements']):
        for axis in [0,2]:assert before['from'][axis]==after['from'][axis] and before['to'][axis]==after['to'][axis],(name,'width/depth changed')
        if before['name'] not in changed:assert before==after,(name,'non-working part changed')
        assert all(-16<=n<=32 for n in after['from']+after['to']),(name,'Minecraft coordinate limit')
    assert old['display']==model['display'],(name,'grip transform changed')
    if row['kind']=='shovel':assert old==model,(name,'shovel changed')
    dest=ROOT/'exports/WT04/assets/interstice/models/item'/(name+'.json');dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
    checks.append({'item':name,'modified_working_elements':len(changed),'all_x_z_bounds_unchanged':True,'unselected_elements_unchanged':True,'hand_transforms_unchanged':True})
source=ROOT/'source/worktools_WT04.json';source.write_text(json.dumps(design,indent=2)+'\n',encoding='utf8')
report={'passed':True,'revision':'WT04','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'height_factor':2.0,'width_factor':1.0,'depth_factor':1.0,'checks':checks,'textures_changed':False,'gameplay_changed':False}
(ROOT/'reports/design_WT04_verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({'passed':True,'models_changed':sum(bool(row['modified_working_elements']) for row in checks),'textures_changed':False}))
