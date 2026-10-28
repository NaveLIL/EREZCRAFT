"""Check thin tide geometry, original dial states and imported clock poses."""
from pathlib import Path
import argparse, hashlib, json, subprocess, sys, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def verify(baseline, candidate, before, thin_baseline):
    assets=ROOT/'src/main/resources/assets/interstice'
    base=json.loads((assets/'models/item/tide_indicator_base.json').read_text())
    elements=base['elements']
    depth=max(e['to'][1] for e in elements)-min(e['from'][1] for e in elements)
    assert 0<depth<=.9
    assert sum(len(e['faces']) for e in elements)==313
    with tempfile.TemporaryDirectory(prefix='ti01-',dir=ROOT/'.verification') as folder:
        subprocess.run([sys.executable,str(ROOT/'tools/build_tide_indicator.py'),'--output-root',folder],check=True,capture_output=True)
        generated=Path(folder)/'src/main/resources/assets/interstice'
        files=[p for p in generated.rglob('*') if p.is_file()]
        assert len(files)==20
        for p in files: assert p.read_bytes()==(assets/p.relative_to(generated)).read_bytes(),str(p)
    prior=json.loads(before.read_text())
    expected={'build.gradle'}|{'src/main/resources/assets/interstice/models/item/tide_indicator'+suffix+'.json' for suffix in ['', '_base']+[f'_{i:02}' for i in range(16)]}
    changed=[p for p,h in prior.items() if not (ROOT/p).exists() or sha(ROOT/p)!=h]
    assert set(changed)==expected,changed
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(candidate) as new:
        classes=[n for n in old.namelist() if n.endswith('.class')]
        pngs=[n for n in old.namelist() if n.endswith('.png')]
        for n in classes+pngs: assert old.read(n)==new.read(n),n
        overrides=json.loads(new.read('assets/interstice/models/item/tide_indicator.json'))['overrides']
        assert overrides==json.loads(old.read('assets/interstice/models/item/tide_indicator.json'))['overrides']
        for i in range(16):
            frame=json.loads(new.read(f'assets/interstice/models/item/tide_indicator_{i:02}.json'))
            assert frame['textures']['dial']==f'interstice:item/tide_indicator_{i:02}'
        changed_jar=[n for n in old.namelist() if not n.endswith('/') and (n not in new.namelist() or old.read(n)!=new.read(n))]
        allowed={f'assets/interstice/models/item/tide_indicator{s}.json' for s in ['', '_base']+[f'_{i:02}' for i in range(16)]}|{'META-INF/neoforge.mods.toml'}
        assert set(changed_jar)<=allowed,changed_jar
        added=[n for n in new.namelist() if not n.endswith('/') and n not in old.namelist()]
        assert set(added)=={'assets/interstice/provenance/tide-indicator-ti01.json','assets/interstice/licenses/MB3D-MIT.txt'},added
    with zipfile.ZipFile(ROOT/'art/sources/mit/mb3d-clock/MB-3D Items Pack 2.4.0.zip') as z:
        donor=json.loads(z.read('assets/minecraft/models/item/display/clock.json'))['display']
        for view in ('firstperson','thirdperson'):
            for hand in ('righthand','lefthand'):
                assert base['display'][f'{view}_{hand}']==donor.get(f'{view}_{hand}',donor[f'{view}_righthand'])
    # Compare textured world-space corners rather than just counts or dimensions.
    # Native FaceInfo / BlockFaceUV vertex order, MC 1.21.1.
    faces={'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)],'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
           'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
           'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)]}
    def textured_corners(element,align):
        result=[]
        for side,f in element['faces'].items():
            u,v,uu,vv=f['uv'];uv=[(u,v),(u,vv),(uu,vv),(uu,v)];r=f.get('rotation',0)//90;corners=[]
            for i,bits in enumerate(faces[side]):
                p=[element[('from','to')[bit]][axis] for axis,bit in enumerate(bits)]
                if align:p=[16-p[0],p[2],p[1]]
                corners.append(tuple(round(x,6) for x in p)+uv[(i+r)%4])
            result.append((f['texture'],tuple(sorted(corners))))
        return sorted(result)
    with zipfile.ZipFile(thin_baseline) as z:
        original=json.loads(z.read('assets/interstice/models/item/tide_indicator_base.json'))['elements']
    assert len(original)==len(elements)==53
    for old_element,new_element in zip(original,elements):
        assert textured_corners(old_element,True)==textured_corners(new_element,False),old_element['name']
    return {'passed':True,'maximum_depth_model_units':depth,'unchanged_runtime_classes':len(classes),'unchanged_pngs':len(pngs),'unchanged_tide_predicates':16,'deterministic_generated_files':20,'exact_imported_clock_hand_poses':4,'rigidly_aligned_unchanged_textured_quads':313,'changed_preexisting_source_files':changed,'changed_jar_entries':changed_jar,'added_jar_entries':added,'candidate_sha256':sha(candidate)}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline',required=True,type=Path);p.add_argument('--candidate',required=True,type=Path);p.add_argument('--before',required=True,type=Path);p.add_argument('--report',required=True,type=Path);p.add_argument('--thin-baseline',required=True,type=Path)
    a=p.parse_args();result=verify(a.baseline,a.candidate,a.before,a.thin_baseline);a.report.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
