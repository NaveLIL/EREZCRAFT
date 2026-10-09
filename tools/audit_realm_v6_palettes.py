"""Independent Pillow audit of V6 candidate packs and the immutable accepted V5 patterns."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'art/sources/accepted-v5-palettes'
OUTPUT=ROOT/'.verification/v6-palette-candidates'
MAIN=ROOT/'src/main/resources/assets/interstice/textures'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rgba(path):
    with Image.open(path) as image:
        converted=image.convert('RGBA')
        return converted.size,list(converted.get_flattened_data())


def argb(pixel):
    r,g,b,a=pixel
    return f'{a:02x}{r:02x}{g:02x}{b:02x}'


def main():
    manifest=json.loads((BASE/'manifest.json').read_text())
    assert sha(BASE/'manifest.json')==(BASE/'manifest.sha256').read_text().split()[0]
    assert manifest['baseline_commit']=='d01c9f2d627ab2d00127613ded5e85d5e9f5936b'
    for entry in manifest['assets']:
        assert sha(BASE/entry['path'])==entry['sha256'],entry['path']
        assert sha(MAIN/entry['path'])==entry['sha256'],'Runtime PNG changed before review: '+entry['path']
    for entry in manifest['excluded_oceans']+manifest['unchanged_other_assets']:
        assert sha(MAIN/entry['path'])==entry['sha256'],'Excluded runtime asset changed: '+entry['path']
    asset_checks=[]
    for palette in ('A','B','C'):
        provenance=json.loads((OUTPUT/f'palette-{palette}-provenance.json').read_text())
        assert provenance['baseline_manifest_sha256']==sha(BASE/'manifest.json')
        assert not provenance['runtime_resources_written'] and provenance['oceans_excluded']
        rows={row['path']:row for row in provenance['assets']}
        assert set(rows)=={entry['path'] for entry in manifest['assets']}
        pack=OUTPUT/f'v6-palette-{palette}'
        actual={p.relative_to(pack/'assets/interstice/textures').as_posix() for p in (pack/'assets/interstice/textures').rglob('*') if p.is_file()}
        assert actual==set(rows),'Unexpected file in palette overlay'
        mappings={}
        for entry in manifest['assets']:
            path=entry['path'];row=rows[path];source=BASE/path;target=pack/'assets/interstice/textures'/path
            assert sha(source)==row['input_sha256'] and sha(target)==row['output_sha256'],path
            first_size,first=rgba(source);last_size,last=rgba(target)
            assert first_size==last_size==(row['width'],row['height']),path
            forward={};reverse={}
            for left,right in zip(first,last):
                assert left[3]==right[3],'Alpha changed: '+path
                assert left not in forward or forward[left]==right,'Pixel class repainted: '+path
                assert right not in reverse or reverse[right]==left,'Pixel classes merged: '+path
                if left[3]==0: assert left==right,'Invisible RGB class changed: '+path
                forward[left]=right;reverse[right]=left
            assert len(forward)==len(reverse)==row['classes'],path
            assert {argb(left):argb(right) for left,right in forward.items()}==row['argb_lut'],path
            mappings[path]=forward
            asset_checks.append({'palette':palette,'path':path,'classes':len(forward),'size':first_size,'output_sha256':sha(target)})
        host=mappings['block/riftstone.png']
        for composite in ('block/riftsilver_ore.png','block/rift_frame.png','block/abyssal_turf_side.png'):
            for left,right in mappings[composite].items():
                if left in host: assert host[left]==right,'Composite host LUT changed'
        for name in ('vaultstone','weathered_vaultstone'):
            _,left=rgba(pack/f'assets/interstice/textures/block/stone/{name}.png')
            for variant in (1,2):
                _,right=rgba(pack/f'assets/interstice/textures/block/stone/{name}_{variant}.png')
                assert mappings[f'block/stone/{name}.png']==mappings[f'block/stone/{name}_{variant}.png']
                for i in range(16):
                    for position in (i,i+240,i*16,i*16+15): assert left[position]==right[position],'Variant edge changed'
    report={'passed':True,'independent_decoder':'Pillow','palette_count':3,'accepted_patterns_checked':len(manifest['assets']),
            'stored_pngs_checked':len(asset_checks),'same_dimensions_coordinates_alpha_classes':True,'transparent_rgb_preserved':True,
            'shared_host_lut_and_variant_edges':True,'excluded_ocean_files_unchanged':len(manifest['excluded_oceans']),
            'all_original_runtime_texture_files_unchanged':len(manifest['assets'])+len(manifest['excluded_oceans'])+len(manifest['unchanged_other_assets']),
            'native_gallery_performed':False,'assets':asset_checks}
    (OUTPUT/'independent-audit.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'Independent V6 audit passed: {len(asset_checks)} candidate PNGs; all original runtime textures and ocean metadata unchanged')


if __name__=='__main__':
    main()
