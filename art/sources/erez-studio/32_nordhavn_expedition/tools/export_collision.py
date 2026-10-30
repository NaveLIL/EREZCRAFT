"""Conservative 1/16-block collision from evaluated closed manifold solids.

Keeps each articulated group separate in closed/open poses. No runtime acceptance
is implied: voxel inflation and native player routes must be inspected separately.
"""
from pathlib import Path
from collections import defaultdict
import bpy
import hashlib
import json
import math
from mathutils import Vector
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[1]
data = json.loads((ROOT / 'source/ne01.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT / 'assembly/ne01.blend'))
R = 16
HALF_DIAGONAL = math.sqrt(3) / (2 * R)
direction = Vector((1, .0271, .0137)).normalized()
moving = {g['id'] for g in data['groups'] if 'axis' in g}
voxels = defaultdict(set)
counts = {}

def map_mc(v):
    return (v.x, v.z, v.y)

def inside(bvh, point):
    # Every solid is manifold with outward normals recalculated at build time.
    # The FIRST crossing exits iff the point is inside, including concave solids
    # and cavities. Counting crossings after an epsilon advance misses very thin
    # grazing entry/exit pairs and incorrectly fills genuine door openings.
    hit, normal, face, distance = bvh.ray_cast(point, direction, 100)
    return hit is not None and normal.dot(direction)>0

def inspect_pose(frame, dynamic_only):
    bpy.context.scene.frame_set(frame)
    bpy.context.view_layer.update()
    graph = bpy.context.evaluated_depsgraph_get()
    for index, ob in enumerate(bpy.data.objects):
        if ob.type != 'MESH' or not ob.get('source_part_id'):
            continue
        dynamic = ob['component'] in moving
        if dynamic_only and not dynamic:
            continue
        key = ob['component'] + ('/open' if frame == 41 else '/closed') if dynamic else 'static'
        ev = ob.evaluated_get(graph)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        verts = [ev.matrix_world @ v.co for v in me.vertices]
        bvh = BVHTree.FromPolygons(verts, [list(t.vertices) for t in me.loop_triangles], all_triangles=True)
        lo = [math.floor(min(v[i] for v in verts) * R + 1e-7) for i in range(3)]
        hi = [math.ceil(max(v[i] for v in verts) * R - 1e-7) for i in range(3)]
        target = voxels[key]
        for x in range(lo[0], hi[0]):
            for y in range(lo[1], hi[1]):
                for z in range(lo[2], hi[2]):
                    mc = (x, z, y)
                    if mc in target:
                        continue
                    center = Vector(((x + .5) / R, (y + .5) / R, (z + .5) / R))
                    nearest, normal, face, distance = bvh.find_nearest(center)
                    if distance <= HALF_DIAGONAL + 1e-7 or inside(bvh, center):
                        target.add(mc)
        counts[f'{frame}/{ob["source_part_id"]}'] = len(target)
        ev.to_mesh_clear()
        if index % 100 == 0:
            print('COLLISION_PROGRESS', frame, index, key, len(target), flush=True)

inspect_pose(1, False)
inspect_pose(41, True)

def merge(cells):
    out = []
    remaining = set(cells)
    while remaining:
        x, y, z = min(remaining)
        dx = 1
        while (x + dx, y, z) in remaining:
            dx += 1
        dz = 1
        while all((i, y, z + dz) in remaining for i in range(x, x + dx)):
            dz += 1
        dy = 1
        while all((i, y + dy, k) in remaining for i in range(x, x + dx) for k in range(z, z + dz)):
            dy += 1
        for i in range(x, x + dx):
            for j in range(y, y + dy):
                for k in range(z, z + dz):
                    remaining.remove((i, j, k))
        out.append([v / R for v in (x, y, z, x + dx, y + dy, z + dz)])
    return out

def encode(values):
    cells = defaultdict(set)
    for v in values:
        p = tuple(n // R for n in v)
        cells[p].add(tuple(n % R for n in v))
    return [dict(pos=p, boxes=merge(v)) for p, v in sorted(cells.items())]

encoded = {key: encode(values) for key, values in voxels.items()}
static_open = set(voxels['static'])
for key, values in voxels.items():
    if key.endswith('/open'):
        static_open.update(values)
inspection = json.loads((ROOT / 'reports/MODEL_INSPECTION.json').read_text())
failures = []
support_samples = []
for sample in inspection['clearance_samples']:
    x, z, y = sample['position_m']
    support = [(j + 1) / R for j in range(math.floor((y - .25) * R), math.ceil((y + .22) * R))
               if (math.floor(x * R), j, math.floor(z * R)) in static_open]
    feet = max([y] + support)
    support_samples.append(dict(name=sample['name'], source_floor_m=y,
                                collision_floor_m=feet, raised_m=feet-y))
    # Same explicit normal stepping allowance as the geometric inspection.
    half = sample.get('width_m', .60) / 2
    lo = (x - half, feet + sample.get('step_allowance_m', 0) + .001, z - half)
    hi = (x + half, feet + sample['height_m'] - .001, z + half)
    hits = []
    for i in range(math.floor(lo[0] * R), math.ceil(hi[0] * R)):
        for j in range(math.floor(lo[1] * R), math.ceil(hi[1] * R)):
            for k in range(math.floor(lo[2] * R), math.ceil(hi[2] * R)):
                if (i, j, k) in static_open:
                    hits.append([i / R, j / R, k / R])
    if hits:
        failures.append(dict(name=sample['name'], hits=hits[:8]))

result = dict(revision=data['revision'], resolution=R,
              coordinate_map='Minecraft X,Y,Z = Blender X,Z,Y',
              geometry_sha256=hashlib.sha256((ROOT / 'source/ne01.json').read_bytes()).hexdigest(),
              assembly_sha256=hashlib.sha256((ROOT / 'assembly/ne01.blend').read_bytes()).hexdigest(),
              groups=encoded, native_accepted=False)
out = ROOT / 'exports/minecraft/ne01_collision_cells.json'
out.write_text(json.dumps(result, separators=(',', ':')) + '\n')
report = dict(revision=data['revision'], geometry_sha256=result['geometry_sha256'],
              assembly_sha256=result['assembly_sha256'], resolution=R,
              maximum_surface_inflation_m=2 * HALF_DIAGONAL,
              voxels={k: len(v) for k, v in voxels.items()},
              boxes={k: sum(len(c['boxes']) for c in v) for k, v in encoded.items()},
              sampled_routes=len(inspection['clearance_samples']), clearance_failures=failures,
              floor_support_samples=support_samples,
              native_accepted=False,
              limitations=['Conservative voxel inflation up to sqrt(3)/16 m.',
                           'Endpoint poses only; no swept mechanism clearance proof.',
                           'Native player, buoyancy and collision performance untested.'])
(ROOT / 'reports/COLLISION_EXPORT.json').write_text(json.dumps(report, indent=2) + '\n')
print('COLLISION_COMPLETE', json.dumps({k: v for k, v in report.items() if k not in ('voxels', 'boxes')}))
