"""Independent source-corner/RGBA/clip checks and prior-candidate preservation."""
from pathlib import Path
import json,base64,io,hashlib,zipfile,math,argparse,subprocess
import numpy as np
from PIL import Image
R=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def verify(reference,candidate):
 s=json.loads((R/'art/fauna/lozhnokoren/lozhnokoren_LK01.bbmodel').read_text(encoding='utf8'));m=json.loads((R/'src/main/resources/assets/interstice/models/entity/lozhnokoren.json').read_text(encoding='utf8'));assert m['animations']==s['animations'];atlas=Image.open(R/'src/main/resources/assets/interstice/textures/entity/lozhnokoren_lk01.png').convert('RGBA')
 for i,t in enumerate(s['textures']):
  image=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA');assert image.size==(16,16)
  for dx in range(0,64,16):
   for dy in range(0,64,16):assert atlas.crop((i%4*64+dx,i//4*64+dy,i%4*64+dx+16,i//4*64+dy+16)).tobytes()==image.tobytes()
 groups={g['name']:g for g in s['groups']};parts={p['uuid']:p for p in m['parts']};points=[];max_error=0
 for e in s['elements']:
  p=parts[e['uuid']];origin=np.array(groups[p['bone']]['origin'],dtype=float);angles=np.radians(e.get('rotation',[0,0,0]));a,b,c=angles
  rx=np.array([[1,0,0],[0,math.cos(a),-math.sin(a)],[0,math.sin(a),math.cos(a)]]);ry=np.array([[math.cos(b),0,math.sin(b)],[0,1,0],[-math.sin(b),0,math.cos(b)]]);rz=np.array([[math.cos(c),-math.sin(c),0],[math.sin(c),math.cos(c),0],[0,0,1]])
  pivot=np.array(e['origin']);expected=[]
  for x in (e['from'][0],e['to'][0]):
   for y in (e['from'][1],e['to'][1]):
    for z in (e['from'][2],e['to'][2]):expected.append(rz@ry@rx@(np.array([x,y,z])-pivot)+pivot)
  actual=[origin+np.array([v[0],-v[1],v[2]]) for v in p['vertices']]
  for v in actual:max_error=max(max_error,min(np.linalg.norm(v-q) for q in expected));points.append(v/16)
 assert max_error<1e-7,max_error
 points=np.array(points);dims=points.max(axis=0)-points.min(axis=0)
 with zipfile.ZipFile(reference) as a,zipfile.ZipFile(candidate) as b:
  old=set(a.namelist());new=set(b.namelist());assert old<=new
  changed=[n for n in old if n.endswith('.class') and a.read(n)!=b.read(n)];allowed={'pro/erez/interstice/Interstice.class','pro/erez/interstice/ToxicLiquidBlock.class','pro/erez/interstice/fauna/RealmFaunaHazards.class'}
  debug=[]
  for n in set(changed)-allowed:
   assert n.startswith('pro/erez/interstice/Interstice$'),n
   exe=R/'.verification/toolchain/jdk-21.0.12.1+1/bin/javap.exe';name=n[:-6].replace('/','.')
   assert subprocess.check_output([str(exe),'-classpath',str(reference),'-c','-p',name])==subprocess.check_output([str(exe),'-classpath',str(candidate),'-c','-p',name]);debug.append(n)
  media=[n for n in old if n.endswith(('.png','.ogg'))];assert all(a.read(n)==b.read(n) for n in media)
  data=[n for n in old if n.startswith('data/')];assert all(a.read(n)==b.read(n) for n in data)
 return {'passed':True,'candidate_sha256':sha(candidate),'reference_sha256':sha(reference),'source_sha256':sha(R/'art/fauna/lozhnokoren/lozhnokoren_LK01.bbmodel'),'source_cubes':len(s['elements']),'bones':len(s['groups']),'quads':sum(len(p['faces']) for p in m['parts']),'source_clips_exact':True,'exact_rgba_tiles':len(s['textures']),'corner_error_model_units':max_error,'dimensions_source_xyz_blocks':dims.tolist(),'previous_classes_byte_exact':sum(n.endswith('.class') and n not in changed for n in old),'changed_previous_classes':sorted(changed),'debug_line_shifts_only':debug,'previous_png_byte_exact':sum(n.endswith('.png') for n in media),'previous_ogg_byte_exact':sum(n.endswith('.ogg') for n in media),'previous_data_files_byte_exact':len(data)}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--reference',type=Path,default=R/'build/libs/interstice-0.7.0-preview.43-lower-ocean-oc01.jar');p.add_argument('--candidate',type=Path,default=R/'build/libs/interstice-0.7.0-preview.44-lozhnokoren.jar');p.add_argument('--output',type=Path,required=True);a=p.parse_args();result=verify(a.reference,a.candidate);a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
