"""Authoritative native geometry for MC01: the owner's calico-cat boat sketch."""
from pathlib import Path
import json,math,hashlib
ROOT=Path(__file__).resolve().parents[1]
parts=[]
def add(name,group,mat,shape,**kw):
    parts.append(dict(name=name,group=group,material=mat,shape=shape,**kw))
def box(name,g,m,c,s,bevel=.04,rotation=(0,0,0)):
    add(name,g,m,'box',center=c,size=s,bevel=bevel,rotation=rotation)
def beam(name,g,m,a,b,width=.07):add(name,g,m,'beam',a=a,b=b,width=width)
def prism(name,g,m,points,front,back,axis='Y',bevel=0):
    # 2D polygon coordinates are x,z for Y extrusion, y,z for X extrusion.
    verts=[]
    for t in [front,back]:
        verts.extend([(a,t,b) if axis=='Y' else (t,a,b) for a,b in points])
    n=len(points);faces=[list(range(n-1,-1,-1)),list(range(n,2*n))]
    faces.extend([[i,(i+1)%n,(i+1)%n+n,i+n] for i in range(n)])
    add(name,g,m,'mesh',vertices=verts,faces=faces,bevel=bevel)
def ring(name,g,m,c,r,t,depth,n=24):add(name,g,m,'ring',center=c,radius=r,thickness=t,depth=depth,segments=n)
def cylinder(name,g,m,c,r,depth,n=16):add(name,g,m,'cylinder',center=c,radius=r,depth=depth,segments=n)
outline=[(-2.8,-6.4),(2.8,-6.4),(3.9,-5.25),(3.9,5.4),(3.1,6.4),(-3.1,6.4),(-3.9,5.4),(-3.9,-5.25)]
def shell(name,g,m,low,high,scale_low=1,scale_high=1):
    v=[(x*s,y*s,z) for z,s in [(low,scale_low),(high,scale_high)] for x,y in outline]
    n=len(outline);f=[list(range(n-1,-1,-1)),list(range(n,n*2))]
    f.extend([[i,(i+1)%n,(i+1)%n+n,i+n] for i in range(n)])
    add(name,g,m,'mesh',vertices=v,faces=f,bevel=.07)

shell('01 / rounded chine','hull','keel',.55,1.35,.78,.92)
shell('02 / ivory hull','hull','cream',1.25,3.35,.92,1)
shell('03 / peach rub rail','hull','peach',3.25,3.48,1.015,1.015)
shell('04 / ivory coaming','hull','white',3.48,3.64,1,1)
box('deck / recessed teak','deck','teak',(0,.6,3.64),(7.2,10.7,.16),.06)
for x in [-3,-2.4,-1.8,-1.2,-.6,0,.6,1.2,1.8,2.4,3]:
    box('deck seam '+str(x),'deck','teak_dark',(x,.55,3.728),(.018,10.6,.012),0)
# Side walls retain the clean, boxy silhouette in the owner's drawing.
for sign in [-1,1]:
    box(f'calico bulwark {sign}','hull','cream',(sign*3.73,.75,4.07),(.3,9.6,.92),.08)
    box(f'peach caprail {sign}','rails','peach_light',(sign*3.73,.75,4.59),(.42,9.8,.18),.05)
    side=sign*3.9
    for label,col,pts in [
      ('warm shoulder','peach',[(-3.7,3.85),(-3.5,4.48),(-2.6,4.48),(-2.4,4.1),(-1.8,4.08),(-1.5,3.8),(-2.5,3.7)]),
      ('round charcoal spot','gray',[(.15,3.85),(.38,4.3),(.85,4.4),(1.55,4.22),(1.8,3.9),(1.3,3.75),(.55,3.7)]),
      ('ginger aft','peach',[(3.1,3.72),(3.0,4.0),(3.5,4.12),(3.8,4.42),(4.8,4.48),(5.15,4.2),(4.6,3.8)])]:
        prism(f'side {sign} / {label}','calico_inlays',col,pts,side,side+sign*.025,axis='X')
    # Lower hull patch continues the calico appearance below the rail.
    prism(f'hull patch {sign}','calico_inlays','peach',[(.2,2.6),(.45,3.2),(1.4,3.24),(1.8,2.8),(1.5,2.55),(.6,2.5)],side-.09*sign,side-.07*sign,axis='X')
box('stern wall','hull','cream',(0,5.55,4.05),(7.1,.35,.86),.08)
box('stern peach lip','rails','peach_light',(0,5.55,4.58),(7.3,.45,.18),.05)

# The bow IS the cat head. Front points negative Y; all facial features are mesh.
face=[(-3.3,2.55),(-3.85,3.1),(-3.85,5.62),(-3.35,6.08),(3.35,6.08),(3.85,5.62),(3.85,3.1),(3.3,2.55)]
prism('cat / ivory head','cat_head','cream',face,-6.48,-4.9,bevel=.1)
prism('cat / grey forehead patch','calico_inlays','gray',[
 (-3.72,5.55),(-3.28,5.98),(-1.32,5.98),(-1.15,5.72),(-.55,5.65),(-.72,5.25),(-1.15,5.04),(-1.0,4.7),(-1.6,4.57),(-2.0,4.8),(-2.8,4.7),(-3.71,4.82)],-6.6,-6.48)
prism('cat / ginger right patch','calico_inlays','peach',[
 (.65,5.98),(3.25,5.98),(3.72,5.55),(3.72,3.95),(3.18,3.82),(2.8,4.05),(2.22,3.95),(1.9,4.25),(1.25,4.42),(1.0,4.98),(.65,5.32)],-6.6,-6.48)
prism('cat / ginger left chin','calico_inlays','peach',[
 (-3.72,4.15),(-3.05,4.03),(-2.7,3.62),(-2.2,3.5),(-2.42,2.66),(-3.26,2.66),(-3.72,3.13)],-6.6,-6.48)
# Raised cheek pads, sculpted as softly chamfered boxes for a voxel-friendly face.
for sign in [-1,1]:
    box(f'cat / raised cheek {sign}','muzzle','white',(sign*.72,-6.89,3.57),(1.65,.84,1.15),.29)
    # Short whiskers stop before the hull sides; no fragile outsized spikes.
    for j in range(2):
        beam(f'cat / whisker {sign} {j}','muzzle','warm_gray',(sign*1.55,-6.78,3.7-j*.22),(sign*2.55,-6.68,3.85-j*.42),.035)
prism('cat / pink triangular nose','muzzle','pink',[(-.31,4.15),(.31,4.15),(0,3.82)],-7.43,-7.17,bevel=.04)
box('cat / nose highlight','muzzle','pink_light',(-.09,-7.46,4.105),(.2,.035,.04),.01)
beam('cat / philtrum','muzzle','cocoa',(0,-7.35,3.84),(0,-7.35,3.6),.05)
for sign in [-1,1]:
    pts=[(0,-7.35,3.6),(sign*.3,-7.33,3.38),(sign*.65,-7.31,3.38),(sign*.94,-7.28,3.6)]
    for j,(a,b) in enumerate(zip(pts,pts[1:])):beam(f'cat / smile {sign} {j}','muzzle','cocoa',a,b,.055)
    # Gently closed eyes. The small outer flick keeps the sleepy expression.
    pts=[(sign*.95,-6.69,4.73),(sign*1.35,-6.69,4.65),(sign*1.83,-6.69,4.65),(sign*2.28,-6.69,4.77)]
    for j,(a,b) in enumerate(zip(pts,pts[1:])):beam(f'cat / sleepy eye {sign} {j}','face_details','cocoa',a,b,.075)
    beam(f'cat / eyelash {sign}','face_details','cocoa',pts[-1],(sign*2.5,-6.69,4.94),.055)
    for j in range(2):box(f'cat / blush {sign} {j}','face_details','blush',(sign*(2.1+j*.15),-6.7,4.28),(.07,.04,.19),.02,rotation=(0,sign*-15,0))
for sign,col in [(-1,'gray'),(1,'peach')]:
    ear=[(sign*3.72,5.72),(sign*2.83,8.1),(sign*1.45,6.05)]
    prism(f'cat / ear {sign}','ears','cream',ear,-6.2,-5.15,bevel=.06)
    inner=[(sign*3.25,6.12),(sign*2.81,7.55),(sign*1.95,6.12)]
    prism(f'cat / ear color {sign}','ears',col,inner,-6.28,-6.18,bevel=.025)
    tiny=[(sign*2.98,6.25),(sign*2.78,7.02),(sign*2.31,6.25)]
    prism(f'cat / ear inner {sign}','ears','pink_light',tiny,-6.32,-6.27)

# Forward bridge is raised behind the forehead, leaving the face unobstructed.
box('bridge / platform','bridge','cream',(0,-3.88,5.58),(6.75,2.35,.25),.09)
box('bridge / teak floor','bridge','teak',(0,-3.86,5.74),(6.42,2.05,.09),.03)
for i in range(5):
    box(f'bridge / stair {i}','access','teak',(0,-1.62-i*.28,3.85+i*.36),(1.32,.38,.22),.035)
for sign in [-1,1]:
    beam(f'stair rail {sign}','access','gold',(sign*.76,-1.25,4.55),(sign*.76,-2.85,6.35),.07)
    box(f'bridge side {sign}','bridge','cream',(sign*3.28,-3.82,6.08),(.18,2.3,.76),.05)
    box(f'bridge side cap {sign}','bridge','peach_light',(sign*3.28,-3.82,6.51),(.27,2.35,.16),.04)
    beam(f'bridge visor post {sign}','bridge','gold',(sign*2.1,-4.94,6.08),(sign*2.1,-4.94,7.0),.07)
box('bridge / clear visor','bridge','glass',(0,-4.95,6.58),(4.15,.055,.82),.025)
box('bridge / visor lip','bridge','peach_light',(0,-4.95,7.03),(4.4,.13,.12),.035)
def seat(name,x,y,z,g='seats'):
    box(name+' pedestal',g,'gold',(x,y,z+.22),(.45,.5,.44),.07)
    box(name+' cushion',g,'peach_light',(x,y,z+.53),(1.07,.92,.23),.12)
    box(name+' back',g,'cream',(x,y+.38,z+1.05),(1.12,.24,1.0),.13)
    box(name+' back pad',g,'peach',(x,y+.22,z+1.06),(.88,.06,.68),.08)
seat('pilot',-1.05,-3.5,5.82);seat('navigator',1.05,-3.5,5.82)
box('helm / console','helm','cream',(-1.05,-4.48,6.24),(1.22,.45,.75),.11)
ring('helm / wheel','helm','gold',(-1.05,-4.08,6.62),.28,.06,.06,12)
for angle in [0,120,240]:
    a=math.radians(angle);beam('wheel spoke '+str(angle),'helm','gold',(-1.05,-4.075,6.62),(-1.05+.25*math.cos(a),-4.075,6.62+.25*math.sin(a)),.035)
box('helm / inset display','helm','dark',(-1.05,-4.73,6.42),(.7,.04,.22),.025)
box('helm / pale green screen','helm','screen',(-1.05,-4.76,6.42),(.55,.02,.12),.01)

# A small useful boat: two aft passengers, storage along the sides, central passage.
for sign in [-1,1]:
    seat('aft passenger '+str(sign),sign*2.25,2.95,3.74)
    for i in range(3):
        y=-.3+i*.88
        box(f'cargo {sign} {i}','cargo','cream',(sign*2.76,y,4.05),(1.3,.79,.62),.07)
        box(f'cargo lid {sign} {i}','cargo','peach_light',(sign*2.76,y,4.43),(1.37,.83,.13),.04)
        box(f'cargo latch {sign} {i}','cargo','gold',(sign*2.07,y,4.13),(.06,.22,.15),.02)
    for i in range(4):
        y=-.7+i*.68
        box(f'boarding rung {sign} {i}','access','gold',(sign*4.0,2.25,3.15-i*.43),(.35,.7,.08),.025)
box('aft / engine bench','engine','cream',(0,4.65,4.04),(2.7,1.08,.66),.1)
box('aft / engine cover','engine','peach_light',(0,4.65,4.43),(2.8,1.17,.15),.05)
for x in [-.9,-.6,-.3,0,.3,.6,.9]:box('engine vent '+str(x),'engine','warm_gray',(x,4.64,4.52),(.1,.62,.03),.02)

# One large circular, flower-like stern fan from the sketch.
fan=(0,6.35,5.55)
for sign in [-1,1]:
    beam(f'fan / stand {sign}','fan_frame','gold',(sign*1.25,5.2,3.75),(sign*1.1,6.27,5.5),.18)
ring('fan / ivory outer duct','fan_frame','cream',fan,2.25,.22,.65,24)
ring('fan / peach front rim','fan_frame','peach_light',(0,5.99,5.55),2.28,.12,.12,24)
ring('fan / peach aft rim','fan_frame','peach',(0,6.72,5.55),2.28,.12,.12,24)
cylinder('fan / gold hub','rotor','gold',(0,6.32,5.55),.38,.62)
cylinder('fan / ivory hubcap','rotor','white',(0,5.96,5.55),.3,.16)
for i in range(7):
    a=i*math.tau/7
    blade=[]
    for r,t in [(.35,-.2),(1.8,-.15),(1.98,.0),(1.65,.33),(.55,.16)]:
        blade.append((r*math.sin(a)+t*math.cos(a),5.55+r*math.cos(a)-t*math.sin(a)))
    prism('fan / ginger petal '+str(i),'rotor','peach',blade,6.19,6.39,bevel=.035)
for a in [0,math.pi/4,math.pi/2,math.pi*3/4]:
    beam('fan / safety spoke '+str(a),'fan_frame','gold',(-2.05*math.cos(a),5.91,5.55-2.05*math.sin(a)),(2.05*math.cos(a),5.91,5.55+2.05*math.sin(a)),.055)
for sign in [-1,1]:
    box(f'bow / tiny lantern {sign}','lights','gold',(sign*3.45,-4.7,6.55),(.27,.28,.42),.04)
    box(f'bow / lantern glass {sign}','lights','lamp',(sign*3.45,-4.87,6.55),(.18,.07,.26),.025)
    box(f'stern marker {sign}','lights','pink',(sign*3.25,5.78,4.2),(.35,.12,.16),.03)
    cylinder(f'mooring bollard {sign}','deck','gold',(sign*3.1,4.65,4.8),.11,.36)

palette={
 'cream':('#F4EEDC',.0,.7),'white':('#FFF9ED',.0,.62),
 'peach':('#E9B681',.0,.72),'peach_light':('#F6D0A3',.0,.62),
 'gray':('#777978',.0,.72),'warm_gray':('#A3A09A',.05,.7),
 'pink':('#D88DAD',.0,.6),'pink_light':('#F0BCD1',.0,.72),'blush':('#D99FA4',.0,.8),
 'cocoa':('#60504C',.0,.8),'gold':('#BDA16C',.55,.37),
 'teak':('#BBA182',.0,.8),'teak_dark':('#8D7964',.0,.8),
 'keel':('#A8ABA3',.15,.6),'dark':('#535E5B',.2,.45),
 'screen':('#B9DBBA',.0,.5),'lamp':('#FFE6AF',.0,.45),'glass':('#D7E3D7',.0,.15)}
data={'revision':'MC01','name':'Mikan / Calico cat launch','units':'1 Blender unit = 1 Minecraft block; visual design units',
 'axis':{'front':'-Y','up':'+Z'},'geometry_authority':'source/calico_MC01.json',
 'owner_reference':'references/owner_calico_sketch.jpg','palette':palette,'parts':parts,
 'requirements':['Match white / peach / gray calico sketch','Raised muzzle, separate pink nose and two cheeks','Closed sleepy eyes','Cat ears and one round stern fan','Open useful deck','Keep existing SK01/SK02/SK03 unchanged'],
 'design_targets':{'seats':4,'storage_modules':6,'main_deck_z':3.73,'bridge_floor_z':5.82,'single_stern_fan':True},
 'sound_design':'source/sound_design.json',
 'runtime_registered':False,'physics_verified':False,'notes':'Native geometry review; no Minecraft/Sable integration or seaworthiness claims.'}
(ROOT/'source/calico_MC01.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
keep={}
old=Path('D:/repos/Blender/creations/25_skat')
for sub in ['source','assembly','exports']:
    for p in (old/sub).iterdir():
        if p.is_file() and 'SK0' in p.name:keep[str(p)]=hashlib.sha256(p.read_bytes()).hexdigest()
(ROOT/'reports/SKAT_PRESERVATION_BASELINE.json').write_text(json.dumps(keep,indent=2)+'\n')
print(json.dumps({'revision':'MC01','parts':len(parts),'source':str(ROOT/'source/calico_MC01.json')}))
