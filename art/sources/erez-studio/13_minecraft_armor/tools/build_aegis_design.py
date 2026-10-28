"""A02 adds the endgame silhouette; the two approved A01 sets remain byte-equivalent as data."""
from pathlib import Path
from copy import deepcopy
import json,shutil,math
ROOT=Path(__file__).resolve().parents[1]
design=json.loads((ROOT/'source/armor_design.json').read_text(encoding='utf-8'))
aegis=deepcopy(design['sets']['riftsteel'])
aegis['palette']={'plate':'#303c50','edge':'#8dabb8','shadow':'#1b263b','cloth':'#202838','strap':'#4e4850','buckle':'#67d9cd','dark':'#102334'}
def box(name,bone,slot,xyz,size,role='plate'):
    aegis['cubes'].append({'name':name,'bone':bone,'slot':slot,'from':xyz,'size':size,'role':role})
# Continuous heavy brow, recessed optic strip, distributed controllers and nested armor tiles.
box('optic_recess','head','head',[-3.2,-5.1,-5.35],[6.4,.85,.35],'dark')
for x in [-2.8,1.8]:box('optic_lens_'+str(x),'head','head',[x,-4.95,-5.55],[1,.5,.3],'buckle')
for s in [-1,1]:
    box('sensor_pod_'+str(s),'head','head',[-5.25 if s<0 else 4.55,-5.8,-1.4],[.7,2.4,2.8],'shadow')
    box('shoulder_outer_plate_'+str(s),'right_arm' if s<0 else 'left_arm','chest',[-4 if s<0 else 1.8,-2.6,-2.8],[2.2,4.3,5.6])
    box('shoulder_sensor_'+str(s),'right_arm' if s<0 else 'left_arm','chest',[-4.15 if s<0 else 3.8,-1.9,-1.5],[.35,1,2.5],'buckle')
    box('thigh_extra_'+str(s),'right_leg' if s<0 else 'left_leg','legs',[-1.65,1,-3.3],[3.3,2.8,.65])
    box('boot_power_strip_'+str(s),'right_leg' if s<0 else 'left_leg','feet',[-1.4,9,-3.25],[2.8,.5,.35],'buckle')
box('chest_controller','body','chest',[-1.1,2.2,-4.7],[2.2,2.1,.65],'shadow')
box('chest_reactor_window','body','chest',[-.65,2.65,-4.9],[1.3,1.2,.35],'buckle')
box('back_gravity_harvester','body','chest',[-2.1,1,3.8],[4.2,5.9,1.2],'shadow')
for y in [1.5,3,4.5]:box('harvester_rib_'+str(y),'body','chest',[-1.7,y,4.85],[3.4,.5,.4],'edge')
box('back_energy_indicator','body','chest',[-.4,1.4,5.05],[.8,4.4,.35],'buckle')
# Power armor A03: replace expedition soft bags/diagonal strap with an enclosed hard shell.
aegis['cubes']=[c for c in aegis['cubes'] if not any(k in c['name'] for k in ['cross_strap','strap_clasp','belt_pouch','pouch_flap','chest_reactor_window'])]
for c in aegis['cubes']:
    name=c['name']
    if name=='helmet_crown':c['from']=[-5.2,-9.1,-5.2];c['size']=[10.4,2,10.4]
    if name=='helmet_crest':c['from']=[-4,-9.6,-4.6];c['size']=[8,.5,8.7]
    if name=='helmet_rear':c['from']=[-5.1,-7.1,3.6];c['size']=[10.2,6.7,1.7]
    if name.startswith('pauldron_cap'):
        center=c['from'][0]+c['size'][0]/2;c['size']=[7.4,2.8,7.1];c['from']=[center-3.7,-3.3,-3.55]
    if name.startswith('pauldron_crown'):
        center=c['from'][0]+c['size'][0]/2;c['size']=[6,.65,5.8];c['from']=[center-3,-3.95,-2.9]
    if name.startswith('breastplate_') and 'rim' not in name:
        c['from'][2]=-4.9;c['size'][2]=2.6
    if name.startswith('breastplate_rim'):c['from'][2]=-5.05;c['size'][2]=2.65
    if name=='chest_controller':c['from']=[-2.25,1.5,-5.6];c['size']=[4.5,4.1,.8]
    if name.startswith('abdomen_'):c['from'][2]-=.7;c['size'][2]+=.65
    if name.startswith('vambrace_') and 'rim' not in name:c['from'][2]-=.6;c['size'][2]+=.65
box('sealed_visor','head','head',[-3.55,-5.5,-5.45],[7.1,1.75,.6],'dark')
box('face_seal_gasket','head','head',[-4.3,-6,-4.7],[8.6,6.1,.6],'dark')
box('sealed_faceplate','head','head',[-3.6,-3.75,-5.35],[7.2,3.45,1.15],'plate')
box('visor_brow_guard','head','head',[-4.6,-6.2,-5.8],[9.2,.8,1.1],'edge')
for s in [-1,1]:
    box('jaw_filter_'+str(s),'head','head',[s*2.55-.65,-2.95,-5.8],[1.3,1.85,.8],'shadow')
    for y in [-2.65,-2.05,-1.45]:box('filter_rib_'+str(s)+'_'+str(y),'head','head',[s*2.55-.52,y,-5.92],[1.04,.2,.3],'edge')
    bone='right_arm' if s<0 else 'left_arm';cx=-1 if s<0 else 1
    box('forearm_outer_shell_'+str(s),bone,'chest',[cx-2.7,6,-3.55],[5.4,3.9,1.4])
    box('power_piston_'+str(s),bone,'chest',[(-4.2 if s<0 else 3.55),2,-.65],[.65,5.5,1.3],'edge')
    leg='right_leg' if s<0 else 'left_leg'
    box('leg_outer_shell_'+str(s),leg,'legs',[(-3.05 if s<0 else 1.85),.7,-1.7],[1.2,3.8,3.5])
    box('calf_outer_shell_'+str(s),leg,'feet',[(-3.15 if s<0 else 1.9),7.2,-1.8],[1.25,3.8,3.9])
    box('hip_controller_'+str(s),'body','legs',[(-5.3 if s<0 else 3.5),9.3,-3.65],[1.8,2.8,1.2],'shadow')
# David's star: six physical metal rails, no painted sprite, centered on the recessed chest controller.
cx,cy,cz=0,3.5,-5.83;r=1.65
triangles=[[(0,-r),(-r*.866,r*.5),(r*.866,r*.5)],[(0,r),(-r*.866,-r*.5),(r*.866,-r*.5)]]
for t,points in enumerate(triangles):
    for i in range(3):
        a,b=points[i],points[(i+1)%3];mx,my=cx+(a[0]+b[0])/2,cy+(a[1]+b[1])/2
        length=math.hypot(b[0]-a[0],b[1]-a[1]);angle=math.degrees(math.atan2(-(b[0]-a[0]),b[1]-a[1]))
        box(f'david_star_{t}_{i}','body','chest',[mx-.14,my-length/2,cz],[.28,length,.18],'buckle')
        aegis['cubes'][-1].update(rotation=[0,0,angle],rotation_pivot=[mx,my,cz])
design['sets']['aegis']=aegis;design['revision']='ARMOR-A03';design['state']='Approved A01 sets plus Aegis power armor; game validation recorded separately'
target=ROOT/'source/armor_design_A03.json';target.write_text(json.dumps(design,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
candidate=Path('D:/repos/Minecraft/EREZCRAFT/.verification/armor_a01/src/main/resources/assets/interstice/armor/armor_design.json')
shutil.copy2(target,candidate)
print({k:len(v['cubes']) for k,v in design['sets'].items()})
