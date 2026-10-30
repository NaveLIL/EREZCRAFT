"""Build only the measured Skat delta over the immutable public jar100."""
from pathlib import Path
import argparse,hashlib,json,zipfile,statistics
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--profile',required=True);p.add_argument('--version',default='0.7.0-preview.110-skat-thrust')
a=p.parse_args();profile=Path(a.profile).resolve()
proof_file=profile/'skat-thrust-create.json'
d=json.loads(proof_file.read_text(encoding='utf-8-sig'))
assert d['passed'] and d['process_exit_code']==0 and d['dh_loaded'] and not d['owner_profile_used']
samples=[s for s in d['propulsion_samples'] if s['phase']==3 and 'kmh' in s]
mean=statistics.mean(s['kmh'] for s in samples[-20:])
assert 45<=mean<=51 and max(s['kmh'] for s in samples)<=53,(mean,'50 km/h target not met')
assert d['passed_runtime'] and d['pilot_failsafe'] and d['max_tilt_all_phases']<.35
assert d['natural_sea_route_density_checked'] and d['terrain_cleared_for_trial'] is False
assert d['terrain_in_world_bounds']==[], 'Terrain collision invalidates the open-water speed measurement'
assert d['reverse_native_control'] and d['reverse_signed_speed_mps']<-.5
assert d['structure_cells_checked']==1509 and d['cargo_containers']==24 and d['cargo_slots']==1296
stage=ROOT/'.verification/skat-thrust-release-20261030';stage.mkdir(exist_ok=True)
base=ROOT/'build/libs/interstice-0.7.0-preview.100-skat-speed.jar'
sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(base.read_bytes())=='3ff24eec51cb3f427195ed164d5ab347be7ba1267f46eba5467be14aee37cd06'
classes=ROOT/'.verification/skat-thrust-isolated/build/classes/java/main'
paths=['pro/erez/interstice/ship/'+n+'.class' for n in ['SkatPropellerEntity','SkatHelmEntity','SkatPropulsion']]
new_bytes={name:(classes/name).read_bytes() for name in paths}
accepted=json.loads((ROOT/'.verification/skat-20261029/accepted-classes.json').read_text(encoding='utf8'))
assert accepted['proof_sha256']==sha(proof_file.read_bytes())
assert all(accepted['class_sha256'][n]==sha(b) for n,b in new_bytes.items()),'Tested classes changed'
jar=stage/('interstice-'+a.version+'.jar')
assert not jar.exists(),'Candidate output is immutable; use a new version after changes'
modified=[]
with zipfile.ZipFile(base) as old,zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
    assert paths[-1] not in old.namelist()
    for item in old.infolist():
        data=old.read(item.filename)
        if item.filename in new_bytes:data=new_bytes[item.filename]
        elif item.filename in ['META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF']:
            data=data.replace(b'0.7.0-preview.100-skat-speed',a.version.encode())
        if data!=old.read(item.filename):modified.append(item.filename)
        out.writestr(item,data)
    entry=zipfile.ZipInfo(paths[-1],(2026,10,30,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
    out.writestr(entry,new_bytes[paths[-1]])
with zipfile.ZipFile(base) as old,zipfile.ZipFile(jar) as new:
    assert new.testzip() is None
    allowed=set(paths+['META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF'])
    preserved=[n for n in old.namelist() if n not in allowed]
    assert all(new.read(n)==old.read(n) for n in preserved)
    assert set(new.namelist())-set(old.namelist())=={paths[-1]}
    assert all(new.read(n)==b for n,b in new_bytes.items())
    assert a.version.encode() in new.read('META-INF/neoforge.mods.toml')
result=dict(passed=True,mod_version=a.version,jar=str(jar),jar_sha256=sha(jar.read_bytes()),
    base_jar_sha256=sha(base.read_bytes()),preserved_entries=len(preserved),modified_entries=modified,
    added_entries=[paths[-1]],class_sha256={n:sha(b) for n,b in new_bytes.items()},
    proof=str(proof_file),proof_sha256=sha(proof_file.read_bytes()),measured_last_10s_kmh=mean,
    maximum_measured_kmh=max(s['kmh'] for s in samples),max_tilt_radians=d['max_tilt_all_phases'],
    public_server_changed=False,owner_profiles_modified=False)
(stage/'input.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print(json.dumps(result))
