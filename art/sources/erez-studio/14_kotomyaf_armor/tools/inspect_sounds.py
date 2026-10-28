from pathlib import Path
import json,zipfile,struct
root=Path('C:/Users/Dimon/.gradle/caches/neoformruntime/assets')
index=json.loads((root/'indexes/17.json').read_text())['objects']
def data(name):
    h=index[name]['hash'];return root/'objects'/h[:2]/h
sounds=json.loads(data('minecraft/sounds.json').read_text())
print({k:sounds[k] for k in ['entity.cat.ambient','entity.cat.purr']})
rows=[]
for k,v in index.items():
    if k.startswith('minecraft/sounds/mob/cat/') and ('meow' in k or 'purr' in k):
        raw=data(k).read_bytes();ident=raw.index(b'\x01vorbis');rate=struct.unpack_from('<I',raw,ident+12)[0]
        pos=0;granule=0
        while pos<len(raw):
            assert raw[pos:pos+4]==b'OggS';count=raw[pos+26]
            g=struct.unpack_from('<Q',raw,pos+6)[0]
            if g<2**63:granule=max(granule,g)
            pos+=27+count+sum(raw[pos+27:pos+27+count])
        rows.append({'asset':k,'sha1':v['hash'],'sample_rate':rate,'samples':granule,'seconds':granule/rate})
print(json.dumps(rows,indent=2))
out=Path(__file__).resolve().parents[1]/'reports/sound-sources.json';out.write_text(json.dumps({'origin':'Installed Minecraft Java 1.21.1 asset index 17','files':rows,'copied_into_mod':False},indent=2),encoding='utf-8')
forge=next(Path('C:/Users/Dimon/.gradle/caches').glob('modules-2/files-2.1/net.neoforged/neoforge/21.1.252/*/*sources.jar'))
with zipfile.ZipFile(forge) as z:
    s=z.read('net/neoforged/neoforge/event/entity/living/LivingEvent.java').decode();print(s[s.index('public static class LivingJumpEvent'):])
