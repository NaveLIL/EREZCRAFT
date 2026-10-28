"""Palette-only Blade 1.0.1 adaptation; retain the author's exact elements and UVs."""
from pathlib import Path
import argparse, copy, hashlib, io, json, zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art/sources/cc0/blade-held-items'
ARCHIVE = SOURCE / 'blade-held-items-resourcepack_1.0.1.zip'
ARCHIVE_SHA256 = 'b886506212c0efd5f5ad524f24a046b19f7a38ad0245bcc6a5c88c3b66e705a7'
KINDS = ('pickaxe', 'axe', 'shovel', 'hoe')
TIERS = ('riftsilver', 'riftsteel')
WOOD = {(138,87,36):(132,111,88), (105,66,28):(105,88,70),
        (74,46,20):(76,63,49), (40,25,10):(43,37,29)}
# The accepted lavender silver and blue-grey riftsteel, retaining all source shading.
METAL = {'riftsilver': ((82,62,101),(174,160,193),(230,225,240)),
         'riftsteel': ((26,45,54),(72,100,110),(181,207,213))}

def digest(data): return hashlib.sha256(data).hexdigest()
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')

def recolor(pixel, tier):
    rgb, alpha = pixel[:3], pixel[3]
    if not alpha: return pixel  # Transparent pixels, including hidden RGB, remain exact.
    if rgb in WOOD: return (*WOOD[rgb], alpha)
    value = sum(rgb)/3
    t = max(0., min(1., (value-124.)/(246.-124.)))
    low, middle, high = METAL[tier]
    a,b,u = (low,middle,t*2) if t<.5 else (middle,high,(t-.5)*2)
    return (*(round(a[i]+(b[i]-a[i])*u) for i in range(3)), alpha)

def build(output):
    raw = ARCHIVE.read_bytes()
    assert digest(raw) == ARCHIVE_SHA256, 'Source archive changed'
    resources = output/'src/main/resources'
    assets = resources/'assets/interstice'
    result = {'revision':'B01','author':'TimmyChips','license':'CC0-1.0',
              'source_url':'https://modrinth.com/resourcepack/blade-held-items',
              'version_id':'LLyZvI13','source_version':'Default_1.0.1',
              'archive_sha256':ARCHIVE_SHA256,'geometry_and_uv_changes':False,
              'palette_only_texture_changes':True,'pommel_dependency_required':False,
              'inventory':'Native 3D models in all contexts, not a separate held-model switch',
              'models':{},'palette':{'metal':METAL,'wood':{str(k):v for k,v in WOOD.items()}}}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        for kind in KINDS:
            model_path = f'assets/minecraft/models/item/pommel_held_models/template_{kind}.json'
            texture_path = f'assets/minecraft/textures/item/pommel_held_models/iron_{kind}_held.png'
            template = json.loads(archive.read(model_path))
            original = Image.open(io.BytesIO(archive.read(texture_path))).convert('RGBA')
            for tier in TIERS:
                name = tier+'_'+kind
                model = copy.deepcopy(template)
                model.pop('groups',None)
                model['credit'] = 'Blade 1.0.1 by TimmyChips (CC0); EREZCRAFT palette adaptation'
                model['parent'] = 'minecraft:block/block'
                model['ambientocclusion'] = False
                model['render_type'] = 'minecraft:cutout'
                model['textures'] = {'model':f'interstice:item/blade/{name}',
                                     'particle':f'interstice:item/blade/{name}'}
                # The upstream held model has no GUI pose; keep a legible native miniature.
                model['display']['gui'] = {'rotation':[25,-35,-35],
                                          'translation':[0,-1,0],'scale':[.75,.75,.75]}
                assert model['elements'] == template['elements']
                assert all(model['display'][k] == v for k,v in template['display'].items())
                image = Image.new('RGBA', original.size)
                image.putdata([recolor(p,tier) for p in original.get_flattened_data()])
                assert image.size == original.size == (16,16)
                assert image.getchannel('A').tobytes() == original.getchannel('A').tobytes()
                # No pixel rotation, mirroring, resizing, edge edits or new details.
                texture_target = assets/f'textures/item/blade/{name}.png'
                texture_target.parent.mkdir(parents=True,exist_ok=True)
                image.save(texture_target)
                target = assets/f'models/item/{name}.json'
                write_json(target,model)
                result['models'][name] = {'source_model':model_path,'source_texture':texture_path,
                    'source_model_sha256':digest(archive.read(model_path)),
                    'source_texture_sha256':digest(archive.read(texture_path)),
                    'elements':len(model['elements']),
                    'faces':sum(len(e['faces']) for e in model['elements']),
                    'model_sha256':digest(target.read_bytes()),
                    'texture_sha256':digest(texture_target.read_bytes()),
                    'alpha_unchanged':True,'model':model}
    notice = ('Blade - Held Items 1.0.1 by TimmyChips\n'
              'Source: https://modrinth.com/resourcepack/blade-held-items\n'
              'Modrinth project QtFiEb3u; version LLyZvI13; license CC0-1.0.\n'
              'Exact model elements and UVs retained. Only texture palette adapted.\n'
              'The archive contains no license file; the original project API license\n'
              'declaration is retained in art/sources/cc0/blade-held-items/.\n\n')
    license_target = resources/'META-INF/licenses/blade-held-items-CC0-1.0.txt'
    license_target.parent.mkdir(parents=True,exist_ok=True)
    license_target.write_text(notice+(SOURCE/'CC0-1.0.txt').read_text(encoding='utf8'),encoding='utf8')
    provenance = copy.deepcopy(result)
    for row in provenance['models'].values(): row.pop('model')
    write_json(assets/'provenance/blade-worktools.json',provenance)
    return result

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-root',type=Path,default=ROOT)
    parser.add_argument('--design',type=Path)
    args=parser.parse_args()
    design=build(args.output_root)
    if args.design: write_json(args.design,design)
    print(json.dumps({'passed':True,'revision':'B01','models':len(design['models']),
                      'source_geometry_and_uv_unchanged':True,'source_alpha_unchanged':True}))
