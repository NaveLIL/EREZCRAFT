from pathlib import Path
import zipfile,json
cache=Path('C:/Users/Dimon/.gradle/caches')
forge=next(cache.glob('modules-2/files-2.1/net.neoforged/neoforge/21.1.252/*/*sources.jar'))
with zipfile.ZipFile(forge) as z:
    for n in z.namelist():
        if n.endswith(('LivingJumpEvent.java','LivingChangeTargetEvent.java','EntityJoinLevelEvent.java')):print(n+'\n'+z.read(n).decode()[:6500])
game=next(cache.glob('neoformruntime/intermediate_results/sourcesWithNeoForge_*_output.zip'))
with zipfile.ZipFile(game) as z:
    for n in ['net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java','net/minecraft/client/resources/sounds/SimpleSoundInstance.java']:
        s=z.read(n).decode();print(n+'\n'+s[:10000])
client=cache/'neoformruntime/artifacts/minecraft_1.21.1_client.jar'
with zipfile.ZipFile(client) as z:
    sounds=json.loads(z.read('assets/minecraft/sounds.json'))
    print('SOUNDS',json.dumps({k:v for k,v in sounds.items() if k in ['entity.cat.ambient','entity.cat.purr']}))
index=json.loads((cache/'neoformruntime/assets/indexes/17.json').read_text())
print('OGG',[k for k in index['objects'] if '/cat/' in k and k.endswith('.ogg')])
