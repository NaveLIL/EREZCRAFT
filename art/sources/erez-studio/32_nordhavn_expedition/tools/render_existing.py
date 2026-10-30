"""Render the saved, inspected assembly without rebuilding or changing its bytes."""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--views',default='afloat,hero,rear,main_interior,bridge,lower_interior,cutaway')
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
assembly=ROOT/'assembly/ne01.blend';before=hashlib.sha256(assembly.read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(assembly));scene=bpy.context.scene
objects=[o for o in bpy.data.objects if o.type=='MESH' and o.get('source_part_id')]
camera=scene.camera;cam=camera.data;water=bpy.data.objects['STUDIO illustrative waterline / not simulation']
def aim(loc,target,scale,perspective=False):
    camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cam.type='PERSP' if perspective else 'ORTHO';cam.ortho_scale=scale;cam.lens=22
for view in args.views.split(','):
    for ob in objects:ob.hide_render=False
    scene.frame_set(1);water.hide_render=True
    if view=='hero':aim((27,42,23),(0,0,5.3),40)
    elif view=='afloat':water.hide_render=False;aim((24,36,18),(0,0,6),39)
    elif view=='rear':aim((-25,-42,20),(0,-.8,5),40)
    elif view=='side':aim((55,0,6.5),(0,0,6.5),35)
    elif view=='main_interior':aim((.4,-8.8,5.85),(-.5,-2.5,5.4),10,True)
    elif view=='bridge':aim((0,.3,8.7),(0,4.6,8.35),6,True)
    elif view=='lower_interior':aim((0,2.9,3.08),(0,7.4,2.5),7,True)
    elif view=='cutaway':
        for ob in objects:
            if ob['component'] in ['FLY','UPPER','GLASS','FITTINGS'] or ob.name.startswith(('Main side 1','Main window 1','Main roof','Main forward','Continuous watertight')):ob.hide_render=True
        aim((27,-28,31),(0,0,4.3),38)
    else:raise ValueError(view)
    scene.render.filepath=str(ROOT/f'preview/ne01_{view}.png');bpy.ops.render.render(write_still=True)
assert hashlib.sha256(assembly.read_bytes()).hexdigest()==before
report=dict(revision=scene['revision'],assembly_sha256=before,source_sha256=hashlib.sha256((ROOT/'source/ne01.json').read_bytes()).hexdigest(),
            views={view:hashlib.sha256((ROOT/f'preview/ne01_{view}.png').read_bytes()).hexdigest() for view in args.views.split(',')},assembly_modified=False)
(ROOT/'reports/PREVIEW_RENDER.json').write_text(json.dumps(report,indent=2)+'\n')
print('PREVIEW_RENDER',json.dumps(report))
