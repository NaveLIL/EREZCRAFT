"""SP03: preserve source pixel masks; compress colour contrast within each zone."""
from pathlib import Path
from PIL import Image
import copy,json,hashlib,base64,uuid,shutil
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'source/spitter_SP02.bbmodel'
base=json.loads(SOURCE.read_text(encoding='utf8'));model=copy.deepcopy(base)
model['name']='EREZCRAFT Spitter SP03 - softer natural surface trial'
design=json.loads((ROOT/'reports/design_SP02.json').read_text())
palettes=design['palettes'];new_palettes={};lookup={}
factors={'legs':.16,'abdomen':.13,'carapace':.14,'knee':.18,'hair':.35,'fangs':.22,'eyes':1.0}
for role,hexes in palettes.items():
    colors=[tuple(bytes.fromhex(h)) for h in hexes];center=colors[2]
    result=[]
    for color in colors:
        changed=tuple(round(center[i]+(color[i]-center[i])*factors[role]) for i in range(3))
        lookup[color]=changed;result.append(changed)
    assert len(set(result))==len(set(colors)),role
    new_palettes[role]=[''.join(f'{v:02x}' for v in c) for c in result]
texture_checks=[]
for index,texture in enumerate(model['textures']):
    before=ROOT/'source'/base['textures'][index]['name']
    after=ROOT/'source'/before.name.replace('SP02','SP03')
    image=Image.open(before).convert('RGBA');old_pixels=list(image.getdata());out=[]
    role=base['erezcraft_material_roles'][str(index)]['role']
    for p in old_pixels:
        if p[3]:
            assert p[:3] in lookup,(before.name,p)
            out.append((*lookup[p[:3]],p[3]))
        else:out.append(p)
    result=Image.new('RGBA',image.size);result.putdata(out)
    assert result.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    if role=='eyes':
        assert out==old_pixels
        shutil.copyfile(before,after)
    else:result.save(after)
    texture.update(name=after.name,relative_path=after.name,
                   uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,'erezcraft/SP03/'+after.name)),
                   source='data:image/png;base64,'+base64.b64encode(after.read_bytes()).decode())
    settings=model['erezcraft_material_roles'][str(index)]
    if role not in ['eyes','fangs']:settings.update(roughness=.96,specular=.08)
    texture_checks.append({'file':after.name,'role':role,'alpha_identical':True,'size':list(result.size),
                           'only_rgb_palette_substitution':True,'eyes_bytes_identical':before.read_bytes()==after.read_bytes() if role=='eyes' else None,
                           'sha256':hashlib.sha256(after.read_bytes()).hexdigest()})
def geometry(d):
    d=copy.deepcopy(d)
    for key in ['name','textures','erezcraft_material_roles']:d.pop(key,None)
    return d
assert geometry(model)==geometry(base)
target=ROOT/'source/spitter_SP03.bbmodel';target.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
report={'design':'SP03','baseline':'SP02','source':str(SOURCE),'role':'spitter','variant_scale':.55,
        'base_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'model_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
        'geometry_identical_to_SP02':True,'all_uv_and_texture_indices_identical':True,'eye_positions_identical':True,
        'eyes_atlas_bytes_identical':True,'animations_identical':True,'alpha_and_dimensions_identical':True,
        'pixel_category_masks_preserved':True,'new_painted_details':False,
        'contrast_factors':factors,'original_palettes':palettes,'softened_palettes':new_palettes,
        'matte_body_roughness':.96,'matte_body_specular':.08,'textures':texture_checks,
        'game_verified':False,'status':'Single-variant visual trial; selected SP02 retained'}
(ROOT/'reports/design_SP03.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps({'revision':'SP03','geometry_and_uv_identical':True,'eyes_bytes_identical':True,'texture_count':len(texture_checks),'contrast_factors':factors}))
