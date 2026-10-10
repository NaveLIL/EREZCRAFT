"""Build a locally importable Modrinth spine pack from pinned original upstream files."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
def hash_bytes(data,algorithm):return hashlib.new(algorithm,data).hexdigest()

def main():
    lock=json.loads((ROOT/'distribution/techmagic-spine.lock.json').read_text(encoding='utf-8'))
    files=[]
    for mod in lock['required_spine']:
        name=mod.get('filename',mod.get('file'))
        local=ROOT/'research/dependencies'/name
        data=local.read_bytes()
        assert len(data)==mod['bytes'] and hash_bytes(data,'sha512')==mod['sha512'],name
        url=mod['download_url']
        assert url.startswith('https://cdn.modrinth.com/'),url
        files.append({'path':'mods/'+name,'hashes':{'sha1':hash_bytes(data,'sha1'),'sha512':mod['sha512']},
                      'env':{'client':'required','server':'required'},'downloads':[url],'fileSize':len(data)})
    index={'formatVersion':1,'game':'minecraft','versionId':'0.1.0-alpha.1-spine',
           'name':'EREZCRAFT Techmagic - first spine',
           'summary':'First verified client startup: Interstice, Create, Aeronautics/Sable and DH. Magic, interconnected recipes and flight acceptance are pending.',
           'files':files,'dependencies':{'minecraft':lock['minecraft'],'neoforge':lock['neoforge']}}
    own=ROOT/'build/libs/interstice-0.7.0-preview.2.jar'
    assert own.is_file()
    target=ROOT/'build/distributions/erezcraft-techmagic-0.1.0-alpha.1-spine.mrpack'
    target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as pack:
        pack.writestr('modrinth.index.json',json.dumps(index,ensure_ascii=False,indent=2)+'\n')
        pack.write(own,'overrides/mods/'+own.name)
        pack.write(ROOT/'distribution/DistantHorizons.toml','overrides/config/DistantHorizons.toml')
        pack.write(ROOT/'distribution/client-options.txt','client-overrides/options.txt')
        pack.write(ROOT/'distribution/client-jvm-args.txt','overrides/client-jvm-args.txt')
        pack.write(ROOT/'docs/MODPACK_DIRECTION.md','overrides/docs/MODPACK_DIRECTION.md')
        pack.write(ROOT/'distribution/techmagic-spine.lock.json','overrides/distribution/techmagic-spine.lock.json')
        pack.write(ROOT/'LICENSE','overrides/licenses/interstice/LICENSE')
        pack.writestr('overrides/README-TECHMAGIC.txt',
            'EREZCRAFT Techmagic 0.1.0-alpha.1: first technical spine.\n'
            'Import this .mrpack as a NEW profile. External mods download from pinned original Modrinth URLs.\n'
            'This is NOT the complete intended techmagic pack: magic modules, cross-mod recipes, quests and ship physics acceptance are pending.\n'
            'Client startup passed; world saves are not included and existing user profiles must not be replaced.\n'
            'Use Java21. Optional Generational ZGC arguments are supplied in client-jvm-args.txt.\n'
            'V6 requires /interstice explore v6; ordinary explore remains V5.\n')
    with zipfile.ZipFile(target) as pack:
        parsed=json.loads(pack.read('modrinth.index.json'))
        assert len(parsed['files'])==4
        assert pack.read('overrides/mods/'+own.name)==own.read_bytes()
        assert not any('/saves/' in name or name.startswith('world/') for name in pack.namelist())
    receipt={'passed':True,'scope':'Importable first spine, client startup only; not complete techmagic gameplay',
             'path':str(target),'sha256':hash_bytes(target.read_bytes(),'sha256'),'bytes':target.stat().st_size,
             'external_mods':len(files),'own_mod_sha256':hash_bytes(own.read_bytes(),'sha256'),
             'worlds_included':False,'full_gameplay_verified':False}
    (ROOT/'.verification/techmagic-spine-package.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt))
    return 0

if __name__=='__main__':raise SystemExit(main())
