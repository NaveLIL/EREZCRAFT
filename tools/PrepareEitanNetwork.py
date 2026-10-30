"""Stage a disposable dedicated gameplay pack, retaining original third-party bytes.
The production-only PPE TPA utility is outside this vehicle probe. No auth, worlds,
launcher state or owner options are copied.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--run-root',type=Path,required=True);a=p.parse_args()
out=a.run_root.resolve();assert out.parent==ROOT/'.verification' and out.name.startswith('eitan-drive-network-')
assert not out.exists(),'Fresh dedicated verification root required'
source=ROOT/'.verification/tide-release-20261028/client'
exclude={'Controlling-neoforge-1.21.1-19.0.5.jar','derenderpatcher-2.0.0.jar','erezcraft-client-ui-0.1.0.jar','ImmediatelyFast-NeoForge-1.6.14-1.21.1.jar','InventoryProfilesNext-neoforge-1.21.1-2.2.5.jar','iris-neoforge-1.8.14-beta.1-mc1.21.1.jar','lambdynamiclights-4.8.11-1.21.1.jar','libIPN-neoforge-1.21.1-6.6.3.jar','MouseTweaks-neoforge-mc1.21-2.26.1.jar','Searchables-neoforge-1.21.1-1.0.2.jar','sodium-neoforge-0.8.13-mc1.21.1.jar','xaerominimap-neoforge-1.21.1-26.6.0.jar','xaeroworldmap-neoforge-1.21.1-1.47.0.jar'}
all_jars=sorted((source/'mods').glob('*.jar'));assert len(all_jars)==79 and exclude<={f.name for f in all_jars}
server=[f for f in all_jars if f.name not in exclude];assert len(server)==66
provided=('interstice-','DistantHorizons-','lootr-','SmartBrainLib-','azurecortex-')
profile=out/'eitanNetworkServer';(profile/'mods').mkdir(parents=True)
rows=[]
for f in server:
    supplied=f.name.startswith(provided)
    if not supplied:
        dest=profile/'mods'/f.name
        try:os.link(f,dest)
        except OSError:shutil.copyfile(f,dest)
    rows.append(dict(filename=f.name,sha256=hashlib.sha256(f.read_bytes()).hexdigest(),runtime_supplied=supplied))
assert sum(not r['runtime_supplied'] for r in rows)==61
(profile/'eitan-automated-network-profile').write_text('disposable-eitan-loopback\n')
(profile/'eula.txt').write_text('eula=true\n')
(profile/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25576\nonline-mode=false\nlevel-name=eitan-network-owned\nlevel-seed=20261029\ngamemode=creative\ndifficulty=peaceful\nallow-flight=false\nview-distance=8\nsimulation-distance=5\nspawn-protection=0\nmax-players=2\nenable-query=false\nenable-rcon=false\n')
(profile/'config/voicechat').mkdir(parents=True)
(profile/'config/voicechat/voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24476\n')
client=out/'studioEitanNetwork';client.mkdir();(client/'eitan-automated-network-profile').write_text('disposable-eitan-loopback\n')
report=dict(gameplay_server_jars=66,production_only_utility_not_included='PPE Essentials TPA',runtime_supplied=5,rows=rows,owner_data_copied=False)
(out/'staging.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(root=str(out),server_jars=66,external_jars=61,owner_data_copied=False)))
