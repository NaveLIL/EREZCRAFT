"""WT03: narrower continuous curved silhouettes and canonical hand-space correction."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
if (ROOT/'source/worktools_WT03.json').exists():raise SystemExit('Historical WT03 migration already completed; use build_design.py for current geometry')
history=ROOT/'reports/superseded_WT02';history.mkdir(exist_ok=True)
for p in (ROOT/'tools/build_design.py',ROOT/'reports/blender.json',ROOT/'reports/design_verification.json',ROOT/'preview/worktools_WT02.png'):
    if p.exists():shutil.copy2(p,history/p.name)
(history/'decision.json').write_text(json.dumps({'owner_decision':'Make forms smoother and narrower; hand orientation, especially left hand, looks wrong','next_revision':'WT03','gameplay':'Approved switchable modes retained'},indent=2),encoding='utf-8')
p=ROOT/'tools/build_design.py';s=p.read_text(encoding='utf-8')
s=s.replace('import json, zipfile, hashlib','import json, zipfile, hashlib, math')
s=s.replace("'#c8a7d4'","'#bdb2cb'")
a=s.index('def head(');b=s.index('\nwith zipfile.ZipFile(BASE)',a)
head='''def head(steel,kind):
    # A half-unit raster of a continuous profile gives gentle curves, without detached rotated blocks.
    # The same boxes are exported to Minecraft and Blender; there are no render-only curved surfaces.
    metal='steel' if steel else 'silver';shadow='dark' if steel else 'silver_shadow'
    pixels={};step=.5
    def inside(x,y,poly):
        yes=False;j=len(poly)-1
        for i,(xi,yi) in enumerate(poly):
            xj,yj=poly[j]
            if (yi>y)!=(yj>y) and x<(xj-xi)*(y-yi)/(yj-yi)+xi:yes=not yes
            j=i
        return yes
    if kind=='pickaxe':
        # Width12 instead of16. Both working ends taper continuously into a shallow forged arc.
        for ix in range(4,28):
            x=(ix+.5)*step;d=abs(x-8)/6;mid=13.45-2.7*d*d;thickness=1.45-.75*d
            lo=math.floor((mid-thickness/2)/step);hi=math.ceil((mid+thickness/2)/step)
            for iy in range(lo,hi):pixels[ix,iy]='edge' if d>.7 and iy==lo else metal
        for ix in range(14,18):
            for iy in range(22,28):pixels[ix,iy]=metal
    else:
        polygons={
          'axe':[(7.3,14.6),(5.5,14.9),(4.25,14.4),(3.4,13.6),(3.15,12.3),(3.4,11.1),(4.3,10.2),(5.7,10.3),(6.1,11.1),(8.8,11.1),(9.2,11.8),(10.8,11.8),(10.8,13.15),(9.1,13.15),(8.7,14.1),(7.3,14.1)],
          'shovel':[(7.4,10.3),(8.6,10.3),(8.9,11.5),(10,12.1),(10.25,13.3),(9.8,14.7),(8.8,15.9),(8,16.4),(7.2,15.9),(6.2,14.7),(5.75,13.3),(6,12.1),(7.1,11.5)],
          'hoe':[(7.25,10.5),(8.75,10.5),(8.75,11.7),(10.1,11.7),(10.1,12.7),(8.3,12.7),(7.1,13.7),(6.2,14.4),(3.2,14.4),(2.75,14),(2.75,12.9),(3.3,12.6),(6,12.6),(7.25,11.8)]}
        poly=polygons[kind]
        for ix in range(2,30):
            for iy in range(18,34):
                if inside((ix+.5)*step,(iy+.5)*step,poly):pixels[ix,iy]=metal
        for (ix,iy) in list(pixels):
            x=(ix+.5)*step;y=(iy+.5)*step
            if kind=='axe' and x<5 and (ix-1,iy) not in pixels:pixels[ix,iy]='edge'
            elif kind=='shovel' and y>12 and ((ix-1,iy) not in pixels or (ix+1,iy) not in pixels or (ix,iy+1) not in pixels):pixels[ix,iy]='edge'
            elif kind=='hoe' and x<6.5 and (ix,iy+1) not in pixels:pixels[ix,iy]='edge'
    todo=[next(iter(pixels))];seen=set(todo)
    while todo:
        x,y=todo.pop()
        for n in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if n in pixels and n not in seen:seen.add(n);todo.append(n)
    assert len(seen)==len(pixels),(kind,'disconnected curved profile')
    remaining=dict(pixels);counter=0;depth=1.8 if steel else 1.5
    while remaining:
        x,y=min(remaining,key=lambda k:(k[1],k[0]));role=remaining[x,y];w=1
        while remaining.get((x+w,y))==role:w+=1
        h=1
        while all(remaining.get((xx,y+h))==role for xx in range(x,x+w)):h+=1
        for xx in range(x,x+w):
            for yy in range(y,y+h):del remaining[xx,yy]
        box('curved_'+kind+'_'+str(counter),x*step,y*step,8-depth/2,w*step,h*step,depth,role);counter+=1
    box('eye_reinforcement',7.15,11.2,7-depth/2,1.7,2.0,depth+2,shadow)
    for z in (6.75-depth/2,9+depth/2):
        box('eye_pin_'+str(z),7.7,11.9,z,.6,.6,.25,'brass')
        if steel:box('small_inlay_'+str(z),7.4,12,z,.2,.4,.25,'cyan')
'''
s=s[:a]+head+s[b:]
# Our tool haft is authored upright. Vanilla handheld transforms assume the sprite's diagonal haft.
# Compose a -45deg local Z correction; left-hand transforms negate Y and Z internally.
s=s.replace("[0,-90,55]","[0,-90,10]").replace("[0,90,-55]","[0,90,-10]")
s=s.replace("[0,-90,25]","[0,-90,-20]").replace("[0,-90,-25]","[0,90,20]")
s=s.replace("'stage':'Visual candidate; special tool abilities are proposals, not active'","'stage':'WT03 visual candidate; implemented gameplay is recorded separately in reports/game_review.json'")
s=s.replace('WT02','WT03')
p.write_text(s,encoding='utf-8')
for name in ('prepare_blender.py','verify_design.py','prepare_native.py','native_tail.java.txt'):
    p=ROOT/'tools'/name;p.write_text(p.read_text(encoding='utf-8').replace('WT02','WT03'),encoding='utf-8')
print('WT03 narrower curves and hand transform corrections prepared')
