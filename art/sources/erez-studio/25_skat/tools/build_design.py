"""SK01 game art model, block units. This is not a naval engineering simulation."""
from pathlib import Path
import json, math
ROOT=Path(__file__).resolve().parents[1]
parts=[];groups=[]
def group(name,parent=None,pivot=(0,0,0),role='static'):
    groups.append(dict(name=name,parent=parent,pivot=list(pivot),role=role));return name
def cube(name,center,size,mat,grp,rotation=(0,0,0),tag=None):
    assert min(size)>0
    parts.append(dict(name=name,shape='cube',center=list(center),size=list(size),material=mat,group=grp,rotation=list(rotation),tag=tag))
def beam(name,a,b,width,mat,grp,depth=None):
    parts.append(dict(name=name,shape='beam',a=list(a),b=list(b),width=width,depth=depth or width,material=mat,group=grp))
def ring(name,center,radius,thickness,depth,mat,grp,n=12):
    parts.append(dict(name=name,shape='ring',center=list(center),radius=radius,thickness=thickness,depth=depth,segments=n,material=mat,group=grp))
def cylinder(name,center,radius,depth,mat,grp,axis='Y',n=12):
    parts.append(dict(name=name,shape='cylinder',center=list(center),radius=radius,depth=depth,segments=n,axis=axis,material=mat,group=grp))
def tile_surface(name,xs,ys,z,mat,grp,sx=.98,sy=.98,sz=.12):
    for i,x in enumerate(xs):
        for j,y in enumerate(ys):cube(f'{name}_{i}_{j}',(x,y,z),(sx,sy,sz),mat,grp)
root=group('SKAT_SK01')
hull=group('01_Twin_hulls',root);deck=group('02_Main_deck',root)
hold=group('03_Cargo_hold_shell',root);roof=group('04_Cargo_roof',root,role='cutaway_hide')
storage=group('05_Cargo_modules_24',root);interior=group('06_Hold_interior',root)
cab=group('07_Cabin_shell',root);cabroof=group('08_Cabin_roof',root,role='cutaway_hide')
windows=group('09_Cabin_windows',root,role='cutaway_hide');cabinside=group('10_Cabin_interior',root)
rails=group('11_Rails_and_lights',root);machines=group('12_Drive_machinery',root)
details=group('13_Exterior_fittings',root);crane=group('14_Cargo_crane',root)

# Genuine twin hull silhouette: open bow channel, transverse bridges aft of it.
for side in [-1,1]:
    x=side*2.9;label='Port' if side<0 else 'Starboard'
    # Rectangular armor tiles generate shape detail, not new painted texture detail.
    for j in range(14):
        y=-6.5+j
        for iz in range(3):
            width=[1.6,2.25,2.6][iz]
            cube(f'{label}_hull_{j}_{iz}',(x,y,.35+iz*.65),(width,.98,.65),'graphite',hull)
        for outer in [-1,1]:
            xx=x+outer*1.305
            cube(f'{label}_teal_belt_{j}_{outer}',(xx,y,1.45),(.055,.96,.28),'teal',hull)
    # Stepped long noses; lower keel stops earlier so the bow rake reads from the side.
    for k,(y,w,z,height) in enumerate([(-7.25,1.65,1.15,1.65),(-7.85,.95,1.28,1.4)]):
        cube(f'{label}_bow_step_{k}',(x,y,z),(w,.65,height),'graphite',hull)
        cube(f'{label}_bow_cap_{k}',(x,y,z+height/2+.08),(w+.08,.7,.16),'steel',deck)
        cube(f'{label}_bow_copper_{k}',(x,y-.34,z+.2),(w*.72,.1,.2),'copper',details)
    # Deliberate ribs, bolts and edge strips on both hulls.
    for j,y in enumerate([-5.8,-3.8,-1.8,.2,2.2,4.2,6.2]):
        for sign in [-1,1]:
            xx=x+sign*1.33
            cube(f'{label}_belt_rib_{j}_{sign}',(xx,y,1.3),(.11,.17,1.72),'copper',details)
            for zz in [.65,1.95]:cube(f'{label}_rib_bolt_{j}_{sign}_{zz}',(xx+sign*.06,y,zz),(.065,.09,.09),'brass',details)
    tile_surface(f'{label}_gunwale',[x-.85,x,x+.85],[-6.5+i for i in range(14)],2.38,'steel',deck,.83,.98,.18)
    for yy in [-5,0,5]:
        cube(f'{label}_rubber_fender_{yy}',(x+side*1.4,yy,1.9),(.24,1.2,.6),'rubber',details)
    for yy in [-6.5,6.5]:
        cube(f'{label}_cleat_base_{yy}',(x,yy,2.55),(.6,.35,.12),'graphite',details)
        cube(f'{label}_cleat_{yy}',(x,yy,2.72),(.65,.14,.13),'steel',details)
    # Headlight at each bow, mechanical housing and visor.
    cube(f'{label}_bow_headlight_case',(x,-7.45,2.1),(.65,.4,.45),'graphite',details)
    cube(f'{label}_bow_headlight',(x,-7.67,2.12),(.47,.06,.25),'lamp',details)
    cube(f'{label}_bow_light_visor',(x,-7.72,2.36),(.7,.24,.08),'copper',details)

# Central bridge and exterior circulation: full floor, separate low beams beneath.
for y in [-5.6,-2.8,0,2.8,5.8]:
    cube(f'Cross_beam_{y}',(0,y,2.1),(5.8,.38,.4),'graphite',deck)
tile_surface('Central_bridge',[-1.5,-.5,.5,1.5],[-5.5+i for i in range(13)],2.38,'steel',deck,.98,.98,.18)
floor_z=2.48

# Hold envelope. The rear wall has an actual 1.4 x 2.1 clear doorway.
cube('Hold_floor',(0,1.05,2.51),(5.8,6.8,.08),'rubber',interior)
for side in [-1,1]:
    cube(f'Hold_side_sill_{side}',(side*2.98,1.05,2.79),(.2,6.8,.6),'graphite',hold)
    for j in range(7):
        y=-1.95+j
        cube(f'Hold_side_panel_{side}_{j}',(side*2.98,y,3.71),(.2,.98,1.25),'teal' if j%3==1 else 'graphite',hold)
        cube(f'Hold_upper_rim_{side}_{j}',(side*3.02,y,4.61),(.28,.98,.27),'steel',hold)
        if j%2==0:cube(f'Hold_copper_frame_{side}_{j}',(side*3.11,y,3.7),(.12,.15,2.1),'copper',hold)
for yy,label in [(-2.35,'forward'),(4.45,'aft')]:
    for side in [-1,1]:cube(f'Hold_{label}_door_side_{side}',(side*1.88,yy,3.55),(2.36,.2,2.2),'graphite',hold)
    cube(f'Hold_{label}_door_header',(0,yy,4.71),(5.9,.23,.15),'steel',hold)
    for side in [-1,1]:cube(f'Hold_{label}_door_jamb_{side}',(side*.77,yy,3.55),(.12,.26,2.15),'copper',interior)
    cube(f'Hold_{label}_door_warning',(0,yy,4.73),(1.1,.28,.12),'lamp',interior)
tile_surface('Cargo_roof',[-2.5+i for i in range(6)],[-1.85+i for i in range(7)],4.86,'steel',roof,.98,.98,.18)
# Flush cargo lift hatches, all on the removable roof collection.
for j,y in enumerate([-1.25,.85,2.95]):
    cube(f'Cargo_hatch_frame_{j}',(0,y,4.99),(3.1,1.5,.12),'copper',roof)
    cube(f'Cargo_hatch_leaf_{j}',(0,y,5.07),(2.92,1.32,.1),'graphite',roof)
    for x in [-.65,.65]:
        cube(f'Cargo_hatch_rib_{j}_{x}',(x,y,5.14),(.13,1.25,.08),'steel',roof)
    for x in [-1.05,1.05]:cube(f'Cargo_hatch_hinge_{j}_{x}',(x,y+.7,5.09),(.35,.16,.14),'brass',roof)
    cube(f'Cargo_hatch_handle_{j}',(0,y-.45,5.19),(.5,.1,.09),'copper',roof)

# 24 purpose-designed front-opening drawers: 2 banks x 6 bays x 2 tiers.
modules=[]
for side in [-1,1]:
    for bay in range(6):
        y=-1.7+bay*1.05
        for tier in range(2):
            z=3.02+tier*1.02;mid=f'{"P" if side<0 else "S"}{bay+1}{"L" if tier==0 else "U"}'
            x=side*1.97
            cube(f'Cargo_{mid}_case',(x,y,z),(1.78,.9,.83),'graphite',storage,tag='cargo_module')
            frontx=side*1.06
            cube(f'Cargo_{mid}_drawer',(frontx,y,z),(.06,.81,.68),'wood' if bay%3==0 else 'teal',storage)
            cube(f'Cargo_{mid}_handle',(frontx-side*.06,y,z+.02),(.09,.33,.085),'brass',storage)
            for edge in [-1,1]:cube(f'Cargo_{mid}_strap_{edge}',(frontx-side*.04,y+edge*.3,z),(.09,.07,.68),'copper',storage)
            cube(f'Cargo_{mid}_ID',(frontx-side*.07,y-.12,z+.22),(.025,.13,.1),'ivory',storage)
            modules.append({'id':mid,'center':[x,y,z],'size':[1.78,.9,.83],'target_slots':54,'implemented_inventory':False})
        cube(f'Rack_foot_{side}_{bay}',(side*1.97,y,2.58),(1.86,.95,.13),'steel',storage)
        for yedge in [-.49,.49]:cube(f'Rack_upright_{side}_{bay}_{yedge}',(side*2.91,y+yedge,3.57),(.1,.08,2.1),'steel',storage)
for yy in [-1.7,.4,2.5,3.55]:
    cube(f'Aisle_floor_marker_{yy}',(0,yy,2.57),(.38,.55,.015),'ivory',interior)
for side in [-1,1]:cube(f'Hold_inner_light_{side}',(side*2.79,1.05,4.63),(.1,5.8,.1),'cyan',interior)

# Forward cabin, diagonal front corners, broad windows and thick roof visor.
cube('Cabin_floor',(0,-4.35,2.55),(5.1,3.8,.12),'wood',cabinside)
cube('Cabin_front_lower',(0,-6.2,2.97),(3.4,.23,.9),'graphite',cab)
for side in [-1,1]:
    cube(f'Cabin_corner_lower_{side}',(side*2,-5.9,2.97),(.92,.23,.9),'graphite',cab,rotation=(0,0,side*-45))
    cube(f'Cabin_side_lower_{side}',(side*2.35,-4.18,2.97),(.24,3.48,.9),'graphite',cab)
    cube(f'Cabin_side_belt_{side}',(side*2.49,-4.18,3.04),(.08,3.4,.22),'copper',details)
    for y in [-5.5,-4.1,-2.55]:cube(f'Cabin_pillar_{side}_{y}',(side*2.35,y,4.1),(.24,.16,1.55),'copper',cab)
    for i,y in enumerate([-4.83,-3.33]):
        cube(f'Cabin_side_window_{side}_{i}',(side*2.35,y,4.1),(.07,1.23,1.23),'glass',windows)
    cube(f'Cabin_corner_glass_{side}',(side*2,-5.9,4.1),(.86,.075,1.23),'glass',windows,rotation=(0,0,side*-45))
    cube(f'Cabin_rear_side_{side}',(side*1.58,-2.45,3.66),(1.75,.2,2.18),'graphite',cab)
    cube(f'Cabin_front_lamp_case_{side}',(side*1.23,-6.4,3.02),(.6,.35,.5),'steel',details)
    cube(f'Cabin_front_lamp_{side}',(side*1.23,-6.6,3.02),(.37,.06,.28),'lamp',details)
for i,x in enumerate([-1.12,0,1.12]):
    cube(f'Cabin_front_glass_{i}',(x,-6.2,4.1),(1,.075,1.23),'glass',windows)
for x in [-1.73,-.56,.56,1.73]:cube(f'Cabin_front_mullion_{x}',(x,-6.22,4.1),(.13,.2,1.55),'steel',cab)
tile_surface('Cabin_roof',[-2+i for i in range(5)],[-6+i for i in range(4)],4.98,'steel',cabroof,.98,.98,.22)
cube('Cabin_roof_visor',(0,-6.46,4.94),(4.35,.5,.18),'graphite',cabroof)
for x in [-1.8,1.8]:cube(f'Cabin_roof_copper_strap_{x}',(x,-4.55,5.12),(.16,4.3,.08),'copper',cabroof)
# Two seats, controls, actual rear passage behind them.
for side in [-1,1]:
    x=side*.95
    cube(f'Seat_{side}_pedestal',(x,-4.1,2.94),(.32,.32,.7),'steel',cabinside)
    cube(f'Seat_{side}_cushion',(x,-4.1,3.3),(.68,.7,.2),'rubber',cabinside)
    cube(f'Seat_{side}_back',(x,-3.82,3.65),(.68,.15,.65),'rubber',cabinside)
    cube(f'Console_{side}',(x,-5.48,3.26),(1.3,.65,.9),'teal',cabinside)
    cube(f'Console_{side}_top',(x,-5.46,3.77),(1.34,.7,.1),'graphite',cabinside)
    for k in range(3):cube(f'Console_gauge_{side}_{k}',(x-.37+k*.37,-5.29,3.86),(.23,.2,.06),'cyan' if k<2 else 'lamp',cabinside)
    beam(f'Control_lever_{side}',(x+.2,-5.45,3.82),(x+.2,-5.26,4.12),.08,'brass',cabinside)
    cube(f'Control_lever_grip_{side}',(x+.2,-5.26,4.13),(.19,.11,.11),'rubber',cabinside)
# Filtering equipment is visual reservation; sealing and filtration need gameplay integration.
for side in [-1,1]:
    x=side*2.16
    cube(f'Filter_box_{side}',(x,-3.0,5.25),(.52,.85,.35),'graphite',cabroof)
    for j in range(5):cube(f'Filter_grille_{side}_{j}',(x,-3.32+j*.14,5.46),(.42,.055,.05),'steel',cabroof)
    beam(f'Filter_intake_{side}',(x,-3,5.4),(x,-3,5.85),.22,'copper',cabroof)
    cube(f'Filter_intake_cap_{side}',(x,-3,5.88),(.43,.4,.1),'graphite',cabroof)
cube('Navigation_mast',(0,-4.1,5.6),(.18,.18,1.1),'graphite',cabroof)
cube('Mast_crossbar',(0,-4.1,5.85),(1.05,.13,.13),'steel',cabroof)
cube('Mast_lamp',(0,-4.1,6.23),(.23,.23,.3),'lamp',cabroof)

# Exterior walks remain >= 0.9 blocks between side shell and rail.
for side in [-1,1]:
    x=side*4.08
    for j in range(13):
        yy=-5.75+j
        cube(f'Rail_post_{side}_{j}',(x,yy,2.99),(.1,.1,1.12),'copper',rails)
        if j%3==0:
            cube(f'Deck_lantern_base_{side}_{j}',(x,yy,3.53),(.24,.24,.1),'graphite',rails)
            cube(f'Deck_lantern_{side}_{j}',(x,yy,3.72),(.17,.17,.26),'lamp',rails)
            cube(f'Deck_lantern_cap_{side}_{j}',(x,yy,3.88),(.26,.26,.07),'graphite',rails)
    for z in [2.88,3.47]:beam(f'Rail_long_{side}_{z}',(x,-5.8,z),(x,6.3,z),.075,'brass',rails)
    beam(f'Bow_rail_{side}',(x,-5.8,3.47),(side*2.1,-6.55,3.47),.075,'brass',rails)

# Machinery lies aft of the hold; no thrust/force claims attached to the art rig.
rotors=[]
for side in [-1,1]:
    x=side*2.7;y=6.4;z=5.17;label='Port' if side<0 else 'Starboard'
    cube(f'{label}_engine_plinth',(x,5.7,2.82),(1.85,1.7,.66),'graphite',machines)
    cube(f'{label}_engine_case',(x,5.72,3.4),(1.4,1.45,.65),'teal',machines)
    for k in range(4):cube(f'{label}_cooling_fin_{k}',(x,5.2+k*.28,3.79),(1.35,.1,.13),'steel',machines)
    for xx in [-.63,.63]:
        beam(f'{label}_fan_stanchion_{xx}',(x+xx,5.9,3.1),(x+xx*.7,6.4,5.17),.24,'copper',machines)
        beam(f'{label}_fan_brace_{xx}',(x+xx,5.15,3.2),(x+xx*.7,6.4,4.7),.12,'steel',machines)
    ring(f'{label}_fan_ring',(x,y,z),1.55,.17,.42,'graphite',machines)
    ring(f'{label}_fan_ring_inlay',(x,y-.23,z),1.53,.055,.045,'teal',machines)
    for a in [0,90,180,270]:
        rad=math.radians(a);xx=x+math.cos(rad)*1.55;zz=z+math.sin(rad)*1.55
        cube(f'{label}_ring_clamp_{a}',(xx,y-.255,zz),(.23,.13,.23),'copper',machines)
    for a in [45,135,225,315]:
        rad=math.radians(a)
        beam(f'{label}_fan_guard_{a}',(x,y-.31,z),(x+math.cos(rad)*1.45,y-.31,z+math.sin(rad)*1.45),.055,'steel',machines)
    rotor=group(f'{label}_rotor',root,(x,y,z),'rotor');rotors.append(rotor)
    cylinder(f'{label}_hub',(x,y,z),.24,.56,'copper',rotor)
    cylinder(f'{label}_hub_cap',(x,y-.32,z),.15,.12,'brass',rotor)
    for k in range(6):
        a=math.radians(k*60)
        # Flat six-blade rotor, tapered visually with staggered rectangular pieces.
        for j,(r,w) in enumerate([(.5,.23),(.88,.34),(1.24,.28)]):
            xx=x+math.cos(a)*r;zz=z+math.sin(a)*r
            cube(f'{label}_blade_{k}_{j}',(xx,y,zz),(.45,.12,w),'ivory',rotor,rotation=(0,-k*60,0))

# One compact folded crane at the stern center, clear of both blade disks.
cube('Crane_pedestal',(0,5.55,2.9),(.85,.85,.85),'graphite',crane)
cylinder('Crane_slew_base',(0,5.55,3.42),.45,.23,'copper',crane,axis='Z')
beam('Crane_upright',(0,5.55,3.48),(0,5.55,4.46),.3,'teal',crane)
beam('Crane_folded_boom',(0,5.55,4.43),(-1.35,4.4,4.65),.25,'copper',crane)
beam('Crane_return_arm',(-1.35,4.4,4.65),(-.45,4.65,4.18),.2,'graphite',crane)
beam('Crane_hydraulic',(0,5.55,3.9),(-1.1,4.6,4.6),.11,'steel',crane)
beam('Crane_cable',(-.45,4.65,4.18),(-.45,4.65,3.9),.035,'rubber',crane)
cube('Crane_stowed_hook',(-.45,4.65,3.88),(.17,.16,.19),'brass',crane)
# Compact secured supplies on the aft roof, outside hatch outlines.
for side in [-1,1]:
    x=side*2.17;y=2.9
    cube(f'Deck_supply_case_{side}',(x,y,5.28),(.85,1.3,.6),'teal',roof)
    for yy in [y-.43,y+.43]:cube(f'Deck_supply_strap_{side}_{yy}',(x,yy,5.61),(.85,.09,.08),'copper',roof)

model={'name':'Skat SK01','revision':'SK01','units':'1 unit = 1 Minecraft block; art dimensions, not physical engineering',
    'authoritative_geometry':'source/skat_SK01.json','coordinate_system':{'X':'starboard','Y':'aft','Z':'up'},
    'groups':groups,'parts':parts,'cargo_modules':modules,'rotor_groups':rotors,
    'walkable_floor_z':floor_z,'cargo_aisle':{'x':[-.91,.91],'y':[-2.23,4.3],'z':[2.58,4.61]},
    'target_capacity':{'modules':24,'slots_per_module':54,'total_slots':1296,'stack64_items':82944,'status':'design target; no runtime inventories'},
    'cutaway_hide_groups':[roof,cabroof,windows],
    'runtime_entity':False,'physics_verified':False,'designed_for':'lower toxic sea only'}
names=[p['name'] for p in parts];assert len(names)==len(set(names))
assert len(modules)==24
(ROOT/'source/skat_SK01.json').write_text(json.dumps(model,indent=2)+'\n')
(ROOT/'reports/DESIGN_CHECK.json').write_text(json.dumps({'revision':'SK01','parts':len(parts),'groups':len(groups),'cargo_modules':len(modules),
    'cargo_slot_target':1296,'unique_part_names':True,'units':'Minecraft blocks','runtime_inventory_verified':False},indent=2)+'\n')
print(json.dumps({'parts':len(parts),'groups':len(groups),'cargo_modules':len(modules),'slots_target':1296}))
