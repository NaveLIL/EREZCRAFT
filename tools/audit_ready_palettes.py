#!/usr/bin/env python3
"""Read-only audit of ready CC0 sprites: resolution, alpha, pixel classes and recorded hashes."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/interstice'


def main():
    rows=[]
    for registry, source_dir in [('cave-ready','cave'),('minerals','minerals'),('realm-surfaces','realm-surfaces'),('forest-ecology','forest-ecology')]:
        data=json.loads((ASSETS / 'provenance' / (registry+'.json')).read_text(encoding='utf-8-sig'))
        for item in data['outputs']:
            source=ROOT/'art/sources/cc0'/source_dir/item['source']
            output=ASSETS/'textures'/item['output']
            with Image.open(source) as image: before=image.convert('RGBA'); before.load()
            with Image.open(output) as image: after=image.convert('RGBA'); after.load()
            if before.size!=after.size: raise ValueError(f'Resolution changed: {output}')
            if registry=='forest-ecology' and before.size!=(16,16): raise ValueError(f'Forest sprite is not 16x16: {source}')
            forward={}; reverse={}
            for left,right in zip(before.get_flattened_data(),after.get_flattened_data()):
                if left[3]!=right[3]: raise ValueError(f'Alpha changed: {output}')
                if left in forward and forward[left]!=right: raise ValueError(f'Pattern repainted: {output}')
                if right in reverse and reverse[right]!=left: raise ValueError(f'Pattern classes merged: {output}')
                forward[left]=right; reverse[right]=left
            if 'classes' in item and len(forward)!=item['classes']: raise ValueError(f'Provenance class count mismatch: {output}')
            source_hash=hashlib.sha256(source.read_bytes()).hexdigest()
            out_hash=hashlib.sha256(output.read_bytes()).hexdigest()
            if source_hash!=item.get('source_sha256',item.get('sha256')) or out_hash!=item['output_sha256']:
                raise ValueError(f'Provenance hash mismatch: {output}')
            rows.append({'registry':registry,'asset':item['output'],'source':source.relative_to(ROOT).as_posix(),
                         'classes':len(forward),'resolution':before.size,'source_sha256':source_hash,'output_sha256':out_hash})
    report={'passed':True,'ready_assets_checked':len(rows),'pattern_and_alpha_preserved':True,'assets':rows}
    target=ROOT/'.verification/ready-palette-audit.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f'Ready CC0 palettes: {len(rows)} assets, original coordinates/resolution/alpha/classes preserved')


if __name__=='__main__': main()
