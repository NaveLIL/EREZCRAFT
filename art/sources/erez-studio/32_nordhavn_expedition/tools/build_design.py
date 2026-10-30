"""NE01: shared boundaries, closed hull skin and overlapping window rebates.
Authoritative game geometry in metres. Published N96 outline is a reference;
room sizes and mechanisms are an explicitly adapted EREZCRAFT design.
"""
from pathlib import Path
import json,math,hashlib,csv,struct,zlib,random
ROOT=Path(__file__).resolve().parents[1]
for folder in ['source','textures','assembly','exports','preview','reports','verification','docs']:(ROOT/folder).mkdir(exist_ok=True)
P=dict(revision='NE01-A06',length_m=29.44,beam_m=7.32,published_draft_m=2.49,
 bridge_front_y_m=2.80,bow_rake_start_y_m=11.50,bow_rake_top_z_m=6.95,bow_rake_dy_per_down_m=.55,bow_rake_transition=.85,
 lower_floor_m=1.50,main_floor_m=4.20,upper_floor_m=6.95,fly_floor_m=9.70,
 wall_m=.18,glass_m=.036,glass_frame_overlap_m=.028,door_width_m=1.18,
 corridor_width_m=1.40,stair_width_m=1.28,nominal_route_headroom_m=2.20,
 coordinates='X across beam, Y stern(-) to bow(+), Z up; 1 unit = 1 m = 1 Minecraft block',
 source='Nordhavn N96 public profile/GA; game layout and material appearance adapted',
 status='authored geometry, runtime integration in progress')
parts=[];groups=[dict(id='ROOT',parent=None,pivot=[0,0,0]),dict(id='HULL',parent='ROOT',pivot=[0,0,0])]
for g in ['LOWER','MAIN','UPPER','FLY','FITTINGS','GLASS','ELECTRICAL']:groups.append(dict(id=g,parent='ROOT',pivot=[0,0,0]))
seams=[];windows=[];doors=[];stairs=[];beds=[];terminals=[];controls=[]
def add(name,group,material,shape,**kw):
    assert not any(p['name']==name for p in parts),name
    p=dict(id=hashlib.sha1(name.encode()).hexdigest()[:12],name=name,group=group,material=material,shape=shape,**kw);parts.append(p);return p
def box(n,g,m,c,s,bevel=0,rot=(0,0,0),**kw):return add(n,g,m,'box',center=c,size=s,bevel=bevel,rotation=rot,**kw)
def mesh(n,g,m,v,f,**kw):return add(n,g,m,'mesh',vertices=v,faces=f,**kw)
def beam(n,g,m,a,b,width=.035):return add(n,g,m,'beam',a=a,b=b,width=width)
def cyl(n,g,m,c,r,d,axis='Z',**kw):return add(n,g,m,'cylinder',center=c,radius=r,depth=d,axis=axis,**kw)
def ring_y(n,g,m,c,r,t,d):
    v=[];steps=24
    for yy,rr in [(-d/2,r),(d/2,r),(-d/2,r-t),(d/2,r-t)]:
        v.extend([(c[0]+rr*math.cos(k*math.tau/steps),c[1]+yy,c[2]+rr*math.sin(k*math.tau/steps)) for k in range(steps)])
    f=[]
    for k in range(steps):
        q=(k+1)%steps;f.extend([(k,q,steps+q,steps+k),(2*steps+k,3*steps+k,3*steps+q,2*steps+q),(k,2*steps+k,2*steps+q,q),(steps+k,steps+q,3*steps+q,3*steps+k)])
    return mesh(n,g,m,v,f)
def prism(n,g,m,xy,z0,z1,holes=None):
    nn=len(xy);v=[(x,y,z) for z in [z0,z1] for x,y in xy]
    f=[list(range(nn-1,-1,-1)),list(range(nn,2*nn))]+[(i,(i+1)%nn,(i+1)%nn+nn,i+nn) for i in range(nn)]
    return mesh(n,g,m,v,f,cutouts=holes or [])

# Continuous open-top shell, with solid transom and bow end caps. One source mesh.
stations=[(-14.72,2.98,.55),(-13,3.50,.15),(-10,3.66,0),(-6,3.66,0),(0,3.66,0),(6,3.40,.05),(9.5,2.90,.35),(12,2,.90),(14.2,.70,1.90),(14.72,.025,3.55)]
def section(w,k,inner=False):
    t=min(.19,w*.35) if inner else 0
    chine=min(4.05,max(k+.45,1.65+k*.25))
    return [(-w+t,4.18),(-w*.95+t,chine),(-w*.62+t,k+.30+t),(0,k+t),(w*.62-t,k+.30+t),(w*.95-t,chine),(w-t,4.18)]
hv=[]
for inside in [False,True]:
    for y,w,k in stations:hv.extend([(x,y,z) for x,z in section(w,k,inside)])
nn=len(stations)*7;hf=[]
for inside in [0,1]:
    off=inside*nn
    for j in range(len(stations)-1):
        for k in range(6):hf.append((off+j*7+k,off+j*7+k+1,off+(j+1)*7+k+1,off+(j+1)*7+k))
for j in range(len(stations)-1):
    for k in [0,6]:hf.append((j*7+k,(j+1)*7+k,nn+(j+1)*7+k,nn+j*7+k))
for j in [0,len(stations)-1]:
    for k in range(6):hf.append((j*7+k,nn+j*7+k,nn+j*7+k+1,j*7+k+1))
mesh('Continuous watertight hull skin','HULL','hull_ivory',hv,hf,seal_role='primary_shell',cutouts=[dict(center=[side*2.5,-14.72,3.7],size=[1.12,.8,1.20]) for side in [-1,1]])
# Waterline bands are separate surfaces sitting outside the primary shell.
for side in [-1,1]:
    for j in range(len(stations)-2):
        a,b=stations[j],stations[j+1]
        # Slim metallic rubbing strip follows the same station endpoints.
        def strip_x(st):return st[1]-.05*st[1]*.38/(4.18-1.65-st[2]*.25)+.012
        beam(f'Hull rubbing strip {side} {j}','FITTINGS','brushed_steel',(side*strip_x(a),a[0],3.80),(side*strip_x(b),b[0],3.80),.055)

aft_stair=dict(id='AFT_LOWER',x=1.85,y=-8.0,z=1.50,rise=2.70,steps=12,tread=.32,upper='MAIN')
fore_stair=dict(id='FORE_LOWER',x=0,y=7.95,z=1.50,rise=2.70,steps=12,tread=.32,upper='MAIN')
main_stair=dict(id='MAIN_UP',x=1.85,y=-8.9,z=4.20,rise=2.75,steps=12,tread=.30,upper='UPPER',opening_end=3.95,landing_probe_forward=3.75)
fly_stair=dict(id='FLY_UP',x=-1.70,y=-3.8,z=6.95,rise=2.75,steps=12,tread=.32,upper='FLY')
stairs=[aft_stair,fore_stair,main_stair,fly_stair]
def stair_cut(st,z):
    end=st.get('opening_end',4.425)
    return dict(center=[st['x'],st['y']+(end-.90)/2,z],size=[1.44,end+.90,.8])
outline=[(-min(w,3.48),y) for y,w,k in stations]+[(min(w,3.48),y) for y,w,k in reversed(stations)]
prism('Lower continuous floor','LOWER','interior_floor',[(-2.70,-13.55),(-2.70,7.9),(-1.9,10.4),(1.9,10.4),(2.70,7.9),(2.70,-13.55)],1.36,1.50)
main_deck=prism('Main structural deck','MAIN','teak',outline,4.02,4.20,[stair_cut(aft_stair,4.2)])
main_deck['post_cutouts']=[stair_cut(fore_stair,4.2)]
roof_outline=[(-3.02,-11.7),(-3.02,5.3),(-2.65,9.5),(-1.80,12.2),(0,14.72),(1.80,12.2),(2.65,9.5),(3.02,5.3),(3.02,-11.7)]
prism('Main roof and raised foredeck','UPPER','teak',roof_outline,6.77,6.95,[stair_cut(main_stair,6.95)])
# The raised forecastle is an enclosed continuation of the hull, not a floating
# decorative roof over open sky. Shared polygon endpoints carry its entire seam.
fore_section=[(-2.95,6.5),(-2.65,9.5),(-1.80,12.2),(0,14.72),(1.80,12.2),(2.65,9.5),(2.95,6.5)]
for i,(a,b) in enumerate(zip(fore_section,fore_section[1:])):
    x0,y0=a;x1,y1=b
    v=[(x0,y0,4.15),(x1,y1,4.15),(x1,y1,6.79),(x0,y0,6.79),
       (x0*.945,y0,4.15),(x1*.945,y1,4.15),(x1*.945,y1,6.79),(x0*.945,y0,6.79)]
    mesh(f'Raised forecastle shell {i}','HULL','hull_ivory',v,[(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)],seal_role='forecastle_shell')
for side in [-1,1]:box(f'Forecastle shoulder closure {side}','HULL','hull_ivory',(side*2.78,6.5,5.47),(.36,.18,2.64),seal_role='forecastle_shell')
fly_outline=[(-2.80,-6.1),(-2.80,1.4),(-2.3,3.3),(2.3,3.3),(2.80,1.4),(2.80,-6.1)]
prism('Upper roof and flybridge deck','FLY','teak',fly_outline,9.52,9.70,[stair_cut(fly_stair,9.7)])
for st in stairs:
    for i in range(st['steps']):
        z=st['z']+(i+1)*st['rise']/st['steps'];y=st['y']+(i+.5)*st['tread']
        box(f"{st['id']} tread {i}",st['upper'],'teak',(st['x'],y,z-.055),(1.28,st['tread']+.012,.11))
        box(f"{st['id']} riser {i}",st['upper'],'interior_white',(st['x'],y-st['tread']/2,z-st['rise']/st['steps']/2),(1.28,.03,st['rise']/st['steps']))
    for side in [-1,1]:
        a=(st['x']+side*.69,st['y']-.15,st['z']+.97);b=(st['x']+side*.69,st['y']+3.99,st['z']+st['rise']+.97)
        beam(st['id']+f' handrail {side}',st['upper'],'brushed_steel',a,b,.035)
        for k in [0,4,8,12]:
            yy=st['y']+k*.32;zz=st['z']+k*st['rise']/12
            beam(st['id']+f' baluster {side} {k}',st['upper'],'brushed_steel',(a[0],yy,zz),(a[0],yy,zz+.98),.025)
    begin=st['y']+st['steps']*st['tread']+.006;end=st['y']+st.get('opening_end',4.425)
    box(st['id']+' continuous upper landing',st['upper'],'teak',(st['x'],(begin+end)/2,st['z']+st['rise']-.07),(1.44,end-begin,.14))
    # The native player needs head clearance while still approaching the first
    # riser. Guard the enlarged opening at the upper deck independently of the
    # sloping handrails below it; the landing end remains open for walking.
    top=st['z']+st['rise'];back=st['y']-.90;front=st['y']+st['steps']*st['tread']
    for side in [-1,1]:
        xx=st['x']+side*.79
        beam(st['id']+f' upper guard top {side}',st['upper'],'brushed_steel',(xx,back,top+1.02),(xx,front,top+1.02),.03)
        for yy in [back,back+1.5,back+3.0,front]:beam(st['id']+f' upper guard post {side} {yy}',st['upper'],'brushed_steel',(xx,yy,top),(xx,yy,top+1.02),.025)
    if st['id']!='AFT_LOWER':
        beam(st['id']+' upper guard rear',st['upper'],'brushed_steel',(st['x']-.79,back,top+1.02),(st['x']+.79,back,top+1.02),.03)

def window(n,x,a,b,c,d,side,g):
    # Glass and gasket overlap by 28 mm behind the frame. No bevel on seam edges.
    w=b-a;h=d-c;mid=(a+b)/2;z=(c+d)/2
    glass=box(n+' glass','GLASS','glass',(x,mid,z),(.036,w+.056,h+.056),window=n)
    p=box(n+' continuous frame',g,'window_frame',(x+side*.025,mid,z),(.11,w+.16,h+.16),bevel=.008)
    p['cutouts']=[dict(center=[x,mid,z],size=[.5,w,h])];seal=[p['id']]
    windows.append(dict(id=n,glass_part=glass['id'],frame_parts=seal,plane='X',opening=[x,a,b,c,d],overlap_m=.028))
def side_wall(n,x,a,b,c,d,holes,g='MAIN',mat='superstructure_white'):
    ys=sorted(set([a,b]+[v for hole in holes for v in hole[:2]]));zs=sorted(set([c,d]+[v for hole in holes for v in hole[2:]]))
    for i in range(len(ys)-1):
        for j in range(len(zs)-1):
            yy=(ys[i]+ys[i+1])/2;zz=(zs[j]+zs[j+1])/2
            if any(h[0]<yy<h[1] and h[2]<zz<h[3] for h in holes):continue
            box(f'{n} skin {i} {j}',g,mat,(x,yy,zz),(.18,ys[i+1]-ys[i],zs[j+1]-zs[j]),seal_role='continuous_wall')
    seams.append(dict(id=n,shared_y=ys,shared_z=zs,beveled_seams=False))
def door(n,pivot,center,size,g='MAIN',angle=105):
    groups.append(dict(id=n,parent=g,pivot=pivot,axis='Z',open_deg=angle))
    box(n+' leaf',n,'superstructure_white',center,size,bevel=.008)
    for dx in [-.10,.10]:box(n+f' handle {dx}',n,'brushed_steel',(center[0]+dx,center[1]-.10,center[2]),(.035,.04,.30),bevel=.01)
    doors.append(dict(id=n,pivot=pivot,open_deg=angle,leaf_center=center,leaf_size=size))

for side in [-1,1]:
    mainholes=[(-9.0,-6.8,5.0,6.45),(-6.5,-3.9,5.0,6.45),(-3.6,-1.0,5.0,6.45),(-.5,.78,4.2,6.55),(1.15,3.5,5.0,6.40),(3.8,6.4,5.0,6.40)]
    side_wall(f'Main side {side}',side*2.62,-9.9,7.0,4.2,6.82,mainholes)
    for i,h in enumerate(mainholes):
        if i==3:
            door(f'MAIN_SIDE_{side}',[side*2.62,h[0],4.2],[side*2.62,(h[0]+h[1])/2,5.36],[.12,h[1]-h[0]-.025,2.30],angle=side*100)
            f=box(f'Main side door rebate {side}','MAIN','window_frame',(side*2.65,(h[0]+h[1])/2,5.375),(.12,1.44,2.51))
            f['cutouts']=[dict(center=[side*2.65,(h[0]+h[1])/2,5.375],size=[.6,1.25,2.32])]
        else:window(f'Main window {side} {i}',side*2.62,*h,side,'MAIN')
    upholes=[(-4.4,-.8,7.62,9.26),(.2,1.4,7.62,9.26),(1.55,2.1,7.62,9.26)]
    side_wall(f'Upper side {side}',side*2.40,-5.2,2.8,6.95,9.58,upholes,'UPPER')
    for i,h in enumerate(upholes):window(f'Upper window {side} {i}',side*2.40,*h,side,'UPPER')
    box(f'Main eave {side}','UPPER','superstructure_white',(side*3.03,-3.1,6.88),(.18,17.25,.20))
    box(f'Upper eave {side}','FLY','superstructure_white',(side*2.82,-1.4,9.62),(.15,9.4,.18))

# End walls with real door openings. Frame around the opening, not a covered wall.
for g,y,z,w,height in [('MAIN',-9.90,4.20,2.62,2.62),('UPPER',-4.55,6.95,2.40,2.63)]:
    for side in [-1,1]:box(g+f' aft jamb wall {side}',g,'superstructure_white',(side*(w+.64)/2,y,z+height/2),(w-.64,.18,height))
    box(g+' aft door lintel',g,'superstructure_white',(0,y,z+2.49),(1.28,.18,.26))
    door(g+'_AFT',[-.60,y,z],[0,y,z+1.16],[1.18,.12,2.30],g)
    for side in [-1,1]:box(g+f' aft door frame {side}',g,'window_frame',(side*.626,y-.04,z+1.18),(.065,.09,2.36))
    box(g+' aft top seal',g,'window_frame',(0,y-.04,z+2.36),(1.30,.09,.07))
front=box('Main forward bulkhead','MAIN','superstructure_white',(0,7.0,5.5),(5.24,.18,2.6))
front['cutouts']=[dict(center=[1.7,7.0,5.27],size=[1.24,.6,2.14])]
door('BOW_SERVICE_DOOR',[1.11,7.0,4.2],[1.7,7.0,5.25],[1.18,.10,2.10])
# Three bridge panes meet via shared mullions, with an underlying continuous sill.
box('Bridge front sill','UPPER','superstructure_white',(0,5.0,7.32),(4.80,.18,.74))
box('Bridge front header','UPPER','superstructure_white',(0,5.0,9.43),(4.8,.18,.30))
for i in [-1,0,1]:
    x=i*1.55;box(f'Bridge front glass {i}','GLASS','glass',(x,5.0,8.49),(1.51,.036,1.69))
front=box('Bridge continuous front frame','UPPER','window_frame',(0,5.025,8.49),(4.90,.12,1.84),bevel=.008)
front['cutouts']=[dict(center=[i*1.55,5.025,8.49],size=[1.455,.6,1.60]) for i in [-1,0,1]]
for part in parts:
    if part['name'].startswith(('Bridge front ','Bridge continuous front ')):part['shear_yz']=[-.18,8.49]
for side in [-1,1]:
    v=[(side*2.49,4.42,6.95),(side*2.49,5.28,6.95),(side*2.49,4.80,9.58),(side*2.49,4.42,9.58),
       (side*2.31,4.42,6.95),(side*2.31,5.28,6.95),(side*2.31,4.80,9.58),(side*2.31,4.42,9.58)]
    mesh(f'Bridge raked corner post {side}','UPPER','superstructure_white',v,[(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)])

# Match the published side-profile setback, while retaining the aft stair landings.
for part in parts:
    if part['name'].startswith(('Bridge front ','Bridge continuous front ')):
        part['center']=[part['center'][0],part['center'][1]-2.2,part['center'][2]]
        for cut in part.get('cutouts',[]):cut['center'][1]-=2.2
    elif part['name'].startswith('Bridge raked corner post '):
        part['vertices']=[(x,y-2.2,z) for x,y,z in part['vertices']]

# Interiors: all partition joints overlap the exterior liner. Doors remain openable.
def partition(n,x,y,axis,length,z=1.5,height=2.5,opening=True,g='LOWER',clearance=2.15):
    size=[.10,length,height] if axis=='X' else [length,.10,height]
    p=box(n,g,'interior_white',(x,y,z+height/2),size)
    if opening:
        cut=[.5,1.22,clearance+.20] if axis=='X' else [1.22,.5,clearance+.20]
        p['cutouts']=[dict(center=[x,y,z+(clearance-.20)/2],size=cut)]
        ds=[.065,1.18,clearance-.05] if axis=='X' else [1.18,.065,clearance-.05]
        piv=[x,y-.59,z] if axis=='X' else [x-.59,y,z]
        door(n+'_DOOR',piv,[x,y,z+(clearance-.05)/2],ds,g,-95 if axis=='X' and x>0 else 95)
        frame=box(n+' timber jamb',g,'interior_wood',(x,y,z+(clearance+.01)/2),[.16,length if length<1.4 else 1.38,clearance+.15] if axis=='X' else [length if length<1.4 else 1.38,.16,clearance+.15])
        frame['cutouts']=[dict(center=[x,y,z+(clearance-.20)/2],size=[.6,1.19,clearance+.20] if axis=='X' else [1.19,.6,clearance+.20])]
for side in [-1,1]:box(f'Lower sealed liner {side}','LOWER','interior_white',(side*2.73,-2.75,2.76),(.10,21.30,2.52),seal_role='liner')
box('Lower aft liner','LOWER','interior_white',(0,-13.47,2.76),(5.56,.10,2.52),seal_role='liner')
for y in [-9.15,-1.80,2.35,7.35]:partition(f'Lower transverse {y}',0,y,'Y',5.50,opening=True,clearance=2.30 if y==7.35 else 2.15)
for side in [-1,1]:
    partition(f'Aft cabin wall {side}',side*.73,-11.25,'X',3.75)
    partition(f'Fore cabin wall {side}',side*.73,5.05,'X',5.40)
    partition(f'ME service wall {side}',side*.73,.28,'X',3.75)
    # A double berth with side access, adapted to the narrow lower hull.
    for zone,yy in [('aft',-11.35),('fore',4.8)]:
        for k in [0,1]:
            x=side*(1.22+k*.88)
            box(f'{zone} bed base {side} {k}','LOWER','interior_wood',(x,yy,1.73),(.80,2.05,.45),bevel=.025)
            box(f'{zone} mattress {side} {k}','LOWER','fabric',(x,yy,2.04),(.79,2.00,.20),bevel=.035)
            box(f'{zone} pillow {side} {k}','LOWER','interior_white',(x,yy+.72,2.19),(.60,.43,.11),bevel=.035)
            beds.append(dict(id=f'{zone}_{side}_{k}',center=[x,yy,2.04],runtime_bed=False))
    for yy in [-12.65,3.1,6.5]:
        box(f'Cabin desk {side} {yy}','LOWER','interior_wood',(side*1.9,yy,2.20),(1.5,.48,.06),bevel=.015)
        box(f'Cabin light {side} {yy}','ELECTRICAL','lamp',(side*2.60,yy,3.65),(.04,.50,.05))
    box(f'Master bed base {side}','MAIN','interior_wood',(side*.50,4.60,4.48),(.95,2.10,.56),bevel=.025)
    box(f'Master mattress {side}','MAIN','fabric',(side*.50,4.60,4.85),(.94,2.05,.18),bevel=.035)
    box(f'Master pillow {side}','MAIN','interior_white',(side*.50,5.3,5.0),(.75,.48,.12),bevel=.03)
    beds.append(dict(id=f'master_{side}',center=[side*.5,4.6,4.85],runtime_bed=False))
    # Two main propulsion machines, with an accessible side service passage.
    box(f'Diesel engine block {side}','LOWER','engine_metal',(side*.68,-5.4,2.1),(.78,3.0,1.20),bevel=.06)
    for j in range(6):box(f'Engine head {side} {j}','LOWER','brushed_steel',(side*.68,-6.6+j*.48,2.77),(.68,.34,.20),bevel=.025)
    cyl(f'Propeller shaft {side}','HULL','brushed_steel',(side*.9,-10.95,.60),.07,8.2,axis='Y')
    cyl(f'Marine propeller hub {side}','HULL','brass',(side*.9,-15.05,.60),.17,.25,axis='Y')
    for k in range(4):
        a=k*math.tau/4
        box(f'Marine propeller blade {side} {k}','HULL','brass',(side*.9+math.sin(a)*.38,-15.05,.60+math.cos(a)*.38),(.25,.065,.75),rot=(0,math.degrees(a),23))

# Saloon, galley, workshop. Distinct materials, never vanilla texture tiles.
partition('Main owner cabin bulkhead',0,2.35,'Y',5.20,z=4.2,height=2.52,g='MAIN')
box('Saloon table','MAIN','interior_wood',(-.80,-3.10,5.01),(1.00,2.35,.09),bevel=.04)
for y in [-4.15,-2.05]:box('Table pedestal '+str(y),'MAIN','brushed_steel',(-.50,y,4.60),(.13,.13,.75),bevel=.02)
for side in [-1]:
    box(f'Saloon bench {side}','MAIN','fabric',(side*1.60,-3.1,4.66),(.62,3.05,.35),bevel=.06)
    box(f'Saloon backrest {side}','MAIN','fabric',(side*1.88,-3.1,5.10),(.17,3.05,.75),bevel=.05)
box('Galley countertop','MAIN','countertop',(-1.80,.30,5.10),(1.04,3.35,.09),bevel=.025)
box('Galley cabinetry','MAIN','interior_wood',(-1.8,.30,4.65),(.96,3.35,.85))
for yy in [-.7,.0,.7,1.4]:box('Galley drawer '+str(yy),'MAIN','interior_white',(-1.30,yy,4.75),(.025,.63,.54),bevel=.007)
box('Workshop counter','MAIN','countertop',(-1.83,-7.25,5.10),(1.04,2.8,.10),bevel=.025)
box('Workshop base','MAIN','interior_white',(-1.83,-7.25,4.65),(.98,2.8,.8))
for j in range(7):box('Workshop tool board '+str(j),'MAIN','engine_metal',(-2.43,-8.20+j*.32,5.72),(.045,.07,.48),bevel=.01)
for side in [1]:
    box(f'Upper lounge sofa {side}','UPPER','fabric',(side*1.5,-1.0,7.42),(.65,2.20,.5),bevel=.06)
    box(f'Upper lounge back {side}','UPPER','fabric',(side*1.84,-1.0,7.83),(.16,2.25,.68),bevel=.05)
box('Upper lounge table','UPPER','interior_wood',(.80,-1.5,7.65),(.7,1.6,.10),bevel=.04)

for deck,y,z,g in [('Bridge',1.55,6.95,'UPPER'),('Flybridge',1.7,9.70,'FLY')]:
    controls.append(dict(id=deck,group=g,floor_m=z,wheel_center_m=[-.65,y-.44,z+1.02],seat_centers_m=[[x,y-1.15,z+.66] for x in [-.65,.65]],display_centers_m=[[x,y-.407,z+1.28] for x in [-.9,0,.9]]))
    box(deck+' console',g,'console',(.0,y,z+.78),(3.1,.65,.9),bevel=.05)
    for x in [-.9,0,.9]:
        box(deck+f' display bezel {x}',g,'window_frame',(x,y-.36,z+1.28),(.72,.075,.51),bevel=.02)
        box(deck+f' navigation display {x}',g,'display',(x,y-.407,z+1.28),(.64,.014,.43))
    for x in [-.65,.65]:
        cyl(deck+f' helm seat pedestal {x}',g,'brushed_steel',(x,y-1.15,z+.3),.10,.6)
        box(deck+f' helm cushion {x}',g,'fabric',(x,y-1.15,z+.66),(.55,.54,.14),bevel=.04)
        box(deck+f' helm back {x}',g,'fabric',(x,y-1.4,z+1.03),(.55,.13,.65),bevel=.04)
    wheel=(-.65,y-.44,z+1.02)
    ring_y(deck+' steering wheel',g,'window_frame',wheel,.23,.027,.04)
    cyl(deck+' steering hub',g,'brushed_steel',wheel,.055,.18,axis='Y')
    for a in [90,210,330]:
        rad=math.radians(a);beam(deck+' wheel spoke '+str(a),g,'brushed_steel',wheel,(wheel[0]+.205*math.cos(rad),wheel[1],wheel[2]+.205*math.sin(rad)),.021)
    for xx in [.89,1.05]:
        box(deck+' throttle pedestal '+str(xx),g,'window_frame',(xx,y-.28,z+1.26),(.13,.24,.08),bevel=.01)
        beam(deck+' throttle lever '+str(xx),g,'brushed_steel',(xx,y-.28,z+1.30),(xx,y-.36,z+1.48),.022)
        box(deck+' throttle grip '+str(xx),g,'window_frame',(xx,y-.36,z+1.49),(.10,.08,.05),bevel=.012)

# Native AE2 block reservation: keep these cells accessible and free of hull trim.
for i,(g,x,y,z) in enumerate([('UPPER',-1.5,.3,8.0),('MAIN',-.9,-8.9,5.35),('MAIN',-1.25,-7.1,5.55),('MAIN',-1.25,.1,5.55),('LOWER',-.73,3.05,2.75),('LOWER',-1.3,.0,2.8)],1):
    box(f'ME M{i} bezel',g,'window_frame',(x,y,z),(.10,.66,.55),bevel=.018)
    box(f'ME M{i} preview face',g,'me_display',(x+.06,y,z),(.012,.57,.46))
    terminals.append(dict(id=f'M{i}',group=g,center_m=[x,y,z],runtime_native_part=True,preview_only_face=True))
for yy in [-.8,.2]:
    box('ME rack '+str(yy),'LOWER','console',(-2.02,yy,2.65),(1.10,.86,2.08))
    for j in range(5):
        box(f'ME drive slot {yy} {j}','LOWER','engine_metal',(-1.45,yy,1.85+j*.34),(.035,.65,.25))
        box(f'ME status {yy} {j}','ELECTRICAL','lamp',(-1.426,yy+.27,1.85+j*.34),(.01,.035,.16))

# Boarding system: structural platform, rails with real gate gaps and stairs.
prism('Stern boarding platform','MAIN','teak',[(-2.9,-15.1),(-2.9,-13.9),(2.9,-13.9),(2.9,-15.1)],3.05,3.20)
for side in [-1,1]:
    for i in range(5):box(f'Platform access stair {side} {i}','MAIN','teak',(side*2.5,-13.90+i*.30,3.20+(i+1)*.20-.06),(.90,.32,.12))
    for z,g,ys,x in [(4.2,'MAIN',[-13.0,-11,-9,-8.45,-7.15,-5,-3,2,4,6],3.38),(6.95,'UPPER',[-10.9,-8.5,-6.1,7.2,9.1,11,12.3,13.3,14.15],2.85),(9.7,'FLY',[-5.5,-3,-1,1.2,3.1],2.65)]:
        rail_x=[]
        for yy in ys:
            width=x
            if g=='UPPER' and yy>6.5:
                positive=sorted([(y,xx) for xx,y in roof_outline if xx>=0])
                for (ya,xa),(yb,xb) in zip(positive,positive[1:]):
                    if ya<=yy<=yb:width=min(x,xa+(xb-xa)*(yy-ya)/(yb-ya)-.14);break
            rail_x.append(max(.08,width))
        for j,yy in enumerate(ys):
            beam(f'{g} rail post {side} {yy}',g,'brushed_steel',(side*rail_x[j],yy,z),(side*rail_x[j],yy,z+1.08),.030)
            gap=g=='MAIN' and j and ys[j-1]<=-8.45 and yy>=-7.15
            if j and yy-ys[j-1]<2.6 and not gap:
                for h in [.52,1.08]:beam(f'{g} rail span {side} {yy} {h}',g,'brushed_steel',(side*rail_x[j-1],ys[j-1],z+h),(side*rail_x[j],yy,z+h),.025)
    # Gangway stored against the aft side; dynamic lowering gets its own pivot.
    gid=f'GANGWAY_{side}';groups.append(dict(id=gid,parent='MAIN',pivot=[side*3.30,-7.8,4.20],axis='Y',open_deg=side*85))
    box(gid+' folded walkway',gid,'teak',(side*3.40,-7.8,5.65),(.13,1.15,2.90))
    for edge in [-1,1]:
        rid=gid+f'_RAIL_{edge}';yy=-7.8+edge*.55;xx=side*(3.48+edge*.02)
        groups.append(dict(id=rid,parent=gid,pivot=[xx,yy,4.2],axis='Z',open_deg=-side*edge*90))
        for zz in [4.35,5.65,6.95]:beam(rid+' post '+str(zz),rid,'brushed_steel',(xx,yy,zz),(xx,yy-edge*1.05,zz),.028)
        beam(rid+' handrail',rid,'brushed_steel',(xx,yy-edge*1.05,4.25),(xx,yy-edge*1.05,7.05),.035)
    beam(f'Recovery ladder {side}','MAIN','brushed_steel',(side*2.75,-15.18,1.90),(side*2.75,-15.18,3.95),.035)
    beam(f'Recovery ladder inner {side}','MAIN','brushed_steel',(side*2.15,-15.18,1.90),(side*2.15,-15.18,3.95),.035)
    for j in range(7):beam(f'Recovery rung {side} {j}','MAIN','brushed_steel',(side*2.15,-15.18,2.02+j*.28),(side*2.75,-15.18,2.02+j*.28),.03)

# Exterior roof, mast, anchors, fenders and working lights.
prism('Flybridge canopy','FLY','superstructure_white',[(-2.65,-4.5),(-2.65,1.0),(2.65,1.0),(2.65,-4.5)],12.00,12.16)
for x in [-2.25,2.25]:
    for y in [-3.8,.5]:beam(f'Canopy pillar {x} {y}','FLY','brushed_steel',(x,y,9.7),(x,y,12.02),.075)
cyl('Radar mast','FITTINGS','superstructure_white',(0,-2.7,13.05),.13,1.8)
box('Radar scanner','FITTINGS','superstructure_white',(0,-2.7,13.85),(2.20,.28,.20),bevel=.045)
for x in [-.85,.85]:
    cyl('Radar dome '+str(x),'FITTINGS','superstructure_white',(x,-3.0,12.50),.38,.55)
    beam('Antenna '+str(x),'FITTINGS','brushed_steel',(x,-3.0,12.70),(x,-3.0,14.55),.015)
for y in [-12.4,-9.0,8.8,11.0]:
    for side in [-1,1]:
        x=side*(3.2 if y<0 else 2.5)
        box(f'Cleat base {side} {y}','FITTINGS','brushed_steel',(x,y,4.28 if y<0 else 7.02),(.30,.14,.05),bevel=.01)
        beam(f'Cleat horn {side} {y}','FITTINGS','brushed_steel',(x-.23,y,4.38 if y<0 else 7.12),(x+.23,y,4.38 if y<0 else 7.12),.055)
for g,z,ys in [('LOWER',3.88,[-11,-7,-3,1,5]),('MAIN',6.68,[-8,-4,0,4]),('UPPER',9.43,[-3,1,4])]:
    for y in ys:box(f'{g} ceiling light {y}','ELECTRICAL','lamp',(0,y,z),(.55,.20,.045),bevel=.008)
for side in [-1,1]:box(f'Navigation lamp {side}','ELECTRICAL','port_lamp' if side<0 else 'starboard_lamp',(side*2.51,2.4,7.18),(.12,.24,.18),bevel=.015)

# Distinctive hull windows. Cut through the real shell; slope glass and frame
# together so their rebate stays closed on the shaped hull.
def slope_window(n,side,y,z,w,h,x,slope_y,slope_z,shell):
    shell.setdefault('cutouts',[]).append(dict(center=[side*x,y,z],size=[.90,w,h]))
    window(n,side*x,y-w/2,y+w/2,z-h/2,z+h/2,side,'FITTINGS')
    for p in parts[-2:]:
        p['shear_xy']=[side*slope_y,y];p['shear_xz']=[side*slope_z,z]
for side in [-1,1]:
    for j,y in enumerate([8.1,9.0,10.5]):
        if y<9.5:x=2.95+(y-6.5)*(2.65-2.95)/3.;dy=-.10;index=0 if side<0 else 5
        else:x=2.65+(y-9.5)*(1.8-2.65)/2.7;dy=(1.8-2.65)/2.7;index=1 if side<0 else 4
        shell=next(p for p in parts if p['name']==f'Raised forecastle shell {index}')
        slope_window(f'Forecastle portrait window {side} {j}',side,y,5.35,.58,1.08,x,dy,0,shell)
    for j,y in enumerate([-12.0,-10.7,2.9,4.3,5.7]):
        def hull_x(yy,zz):
            for (ya,wa,ka),(yb,wb,kb) in zip(stations,stations[1:]):
                if ya<=yy<=yb:
                    t=(yy-ya)/(yb-ya);w=wa+(wb-wa)*t;k=ka+(kb-ka)*t;ch=min(4.05,max(k+.45,1.65+k*.25))
                    return w-.05*w*(4.18-zz)/(4.18-ch)
            raise ValueError(yy)
        x=hull_x(y,3.35);dy=(hull_x(y+.1,3.35)-hull_x(y-.1,3.35))/.2;dz=(hull_x(y,3.45)-hull_x(y,3.25))/.2
        shell=next(p for p in parts if p['name']=='Continuous watertight hull skin')
        slope_window(f'Lower cabin port {side} {j}',side,y,3.35,.65,.36,x,dy,dz,shell)
        liner=next(p for p in parts if p['name']==f'Lower sealed liner {side}')
        liner.setdefault('cutouts',[]).append(dict(center=[side*2.73,y,3.35],size=[.6,.65,.36]))
        outer=[(-.36,-.215),(.36,-.215),(.36,.215),(-.36,.215)]
        inner=[(-.325,-.18),(.325,-.18),(.325,.18),(-.325,.18)]
        vv=[]
        for end in [0,1]:
            for contour in [outer,inner]:
                for yy,zz in contour:
                    xx=2.675 if not end else x-.015+dy*yy+dz*zz
                    vv.append((side*xx,y+yy,3.35+zz))
        ff=[]
        for k in range(4):
            q=(k+1)%4;ff.extend([(k,q,8+q,8+k),(4+k,12+k,12+q,4+q),(k,4+k,4+q,q),(8+k,8+q,12+q,12+k)])
        mesh(f'Cabin port continuous reveal {side} {j}','LOWER','interior_white',vv,ff)

palette={'hull_ivory':('#c7c0aa',.46),'superstructure_white':('#e4e5df',.34),'interior_white':('#dadbd1',.65),'teak':('#98754c',.78),'interior_floor':('#8c806a',.78),'interior_wood':('#7c6041',.52),'fabric':('#777f7c',.85),'brushed_steel':('#9ea8aa',.28),'brass':('#9c8144',.35),'window_frame':('#26373d',.4),'glass':('#e5eff1',.12),'engine_metal':('#4f6668',.55),'console':('#35494f',.45),'countertop':('#b8b9b2',.55),'display':('#22444a',.45),'me_display':('#47614a',.5),'lamp':('#ece4c9',.4),'port_lamp':('#b84b43',.4),'starboard_lamp':('#4e9c75',.4)}

# Original repeatable game textures. No photographs or Minecraft pixels copied.
def png(path,w,h,rgba):
    def chunk(tag,data):return struct.pack('!I',len(data))+tag+data+struct.pack('!I',zlib.crc32(tag+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(rgba[y*w*4:(y+1)*w*4]) for y in range(h))
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
manifest=[]
for name,(col,rough) in palette.items():
    n=256;base=tuple(int(col[k:k+2],16) for k in [1,3,5]);pixels=[];rng=random.Random(name);noise=[rng.random() for _ in range(n*n)]
    for y in range(n):
        for x in range(n):
            delta=(noise[y*n+x]-.5)*2.8
            if name in ['teak','interior_wood']:
                plank=x//8;delta+=5*math.sin(plank*1.7)+3*math.sin(y*.17+math.sin(x*.48))
                if x%8==0 or (y+(plank%4)*64)%256==0:delta=-35
            if name=='brushed_steel':delta+=2*math.sin(y*.8)
            if name in ['display','me_display']:
                if x in range(24,232) and (y%35 in [0,1,2] or (x%35 in [0,1] and name=='me_display')):delta+=24
            if name=='fabric':delta+=1.8*((x+y)%2)
            alpha=105 if name=='glass' else 255
            pixels.extend([max(0,min(255,round(v+delta))) for v in base]+[alpha])
    file=ROOT/f'textures/{name}.png';png(file,n,n,pixels)
    manifest.append(dict(name=name,file=f'textures/{name}.png',sha256=hashlib.sha256(file.read_bytes()).hexdigest(),size=[n,n],tile_m=4,roughness=rough,metallic=.75 if name in ['brushed_steel','brass'] else 0,origin='Original deterministic project-authored material; no third-party pixels'))

data=dict(id='NE01',revision=P['revision'],unit='m',geometry_authority='source/ne01.json',parameters=P,parts=parts,groups=groups,seams=seams,windows=windows,doors=doors,stairs=stairs,beds=beds,terminals=terminals,controls=controls,palette={n:v[0] for n,v in palette.items()},materials=manifest,
 limitations=['Interior dimensions and opening directions are adapted, not factory drawings.','Materials reproduce the appearance, not original copyrighted texture files.','Native game integration and final walking/sealing checks remain separate.'])
(ROOT/'source/ne01.json').write_text(json.dumps(data,indent=2)+'\n')
(ROOT/'source/parameters.json').write_text(json.dumps(P,indent=2)+'\n')
(ROOT/'source/texture_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
with (ROOT/'docs/bom.csv').open('w',newline='',encoding='utf8') as f:
    w=csv.writer(f);w.writerow(['id','name','group','material','shape']);w.writerows([[p['id'],p['name'],p['group'],p['material'],p['shape']] for p in parts])
print(json.dumps(dict(revision=P['revision'],parts=len(parts),windows=len(windows),doors=len(doors),beds=len(beds),terminals=len(terminals),textures=len(manifest))))
