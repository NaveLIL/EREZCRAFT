"""Extract compile-only API jars from the original pinned local pack, never repackage them."""
from pathlib import Path
import shutil, zipfile, hashlib, json
root=Path(__file__).resolve().parents[1]
mods=root/'.verification/tide-release-20261028/client/mods'
dest=root/'.verification/skat-20261029/apis';dest.mkdir(parents=True,exist_ok=True)
pending=[mods/n for n in ['create-1.21.1-6.0.10.jar','create-aeronautics-bundled-1.21.1-1.3.2.jar','sable-neoforge-1.21.1-2.0.6.jar','lambdynamiclights-4.8.11-1.21.1.jar']]
seen={}
while pending:
    p=pending.pop()
    if p.name in seen:continue
    d=dest/p.name
    if p!=d:shutil.copyfile(p,d)
    seen[p.name]=hashlib.sha256(d.read_bytes()).hexdigest()
    with zipfile.ZipFile(d) as z:
        for n in z.namelist():
            if n.endswith('.jar'):
                q=dest/Path(n).name;q.write_bytes(z.read(n));pending.append(q)
(dest.parent/'compile-api-hashes.json').write_text(json.dumps(seen,indent=2))
print('\n'.join(seen))
