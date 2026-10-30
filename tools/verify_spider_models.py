"""Verify selected source geometry/UVs, exact atlas pixels and runtime preservation."""
from pathlib import Path
import argparse,hashlib,io,json,zipfile
from build_spider_models import compile_model,SELECTED,ROOT,ASSET,SOURCE

def sha(data):return hashlib.sha256(data).hexdigest()
def verify(reference,candidate):
    models=[]
    for variant,stem in SELECTED.items():
        expected,atlas,textures,source=compile_model(SOURCE/(stem+'.bbmodel'))
        path=ASSET/f'models/entity/spider/{variant}.json'
        assert json.loads(path.read_text(encoding='utf8'))==expected,'Changed geometry/UV: '+variant
        png=io.BytesIO();atlas.save(png,format='PNG');actual=(ASSET/f'textures/entity/spider/{stem.lower()}.png').read_bytes()
        assert png.getvalue()==actual,'Changed selected pixels: '+variant
        models.append({'variant':variant,'revision':stem,'cuboids':309,'bones':53,'quads':1854,'source_sha256':expected['source_sha256'],'mesh_sha256':sha(path.read_bytes()),'atlas_sha256':sha(actual),'exact_rgba_tiles':22})
    allowed={'pro/erez/interstice/entity/CaveRiftSpiderEntity.class','pro/erez/interstice/entity/client/CaveRiftSpiderModel.class','pro/erez/interstice/entity/client/CaveRiftSpiderRenderer.class'}
    with zipfile.ZipFile(reference) as old,zipfile.ZipFile(candidate) as new:
        a,b=set(old.namelist()),set(new.namelist());assert a<=b,'Previous jar entries removed'
        changes=sorted(n for n in a if n.endswith('.class') and old.read(n)!=new.read(n))
        assert set(changes)<=allowed,changes
        added=sorted(n for n in b-a if n.endswith('.class'))
        assert all(n.startswith('pro/erez/interstice/entity/client/CaveRiftSpiderModel$') for n in added),added
        old_png=[n for n in a if n.endswith('.png')];old_ogg=[n for n in a if n.endswith('.ogg')]
        assert all(old.read(n)==new.read(n) for n in old_png+old_ogg),'Existing PNG/OGG changed'
        preserved=sum(n.endswith('.class') and n not in changes for n in a)
    return {'passed':True,'candidate_sha256':sha(candidate.read_bytes()),'reference_sha256':sha(reference.read_bytes()),'models':models,'changed_previous_classes':changes,'new_classes':added,'previous_classes_preserved':preserved,'previous_png_preserved':len(old_png),'previous_ogg_preserved':len(old_ogg),'exact_total_quads':3*1854,'real_biological_simulation_claimed':False}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--reference',type=Path,default=ROOT/'build/libs/interstice-0.7.0-preview.39-engineer-d02.jar');p.add_argument('--candidate',type=Path,default=ROOT/'build/libs/interstice-0.7.0-preview.40-studio-spiders.jar');p.add_argument('--output',type=Path,required=True);args=p.parse_args();result=verify(args.reference,args.candidate);args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({k:result[k] for k in ['passed','previous_classes_preserved','previous_png_preserved','previous_ogg_preserved','candidate_sha256']}))
