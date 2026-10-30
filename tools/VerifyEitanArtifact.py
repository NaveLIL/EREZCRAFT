"""Check that the local candidate contains the tested main classes and assets."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('jar',type=Path);a=p.parse_args()
acceptance=json.loads((ROOT/'docs/verification/EITAN_DRIVE.json').read_text(encoding='utf-8-sig'))
assert acceptance['passed']
digest=lambda b:hashlib.sha256(b).hexdigest()
for name,expected in acceptance['runtime_source_sha256'].items():
    assert digest((ROOT/name).read_bytes())==expected,name
classes={}
with zipfile.ZipFile(a.jar) as archive:
    names=set(archive.namelist())
    assert not any(n.startswith(('dev/ryanhcode/','com/simibubi/')) for n in names),'Compile-only third-party API leaked into the candidate'
    assert 'pro/erez/interstice/client/EitanDriveNative.class' not in names
    assert 'pro/erez/interstice/test/EitanNetworkProbe.class' not in names
    for path in sorted((ROOT/'build/classes/java/main/pro/erez/interstice').rglob('Eitan*.class')):
        name=path.relative_to(ROOT/'build/classes/java/main').as_posix()
        assert archive.read(name)==path.read_bytes(),name
        classes[name]=digest(path.read_bytes())
    resources=[]
    base=ROOT/'src/main/resources'
    for folder in ['data/interstice/eitan','assets/interstice/models/block/eitan_vehicle','assets/interstice/textures/block/eitan']:
        resources.extend(path for path in (base/folder).rglob('*') if path.is_file())
    resources.append(base/'assets/interstice/provenance/eitan_vehicle.json')
    for path in resources:
        name=path.relative_to(base).as_posix()
        assert archive.read(name)==path.read_bytes(),name
    for name in ['META-INF/licenses/SecurityCraft-camera-reference-MIT.txt','META-INF/licenses/EITAN_CAMERA_NOTICE.txt']:
        assert name in names,name
    assert classes and resources
report=dict(passed=True,jar=str(a.jar.resolve()),sha256=digest(a.jar.read_bytes()),main_classes=classes,resource_files_checked=len(resources),smoke_harness_bundled=False,compile_only_apis_bundled=False,installed=False,published=False)
(ROOT/'docs/verification/EITAN_ARTIFACT.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='main_classes'}))
