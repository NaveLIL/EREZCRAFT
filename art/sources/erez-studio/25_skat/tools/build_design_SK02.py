"""SK02: rebuild the silhouette around the original concept, with cargo below deck."""
from pathlib import Path
import math,json,importlib.util
ROOT=Path(__file__).resolve().parents[1]
# Reuse the primitive schema without executing or changing the archived SK01 builder.
parts=[];groups=[]
def group(name,parent=None,pivot=(0,0,0),role='static'):
    groups.append(dict(name=name,parent=parent,pivot=list(pivot),role=role));return name
def cube(name,center,size,mat,grp,rotation=(0,0,0),tag=None):
    parts.append(dict(name=name,shape='cube',center=list(center),size=list(size),material=mat,group=grp,rotation=list(rotation),tag=tag))
def beam(name,a,b,width,mat,grp,depth=None):
    parts.append(dict(name=name,shape='beam',a=list(a),b=list(b),width=width,depth=depth or width,material=mat,group=grp))
def ring(name,center,radius,thickness,depth,mat,grp,n=12):
    parts.append(dict(name=name,shape='ring',center=list(center),radius=radius,thickness=thickness,depth=depth,segments=n,material=mat,group=grp))
def cylinder(name,center,radius,depth,mat,grp,axis='Y',n=12):
    parts.append(dict(name=name,shape='cylinder',center=list(center),radius=radius,depth=depth,segments=n,axis=axis,material=mat,group=grp))
def solid(name,vertices,faces,mat,grp):
    parts.append(dict(name=name,shape='solid',vertices=vertices,faces=faces,material=mat,group=grp))
def pane(name,a,b,bottom,top,inset,mat,grp):
    # A glass/wall trapezoid: top edge is recessed toward the cabin center.
    ax,ay=a;bx,by=b;tx,ty=inset
    vertices=[(ax,ay,bottom),(bx,by,bottom),(bx+tx,by+ty,top),(ax+tx,ay+ty,top)]
    # Thin two-sided pane, so it remains a solid editable mesh in GLB.
    normal=(-.045 if abs(bx-ax)<abs(by-ay) else 0,-.045 if abs(bx-ax)>=abs(by-ay) else 0,0)
    vertices += [(x+normal[0],y+normal[1],z) for x,y,z in vertices]
    solid(name,vertices,[(0,1,2,3),(7,6,5,4),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)],mat,grp)
root=group('SKAT_SK02')
hulls=group('01_Hull_shells',root);bow=group('02_Long_stepped_bows',root);ribs=group('03_Hull_plating_and_ribs',root)
structure=group('04_Cross_structure',root);cargo=group('05_Cargo_modules_24',root);cargo_in=group('06_Hold_interior',root)
deck=group('07_Main_cargo_deck',root,role='cutaway_hide');hatches=group('08_Deck_hatches',root,role='cutaway_hide')
cab=group('09_Sloped_cabin',root);glass=group('10_Cabin_glass',root,role='cutaway_hide');cabroof=group('11_Cabin_roof',root,role='cutaway_hide')
cab_in=group('12_Cabin_interior',root);rails=group('13_Deck_rails',root);lights=group('14_Lamps_and_fittings',root)
engines=group('15_Engines_and_ducts',root);crane=group('16_Cargo_crane',root);supplies=group('17_Secured_deck_cargo',root,role='cutaway_hide')
stair_leaf=group('18_Stair_hatch_leaf',root,(0,9.3,4.1),'hinged_hatch')

# The two hulls remain hollow. Inner plates stop below cargo floor to avoid hidden
# solid cubes intersecting the hold. The bow channel stays open to Y=-6.7.
for side in [-1,1]:
    x=side*3.6;label='Port' if side<0 else 'Starboard'
    for j in range(19):
        y=-6.25+j
        cube(f'{label}_keel_{j}',(x,y,.4),(2.1,.98,.7),'graphite',hulls)
        cube(f'{label}_bottom_chine_{j}',(x,y,.84),(2.85,.98,.25),'graphite',hulls)
        cube(f'{label}_inner_lower_{j}',(side*2.04,y,1.04),(.18,.98,.58),'graphite',hulls)
        # Independent outer plates, including seams and a restrained colored belt.
        for row in range(3):
            z=1.38+row*.88
            cube(f'{label}_outer_armor_{j}_{row}',(side*5.12,y,z),(.25,.97,.86),'graphite',hulls)
        cube(f'{label}_teal_belt_{j}',(side*5.263,y,2.45),(.06,.97,.29),'teal',ribs)
        cube(f'{label}_armor_lip_{j}',(side*5.28,y,3.56),(.08,.97,.15),'steel',ribs)
        cube(f'{label}_inside_roof_support_{j}',(side*4.16,y,2.53),(.14,.98,2.5),'graphite',structure)
        # Top side walk is flush with cargo roof, as in the concept.
        cube(f'{label}_side_deck_{j}',(side*4.7,y,3.87),(1.23,.98,.18),'deck',deck)
        if j%2==0:
            cube(f'{label}_side_deck_grate_{j}',(side*4.72,y,3.98),(.83,.88,.045),'graphite',deck)
            for k in range(4):cube(f'{label}_grate_bar_{j}_{k}',(side*4.72,y-.29+k*.19,4.015),(.78,.06,.04),'steel',deck)
    # Ends of hollow hulls; stern lower lip and prow rake.
    for yy in [-6.77,12.24]:
        cube(f'{label}_endplate_{yy}',(x,yy,2.1),(3.2,.2,3.2),'graphite',hulls)
    # Six stepped sections give each bow a proper long, narrowing nose.
    for k in range(6):
        yy=-12.8+k;w=.72+k*.49;top=1.5+k*.4;bottom=.35
        cube(f'{label}_prow_section_{k}',(x,yy,(top+bottom)/2),(w,.98,top-bottom),'graphite',bow)
        for level in range(2):
            capw=w+.1-level*.17
            cube(f'{label}_prow_cap_{k}_{level}',(x,yy,top+.065+level*.12),(capw,.98,.12),'steel' if level==0 else 'deck',bow)
        for edge in [-1,1]:
            cube(f'{label}_prow_edge_{k}_{edge}',(x+edge*(w/2-.07),yy,top+.18),(.1,.9,.1),'steel',bow)
        if k in [0,2,4]:cube(f'{label}_prow_copper_band_{k}',(x,yy-.5,top-.22),(w*.75,.07,.16),'copper',bow)
    for j,yy in enumerate([-6.2,-3.7,-1.2,1.3,3.8,6.3,8.8,11.3]):
        cube(f'{label}_copper_rib_{j}',(side*5.32,yy,2.18),(.12,.23,2.65),'copper',ribs)
        cube(f'{label}_rib_shoulder_{j}',(side*5.19,yy,3.66),(.42,.23,.2),'copper',ribs)
        for z in [1.05,2.35,3.4]:cube(f'{label}_rib_bolt_{j}_{z}',(side*5.39,yy,z),(.05,.095,.095),'brass',ribs)
    cube(f'{label}_prow_transition',(x,-7.02,2.0),(3.2,.56,3.3),'graphite',bow)
    cube(f'{label}_prow_transition_cap',(x,-7.02,3.75),(3.28,.56,.2),'deck',bow)
    for yy in [-4.6,2.9,10.4]:
        cube(f'{label}_fender_{yy}',(side*5.4,yy,2.9),(.25,1.35,.65),'rubber',ribs)
        for zz in [2.67,3.12]:cube(f'{label}_fender_clip_{yy}_{zz}',(side*5.53,yy,zz),(.05,1.1,.07),'steel',ribs)
    # Built-up headlamp housing on each narrow prow.
    xx=x;yy=-12.8
    cube(f'{label}_prow_lamp_base',(xx,yy,1.77),(.52,.45,.18),'graphite',lights)
    cube(f'{label}_prow_lamp_glass',(xx,yy,2.02),(.27,.27,.38),'lamp',lights)
    for e in [-1,1]:cube(f'{label}_prow_lamp_guard_{e}',(xx+e*.18,yy,2.02),(.045,.3,.44),'copper',lights)
    cube(f'{label}_prow_lamp_cap',(xx,yy,2.25),(.46,.42,.1),'graphite',lights)

# Low bridge floor and beams leave a visible sea channel forward of the cabin.
for yy in [-6.5,-4,-1.5,1,3.5,6,8.5,11.5]:
    cube(f'Cross_member_{yy}',(0,yy,1.32),(7.2,.32,.3),'graphite',structure)
cube('Hold_floor',(0,3.35,1.52),(8.1,11.1,.16),'graphite',cargo_in)
for side in [-1,1]:
    for yy in [-2.1,8.9]:cube(f'Hold_bulkhead_{side}_{yy}',(side*2.62,yy,2.65),(2.85,.18,2.12),'graphite',structure)
for side in [-1,1]:cube(f'Hold_long_light_{side}',(side*3.94,2.2,3.64),(.08,8.5,.08),'cyan',cargo_in)
for yy in [-1,1,3,5]:cube(f'Hold_aisle_marker_{yy}',(0,yy,1.615),(.4,.6,.018),'ivory',cargo_in)

modules=[]
for side in [-1,1]:
    for bay in range(6):
        yy=-1.45+1.32*bay
        for tier in range(2):
            z=2.08+tier*.99;ident=f'{"P" if side<0 else "S"}{bay+1}{"L" if tier==0 else "U"}'
            xx=side*2.72
            cube(f'Cargo_{ident}_case',(xx,yy,z),(2.4,1.13,.82),'graphite',cargo,tag='cargo_module')
            face=side*1.49
            cube(f'Cargo_{ident}_front',(face,yy,z),(.08,1.04,.69),'wood' if bay%3==0 else 'teal',cargo)
            for e in [-1,1]:cube(f'Cargo_{ident}_strap_{e}',(face-side*.06,yy+e*.38,z),(.07,.07,.71),'copper',cargo)
            cube(f'Cargo_{ident}_handle',(face-side*.1,yy,z+.01),(.1,.38,.1),'brass',cargo)
            cube(f'Cargo_{ident}_label',(face-side*.1,yy-.2,z+.22),(.03,.17,.11),'ivory',cargo)
            modules.append(dict(id=ident,center=[xx,yy,z],size=[2.4,1.13,.82],target_slots=54,implemented_inventory=False))
        for edge in [-.62,.62]:
            cube(f'Rack_post_{side}_{bay}_{edge}',(side*4.0,yy+edge,2.68),(.1,.08,2.1),'steel',cargo)
        cube(f'Rack_plinth_{side}_{bay}',(side*2.72,yy,1.64),(2.47,1.18,.08),'steel',cargo)

# The upper deck is the cargo hold roof, so the bulky container disappears.
for ix in range(8):
    xx=-3.5+ix
    for j in range(19):
        yy=-6.25+j
        if ix in [3,4] and 6.25<yy<9.25:continue  # genuine stair opening beneath hinged hatch
        cube(f'Deck_plate_{ix}_{j}',(xx,yy,3.87),(.98,.98,.18),'deck',deck)
        if (ix+j)%4==0:
            for dx in [-.36,.36]:cube(f'Deck_fastener_{ix}_{j}_{dx}',(xx+dx,yy-.35,3.971),(.07,.07,.035),'steel',deck)
# Six hatch leaves arranged as three pairs, following the concept's cargo grid.
for j,yy in enumerate([-.4,2.65,5.7]):
    for side in [-1,1]:
        xx=side*1.7
        cube(f'Hatch_frame_{j}_{side}',(xx,yy,4.005),(2.8,2.25,.13),'copper',hatches)
        cube(f'Hatch_recess_{j}_{side}',(xx,yy,4.085),(2.62,2.07,.08),'rubber',hatches)
        cube(f'Hatch_leaf_{j}_{side}',(xx,yy,4.14),(2.44,1.89,.08),'graphite',hatches)
        for q in [-1,1]:
            cube(f'Hatch_crossstrap_{j}_{side}_{q}',(xx+q*.7,yy,4.2),(.1,1.83,.05),'steel',hatches)
            cube(f'Hatch_hinge_{j}_{side}_{q}',(xx+q*.85,yy+1.09,4.16),(.37,.16,.15),'brass',hatches)
        cube(f'Hatch_handle_{j}_{side}',(xx,yy-.65,4.25),(.47,.12,.12),'copper',hatches)
        for e in [-1,1]:cube(f'Hatch_latch_{j}_{side}_{e}',(xx+e*1.25,yy-.55,4.2),(.12,.3,.12),'brass',hatches)
# Real stairs occupy a reserved rear bay, separate from the cargo rows.
for step in range(6):
    yy=6.4+step*.42;zz=1.7+step*.38
    cube(f'Hold_stair_{step}',(0,yy,zz),(1.6,.42,.16),'steel',cargo_in)
for side in [-1,1]:beam(f'Hold_stair_handhold_{side}',(side*.85,6.3,2.5),(side*.85,8.6,4.6),.07,'copper',cargo_in)
# For the solid presentation the stair hatch is closed; cutaway hides it with the deck.
for side in [-1,1]:cube(f'Stair_hatch_frame_side_{side}',(side*1.07,7.75,4.01),(.14,3.3,.13),'copper',hatches)
for yy in [6.17,9.33]:cube(f'Stair_hatch_frame_end_{yy}',(0,yy,4.01),(2.0,.14,.13),'copper',hatches)
cube('Stair_hatch_leaf',(0,7.75,4.13),(2.05,3.1,.1),'graphite',stair_leaf)
cube('Stair_hatch_handle',(0,6.5,4.23),(.55,.12,.12),'brass',stair_leaf)
for xx in [-.65,.65]:cube(f'Stair_hatch_hinge_{xx}',(xx,9.3,4.12),(.35,.18,.16),'brass',hatches)

# Sloping forward bridge; large readable glazed belt instead of dark vertical boxes.
# Lower footprint front corners are chamfered; upper glazing leans aft by .5 block.
cube('Cabin_floor',(0,-4.45,4.0),(7.8,4.2,.12),'wood',cab_in)
cube('Cabin_front_lower',(0,-6.6,4.38),(5.8,.28,.82),'graphite',cab)
for side in [-1,1]:
    cube(f'Cabin_corner_plinth_{side}',(side*3.35,-6.1,4.38),(1.45,.28,.82),'graphite',cab,rotation=(0,0,side*-45))
    cube(f'Cabin_side_plinth_{side}',(side*3.85,-4.2,4.38),(.25,3.4,.82),'graphite',cab)
    cube(f'Cabin_side_belt_{side}',(side*4.0,-4.2,4.48),(.07,3.35,.2),'copper',cab)
    for yy in [-5.35,-3.95,-2.5]:
        beam(f'Cabin_side_mullion_{side}_{yy}',(side*3.85,yy,4.81),(side*3.5,yy+.12,6.03),.16,'steel',cab)
    for i,yy in enumerate([-4.66,-3.23]):
        pane(f'Cabin_side_glass_{side}_{i}',(side*3.85,yy-.6),(side*3.85,yy+.6),4.82,6.01,(-side*.35,.12),'glass',glass)
    pane(f'Cabin_corner_glass_{side}',(side*2.93,-6.56),(side*3.8,-5.58),4.82,6.01,(-side*.34,.48),'glass',glass)
    for i,yy in enumerate([-4.8,-3.6]):
        # Small mechanical vents below the glazing.
        for k in range(4):cube(f'Cabin_louvre_{side}_{i}_{k}',(side*4.01,yy-.35+k*.22,4.33),(.06,.12,.28),'steel',cab)
    cube(f'Cabin_headlamp_housing_{side}',(side*2.1,-6.85,4.4),(.85,.3,.58),'steel',lights)
    cube(f'Cabin_headlamp_face_{side}',(side*2.1,-7.02,4.4),(.5,.05,.34),'lamp',lights)
    cube(f'Cabin_headlamp_visor_{side}',(side*2.1,-7.07,4.73),(.91,.38,.1),'graphite',lights)
    # Rear cabin doors retain the central passage; closed exterior is an art pose.
    cube(f'Cabin_rear_wall_{side}',(side*2.45,-2.3,5.02),(2.95,.22,2.13),'graphite',cab)
for i,xx in enumerate([-2.12,-.7,.7,2.12]):
    pane(f'Cabin_windscreen_{i}',(xx-.61,-6.62),(xx+.61,-6.62),4.82,6.02,(0,.52),'glass',glass)
for xx in [-2.92,-1.4,0,1.4,2.92]:
    beam(f'Cabin_windscreen_frame_{xx}',(xx,-6.65,4.78),(xx,-6.12,6.08),.16,'steel',cab)
for z,yy in [(4.79,-6.64),(6.08,-6.12)]:cube(f'Cabin_front_frame_{z}',(0,yy,z),(6.05,.2,.14),'graphite',cab)
for side in [-1,1]:
    beam(f'Cabin_glass_wiper_{side}',(side*1.4,-6.69,4.9),(side*2.08,-6.3,5.83),.045,'rubber',cab)
    cube(f'Cabin_rear_door_{side}',(side*.48,-2.32,5.02),(.92,.18,2.02),'teal',cab)
    cube(f'Cabin_rear_door_latch_{side}',(side*.16,-2.18,4.95),(.1,.1,.25),'brass',cab)
# The roof matches the recessed glass edge, with a broad layered visor.
for ix in range(8):
    for j in range(4):cube(f'Cabin_roof_plate_{ix}_{j}',(-3.5+ix,-5.7+j,6.16),(.98,.98,.19),'deck',cabroof)
cube('Cabin_roof_front_visor',(0,-6.25,6.14),(7.0,.58,.24),'graphite',cabroof)
cube('Cabin_roof_copper_front',(0,-6.31,6.3),(6.9,.17,.09),'copper',cabroof)
for side in [-1,1]:
    cube(f'Cabin_roof_binding_{side}',(side*2.55,-4.3,6.31),(.18,4.0,.12),'copper',cabroof)
    cube(f'Cabin_roof_light_case_{side}',(side*3.3,-3.45,6.45),(.5,.5,.26),'graphite',cabroof)
    cube(f'Cabin_roof_light_{side}',(side*3.3,-3.45,6.71),(.24,.24,.29),'lamp',cabroof)
    cube(f'Filter_case_{side}',(side*2.8,-2.95,6.55),(.72,1.0,.55),'graphite',cabroof)
    for k in range(5):cube(f'Filter_top_grille_{side}_{k}',(side*2.8,-3.31+k*.18,6.85),(.6,.07,.05),'steel',cabroof)
    beam(f'Filter_duct_{side}',(side*2.8,-2.7,6.8),(side*2.8,-2.7,7.2),.2,'copper',cabroof)
    cube(f'Filter_duct_cap_{side}',(side*2.8,-2.7,7.23),(.42,.42,.12),'graphite',cabroof)
# Navigation cluster is compact and asymmetrical, like real deck equipment.
cube('Navigation_mast_base',(-.9,-4.35,6.38),(1.55,1.2,.3),'copper',cabroof)
cube('Navigation_mast',(-.9,-4.35,7.08),(.22,.22,1.1),'graphite',cabroof)
cube('Navigation_crossbar',(-.9,-4.35,7.3),(1.25,.15,.15),'steel',cabroof)
cube('Navigation_beacon',(-.9,-4.35,7.75),(.22,.22,.3),'lamp',cabroof)
for x,z in [(-1.6,7.15),(-.25,7.0)]:
    cube(f'Antenna_{x}',(x,-4.35,z),(.1,.1,.7),'steel',cabroof)
# Two detailed seats and a broad dashboard remain editable under the removable roof.
for side in [-1,1]:
    xx=side*1.65
    cube(f'Seat_pedestal_{side}',(xx,-4.2,4.4),(.35,.35,.7),'steel',cab_in)
    cube(f'Seat_cushion_{side}',(xx,-4.2,4.84),(.76,.8,.19),'rubber',cab_in)
    cube(f'Seat_back_{side}',(xx,-3.85,5.21),(.76,.17,.7),'rubber',cab_in)
    for e in [-1,1]:cube(f'Seat_armrest_{side}_{e}',(xx+e*.43,-4.16,5.15),(.13,.62,.13),'graphite',cab_in)
    cube(f'Console_{side}',(xx,-5.66,4.67),(2.2,.8,1.05),'teal',cab_in)
    cube(f'Console_top_{side}',(xx,-5.62,5.26),(2.23,.85,.13),'graphite',cab_in)
    for k in range(4):
        cube(f'Gauge_frame_{side}_{k}',(xx-.78+k*.5,-5.53,5.35),(.4,.32,.07),'brass',cab_in)
        cube(f'Gauge_face_{side}_{k}',(xx-.78+k*.5,-5.53,5.4),(.29,.23,.04),'cyan' if k%2 else 'glass',cab_in)
    beam(f'Control_stick_{side}',(xx+.5,-5.35,5.35),(xx+.5,-5.04,5.66),.09,'steel',cab_in)
    cube(f'Control_grip_{side}',(xx+.5,-5.04,5.67),(.24,.12,.12),'rubber',cab_in)

# Walkable upper deck and rich railing rhythm; copper trim runs the full length.
for side in [-1,1]:
    xx=side*5.12
    for j in range(19):
        yy=-6.25+j
        cube(f'Rail_post_{side}_{j}',(xx,yy,4.48),(.1,.1,1.12),'copper',rails)
        cube(f'Rail_foot_{side}_{j}',(xx,yy,4.01),(.26,.26,.13),'graphite',rails)
        if j%3==0:
            cube(f'Lantern_base_{side}_{j}',(xx,yy,5.01),(.33,.33,.12),'graphite',lights)
            cube(f'Lantern_glass_{side}_{j}',(xx,yy,5.24),(.23,.23,.35),'lamp',lights)
            cube(f'Lantern_cap_{side}_{j}',(xx,yy,5.46),(.35,.35,.1),'graphite',lights)
    for z in [4.43,5.02]:beam(f'Long_rail_{side}_{z}',(xx,-6.3,z),(xx,11.8,z),.075,'brass',rails)
    beam(f'Bow_rail_{side}',(xx,-6.3,5.02),(side*4.0,-7.1,4.65),.075,'brass',rails)
    for yy in [-5.5,10.9]:
        cube(f'Cleat_base_{side}_{yy}',(side*4.55,yy,4.05),(.8,.45,.15),'graphite',lights)
        for a in [-1,1]:cube(f'Cleat_stem_{side}_{yy}_{a}',(side*4.55+a*.23,yy,4.23),(.12,.15,.3),'steel',lights)
        cube(f'Cleat_bar_{side}_{yy}',(side*4.55,yy,4.37),(.94,.17,.12),'steel',lights)

rotors=[]
for side in [-1,1]:
    xx=side*3.0;yy=10.95;zz=6.5;label='Port' if side<0 else 'Starboard'
    cube(f'{label}_engine_skid',(xx,10.75,4.13),(2.4,2.4,.36),'graphite',engines)
    cube(f'{label}_engine_case',(xx,10.2,4.72),(1.9,1.8,.85),'teal',engines)
    for a in [-1,1]:
        cube(f'{label}_engine_corner_{a}',(xx+a*.92,10.2,4.72),(.16,1.85,.95),'copper',engines)
        beam(f'{label}_ring_support_{a}',(xx+a*.8,11.7,4.3),(xx+a*.55,11.65,6.5),.28,'copper',engines)
        beam(f'{label}_ring_brace_{a}',(xx+a*.8,12.0,4.35),(xx+a*.55,11.65,6.0),.13,'steel',engines)
        cylinder(f'{label}_side_drive_{a}',(xx+a*.7,9.95,5.3),.23,.6,'brass',engines)
    for k in range(6):cube(f'{label}_engine_fin_{k}',(xx,9.5+k*.24,5.22),(1.65,.1,.12),'steel',engines)
    beam(f'{label}_drive_pipe',(xx,9.35,4.5),(xx,9.35,5.7),.18,'copper',engines)
    beam(f'{label}_pipe_elbow',(xx,9.35,5.7),(xx,10.5,5.7),.18,'copper',engines)
    ring(f'{label}_duct_main',(xx,yy,zz),2.18,.24,.64,'graphite',engines,n=12)
    ring(f'{label}_duct_front_rim',(xx,yy-.36,zz),2.2,.09,.12,'steel',engines,n=12)
    ring(f'{label}_duct_teal',(xx,yy-.44,zz),2.13,.1,.05,'teal',engines,n=12)
    ring(f'{label}_duct_rear_rim',(xx,yy+.36,zz),2.2,.09,.12,'steel',engines,n=12)
    for k in range(12):
        a=2*math.pi*(k+.5)/12
        cube(f'{label}_duct_joint_{k}',(xx+math.cos(a)*2.12,yy-.48,zz+math.sin(a)*2.12),(.2,.12,.2),'copper' if k%3==0 else 'steel',engines)
    for a in [45,135,225,315]:
        r=math.radians(a)
        beam(f'{label}_duct_guard_{a}',(xx,yy-.57,zz),(xx+math.cos(r)*1.97,yy-.57,zz+math.sin(r)*1.97),.065,'steel',engines)
    rotor=group(f'{label}_rotor',root,(xx,yy,zz),'rotor');rotors.append(rotor)
    cylinder(f'{label}_rear_drive_bearing',(xx,11.55,zz),.4,.5,'graphite',engines)
    cylinder(f'{label}_rotor_hub',(xx,yy,zz),.32,.85,'copper',rotor)
    cylinder(f'{label}_rotor_cap',(xx,yy-.5,zz),.23,.2,'brass',rotor)
    for k in range(6):
        angle=math.radians(k*60)
        outline=[(.3,-.12),(.75,-.2),(1.65,-.28),(1.96,-.12),(1.9,.22),(.85,.34),(.32,.14)]
        vertices=[]
        for off in [-.1,.1]:
            for r,t in outline:
                r*=.93;t*=.93
                vertices.append((xx+math.cos(angle)*r-math.sin(angle)*t,yy+off,zz+math.sin(angle)*r+math.cos(angle)*t))
        n=len(outline);faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
        solid(f'{label}_blade_{k}',vertices,faces,'ivory',rotor)
        # A dark root collar is separate from the blade face and does not overlap it.
        rad=.42
        cube(f'{label}_blade_root_{k}',(xx+math.cos(angle)*rad,yy-.13,zz+math.sin(angle)*rad),(.38,.08,.29),'steel',rotor,rotation=(0,-k*60,0))

# Centerline articulated-looking crane, all components in front of both rotor disks.
cube('Crane_foundation',(0,9.4,4.2),(1.65,1.5,.5),'graphite',crane)
cylinder('Crane_slew',(0,9.4,4.64),.63,.28,'copper',crane,axis='Z')
cube('Crane_tower',(0,9.4,5.3),(.62,.6,1.15),'teal',crane)
cylinder('Crane_shoulder',(0,9.4,5.88),.32,.82,'copper',crane)
beam('Crane_main_boom',(0,9.4,5.87),(-1.35,7.25,7.1),.32,'copper',crane)
beam('Crane_main_spine',(.22,9.4,5.87),(-1.13,7.25,7.1),.11,'steel',crane)
beam('Crane_return_boom',(-1.35,7.25,7.1),(-.85,6.05,6.65),.25,'graphite',crane)
beam('Crane_ram',(0,9.4,5.1),(-.9,7.95,6.65),.12,'steel',crane)
beam('Crane_ram_barrel',(0,9.4,5.1),(-.5,8.6,5.96),.2,'teal',crane)
beam('Crane_cable',(-.85,6.05,6.65),(-.85,6.05,5.0),.035,'rubber',crane)
cube('Crane_hook_shank',(-.85,6.05,4.92),(.12,.15,.28),'brass',crane)
cube('Crane_hook_toe',(-.76,6.05,4.79),(.28,.15,.1),'brass',crane)
cube('Crane_hook_tip',(-.61,6.05,4.88),(.09,.15,.24),'brass',crane)
# Secured deck containers along outer edges keep the central hatch grid readable.
for side in [-1,1]:
    xx=side*3.8
    for j,yy in enumerate([.3,3.3,6.3]):
        cube(f'Deck_container_{side}_{j}',(xx,yy,4.47),(1.05,2.0,.93),'teal' if j!=1 else 'graphite',supplies)
        cube(f'Deck_container_lid_{side}_{j}',(xx,yy,4.99),(1.13,2.08,.14),'graphite',supplies)
        for a in [-1,1]:
            cube(f'Deck_container_strap_{side}_{j}_{a}',(xx,yy+a*.62,5.1),(1.16,.13,.09),'copper',supplies)
        cube(f'Deck_container_latch_{side}_{j}',(xx-side*.57,yy,4.62),(.09,.26,.35),'brass',supplies)
# A pair of wood sample crates adds material contrast without cluttering the bow.
for i,(xx,yy,z) in enumerate([(1.8,8.55,4.42),(1.8,8.55,5.31)]):
    cube(f'Sample_crate_{i}',(xx,yy,z),(.85,.85,.85),'wood',supplies)
    for e in [-1,1]:
        cube(f'Crate_frame_x_{i}_{e}',(xx+e*.38,yy-.45,z),(.08,.08,.86),'copper',supplies)
        cube(f'Crate_frame_z_{i}_{e}',(xx,yy-.45,z+e*.38),(.85,.08,.08),'copper',supplies)
    beam(f'Crate_diagonal_{i}',(xx-.34,yy-.5,z-.32),(xx+.34,yy-.5,z+.32),.065,'graphite',supplies)

# Keep a landing after the stair exit; shift the crane assembly clear of its hatch.
for part in parts:
    if part['group']==crane:
        for key in ['center','a','b']:
            if key in part:part[key][1]+=1.0

model={'name':'Skat SK02','revision':'SK02','units':'1 unit = 1 Minecraft block; game art, not naval engineering',
    'authoritative_geometry':'source/skat_SK02.json','coordinate_system':{'X':'starboard','Y':'aft','Z':'up'},
    'groups':groups,'parts':parts,'cargo_modules':modules,'rotor_groups':rotors,'walkable_floor_z':3.97,
    'cargo_aisle':{'x':[-1.25,1.25],'y':[-1.9,5.6],'z':[1.64,3.7]},
    'stair_reserved_bay':{'x':[-1,1],'y':[6.0,9.0],'z':[1.6,4.0]},
    'target_capacity':{'modules':24,'slots_per_module':54,'total_slots':1296,'stack64_items':82944,'status':'design target; not implemented'},
    'cutaway_hide_groups':[deck,hatches,glass,cabroof,supplies,stair_leaf],
    'runtime_entity':False,'physics_verified':False,'designed_for':'lower toxic sea only',
    'design_change':'Rebuilt to match original concept: long stepped bows, low below-deck hold, sloping wide windscreen, large ducted fans, detailed armor and deck equipment.'}
assert len(parts)==len({p['name'] for p in parts})
(ROOT/'source/skat_SK02.json').write_text(json.dumps(model,indent=2)+'\n')
(ROOT/'reports/DESIGN_SK02.json').write_text(json.dumps({'revision':'SK02','parts':len(parts),'groups':len(groups),'cargo_modules':24,'cargo_slots_target':1296,'physics_verified':False},indent=2)+'\n')
print(json.dumps({'parts':len(parts),'groups':len(groups),'cargo_modules':24}))
