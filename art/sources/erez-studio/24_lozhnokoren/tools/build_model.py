"""Authoritative LK01 geometry/rig/animation generator. 16 model units per block."""
from pathlib import Path
import json,math,uuid,base64,hashlib
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'source/texture_manifest.json').read_text());TI={t['name']:i for i,t in enumerate(manifest)}
groups=[];elements=[];nodes={};G={};parents={};series=[]
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'erezcraft/lozhnokoren/LK01/'+name))
def bone(name,origin,parent=None):
    g={'name':name,'uuid':uid('bone/'+name),'origin':list(origin),'rotation':[0,0,0],
       'export':True,'visibility':True,'isOpen':False,'shade':True,'mirror_uv':False,'children':[]}
    groups.append(g);G[name]=g;n={'uuid':g['uuid'],'isOpen':False,'children':[]};nodes[name]=n;parents[name]=parent
    if parent:nodes[parent]['children'].append(n)
    return name
def cube(name,parent,center,size,texture='bark',rotation=None,end_texture=None,uv=None):
    center=list(map(float,center));size=list(map(float,size));dx,dy,dz=size
    dims={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
    faces={side:{'uv':list(uv) if uv else [0,0,w,h],'texture':TI[end_texture if side=='up' and end_texture else texture]}
           for side,(w,h) in dims.items()}
    e={'name':name,'uuid':uid('cube/'+name),'type':'cube','box_uv':False,'export':True,'shade':True,
       'from':[center[i]-size[i]/2 for i in range(3)],'to':[center[i]+size[i]/2 for i in range(3)],
       'origin':center,'rotation':list(rotation or [0,0,0]),'faces':faces}
    elements.append(e);nodes[parent]['children'].append(e['uuid']);return e
def rotation_y_to(direction):
    d=np.array(direction,dtype=float);d=d/np.linalg.norm(d);a=np.array([0.,1.,0.]);v=np.cross(a,d);c=float(a@d);s=float(np.linalg.norm(v))
    if s<1e-8:R=np.eye(3) if c>0 else np.diag([1,-1,-1])
    else:
        K=np.array([[0,-v[2],v[1]],[v[2],0,-v[0]],[-v[1],v[0],0]])
        R=np.eye(3)+K+K@K*((1-c)/(s*s))
    y=math.asin(max(-1,min(1,-R[2,0])));x=math.atan2(R[2,1],R[2,2]);z=math.atan2(R[1,0],R[0,0])
    return [math.degrees(x),math.degrees(y),math.degrees(z)]
def beam(name,parent,a,b,width,depth=None,texture='bark',end_texture=None):
    a=np.array(a,dtype=float);b=np.array(b,dtype=float);delta=b-a
    return cube(name,parent,(a+b)/2,[width,float(np.linalg.norm(delta)),depth or width],texture,rotation_y_to(delta),end_texture)
root=bone('root',[0,0,0])
centers=[];widths=[31,34,35,34,32,29,26,23,20,16,12]
for i,w in enumerate(widths):
    z=-38+i*9.5;x=6.0*math.sin(i*.61);y=17+1.0*math.sin(i*.4)
    name=bone(f'segment_{i:02d}',[x,y,z],root if i==0 else f'segment_{i-1:02d}')
    centers.append((x,y,z));series.append(name)
    length=12.5 if i<8 else 11.5
    cube(f'{name}_soft_core',name,[x,y,z],[w*.75,12,length-1],'joints')
    cube(f'{name}_belly',name,[x,y-4,z],[w*.64,5,length-2],'dark_bark')
    cube(f'{name}_crown',name,[x,y+11.5,z-.5],[w*.46,5,length],'bark')
    # A broad arched carapace from rotated flat cuboids, with overlapping margins.
    for sign in [-1,1]:
        cube(f'{name}_upper_arch_{sign}',name,[x+sign*w*.22,y+9.2,z-.3],[w*.32,4.8,length+.5],
             'bark',[0,0,sign*18])
        cube(f'{name}_shell_{sign}',name,[x+sign*(w/2-4.5),y+5.8,z],[9,5,length+1.5],
             'bark',[0,0,sign*39])
        cube(f'{name}_skirt_{sign}',name,[x+sign*(w/2-1),y+2.8,z+.6],[4,5,length-1],
             'dark_bark',[0,0,sign*8])
        cube(f'{name}_front_lip_{sign}',name,[x+sign*w*.12,y+12.7,z-length/2+.2],[w*.23,1.3,2.2],'bark')
    # Irregular bark chips and shallow moss pads; detail is geometry, not repainted pixels.
    for j in range(3 if i<7 else 2):
        xx=x+(-1 if j%2 else 1)*(w*.12+j*.7)
        cube(f'{name}_chip_{j}',name,[xx*.65+x*.35,y+14.2,z-3+j*2.7],[3.5+j*.5,.9,3.6],'dark_bark',[0,(-1 if j%2 else 1)*8,0])
    if i%2==0:
        for j in range(3):
            cube(f'{name}_moss_{j}',name,[x-w*.14+j*2.4,y+14.1,z+1+j*.9],[3.5,.5,2.8],'moss')
    # Walking legs, one articulated pair per segment except the smallest tail plate.
    if i<10:
        for side,sign in [('left',-1),('right',1)]:
            hip=[x+sign*w*.35,y-1,z+.5];knee=[x+sign*(w*.5+5.5),8.5,z+2.5]
            ankle=[x+sign*(w*.5+8),2.0,z+.2]
            leg=bone(f'{name}_{side}_hip',hip,name);lower=bone(f'{name}_{side}_knee',knee,leg)
            foot=bone(f'{name}_{side}_foot',ankle,lower)
            beam(f'{name}_{side}_upper',leg,hip,knee,4.4,5.1,'dark_bark')
            cube(f'{name}_{side}_hip_joint',leg,hip,[4.7,4.2,4.9],'joints')
            beam(f'{name}_{side}_shin',lower,knee,ankle,3.5,4.2,'dark_bark')
            cube(f'{name}_{side}_ankle_collar',lower,knee,[4.5,3.2,4.9],'bark')
            cube(f'{name}_{side}_toe',foot,[ankle[0]+sign*.9,1.1,ankle[2]-1.5],[4.8,2.2,5.7],'horn',[0,sign*11,0])
            cube(f'{name}_{side}_toe_chip',foot,[ankle[0]+sign*1.4,1.6,ankle[2]-3.1],[1.5,1,2.6],'cut_wood')

# Small withdrawn head underneath the forward shell collar.
x,y,z=centers[0]
head=bone('head',[x,y+1,z-7],'segment_00')
cube('head_core',head,[0,18,-49],[18,11,13],'joints')
cube('head_brow',head,[0,24,-49.5],[21,4,13],'bark')
cube('head_muzzle',head,[0,16,-56],[12,7,5],'dark_bark')
for side,sign in [('left',-1),('right',1)]:
    cube(f'{side}_eye_socket',head,[sign*7.4,20.6,-54.4],[4.4,3.8,3.8],'horn')
    cube(f'{side}_main_eye',head,[sign*8.1,21,-56.35],[1.15,1.15,.65],'eyes',uv=[7.15,7.15,7.85,7.85])
    cube(f'{side}_small_eye',head,[sign*9.3,21.7,-54.8],[.7,.7,.7],'eyes',uv=[7.15,7.15,7.85,7.85])
    jaw=bone(f'{side}_jaw',[sign*4.6,15,-55],head)
    beam(f'{side}_jaw_base',jaw,[sign*4.6,15,-55],[sign*6.5,10.5,-60],4.4,3.7,'horn')
    beam(f'{side}_jaw_hook',jaw,[sign*6.5,10.5,-60],[sign*3.7,9,-61.5],2.6,2.4,'horn')

# Exactly two powerful grasping forelimbs, each with rigid articulated links.
for side,sign in [('left',-1),('right',1)]:
    shoulder=[sign*12,19,-42];elbow=[sign*25,17,-52];wrist=[sign*27,11,-66]
    grasp=bone(f'{side}_grasp_shoulder',shoulder,'segment_00')
    elbow_b=bone(f'{side}_grasp_elbow',elbow,grasp);wrist_b=bone(f'{side}_grasp_wrist',wrist,elbow_b)
    beam(f'{side}_grasp_upper',grasp,shoulder,elbow,10,10,'joints')
    beam(f'{side}_grasp_upper_shell',grasp,[sign*14,22,-43],[sign*24,21,-51],8.8,9.5,'bark')
    cube(f'{side}_elbow_knuckle',elbow_b,elbow,[11,10,10],'joints')
    beam(f'{side}_grasp_forearm',elbow_b,elbow,wrist,9,10.5,'bark')
    beam(f'{side}_forearm_moss',elbow_b,[sign*25.5,20,-54],[sign*27,14.5,-64],3.3,7.5,'moss')
    cube(f'{side}_wrist_joint',wrist_b,wrist,[8.5,7.5,8.5],'joints')
    # Outer hooked finger and inner opposing thumb form an open grasping pincer.
    outer=bone(f'{side}_outer_finger',[sign*28,11,-68],wrist_b)
    beam(f'{side}_finger_1',outer,[sign*28,11,-68],[sign*30,6,-75],6.7,6.4,'bark')
    beam(f'{side}_finger_2',outer,[sign*30,6,-75],[sign*27.5,3.8,-80],4.8,4.6,'cut_wood')
    beam(f'{side}_finger_tip',outer,[sign*27.5,3.8,-80],[sign*23.5,5.2,-82],2.6,2.7,'horn')
    thumb=bone(f'{side}_inner_finger',[sign*23.5,10,-67],wrist_b)
    beam(f'{side}_thumb_1',thumb,[sign*23.5,10,-67],[sign*19,7,-72],4.9,4.8,'dark_bark')
    beam(f'{side}_thumb_tip',thumb,[sign*19,7,-72],[sign*20,5.2,-76],3.1,2.9,'cut_wood')

# Sparse forked root stubs give the camouflage outline seen in the concept.
for i in [0,2,5,8]:
    x,y,z=centers[i];w=widths[i]
    for side,sign in [('left',-1),('right',1)]:
        if i==5 and sign>0:continue
        start=[x+sign*w*.31,y+9,z+1]
        height={(0,-1):24,(0,1):17,(2,-1):16,(2,1):23,(5,-1):13,(8,-1):7,(8,1):10}[(i,sign)]
        lean=(5 if sign<0 else -3)+(i%3)
        middle=[start[0]+sign*(3+i%3),start[1]+height*.65,z+lean*.5]
        end=[start[0]+sign*(6+i%3),start[1]+height,z+lean]
        beam(f'root_{i}_{side}_base',f'segment_{i:02d}',start,middle,5.8,6,'dark_bark')
        beam(f'root_{i}_{side}_upper',f'segment_{i:02d}',middle,end,4.2,4.2,'bark','cut_wood')
        fork=[middle[0]-sign*3,middle[1]+6,middle[2]-4]
        beam(f'root_{i}_{side}_fork',f'segment_{i:02d}',middle,fork,2.7,2.8,'bark','cut_wood')
        cube(f'root_{i}_{side}_moss',f'segment_{i:02d}',[start[0],start[1]+2,z-.2],[4,1.0,3.8],'moss')
        if i in [0,4]:
            beam(f'root_{i}_{side}_moss_tendril',f'segment_{i:02d}',[start[0],start[1]+1,z-1],[start[0]+sign,start[1]-4,z-1.5],1.2,1.1,'moss')

# Root-like tapering tail: three articulated links and a fork, with no stinger.
x,y,z=centers[-1]
tail=bone('tail_base',[x,y,z+4],'segment_10')
tail_mid=bone('tail_mid',[x+1,y+2,z+13],tail)
tail_tip=bone('tail_tip',[x+4,y+6,z+21],tail_mid)
beam('tail_wood_1',tail,[x,y,z+4],[x+1,y+2,z+13],9,10,'bark')
beam('tail_wood_2',tail_mid,[x+1,y+2,z+13],[x+4,y+6,z+21],6.5,7,'dark_bark')
beam('tail_root_tip',tail_tip,[x+4,y+6,z+21],[x+5,y+11,z+29],3.6,3.6,'bark','cut_wood')
beam('tail_root_fork',tail_tip,[x+4,y+6,z+21],[x-1,y+9,z+25],3.0,3.1,'cut_wood')

textures=[]
for i,t in enumerate(manifest):
    path=ROOT/t['file']
    textures.append({'name':path.name,'relative_path':'textures/'+path.name,'id':str(i),'uuid':uid('texture/'+t['name']),
                     'width':16,'height':16,'uv_width':16,'uv_height':16,'folder':'entity/lozhnokoren','namespace':'interstice',
                     'render_mode':'default','render_sides':'auto','wrap_mode':'repeat','visible':True,'internal':True,'saved':True,
                     'source':'data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode()})

# In-place clips for an eventual entity; no AI or world movement is implemented here.
def clip(name,length,loop,values):
    anim={'name':name,'uuid':uid('anim/'+name),'length':length,'loop':'loop' if loop else 'once','snapping':24,'animators':{}}
    for part,channels in values.items():
        keys=[]
        for channel,samples in channels.items():
            for time,vector in samples:
                keys.append({'channel':channel,'time':time,'interpolation':'linear',
                             'data_points':[dict(zip('xyz',[str(round(v,5)) for v in vector]))]})
        anim['animators'][G[part]['uuid']]={'name':part,'type':'bone','keyframes':keys}
    return anim
idle={};crawl={};threat={};times=[i*.25 for i in range(9)]
for i,name in enumerate(series):
    idle[name]={'rotation':[(t,[0,.28*math.sin(math.tau*t/2-i*.38),0]) for t in times]}
    crawl[name]={'rotation':[(t,[.35*math.sin(math.tau*t/2-i*.45),1.4*math.sin(math.tau*t/2-i*.55),0]) for t in times]}
    if i<10:
        for side,sign in [('left',-1),('right',1)]:
            phase=i*.82+(0 if sign<0 else math.pi)
            crawl[f'{name}_{side}_hip']={'rotation':[(t,[0,sign*12*math.sin(math.tau*t/1.0+phase),sign*5*math.sin(math.tau*t/1.0+phase)]) for t in times]}
            crawl[f'{name}_{side}_knee']={'rotation':[(t,[0,0,sign*15*max(0,math.sin(math.tau*t/1.0+phase))]) for t in times]}
idle['head']={'rotation':[(t,[.45*math.sin(math.tau*t/2),0,0]) for t in times]}
threat_times=[0,.35,.7,1.05,1.4]
amounts=[0,.45,1,1,.75]
for side,sign in [('left',-1),('right',1)]:
    idle[f'{side}_grasp_shoulder']={'rotation':[(t,[.4*math.sin(math.tau*t/2),sign*.3*math.sin(math.tau*t/2),0]) for t in times]}
    crawl[f'{side}_grasp_shoulder']={'rotation':[(t,[1.6*math.sin(math.tau*t+sign),sign*1.0,0]) for t in times]}
    for part,vector in [(f'{side}_grasp_shoulder',[45,sign*12,sign*5]),(f'{side}_grasp_elbow',[-10,sign*8,0]),
                        (f'{side}_grasp_wrist',[-8,0,sign*8]),(f'{side}_outer_finger',[0,sign*13,0]),
                        (f'{side}_inner_finger',[0,-sign*17,0]),(f'{side}_jaw',[0,sign*18,0])]:
        threat[part]={'rotation':[(t,[v*a for v in vector]) for t,a in zip(threat_times,amounts)]}
threat['head']={'position':[(t,[0,2.1*a,-.5*a]) for t,a in zip(threat_times,amounts)],
                'rotation':[(t,[9*a,0,0]) for t,a in zip(threat_times,amounts)]}
for name in ['tail_base','tail_mid','tail_tip']:
    idle[name]={'rotation':[(t,[0,.3*math.sin(math.tau*t/2),0]) for t in times]}
    crawl[name]={'rotation':[(t,[0,2*math.sin(math.tau*t/2-.6),0]) for t in times]}
animations=[clip('Idle',2,True,idle),clip('Crawl',2,True,crawl),clip('Threat',1.4,False,threat)]
model={'meta':{'format_version':'5.0','model_format':'modded_entity','box_uv':False},'name':'EREZCRAFT Lozhnokoren LK01',
       'model_identifier':'lozhnokoren','modded_entity_version':'1.21','resolution':{'width':16,'height':16},
       'visible_box':[16,8,0],'elements':elements,'groups':groups,'outliner':[nodes['root']],'textures':textures,'animations':animations,
       'erezcraft_materials':manifest,'erezcraft_design':{'units_per_block':16,'variant':'LK01','source_concept':'references/lozhnokoren_concept_LK01.png',
       'body_segments':11,'walking_leg_pairs':10,'grasping_forelimbs':2,'purpose':'Editable creature design asset; not a registered Minecraft entity'}}
assert len({e['uuid'] for e in elements})==len(elements)
assert len({g['uuid'] for g in groups})==len(groups)
path=ROOT/'source/lozhnokoren_LK01.bbmodel';path.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
report={'revision':'LK01','authoritative_source':str(path),'model_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'cuboids':len(elements),'articulated_groups':len(groups),'body_segments':11,'walking_leg_pairs':10,
        'grasping_forelimbs':2,'animation_clips':[{'name':a['name'],'seconds':a['length']} for a in animations],
        'all_game_textures_16x16':True,'registered_in_game':False,'native_game_verified':False}
(ROOT/'reports/design_LK01.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
