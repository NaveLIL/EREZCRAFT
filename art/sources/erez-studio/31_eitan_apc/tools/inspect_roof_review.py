"""Read-only E01-R3 geometry review; never saves the Blender assembly or exports."""
from pathlib import Path
import hashlib
import json
import math
from datetime import datetime, timezone

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'verification/review-20261029'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT / 'source/eitan_E01.json'
ASSEMBLY = ROOT / 'assembly/eitan_E01.blend'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

before = {'source': sha(SOURCE), 'assembly': sha(ASSEMBLY)}
data = json.loads(SOURCE.read_text(encoding='utf-8'))
parts = {p['name']: p for p in data['parts']}
bpy.ops.wm.open_mainfile(filepath=str(ASSEMBLY))
scene = bpy.context.scene
scene.frame_set(1)
bpy.context.view_layer.update()

def bounds(name):
    ev = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in mesh.vertices]
    ev.to_mesh_clear()
    return {'min': [min(v[i] for v in pts) for i in range(3)],
            'max': [max(v[i] for v in pts) for i in range(3)]}

def ray(name, origin, direction):
    ev = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    inv = ev.matrix_world.inverted()
    ok, location, normal, index = ev.ray_cast(inv @ Vector(origin), inv.to_3x3() @ Vector(direction))
    return {'hit': bool(ok), 'location_m': list(ev.matrix_world @ location) if ok else None}

def face_slope(name):
    ev = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = ev.to_mesh()
    faces = []
    for face in mesh.polygons:
        normal = (ev.matrix_world.to_3x3().inverted().transposed() @ face.normal).normalized()
        if normal.x > .8 and face.area > .1:
            faces.append({'area_m2': face.area, 'outward_normal': list(normal),
                          'inward_slope_from_vertical_deg': math.degrees(math.atan2(normal.z, normal.x))})
    ev.to_mesh_clear()
    return max(faces, key=lambda f: f['area_m2'])

roofs = {}
for label, lid, roof, gasket in [
    ('driver', 'driver hatch lid', 'roof centre fore', 'driver hatch gasket'),
    ('commander', 'commander hatch lid', 'crew roof', 'commander hatch gasket'),
]:
    lb, rb = bounds(lid), bounds(roof)
    cut = parts[roof]['cutouts'][0]
    cut_front = cut['center'][1] - cut['size'][1] / 2
    roofs[label] = {'lid_bounds_m': lb, 'support_panel_bounds_m': rb,
        'gasket_bounds_m': bounds(gasket),
        'lid_overhang_ahead_of_own_panel_mm': max(0, rb['min'][1] - lb['min'][1]) * 1000,
        'declared_cutout_beyond_panel_front_mm': max(0, rb['min'][1] - cut_front) * 1000,
        'cutout_is_closed_loop_inside_declared_support_panel': cut_front > rb['min'][1] + 1e-5,
        'lid_inside_overall_closed_vehicle_xy_envelope': lb['min'][0] >= -1.5 and lb['max'][0] <= 1.5 and lb['min'][1] >= -4 and lb['max'][1] <= 4.015}

# Sample the opening of the raised commander lid in front of its own roof.
# These are local vertical surface measurements, not a closed-solid certification.
sample = (-.64, -1.15, 3.2)
support_hit = ray('roof centre fore', sample, (0, 0, -1))
lid_hit = ray('commander hatch lid', (-.64, -1.15, 2.5), (0, 0, 1))
if support_hit['hit'] and lid_hit['hit']:
    roofs['commander']['underside_to_lower_fore_roof_gap_at_sample_mm'] = (lid_hit['location_m'][2] - support_hit['location_m'][2]) * 1000
roofs['commander']['sample_xy_m'] = list(sample[:2])

slopes = {'bow_cheek_right': face_slope('bow cheek 1'),
          'troop_side_right': face_slope('sloped modular side 1 1')}
slopes['difference_deg'] = abs(slopes['bow_cheek_right']['inward_slope_from_vertical_deg'] - slopes['troop_side_right']['inward_slope_from_vertical_deg'])

# Compare the front and rear side planes at the same height and overlap station.
z, y = 2.40, -2.40
hits = {n: ray(n, (2, y, z), (-1, 0, 0)) for n in ['bow cheek 1', 'sloped modular side 1 0']}
side_offset = None
if all(v['hit'] for v in hits.values()):
    side_offset = abs(hits['bow cheek 1']['location_m'][0] - hits['sloped modular side 1 0']['location_m'][0]) * 1000

# Explicitly measure the visible roof/side seam on both sides at the fore-roof
# mid-height; no assumption that individually manifold plates form one closed hull.
roof_seams = []
for side in [-1, 1]:
    a = ray('roof centre fore', (0, -1.8, 2.43), (side, 0, 0))
    b = ray(f'sloped modular side {side} 0', (0, -1.8, 2.43), (side, 0, 0))
    gap = abs(b['location_m'][0] - a['location_m'][0]) * 1000 if a['hit'] and b['hit'] else None
    roof_seams.append({'side': side, 'section_yz_m': [-1.8, 2.43], 'roof_edge': a, 'side_inner': b, 'separation_mm_at_section': gap})

left_lid = bounds('troop hatch half -1')
right_lid = bounds('troop hatch half 1')
troop_centre = {'lid_to_lid_gap_mm': (right_lid['min'][0] - left_lid['max'][0]) * 1000,
                'vertical_ray_xy_m': [0, 1.5],
                'roof_and_lids_rays': {name: ray(name, (0, 1.5, 3.2), (0, 0, -1))
                    for name in ['troop roof', 'troop hatch half -1', 'troop hatch half 1']}}

pose_envelopes = {}
for frame in [1, 41, 121, 161]:
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    pose_envelopes[str(frame)] = {name: bounds(name) for name in ['driver hatch lid', 'commander hatch lid', 'troop hatch half -1', 'troop hatch half 1']}

after = {'source': sha(SOURCE), 'assembly': sha(ASSEMBLY)}
assert before == after, 'Read-only inspection must not change authority or assembly'
report = {'reviewed_at_utc': datetime.now(timezone.utc).isoformat(),
          'revision': data['revision'], 'blender_version': bpy.app.version_string,
          'script_sha256': sha(Path(__file__)), 'inputs_sha256': before,
          'authority_and_assembly_unchanged': before == after,
          'unit': 'metres; angles in degrees; labelled separations in millimetres',
          'closed_pose_frame': 1, 'hatches': roofs, 'side_slopes': slopes,
          'bow_to_main_side_offset_at_y_minus_2_4_z_2_4_mm': side_offset,
          'side_offset_ray_hits': hits, 'fore_roof_seams': roof_seams,
          'troop_hatch_centre_seam': troop_centre,
          'hatch_pose_bounds_m': pose_envelopes,
          'scope': 'Saved-file cosmetic/fit inspection; no Minecraft launch, no manufacturing or dynamics validation.',
          'limitations': ['Named surface rays and pose bounds only, not an exhaustive collision or swept-volume proof.',
                          'No factory dimensions inferred from perspective photographs.',
                          'Existing MODEL_CHECK verifies rear mechanisms and individual meshes; it does not enforce these hatch/roof fit requirements.']}
(OUT / 'geometry-review.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('EITAN_REVIEW', json.dumps({'hatches': roofs, 'side_slopes': slopes, 'side_offset_mm': side_offset, 'roof_seams': roof_seams, 'inputs_unchanged': before == after}))
