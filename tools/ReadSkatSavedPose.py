from pathlib import Path
import gzip,struct,json,zipfile
class NBT:
 def __init__(self,b):self.b=b;self.i=0
 def read(self,n):v=self.b[self.i:self.i+n];self.i+=n;return v
 def number(self,f):return struct.unpack('>'+f,self.read(struct.calcsize(f)))[0]
 def string(self):return self.read(self.number('H')).decode('utf8',errors='replace')
 def tag(self,t):
  if t==1:return self.number('b')
  if t in (2,3,4,5,6):return self.number({2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.read(self.number('i')).hex()
  if t==8:return self.string()
  if t==9:
   kind=self.number('b');return [self.tag(kind) for _ in range(self.number('i'))]
  if t==10:
   d={}
   while True:
    kind=self.number('b')
    if kind==0:return d
    name=self.string();d[name]=self.tag(kind)
  if t in (11,12):return [self.number('i' if t==11 else 'q') for _ in range(self.number('i'))]
 def root(self):t=self.number('b');self.string();return self.tag(t)

import argparse,uuid,zlib,hashlib
p=argparse.ArgumentParser();p.add_argument('--profile',required=True);a=p.parse_args();profile=Path(a.profile).resolve();root=Path(__file__).resolve().parents[1]
assert profile.is_relative_to(root/'.verification') and not (profile/'skat-owner-profile').exists()
prior=json.loads((profile/'skat-create.json').read_text(encoding='utf-8-sig'));target=prior['ship_uuid'];folder=profile/'saves/skat-owned/dimensions/interstice/islands_v6/sublevels'
def entry(path,index,sector):
 b=path.read_bytes();span=struct.unpack_from('>I',b,index*4)[0]
 if not span:return None
 at=(span>>8)*sector;length,flag=struct.unpack_from('>IB',b,at);assert not flag&128,'External entry needs explicit support'
 data=b[at+5:at+4+length];raw=zlib.decompress(data,31) if data[:2]==b'\x1f\x8b' else data
 return NBT(raw).root()
found=[]
for f in folder.glob('*.slvlr'):
 header=f.read_bytes()[:4096]
 for i in range(1024):
  if not struct.unpack_from('>I',header,i*4)[0]:continue
  d=entry(f,i,128)
  for pointer in d.get('pointers',[]):
   storage,slot=pointer>>16,pointer&65535;source=f.with_name(f.name[:-6]+'.'+str(storage)+'.slvls');v=entry(source,slot,4096)
   if not v or 'uuid' not in v:continue
   identifier=str(uuid.UUID(bytes=b''.join(struct.pack('>I',n&0xffffffff) for n in v['uuid'])))
   if identifier==target:
    at=v['pose']['position'];found.append(dict(uuid=identifier,x=at['x'],y=at['y'],z=at['z'],source=source.name,slot=slot,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),world_modified=False))
assert len(found)==1,found
(profile/'skat-saved-location.json').write_text(json.dumps(found[0],indent=2)+'\n');print(json.dumps(found[0]))
