from pathlib import Path
import argparse,os,shutil,zipfile,subprocess,hashlib,json
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description="Verify frontal spider fix preserves all other runtime classes and assets")
p.add_argument('--reference',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--javap',type=Path)
args=p.parse_args();old=args.reference;new=args.candidate
javap=args.javap or (Path(os.environ['JAVA_HOME'])/'bin/javap.exe' if 'JAVA_HOME' in os.environ else shutil.which('javap'))
if not javap:raise SystemExit('Set JAVA_HOME or pass --javap for the code-preservation comparison')
def sha(b):return hashlib.sha256(b).hexdigest()
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assert set(a.namelist())<=set(b.namelist());classes=[n for n in a.namelist() if n.endswith('.class')];changed=[n for n in classes if a.read(n)!=b.read(n)]
 expected={'pro/erez/interstice/entity/CaveRiftSpiderEntity.class','pro/erez/interstice/entity/client/CaveRiftSpiderRenderer.class'};debug={'pro/erez/interstice/entity/CaveRiftSpiderEntity$1.class','pro/erez/interstice/entity/CaveRiftSpiderEntity$Variant.class'}
 assert set(changed)<=expected|debug,changed
 semantic=[]
 for n in set(changed)&debug:
  name=n[:-6].replace('/','.');before=subprocess.check_output([str(javap),'-classpath',str(old),'-c','-p',name]);after=subprocess.check_output([str(javap),'-classpath',str(new),'-c','-p',name]);assert before==after,'Nested bytecode changed: '+n;semantic.append({'class':n,'javap_code_sha256':sha(before)})
 protected=[n for n in a.namelist() if n.endswith(('.png','.ogg')) or n.startswith('assets/interstice/models/entity/spider/')];assert all(a.read(n)==b.read(n) for n in protected),'Visual/audio source changed'
 result={'passed':True,'reference_sha256':sha(old.read_bytes()),'candidate_sha256':sha(new.read_bytes()),'changed_logic_classes':sorted(set(changed)-debug),'debug_line_shifts_only':semantic,'previous_classes_byte_exact':len(classes)-len(changed),'previous_classes_same_code':len(classes)-len(set(changed)-debug),'png_preserved':sum(n.endswith('.png') for n in protected),'ogg_preserved':sum(n.endswith('.ogg') for n in protected),'mesh_json_preserved':sum(n.startswith('assets/interstice/models/entity/spider/') and n.endswith('.json') for n in protected)}
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
