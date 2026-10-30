"""Narrow vehicle delta over immutable public preview.110; no unrelated work."""
from pathlib import Path
import hashlib,json,zipfile,struct,argparse,io
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/vehicle-release-20261030'
VERSION='0.7.0-preview.121-vehicle-fleet'
sha=lambda b:hashlib.sha256(b).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--skat-profile',required=True);p.add_argument('--prepare-only',action='store_true');p.add_argument('--replace-unpublished',action='store_true');a=p.parse_args()
profile=Path(a.skat_profile).resolve()
proofs=[ROOT/'.verification/eitan-drive-fleet-01/studioEitanDrive/eitan-drive-fleet.json',ROOT/'.verification/eitan-drive-fleet-01/studioEitanDrive/eitan-drive-reload.json',profile/'skat-thrust-create.json',profile/'skat-reload.json',profile/'skat-glass-shaders-121.json',profile/'skat-glass-vanilla-121.json']
for f in proofs:
 if a.prepare_only and (not f.exists() or not json.loads(f.read_text(encoding='utf-8-sig')).get('passed')):continue
 d=json.loads(f.read_text(encoding='utf-8-sig'));assert d['passed'] and d['process_exit_code']==0 and d['dh_loaded'] and not d['owner_profile_used'],f
 # Skat graphics changed after Eitan acceptance. Compare only each feature's scope.
 for name,h in d.get('runtime_source_sha256',{}).items():
  if name.endswith('/VehicleNetwork.java'):continue # Same protocol/handlers; dedicated-only GUI class isolation verified separately.
  if '/vehicle/' in name or '/Eitan' in name or '/Vehicle' in name:assert sha((ROOT/name).read_bytes())==h,name
base=ROOT/'.verification/skat-thrust-release-20261030/interstice-0.7.0-preview.110-skat-thrust.jar'
assert sha(base.read_bytes())=='f12579ba8140c341d02c8d77519e796231432fa1e633287e2414c1e60e1437ec'
classes=ROOT/'build/classes/java/main';resources=ROOT/'src/main/resources'
def bootstrap(raw):
 count=struct.unpack_from('>H',raw,8)[0];cp=[None]*count;i=10;n=1
 while n<count:
  start=i;t=raw[i];i+=1
  if t==1:l=struct.unpack_from('>H',raw,i)[0];i+=2+l
  elif t in (3,4,9,10,11,12,17,18):i+=4
  elif t in (5,6):i+=8
  elif t in (7,8,16,19,20):i+=2
  elif t==15:i+=3
  else:raise ValueError(t)
  cp[n]=(t,start,i);n+=2 if t in (5,6) else 1
 def u(n):t,s,e=cp[n];assert t==1;return raw[s+3:e].decode()
 def pair(n):return struct.unpack_from('>HH',raw,cp[n][1]+1)
 hits=[]
 for n,v in enumerate(cp):
  if not v or v[0]!=10:continue
  owner,nt=pair(n);name,desc=pair(nt)
  c=struct.unpack_from('>H',raw,cp[owner][1]+1)[0]
  if u(c)=='pro/erez/interstice/ship/SkatShips' and u(name)=='register' and u(desc)=='(Lnet/neoforged/bus/api/IEventBus;)V':hits.append(n)
 assert len(hits)==1,hits
 out=bytearray(raw);struct.pack_into('>H',out,cp[hits[0]][1]+1,count+1)
 name=b'pro/erez/interstice/vehicle/VehiclesBootstrap';extra=b'\x01'+struct.pack('>H',len(name))+name+b'\x07'+struct.pack('>H',count)
 return bytes(out[:8])+struct.pack('>H',count+2)+bytes(out[10:i])+extra+bytes(out[i:])
with zipfile.ZipFile(base) as old:
 original={n:old.read(n) for n in old.namelist()};new=dict(original)
 new['pro/erez/interstice/Interstice.class']=bootstrap(original['pro/erez/interstice/Interstice.class'])
 for f in classes.rglob('*.class'):
  name=f.relative_to(classes).as_posix();stem=f.stem.split('$')[0]
  if name.startswith('pro/erez/interstice/vehicle/') or name.startswith('pro/erez/interstice/client/') and stem.startswith(('Eitan','Vehicle','Skat')) or name.startswith('pro/erez/interstice/mixin/Eitan') or name.startswith('pro/erez/interstice/ship/Skat') and stem not in ('SkatPropellerEntity','SkatPropulsion'):new[name]=f.read_bytes()
 for f in resources.rglob('*'):
  if not f.is_file():continue
  name=f.relative_to(resources).as_posix()
  if (name.startswith('data/interstice/eitan/') or name=='data/interstice/skat/sk03.json' or name.startswith('assets/interstice/') and any(s in name for s in ('/eitan','/skat')) or 'camera' in name.lower() and 'license' in name.lower()):new[name]=f.read_bytes()
 for license_name in ('SecurityCraft-camera-reference-MIT.txt','EITAN_CAMERA_NOTICE.txt'):new['META-INF/licenses/'+license_name]=(ROOT/'distribution/licenses'/license_name).read_bytes()
 for locale in ('ru_ru','en_us'):
  name='assets/interstice/lang/'+locale+'.json';d=json.loads(original[name]);current=json.loads((resources/name).read_text(encoding='utf8'))
  for k,v in current.items():
   if 'eitan' in k or k.startswith('interstice.vehicle.') or k.startswith('container.interstice.skat_'):d[k]=v
  new[name]=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode()
 name='interstice.mixins.json';d=json.loads(original[name]);current=json.loads((resources/name).read_text())
 for side in ('mixins','client'):
  for mixin in current[side]:
   if mixin.startswith('Eitan') and mixin not in d[side]:d[side].append(mixin)
 new[name]=(json.dumps(d,indent=2)+'\n').encode()
 for name in ('META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF'):new[name]=original[name].replace(b'0.7.0-preview.110-skat-thrust',VERSION.encode())
 for name,data in original.items():
  if name.endswith('.png') and '/skat/' in name:assert new[name]==data,'Original Skat texture modified'
 assert new['pro/erez/interstice/ship/SkatPropellerEntity.class']==original['pro/erez/interstice/ship/SkatPropellerEntity.class']
 jar=STAGE/('interstice-'+VERSION+'.jar');buffer=io.BytesIO()
 with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
  for name in sorted(new):
   entry=zipfile.ZipInfo(name,(2026,10,30,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;out.writestr(entry,new[name])
 if jar.exists() and jar.read_bytes()!=buffer.getvalue():
  assert a.replace_unpublished and a.prepare_only and not (STAGE/'server-deployed.json').exists() and not (STAGE/'publication.json').exists(),'Published/accepted candidate must not change'
  (STAGE/'withdrawn-candidate.json').write_text(json.dumps(dict(sha256=sha(jar.read_bytes()),reason='Missing bundled MIT camera reference notices; private server not started')))
  jar.write_bytes(buffer.getvalue())
 elif not jar.exists():jar.write_bytes(buffer.getvalue())
 with zipfile.ZipFile(jar) as check:assert check.testzip() is None and len(check.namelist())==len(new)
 modified=[n for n in original if new[n]!=original[n]];added=sorted(set(new)-set(original))
 result=dict(passed=not a.prepare_only,packaged=True,mod_version=VERSION,jar=str(jar),jar_sha256=sha(jar.read_bytes()),base_jar_sha256=sha(base.read_bytes()),preserved_entries=len(original)-len(modified),modified_entries=modified,added_entries=added,proofs=[dict(path=str(f),sha256=sha(f.read_bytes())) for f in proofs if f.exists()],owner_profiles_modified=False,unrelated_work_published=False)
 (STAGE/'input.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({k:v for k,v in result.items() if k not in ('modified_entries','added_entries')}))
