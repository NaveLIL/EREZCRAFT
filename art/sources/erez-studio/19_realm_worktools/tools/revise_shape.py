"""WT02 replaces disconnected angled segments with connected grid silhouettes."""
from pathlib import Path
import shutil,json
ROOT=Path(__file__).resolve().parents[1]
if (ROOT/'source/worktools_WT02.json').exists():raise SystemExit('Historical one-time WT01 migration already completed; use build_design.py for current geometry')
history=ROOT/'reports/rejected_WT01';history.mkdir(exist_ok=True)
for p in (ROOT/'tools/build_design.py',ROOT/'preview/worktools_WT01.png',ROOT/'preview/worktools_previous.png',ROOT/'reports/blender.json'):
    if p.exists():shutil.copy2(p,history/p.name)
(history/'decision.json').write_text(json.dumps({'owner_decision':'Rejected: ugly, crooked and fragmented','next_revision':'WT02: continuous pixel silhouettes, no angled disconnected tips'},indent=2),encoding='utf-8')
p=ROOT/'tools/build_design.py';s=p.read_text(encoding='utf-8');a=s.index('def head(');b=s.index('\nwith zipfile.ZipFile(BASE)',a)
head='''def head(steel,kind):
    # Native one-unit grid. Greedy rectangles share exact edges, without angled fragments.
    metal='steel' if steel else 'silver';shadow='dark' if steel else 'silver_shadow'
    pixels={}
    def row(y,left,right,edge=False):
        for x in range(left,right+1):pixels[x,y]='edge' if edge else metal
    if kind=='pickaxe':
        row(14,3,12,True);row(13,1,14)
        for y,left,right in ((12,0,4),(12,6,9),(12,11,15),(11,0,2),(11,6,9),(11,13,15),(10,0,1),(10,7,8),(10,14,15)):row(y,left,right)
        for x in (0,15):
            for y in (10,11,12):pixels[x,y]='edge'
    elif kind=='axe':
        for y,l,r in ((15,3,6),(14,2,9),(13,1,11 if not steel else 12),(12,1,11 if not steel else 12),(11,1,9),(10,2,9),(9,2,5)):
            row(y,l,r);pixels[l,y]='edge'
    elif kind=='shovel':
        for y,l,r in ((16,7,8),(15,6,9),(14,5,10),(13,5,10),(12,5,10),(11,6,9),(10,7,8)):
            row(y,l,r)
            if y>=12:pixels[l,y]=pixels[r,y]='edge'
        if steel:row(11,4,11)
    else:
        row(14,2,6,True);row(13,2,9);row(12,2,11);row(11,6,9);row(10,7,8)
    # Assert one connected metal head, including the actual socket area over the haft.
    todo=[next(iter(pixels))];seen=set(todo)
    while todo:
        x,y=todo.pop()
        for n in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if n in pixels and n not in seen:seen.add(n);todo.append(n)
    assert len(seen)==len(pixels),(kind,'disconnected head')
    remaining=dict(pixels);counter=0
    while remaining:
        x,y=min(remaining,key=lambda k:(k[1],k[0]));role=remaining[x,y];w=1
        while remaining.get((x+w,y))==role:w+=1
        h=1
        while all(remaining.get((xx,y+h))==role for xx in range(x,x+w)):h+=1
        for xx in range(x,x+w):
            for yy in range(y,y+h):del remaining[xx,yy]
        box('continuous_'+kind+'_'+str(counter),x,y,6.8 if steel else 7.0,w,h,2.4 if steel else 2.0,role);counter+=1
    box('eye_reinforcement',7.0,11.0,6.55 if steel else 6.8,2,2.4,2.9 if steel else 2.4,shadow)
    for z in (6.3 if steel else 6.55,9.45 if steel else 9.2):
        box('eye_pin_'+str(z),7.6,11.9,z,.8,.8,.25,'brass')
        if steel:box('small_inlay_'+str(z),7.22,11.98,z,.22,.6,.25,'cyan')
'''
s=(s[:a]+head+s[b:]).replace('WT01','WT02')
p.write_text(s,encoding='utf-8')
p=ROOT/'tools/prepare_blender.py';p.write_text(p.read_text(encoding='utf-8').replace('WT01','WT02'),encoding='utf-8')
print('WT02 continuous shapes prepared')
