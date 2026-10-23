"""Convert Willmood's approved Wendigo into vanilla ModelParts without changing its PNG.

The original .bbmodel is retained in art/fauna/willmood with attribution. Geometry
uses Blockbench's Java coordinate conversion (-X,-Y,+Z), preserving pivots,
rotated cubes, inflation and the original per-face UV rectangles.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art/fauna/willmood/windigo.bbmodel'
EXPECTED = 'e7907b5a153bd7e1cb1e15342e17407745a648a07cc6cfa5af944311fdb2e24d'


def number(n):
    return f'{0 if abs(n) < 1e-7 else n:.6f}F'


def generate():
    raw = SOURCE.read_bytes()
    if hashlib.sha256(raw).hexdigest() != EXPECTED:
        raise ValueError('Unexpected Wendigo source; review provenance before importing')
    model = json.loads(raw)
    elements = {e['uuid']: e for e in model['elements']}
    lines = []

    def group(g, parent='root', parent_origin=None):
        name = g['name']
        origin = g['origin']
        offset = [origin[i] - (parent_origin[i] if parent_origin else 0) for i in range(3)]
        offset = [-offset[0], -offset[1] + (24 if parent_origin is None else 0), offset[2]]
        rot = g.get('rotation', [0, 0, 0])
        rot = [-rot[0], -rot[1], rot[2]]
        import math
        args = offset + [math.radians(r) for r in rot]
        lines.append(f'        var {name} = part({parent}, "{name}", ' + ', '.join(map(number, args)) + ');')
        for child in g['children']:
            if isinstance(child, dict):
                group(child, name, origin)
                continue
            e = elements[child]
            if not e.get('export', True):
                continue
            # A separate pivot for every cube preserves rotations relative to its bone.
            pivot = e.get('origin', origin)
            offset = [-(pivot[0]-origin[0]), -(pivot[1]-origin[1]), pivot[2]-origin[2]]
            r = e.get('rotation', [0, 0, 0])
            args = offset + [math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])]
            cube_name = 'cube_' + str(len(lines))
            lines.append(f'        var {cube_name} = part({name}, "{cube_name}", ' + ', '.join(map(number,args)) + ');')
            lo, hi = e['from'], e['to']
            args = [pivot[0]-hi[0], pivot[1]-hi[1], lo[2]-pivot[2]] + [hi[i]-lo[i] for i in range(3)] + [e.get('inflate',0)]
            uv = []
            for face in ['up','down','east','north','west','south']:
                f = e['faces'][face]
                rect = f['uv']
                if face in ['up','down']:
                    rect = rect[2:] + rect[:2]
                uv.append('null' if f.get('texture') is None else 'new float[]{' + ', '.join(map(number,rect)) + '}')
            lines.append(f'        cube({cube_name}, ' + ', '.join(map(number,args)) + ', new float[][]{' + ', '.join(uv) + '});')

    for g in model['outliner']:
        group(g)
    template = (ROOT/'tools/vault_hunter_geometry.java.in').read_text(encoding='utf-8')
    result = template.replace('/* GENERATED_GEOMETRY */', '\n'.join(lines))
    target = ROOT/'src/main/java/pro/erez/interstice/client/VaultHunterGeometry.java'
    target.write_text(result, encoding='utf-8', newline='\n')
    print(f'Imported {len(elements)} original cubes; wrote {target.relative_to(ROOT)}')


if __name__ == '__main__':
    generate()
