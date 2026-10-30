"""E01 dimensional game replica. Sources, estimates and adaptations stay explicit."""
from pathlib import Path
import json,math,csv
ROOT=Path(__file__).resolve().parents[1]
P={'revision':'E01-R7','length_m':8.,'width_m':3.,'nominal_height_m':3.,
 'roof_z_m':2.60,'floor_z_m':.88,'ceiling_z_m':2.50,
 'wheel_radius_m':.68,'wheel_width_m':.40,'wheel_x_m':1.26,
 'axles_y_m':[-2.68,-.92,.84,2.60],
 'ramp_hinge_m':[0,3.27,.88],'ramp_length_m':1.62,'ramp_width_m':1.40,
 'rear_shoulder_aft_y_m':3.97,'rear_shoulder_top_z_m':2.77,
 'rear_recess_status':'Photo-based adaptation: shoulders extend 0.70 m aft of the ramp hinge plane, about 0.59 m aft of the closed frame face. Estimated, not factory data.',
 'ramp_tip_open_z_m':.005,'troop_aisle_width_m':1.10,
 'unit_policy':'Model metres: 1 Blender unit = 1 m = 1 Minecraft block. Register measurements are mm.',
 'height_scope':'Body, folded mirrors and compact RCWS envelope; flexible antennas excluded.',
 'dimension_status':'8x3x3m is a working nominal target from the owner-linked public overview, not a detailed manufacturer drawing.'}
P['ramp_open_deg']=math.degrees(math.acos((P['ramp_tip_open_z_m']-P['ramp_hinge_m'][2])/math.hypot(P['ramp_length_m'],.11))-math.atan2(.11,P['ramp_length_m']))
(ROOT/'source/parameters.json').write_text(json.dumps(P,indent=2)+'\n')
parts=[]
groups=[{'id':'ROOT','parent':None,'pivot':[0,0,0],'role':'vehicle'},
 {'id':'HULL','parent':'ROOT','pivot':[0,0,0],'role':'fixed_shell'},
 {'id':'ROOF','parent':'ROOT','pivot':[0,0,0],'role':'removable_roof_for_cutaway'},
 {'id':'INTERIOR','parent':'ROOT','pivot':[0,0,0],'role':'crew_and_troop_compartment'},
 {'id':'ANTENNAS','parent':'ROOT','pivot':[0,0,0],'role':'excluded_flexible_antenna_envelope'},
 {'id':'RAMP','parent':'ROOT','pivot':P['ramp_hinge_m'],'role':'rear_ramp','axis':'X','open_deg':-P['ramp_open_deg']},
 {'id':'RAMP_DOOR','parent':'RAMP','pivot':[-.36,3.35,1.075],'role':'door_in_ramp','axis':'Z','open_deg':95}]
def group(id,parent,pivot,role,axis=None,angle=None):
    d=dict(id=id,parent=parent,pivot=pivot,role=role)
    if axis:d.update(axis=axis,open_deg=angle)
    groups.append(d)
for id,pivot,axis,angle in [('DRIVER_HATCH',[-.65,-1.67,2.49],'X',-105),('COMMANDER_HATCH',[-.64,-.57,2.63],'X',-105),('TROOP_HATCH_L',[-.69,1.50,2.665],'Y',-105),('TROOP_HATCH_R',[.69,1.50,2.665],'Y',105)]:group(id,'ROOF',pivot,'roof_escape_lid',axis,angle)
def add(n,g,m,shape,**kw):parts.append(dict(id=f'P{len(parts)+1:04}',name=n,group=g,material=m,shape=shape,**kw))
def box(n,g,m,c,s,b=.008,rot=(0,0,0)):add(n,g,m,'box',center=c,size=s,bevel=b,rotation=rot)
def beam(n,g,m,a,b,w=.025):add(n,g,m,'beam',a=a,b=b,width=w)
def cyl(n,g,m,c,r,d,axis='Z',segments=16):add(n,g,m,'cylinder',center=c,radius=r,depth=d,axis=axis,segments=segments)
def ring(n,g,m,c,r,t,d,axis='Z',segments=24):add(n,g,m,'ring',center=c,radius=r,thickness=t,depth=d,axis=axis,segments=segments)
def solid(n,g,m,v,f,b=.005):add(n,g,m,'mesh',vertices=v,faces=f,bevel=b)
def panel(n,g,m,pts,front,back,axis='X',b=.006):
    v=[(t,a,z) if axis=='X' else (a,t,z) for t in [front,back] for a,z in pts];nn=len(pts)
    f=[list(range(nn-1,-1,-1)),list(range(nn,2*nn))]+[[i,(i+1)%nn,(i+1)%nn+nn,i+nn] for i in range(nn)]
    solid(n,g,m,v,f,b)

# Hull is made as separate wall/floor panels, retaining actual apertures.
box('central floor','HULL','floor',(0,.48,.825),(1.93,5.40,.11),.008)
for side in [-1,1]:
    panel(f'V belly {side}','HULL','olive_dark',[(-3.45,.80),(-3.0,.52),(3.5,.52),(3.88,.80)],side*.90,side*1.18,b=.012)
    box(f'raised wheelwell {side}','HULL','olive_dark',(side*1.055,.22,1.05),(.30,6.6,.40),.015)
    # Main plates slope inward towards the roof, like the photographed hull.
    for i,(a,b) in enumerate([(-2.52,-1.2),(-1.2,.0),(.0,1.2),(1.2,2.4),(2.4,3.03)]):
        v=[(side*1.49,a,1.52),(side*1.49,b,1.52),(side*1.30,b,2.56),(side*1.30,a,2.56),
           (side*1.38,a,1.52),(side*1.38,b,1.52),(side*1.19,b,2.56),(side*1.19,a,2.56)]
        f=[(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)]
        if i==0:
            # The fore side rim follows the low driver roof, then rises with
            # its transition. A tall rim above the low roof looked like a slit.
            v=[]
            for yy,top in [(a,2.48),(-1.50,2.48),(-1.40,2.56),(b,2.56)]:
                v.extend([(side*1.49,yy,1.52),(side*(1.49-(top-1.52)*(.19/1.04)),yy,top),
                          (side*(1.38-(top-1.52)*(.19/1.04)),yy,top),(side*1.38,yy,1.52)])
            f=[(3,2,1,0),(12,13,14,15)]+[(k*4+j,k*4+(j+1)%4,(k+1)*4+(j+1)%4,(k+1)*4+j) for k in range(3) for j in range(4)]
        solid(f'sloped modular side {side} {i}','HULL','olive' if i%2 else 'olive_light',v,f,.009)
        # Visible fasteners on the bolt-on panels.
        for yy in [a+.11,b-.11]:
            for zz in [1.66,2.40]:cyl(f'armor fastener {side} {i} {yy} {zz}','HULL','steel',(side*(1.49-(zz-1.52)*.182+.008),yy,zz),.018,.025,axis='X',segments=8)
    # Angular lower side skirts dip between wheel arches.
    lower=[(-3.47,1.51),(-3.47,1.24),(-3.18,1.16),(-3.08,1.48),(-2.24,1.48),(-1.84,1.14),(-1.65,1.48),(-.43,1.48),(-.13,1.14),(.12,1.48),(1.27,1.48),(1.57,1.14),(1.77,1.48),(3.06,1.48),(3.35,1.14),(3.84,1.26),(3.84,1.51)]
    panel(f'wheel arch scallops {side}','HULL','olive',lower,side*1.49,side*1.39,b=.009)
    for y in [-3.43,3.38]:box(f'rubber mudflap {side} {y}','HULL','rubber',(side*1.28,y,.68),(.40,.05,.83),.005)
    box(f'interior liner {side}','INTERIOR','ivory',(side*1.16,.47,1.86),(.05,5.40,1.16),.008)
    box(f'overhead service channel {side}','INTERIOR','ivory',(side*1.05,.47,2.38),(.22,5.40,.20),.018)
    for i in range(9):
        y=-1.7+i*.61
        cyl(f'vent outlet {side} {i}','INTERIOR','vent',(side*.921,y,2.38),.055,.027,axis='X',segments=12)
        box(f'channel plate {side} {i}','INTERIOR','red_label',(side*.929,y+.18,2.37),(.008,.10,.055),.001)

# Glacis: broad sloped hood and narrower lower nose.
v=[(-1.26,-3.96,1.50),(1.26,-3.96,1.50),(1.25,-2.54,2.37),(-1.25,-2.54,2.37),
   (-1.26,-3.88,1.40),(1.26,-3.88,1.40),(1.25,-2.54,2.25),(-1.25,-2.54,2.25)]
solid('main sloped glacis','HULL','olive_light',v,[(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)],.012)
panel('lower nose','HULL','olive',[(-1.25,1.48),(1.25,1.48),(1.05,.86),(-1.05,.86)],-3.99,-3.79,axis='Y',b=.015)
box('front identification plate','HULL','olive_dark',(0,-3.998,1.36),(.34,.004,.10),.001)
for side in [-1,1]:
    # Share the main side's transverse slope; the bow also tapers in plan.
    # A loft avoids a vertical front cheek jutting through the sloped main side.
    stations=[(-3.99,1.48,1.51,.21),(-3.50,1.30,1.95,.21*(3.50-2.54)/(3.99-2.54)),(-2.54,1.53,2.48,0),(-2.52,1.53,2.48,0)]
    vv=[]
    for yy,low,high,taper in stations:
        for zz,inset in [(low,0),(high,0),(high,.11),(low,.11)]:
            vv.append((side*(1.49-(zz-1.52)*(.19/1.04)-taper-inset),yy,zz))
    ff=[(3,2,1,0),(12,13,14,15)]+[(k*4+i,k*4+(i+1)%4,(k+1)*4+(i+1)%4,(k+1)*4+i) for k in range(3) for i in range(4)]
    solid(f'bow cheek {side}','HULL','olive',vv,ff,.008)
    box(f'headlight recess {side}','HULL','black',(side*1.04,-3.83,1.64),(.27,.11,.16),.015,rot=(31,0,0))
    box(f'headlight lens {side}','HULL','lens',(side*1.04,-3.89,1.69),(.19,.025,.075),.008,rot=(31,0,0))
    ring(f'front towing eye {side}','HULL','steel',(side*.74,-3.955,1.16),.10,.034,.05,axis='Y',segments=16)
    box(f'folded mirror arm {side}','HULL','steel',(side*1.37,-2.44,2.11),(.09,.35,.055),.006)
    box(f'folded mirror {side}','HULL','olive_dark',(side*1.43,-2.32,2.23),(.11,.09,.28),.012)
    box(f'mirror glass {side}','HULL','glass',(side*1.493,-2.32,2.23),(.014,.07,.23),.004)
for i in range(5):
    y=-3.72+i*.23;z=1.50+(y+3.96)*.613
    box('hood seam '+str(i),'HULL','olive_dark',(0,y,z+.009),(2.25,.018,.009),.002,rot=(31.5,0,0))
for x in [-.93,.0,.93]:
    for y in [-3.5,-2.83]:
        z=1.50+(y+3.96)*.613
        box(f'hood service latch {x} {y}','HULL','steel',(x,y,z+.03),(.11,.07,.025),.005,rot=(31.5,0,0))

# Roof with deliberate holes, not a solid box hidden behind moving lids.
box('roof centre fore','ROOF','olive',(0,-2.025,2.428),(2.47,1.05,.10),.003)
# Cut driver aperture out of that panel with a source-declared Boolean box.
parts[-1]['cutouts']=[{'center':[-.65,-2.08,2.43],'size':[.73,.74,.4]}]
box('glacis roof transition','ROOF','olive',(.0,-2.55,2.425),(2.47,.075,.13),.006)
solid('stepped roof transition','ROOF','olive',
      [(-1.235,-1.50,2.378),(1.235,-1.50,2.378),(-1.235,-1.50,2.478),(1.235,-1.50,2.478),
       (-1.235,-1.40,2.52),(1.235,-1.40,2.52),(-1.235,-1.40,2.62),(1.235,-1.40,2.62)],
      [(0,2,3,1),(4,5,7,6),(0,1,5,4),(2,6,7,3),(0,4,6,2),(1,3,7,5)],.004)
box('crew roof','ROOF','olive_light',(0,-.725,2.57),(2.47,1.35,.10),.01)
parts[-1]['cutouts']=[{'center':[-.64,-.92,2.57],'size':[.73,.65,.4]}]
box('troop roof','ROOF','olive',(0,1.58,2.57),(2.46,3.28,.10),.01)
parts[-1]['cutouts']=[{'center':[0,1.5,2.57],'size':[1.30,1.20,.4]}]
box('recessed aft roof lintel','HULL','olive_light',(0,3.27,2.565),(1.52,.14,.10),.01)
for name,x,y,w,d,z,g in [('driver',-.65,-2.08,.77,.80,2.49,'DRIVER_HATCH'),('commander',-.64,-.92,.78,.68,2.63,'COMMANDER_HATCH')]:
    box(name+' hatch gasket','ROOF','black',(x,y,z-.005),(w+.05,d+.05,.028),.012)
    parts[-1]['cutouts']=[{'center':[x,y,z],'size':[w-.12,d-.12,.2]}]
    box(name+' hatch lid',g,'olive_light',(x,y,z+.04),(w,d,.065),.025)
    for sx in [-1,1]:cyl(name+f' hinge {sx}',g,'steel',(x+sx*w*.32,y+d/2,z),.035,.13,axis='X')
    beam(name+' grab handle',g,'steel',(x-.14,y-.18,z+.10),(x+.14,y-.18,z+.10),.028)
    for j in range(3):
        xx=x+(j-1)*.20;box(name+' periscope body '+str(j),'ROOF','olive_dark',(xx,y-d/2-.03,z+.10),(.17,.11,.13),.015)
        box(name+' periscope glass '+str(j),'ROOF','glass',(xx,y-d/2-.089,z+.10),(.13,.014,.065),.004)
for side,g in [(-1,'TROOP_HATCH_L'),(1,'TROOP_HATCH_R')]:
    box('troop hatch half '+str(side),g,'olive_light',(side*.345,1.50,2.665),(.67,1.20,.07),.018)
    beam('troop hatch handle '+str(side),g,'steel',(side*.34,1.34,2.735),(side*.34,1.66,2.735),.03)
    for yy in [1.08,1.92]:cyl('troop hatch hinge '+str((side,yy)),g,'steel',(side*.69,yy,2.665),.035,.14,axis='Y')

# Rear passage, ramp frame and separately articulated door within it.
for side in [-1,1]:
    # These are structural continuations of the aft hull, not boxes on a flat wall.
    # The central ramp is recessed; the two tall, faceted shoulders project aft.
    cross_sections=[(2.86,[(.78,1.48),(1.49,1.52),(1.30,2.56),(.92,2.60),(.78,2.48)]),
                    (3.34,[(.78,1.42),(1.46,1.48),(1.31,2.69),(.95,2.77),(.78,2.54)]),
                    (3.97,[(.78,1.43),(1.41,1.47),(1.30,2.56),(.95,2.68),(.78,2.49)])]
    verts=[(side*x,y,z) for y,section in cross_sections for x,z in section]
    faces=[tuple(range(4,-1,-1)),tuple(range(10,15))]
    faces += [(k*5+i,k*5+(i+1)%5,(k+1)*5+(i+1)%5,(k+1)*5+i) for k in range(2) for i in range(5)]
    solid(f'projecting aft hull shoulder {side}','HULL','olive',verts,faces,.012)
    # Small rear service faces sit on the massive shoulder, below its angular cap.
    box(f'rear service cover {side}','HULL','olive_light',(side*1.075,3.980,2.10),(.46,.030,.57),.012)
    box(f'rear light surround {side}','HULL','black',(side*.95,3.981,2.54),(.18,.017,.15),.008)
    box(f'rear red lamp {side}','HULL','red',(side*.95,3.992,2.55),(.12,.016,.095),.008)
    for z in [1.85,2.37]:
        box(f'service cover hinge {side} {z}','HULL','steel',(side*1.29,3.999,z),(.055,.018,.07),.004)
    beam(f'service cover pull {side}','HULL','steel',(side*.94,4.002,2.18),(side*1.11,4.002,2.18),.025)
    box(f'aft shoulder cap seam {side}','HULL','olive_dark',(side*1.115,3.69,2.679),(.28,.018,.016),.003)
    # Narrow side cheeks define the recessed central passage; no transverse roof
    # slab bridges the projecting ends and erases the U-shaped silhouette.
    box(f'recess passage cheek {side}','HULL','olive_dark',(side*.805,3.60,1.90),(.05,.67,1.13),.006)
    panel(f'lower aft corner {side}','HULL','olive_dark',[(3.18,.84),(3.79,.84),(3.94,1.00),(3.94,1.47),(3.18,1.47)],side*.80,side*1.36,b=.012)
    box(f'lower corner access cover {side}','HULL','olive',(side*1.08,3.948,1.27),(.37,.025,.26),.008)
    # A mounted towing shackle: structural socket, clevis cheeks and cross pin.
    box(f'towing socket {side}','HULL','olive_dark',(side*1.035,3.92,1.16),(.24,.13,.24),.009)
    for xx in [-.071,.071]:box(f'towing clevis {side} {xx}','HULL','steel',(side*1.035+xx,3.971,1.145),(.036,.065,.17),.006)
    cyl(f'towing cross pin {side}','HULL','steel',(side*1.035,3.983,1.12),.026,.21,axis='X',segments=12)
    ring(f'rear towing shackle {side}','HULL','steel',(side*1.035,3.98,1.025),.095,.028,.045,axis='Y',segments=16)
    box(f'rear skid bracket {side}','HULL','olive_dark',(side*.89,3.63,.84),(.16,.28,.25),.008)
    box(f'rear skid {side}','HULL','olive_dark',(side*.89,3.63,.68),(.20,.50,.12),.01)
    box(f'fixed ramp jamb {side}','HULL','olive',(side*.746,3.25,1.69),(.064,.15,1.64),.005)
    box(f'ramp side seal {side}','HULL','black',(side*.708,3.29,1.69),(.009,.055,1.61),.002)
# One continuous mesh around the inner door. Overlapping coplanar frame blocks
# caused the black corner rectangles in the rejected render; no hidden patching.
outer=[(-.70,.88),(.70,.88),(.70,2.50),(-.70,2.50)]
inner=[(-.36,1.075),(.36,1.075),(.36,2.325),(-.36,2.325)]
rv=[(x,y,z) for y in [3.82,3.94] for contour in [outer,inner] for x,z in contour]
rf=[]
for i in range(4):
    j=(i+1)%4
    rf.extend([(i,j,8+j,8+i),(4+i,12+i,12+j,4+j),(i,4+i,4+j,j),(8+i,8+j,12+j,12+i)])
solid('continuous ramp frame','RAMP','olive',rv,rf,.006)
box('ramp door leaf','RAMP_DOOR','olive_light',(0,3.92,1.70),(.70,.095,1.23),.014)
box('door inner pad','RAMP_DOOR','ivory',(0,3.858,1.70),(.57,.025,1.06),.012)
for z in [1.24,2.12]:cyl('door hinge '+str(z),'RAMP_DOOR','steel',(-.36,3.96,z),.032,.12)
box('door outside handle','RAMP_DOOR','steel',(.24,3.987,1.70),(.045,.045,.18),.01)
box('door inside handle','RAMP_DOOR','steel',(.24,3.823,1.70),(.045,.045,.18),.01)
for side in [-1,1]:
    beam('ramp handrail '+str(side),'RAMP','steel',(side*.59,3.792,1.17),(side*.59,3.792,2.25),.027)
    for j in range(5):box('ramp grip '+str((side,j)),'RAMP','floor',(side*.53,3.795,1.13+j*.25),(.20,.025,.04),.003)
    cyl('ramp main hinge '+str(side),'HULL','steel',(side*.61,3.83,.88),.07,.18,axis='X')

# Relocate the complete authored ramp assembly and its fixed hinge coherently.
ramp_shift=P['ramp_hinge_m'][1]-3.83
for part in parts:
    if part['group'] in ['RAMP','RAMP_DOOR'] or part['name'].startswith('ramp main hinge '):
        for key in ['center','a','b']:
            if key in part:part[key]=[part[key][0],part[key][1]+ramp_shift,part[key][2]]
        if 'vertices' in part:part['vertices']=[(x,y+ramp_shift,z) for x,y,z in part['vertices']]

# Eight wheels: four axles, separate steering and spin pivots, detailed tread.
for side in [-1,1]:
    for a,y in enumerate(P['axles_y_m']):
        gid=f'WHEEL_{"L" if side<0 else "R"}{a+1}';pivot=[side*P['wheel_x_m'],y,.68]
        group(gid,'ROOT',pivot,'wheel_hub')
        add(f'{gid} tyre',gid,'rubber','tire',center=pivot,radius=.68,width=.40,segments=40)
        cyl(f'{gid} rim',gid,'dust',(side*1.45,y,.68),.415,.045,axis='X',segments=24)
        ring(f'{gid} rim lip',gid,'olive_dark',(side*1.479,y,.68),.397,.035,.02,axis='X')
        cyl(f'{gid} hub',gid,'olive_light',(side*1.470,y,.68),.22,.035,axis='X')
        for k in range(10):
            ang=k*math.tau/10;cyl(f'{gid} lug {k}',gid,'steel',(side*1.488,y+.285*math.cos(ang),.68+.285*math.sin(ang)),.023,.022,axis='X',segments=6)
        # Suspension wishbones visible beneath the hull; their motion is demonstrative.
        for dz in [-.16,.16]:beam(f'{gid} wishbone {dz}','HULL','steel',(side*.65,y-.20,.67+dz),(side*1.13,y,.68+dz),.065)
        beam(f'{gid} damper','HULL','olive_dark',(side*.85,y+.18,1.18),(side*1.16,y+.13,.48),.075)
        for k in range(6):ring(f'{gid} coil {k}','HULL','steel',(side*1.03,y+.155,.55+k*.072),.085,.018,.025)

# Perforated shells and folding pans, based on public interior photographs.
seats=[]
for side,ys in [(-1,[-.10,.62,1.34,2.06,2.78]),(1,[.62,1.34,2.06,2.78])]:
    for i,y in enumerate(ys):
        gid=f'TROOP_SEAT_{"L" if side<0 else "R"}{i+1}'
        group(gid,'INTERIOR',[side*1.02,y,1.18],'folding_troop_pan','Y',side*82)
        add(f'{gid} perforated back','INTERIOR','ivory','seat_back',center=[side*1.075,y,1.63],side=side,width=.48,height=.64,depth=.035)
        box(f'{gid} seat pan',gid,'ivory',(side*.81,y,1.18),(.43,.48,.045),.012)
        box(f'{gid} olive pad',gid,'seat_pad',(side*.81,y,1.216),(.36,.39,.035),.013)
        beam(f'{gid} suspension rail','INTERIOR','steel',(side*1.115,y,1.20),(side*1.115,y,2.25),.045)
        for yy in [-.17,.17]:
            beam(f'{gid} harness {yy}','INTERIOR','strap',(side*1.046,y+yy,1.91),(side*.76,y+yy*.35,1.26),.035)
        box(f'{gid} belt buckle',gid,'steel',(side*.74,y,1.25),(.055,.055,.04),.004)
        seats.append({'id':gid,'role':'troop','position_m':[side*.81,y,1.20],'facing':'inward','certainty':'Seat shell style from reference photographs; exact position adapted.'})
def crew_seat(n,x,y,z,role):
    box(n+' pedestal','INTERIOR','steel',(x,y,z-.22),(.30,.34,.36),.015)
    box(n+' cushion','INTERIOR','seat_pad',(x,y,z),(.48,.48,.10),.028)
    box(n+' back','INTERIOR','ivory',(x,y+.22,z+.34),(.51,.10,.61),.024)
    box(n+' back pad','INTERIOR','seat_pad',(x,y+.158,z+.34),(.41,.028,.45),.018)
    box(n+' headrest','INTERIOR','seat_pad',(x,y+.21,z+.72),(.29,.13,.18),.025)
    seats.append({'id':n,'role':role,'position_m':[x,y,z],'facing':'forward','certainty':'Role count confirmed; exact placement adapted from public photos.'})
crew_seat('CREW_DRIVER',-.68,-2.37,1.17,'driver')
crew_seat('CREW_COMMANDER',-.64,-1.08,1.23,'commander')
crew_seat('CREW_OPERATOR',.65,-.30,1.23,'remote_station_operator')
box('sealed engine bay','INTERIOR','olive_dark',(.65,-2.18,1.54),(1.03,1.72,1.27),.025)
box('engine partition liner','INTERIOR','ivory',(.65,-1.30,1.65),(1.08,.05,1.45),.012)
box('driver console','INTERIOR','ivory',(-.65,-2.89,1.45),(.90,.24,.46),.02)
ring('driver steering wheel','INTERIOR','black',(-.67,-2.69,1.58),.18,.03,.035,axis='Y',segments=20)
for a in [0,120,240]:
    t=math.radians(a);beam('steering spoke '+str(a),'INTERIOR','steel',(-.67,-2.69,1.58),(-.67+.15*math.cos(t),-2.69,1.58+.15*math.sin(t)),.018)
for i in range(3):
    x=-.92+i*.27;box('driver monitor '+str(i),'INTERIOR','black',(x,-2.78,1.85),(.25,.05,.19),.012)
    box('driver display '+str(i),'INTERIOR','screen',(x,-2.748,1.85),(.215,.008,.15),.003)
for name,x,y in [('commander',-.65,-1.50),('operator',.65,-.73)]:
    box(name+' display bezel','INTERIOR','black',(x,y,1.86),(.40,.055,.27),.015)
    box(name+' display','INTERIOR','screen',(x,y+.033,1.86),(.35,.01,.22),.005)
    box(name+' control shelf','INTERIOR','ivory',(x,y+.05,1.49),(.43,.25,.05),.008)
    cyl(name+' controller','INTERIOR','black',(x+.13,y+.05,1.58),.028,.13)
for side in [-1,1]:
    box('electronics rack '+str(side),'INTERIOR','equipment',(side*1.06,-.2,2.03),(.18,.60,.31),.012)
    for j in range(5):box('radio unit '+str((side,j)),'INTERIOR','black',(side*.95,-.44+j*.11,2.04),(.035,.085,.20),.004)
    beam('overhead grab rail '+str(side),'INTERIOR','steel',(side*.73,-.08,2.40),(side*.73,3.12,2.40),.028)
for i in [-1,1]:
    cyl('extinguisher '+str(i),'INTERIOR','red',(i*.12,-.95,1.13),.065,.44)
    box('extinguisher top '+str(i),'INTERIOR','ivory',(i*.12,-.95,1.38),(.07,.10,.08),.008)
box('equipment bag','INTERIOR','seat_pad',(.43,-1.0,1.08),(.27,.25,.37),.035)
for i in range(8):
    y=-1.3+i*.60;box('floor grip strip '+str(i),'INTERIOR','steel',(0,y,.89),(1.10,.025,.008),.001)
for y in [-.1,1.1,2.4]:box('interior ceiling lamp '+str(y),'ROOF','lamp',(0,y,2.505),(.28,.09,.022),.006)

# Compact external station: visual placeholder, no weapon or APS simulation.
cyl('remote station base','ROOF','olive_dark',(.25,-.75,2.67),.30,.10,segments=24)
box('remote station body','ROOF','olive_light',(.25,-.75,2.83),(.36,.46,.22),.025)
box('remote station optics','ROOF','black',(-.02,-.84,2.87),(.15,.18,.18),.015)
box('optics lens','ROOF','glass',(-.02,-.938,2.87),(.105,.012,.10),.004)
box('remote station receiver','ROOF','olive_dark',(.40,-.88,2.945),(.12,.31,.11),.009)
cyl('visual barrel','ROOF','black',(.40,-1.29,2.96),.018,.62,axis='Y',segments=12)
for side in [-1,1]:
    box('roof electronics '+str(side),'ROOF','olive_dark',(side*.98,.0,2.72),(.28,.44,.23),.018)
    box('sensor face '+str(side),'ROOF','black',(side*1.13,-.01,2.74),(.025,.28,.13),.01)
    for y in [-.95,.36]:
        beam('roof rack '+str((side,y)),'ROOF','olive_dark',(side*.92,y,2.70),(side*.92,y,2.97),.025)
    beam('roof rack top '+str(side),'ROOF','olive_dark',(side*.92,-.95,2.97),(side*.92,.36,2.97),.025)
    cyl('antenna socket '+str(side),'ROOF','olive_dark',(side*.95,2.85,2.68),.055,.15)
    beam('flexible antenna '+str(side),'ANTENNAS','black',(side*.95,2.85,2.74),(side*.98,2.85,3.78),.009)

# Underlapping lip is attached to the left troop lid and moves out of the
# aperture with it; unlike a fixed crossbar it leaves the full open exit clear.
box('troop hatch centre underlap','TROOP_HATCH_L','olive_dark',(0,1.50,2.6185),(.065,1.18,.023),.002)

# Close the driver/engine footwell which previously ended ahead of the central
# floor at Y=-2.22. Taper stays inside the existing bow envelope. The rear
# underlap is 5 mm below the old top plane, avoiding coplanar flickering.
footwell=[(-1.055,-3.81),(1.055,-3.81),(1.21,-3.45),(1.21,-2.26),(.965,-2.16),(-.965,-2.16),(-1.21,-2.26),(-1.21,-3.45)]
n=len(footwell);vv=[(x,y,z) for z in [.765,.875] for x,y in footwell]
ff=[list(range(n-1,-1,-1)),list(range(n,2*n))]+[[i,(i+1)%n,(i+1)%n+n,i+n] for i in range(n)]
solid('sealed fore footwell floor','HULL','floor',vv,ff,.003)

# Inner front wheelhouses: a thin L section closes the cabin from the tyres.
# The fore top follows the low bow; the main cap leaves 0.38 m above the
# nominal 1.36 m tyre top. Contact/suspension clearance is checked in Minecraft.
for side in [-1,1]:
    vv=[];nn=6
    for yy,top in [(-3.81,1.60),(-3.45,1.74),(-1.96,1.74)]:
        cross=[(.99,.865),(1.035,.865),(1.035,top-.045),(1.27,top-.045),(1.27,top),(.99,top)]
        vv.extend([(side*x,yy,z) for x,z in cross])
    ff=[list(range(nn-1,-1,-1)),list(range(2*nn,3*nn))]+[[k*nn+i,k*nn+(i+1)%nn,(k+1)*nn+(i+1)%nn,(k+1)*nn+i] for k in range(2) for i in range(nn)]
    solid('front wheelwell liner '+str(side),'HULL','floor',vv,ff,.004)
# Separate instruments are below/right of the panorama; no fourth world pass.
box('driver instruments mount','INTERIOR','steel',(-.18,-2.82,1.50),(.10,.22,.04),.004)
box('driver instruments case','INTERIOR','black',(-.22,-2.73,1.50),(.28,.05,.21),.012)
box('driver instruments','INTERIOR','screen',(-.22,-2.698,1.50),(.25,.008,.174),.002)

# Real recessed monitor bezels. The live overlay remains behind the front rim.
for display in [p for p in parts if p['name'] in ['driver display 0','driver display 1','driver display 2','commander display','operator display','driver instruments']]:
    candidates=[p for p in parts if p['shape']=='box' and p['material']=='black' and p['size'][0]>display['size'][0] and p['size'][2]>display['size'][2]]
    case=min(candidates,key=lambda p:sum((p['center'][i]-display['center'][i])**2 for i in range(3)))
    cx,cy,cz=case['center'];w,d,h=case['size'];front=d/2;inside=front-.010
    x0=display['center'][0]-cx-display['size'][0]/2-.0015;x1=display['center'][0]-cx+display['size'][0]/2+.0015
    z0=display['center'][2]-cz-display['size'][2]/2-.0015;z1=display['center'][2]-cz+display['size'][2]/2+.0015
    outer=[(-w/2,-h/2),(w/2,-h/2),(w/2,h/2),(-w/2,h/2)];inner=[(x0,z0),(x1,z0),(x1,z1),(x0,z1)]
    vv=[(x,y,z) for ring,y in [(outer,front),(outer,-d/2),(inner,front),(inner,inside)] for x,z in ring]
    ff=[(7,6,5,4),(12,13,14,15)]
    for i in range(4):
        j=(i+1)%4;ff.extend([(i,j,4+j,4+i),(i,8+i,8+j,j),(8+i,12+i,12+j,8+j)])
    case.update(shape='mesh',vertices=vv,faces=ff,bevel=.003);case.pop('size')
    display['center']=list(display['center']);display['size']=list(display['size']);display['center'][1]=cy+front-.005;display['size'][1]=.002;display['bevel']=.0005
# Keep the wheel and spokes forward of the panel, with a visible column and hub.
for p in parts:
    if p['name']=='driver steering wheel':
        p['center']=list(p['center']);p['center'][1]+=.06;p['center'][2]+=.03
    elif p['name'].startswith('steering spoke '):
        p['material']='black'
        for key in ['a','b']:p[key]=list(p[key]);p[key][1]+=.06;p[key][2]+=.03
cyl('steering column','INTERIOR','steel',(-.67,-2.70,1.61),.027,.16,axis='Y',segments=12)
cyl('steering hub','INTERIOR','steel',(-.67,-2.604,1.61),.045,.032,axis='Y',segments=16)
P['monitor_face_recess_m']=.004

# Fixed compressible seat for both troop lids; the full roof opening remains.
for p in parts:
    if p['name'].startswith('troop hatch half '):p['size']=list(p['size']);p['size'][1]=1.32;p['bevel']=.015
    elif p['name']=='troop hatch centre underlap':p['size']=list(p['size']);p['size'][1]=1.30
box('troop hatch gasket','ROOF','black',(0,1.50,2.62),(1.42,1.40,.04),.004)
parts[-1]['cutouts']=[dict(center=(0,1.50,2.62),size=(1.30,1.20,.30))]

palette={'olive':'#77745D','olive_light':'#88846C','olive_dark':'#575844','dust':'#9F9477',
 'rubber':'#34352E','black':'#242A28','steel':'#73756C','ivory':'#BEBEAA','floor':'#777D72',
 'seat_pad':'#7B7A58','strap':'#43483B','equipment':'#676C57','screen':'#537B73',
 'glass':'#344D4B','red':'#A74638','red_label':'#794B43','vent':'#505850','lens':'#DCE7D8','lamp':'#E8E7CF'}
model={'revision':P['revision'],'unit':'m','geometry_authority':'source/eitan_E01.json','parameters':P,'groups':groups,'parts':parts,'seats':seats,'palette':palette,
 'interlocks':['RAMP_DOOR may open only while RAMP is closed. RAMP motion requires RAMP_DOOR closed.'],
 'actuators':[{'id':'RAMP_ACTUATOR_'+str(side),'fixed_m':[side*.735,3.31+ramp_shift,1.94],'moving_closed_m':[side*.69,P['ramp_hinge_m'][1],1.33],'tube_length_m':.55,'rod_start_m':.40,'certainty':'Visual linkage adaptation; no force or real cylinder dimensions known.'} for side in [-1,1]],
 'certainty':'See source/sources.json and docs/ASSUMPTIONS.md. No manufacturing, armour or live weapon claim.',
 'game_integration':{'wheel_backend':'Create Offroad 1.3.2 + Sable 2.0.6 + Create 6.0.10','wheel_radius_adapter_required':True,'wheel_hub_positions_fractional':True,'runtime_registered':False}}
(ROOT/'source/eitan_E01.json').write_text(json.dumps(model,indent=2)+'\n')
with (ROOT/'docs/bom.csv').open('w',newline='',encoding='utf8') as f:
    w=csv.writer(f);w.writerow(['id','name','group','material','shape']);w.writerows([[p['id'],p['name'],p['group'],p['material'],p['shape']] for p in parts])
print(json.dumps({'revision':P['revision'],'parts':len(parts),'groups':len(groups),'seats':len(seats),'ramp_open_deg':P['ramp_open_deg']}))
