#!/usr/bin/env python3
"""Apply one reviewed palette, preserving accepted pixel classes and all excluded ocean resources."""
import argparse, hashlib, json, shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('variant',choices=['A','B','C']);p.add_argument('native_report',type=Path);args=p.parse_args()
    native=json.loads(args.native_report.read_text(encoding='utf-8'));assert native.get('passed') is True
    captures=native['captures'];assert len(captures)==48 and len(native['pack_reloads'])==3
    ids={c['id'] for c in captures};assert len(ids)==8
    for scene in ids:
        selected=[c for c in captures if c['id']==scene];assert len(selected)==6
        assert len({c['geometry_state_sha256'] for c in selected})==1
        assert len({(c['actual_yaw'],c['actual_pitch'],c['eye_y']) for c in selected})==1
    base=ROOT/'art/sources/accepted-v5-palettes';manifest=json.loads((base/'manifest.json').read_text(encoding='utf-8'))
    main=ROOT/'src/main/resources/assets/interstice/textures';candidate=ROOT/f'.verification/v6-palette-candidates/v6-palette-{args.variant}/assets/interstice/textures'
    before={q.relative_to(main).as_posix():sha(q) for q in main.rglob('*') if q.is_file()};outputs=[]
    # Validate every candidate before changing any runtime PNG.
    for row in manifest['assets']:
        rel=row['path'];source=base/'textures'/rel;target=candidate/rel
        if not source.exists():source=base/rel
        a=Image.open(source).convert('RGBA');b=Image.open(target).convert('RGBA');assert a.size==b.size
        forward={};reverse={}
        for y in range(a.height):
            for x in range(a.width):
                aa,bb=a.getpixel((x,y)),b.getpixel((x,y));assert aa[3]==bb[3]
                assert forward.get(aa,bb)==bb and reverse.get(bb,aa)==aa;forward[aa]=bb;reverse[bb]=aa
        outputs.append({'path':rel,'baseline_sha256':sha(source),'output_sha256':sha(target),'classes':len(forward)})
    for row in outputs:shutil.copyfile(candidate/row['path'],main/row['path'])
    changed=[rel for rel,digest in before.items() if sha(main/rel)!=digest]
    assert set(changed)<=set(row['path'] for row in outputs)
    assert not any('heavy_' in rel or 'light_' in rel or rel.endswith('.mcmeta') for rel in changed)
    report={'passed':True,'variant':args.variant,'baseline_commit':manifest.get('baseline_commit','d01c9f2'),
        'native_report_sha256':sha(args.native_report),'native_scenes':8,'native_captures':48,'shared_assets_change_old_world_appearance':True,
        'pattern_alpha_dimensions_classes_preserved':True,'oceans_and_other_excluded_resources_unchanged':True,'changed_png_count':len(changed),'outputs':outputs}
    out=ROOT/'src/main/resources/assets/interstice/provenance/realm-v6-palette.json';out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='outputs'}))
if __name__=='__main__':main()
