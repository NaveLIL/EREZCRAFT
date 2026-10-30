import json
from pathlib import Path
d=json.loads(Path('D:/repos/Minecraft/EREZCRAFT/art/sources/cc0/sea_spider/sea_spider.bbmodel').read_text())
groups={g['uuid']:g for g in d['groups']}; elements={e['uuid']:e for e in d['elements']}
def walk(n,depth=0):
    if isinstance(n,str):
        e=elements[n]; print(' '*depth,e['uuid'][:8],e['from'],e['to'])
    else:
        g=groups[n['uuid']];print(' '*depth,g['name'],g['origin'],g['rotation'])
        for c in n['children']:walk(c,depth+1)
walk(d['outliner'][0])
