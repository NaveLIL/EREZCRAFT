"""Keep predecessor resource generators from restoring the retired flat armor sprites."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ai01')
changes=[]
for name,param in [('tools/build_expedition_progression.py','value'),('tools/build_aegis_resources.py','data')]:
    p=CAND/name;s=p.read_text(encoding='utf-8');lines=s.splitlines(True)
    at=next(i for i,line in enumerate(lines) if line.startswith('def write('))
    guard=f'''    # Derived armor miniatures are authored by creations/15_armor_inventory_icons.
    if path.exists() and {param}.get("loader") != "interstice:armor_icon":
        previous = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(previous, dict) and previous.get("loader") == "interstice:armor_icon":
            return
'''
    if 'Derived armor miniatures are authored' not in s:lines.insert(at+1,guard);p.write_text(''.join(lines),encoding='utf-8')
    changes.append(name)
(ROOT/'reports/generator_guards.json').write_text(json.dumps({'candidate_files':changes,'project_kotomyaf_generator':'The same own-loader guard is installed in project 14 at integration.'},indent=2),encoding='utf-8')
print('Protected predecessor generators:',', '.join(changes))
