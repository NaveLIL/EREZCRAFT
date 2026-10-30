"""R4 regression checks for the specific roof and bow defects reported by owner."""
from pathlib import Path
import bpy, json, math, hashlib
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/eitan_E01.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'assembly/eitan_E01.blend'))
scene=bpy.context.scene;scene.frame_set(1);bpy.context.view_layer.update()
parts={p['name']:p for p in data['parts']}
def bounds(name):
    ev=bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh()
    v=[ev.matrix_world@p.co for p in me.vertices];ev.to_mesh_clear()
    return [[min(p[i] for p in v) for i in range(3)],[max(p[i] for p in v) for i in range(3)]]
def ray(name,p,d):
    ev=bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get());inv=ev.matrix_world.inverted()
    ok,v,_,_=ev.ray_cast(inv@Vector(p),inv.to_3x3()@Vector(d));return list(ev.matrix_world@v) if ok else None
checks={};fail=[]
for name,panel in [('driver','roof centre fore'),('commander','crew roof')]:
    lo,hi=bounds(panel);lidlo,lidhi=bounds(name+' hatch lid');glo,ghi=bounds(name+' hatch gasket')
    margins=[glo[0]-lo[0],hi[0]-ghi[0],glo[1]-lo[1],hi[1]-ghi[1]]
    cut=parts[panel]['cutouts'][0]
    cm=[cut['center'][i]-cut['size'][i]/2-lo[i] for i in [0,1]]+[hi[i]-cut['center'][i]-cut['size'][i]/2 for i in [0,1]]
    checks[name]={'gasket_support_margins_mm':[v*1000 for v in margins],'cutout_panel_margins_mm':[v*1000 for v in cm],'lid_bottom_minus_roof_top_mm':(lidlo[2]-hi[2])*1000}
    if min(margins)<.025 or min(cm)<.04:fail.append(name+' unsupported rim')
    if not .01<lidlo[2]-hi[2]<.025:fail.append(name+' lid seating height')
    # Full rectangular opening, not merely a single centre ray.
    for dx in [-.29,0,.29]:
        for dy in [-.25,0,.25]:
            x,y=cut['center'][0]+dx,cut['center'][1]+dy
            for roof in ['roof centre fore','crew roof','stepped roof transition']:
                if ray(roof,(x,y,3.4),(0,0,-1)) is not None:fail.append(name+' obstructed opening '+roof)
seams=[]
for side in [-1,1]:
    r=ray('roof centre fore',(0,-1.6,2.43),(side,0,0));s=ray(f'sloped modular side {side} 0',(0,-1.6,2.43),(side,0,0))
    overlap=(abs(r[0])-abs(s[0]))*1000;seams.append(overlap)
    if overlap<10:fail.append('fore roof/side disconnected')
checks['fore_roof_side_overlap_mm']=seams
# Adjacent patches now meet instead of overlapping on the same outer plane.
# Compare each patch just inside its common joint, avoiding bevel chamfers.
a=ray('bow cheek 1',(2,-2.53,2.30),(-1,0,0));b=ray('sloped modular side 1 0',(2,-2.50,2.30),(-1,0,0))
offset=abs(a[0]-b[0])*1000;checks['bow_main_side_offset_mm']=offset
if offset>1:fail.append('bow side mismatch')
separation=bounds('sloped modular side 1 0')[0][1]-bounds('bow cheek 1')[1][1]
checks['bow_main_longitudinal_separation_mm']=separation*1000
if separation<-.0001:fail.append('coplanar bow/main surface overlap')
seam=ray('troop hatch centre underlap',(0,1.5,3.4),(0,0,-1));checks['troop_centre_sealed']=seam is not None
if seam is None:fail.append('troop centre hole')
scene.frame_set(41);bpy.context.view_layer.update()
open_hit=ray('troop hatch centre underlap',(0,1.5,3.4),(0,0,-1));checks['troop_centre_clear_when_open']=open_hit is None
if open_hit is not None:fail.append('underlap blocks open troop exit')
# Sample all four lid surfaces against the two actual support panels and the
# transition. Hinges/gaskets intentionally contact; they are not test obstacles.
def tree(name):
    ev=bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh()
    t=BVHTree.FromPolygons([ev.matrix_world@v.co for v in me.vertices],[list(p.vertices) for p in me.polygons]);ev.to_mesh_clear();return t
scene.frame_set(1);bpy.context.view_layer.update()
obstacles={n:tree(n) for n in ['roof centre fore','crew roof','troop roof','stepped roof transition']}
collisions=[]
for deg in range(0,106,5):
    for group,lid,axis,sign in [('DRIVER_HATCH','driver hatch lid',0,-1),('COMMANDER_HATCH','commander hatch lid',0,-1),('TROOP_HATCH_L','troop hatch half -1',1,-1),('TROOP_HATCH_R','troop hatch half 1',1,1)]:
        ob=bpy.data.objects[group];ob.rotation_euler[axis]=sign*math.radians(deg);bpy.context.view_layer.update();t=tree(lid)
        for n,fixed in obstacles.items():
            if t.overlap(fixed):collisions.append({'deg':deg,'lid':lid,'fixed':n})
checks['lid_surface_collision_samples_deg']=list(range(0,106,5));checks['lid_surface_intersections']=collisions
if collisions:fail.append('sampled roof/lid intersections')
report={'revision':data['revision'],'blender':bpy.app.version_string,'geometry_sha256':hashlib.sha256((ROOT/'source/eitan_E01.json').read_bytes()).hexdigest(),'assembly_sha256':hashlib.sha256((ROOT/'assembly/eitan_E01.blend').read_bytes()).hexdigest(),'script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'checks':checks,'failures':fail,'passed':not fail,'limitations':['Virtual game geometry. Named-panel sampling is not exhaustive continuous collision certification.']}
(ROOT/'reports/ROOF_FIT.json').write_text(json.dumps(report,indent=2)+'\n')
print('ROOF_FIT',json.dumps(report));assert not fail,fail
