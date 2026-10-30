from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/ne01.json').read_text())
sources=[
 {'id':'N96-SPECS','url':'https://nordhavn.com/nordhavn-yacht-models/n96/','scope':'Published LOA 29.44m / beam 7.32m; real reference, not game tuning.'},
 {'id':'N96-GA','url':'https://nordhavn.com/wp-content/uploads/2019/08/N96-GA-drawing.pdf','scope':'Reviewed published general arrangement; interior adapted for Minecraft player.'},
 {'id':'N96-PROFILE','url':'https://nordhavn.com/wp-content/uploads/2019/08/N96-profile-drawing.pdf','scope':'External silhouette and stepped decks reference; no manufacturer 3D asset copied.'},
 {'id':'OWNER','path':'source/OWNER_A06.md','scope':'Implementation, custom textures, seam priority, profile correction, >=50 km/h, live navigation panels; owner closed Minecraft for isolated testing.'},
 {'id':'RUNTIME-AUDIT','path':'D:/repos/Minecraft/EREZCRAFT/docs/MARINE_EXPEDITION_VESSEL.md','scope':'Actual Skat lessons, installed AE2/Sable versions and unverified runtime gates.'}]
(ROOT/'source/sources.json').write_text(json.dumps(sources,indent=2)+'\n')
m=json.loads((ROOT/'project.json').read_text());m['revision']=data['revision'];m['purpose']='Dimensionally referenced virtual vessel geometry and material review. Minecraft implementation in progress; not manufacturing certification.'
m['units']={'length':'mm','force':'N','stress':'MPa','torque':'N*m','angle':'deg','time':'s'}
m['manufacturing'].update(applicable=False,rationale='Virtual game asset only. No physical manufacturing process is asserted.')
req=[]
def requirement(id,text,metric,unit,value,level='concept'):
    req.append(dict(id=id,text=text,method='inspection',required_for=[level],acceptance=dict(metric=metric,unit=unit,operator='equals',value=value)))
requirement('TOPOLOGY','Evaluated source solids are manifold','nonmanifold_meshes','count',0)
requirement('FACE-AREA','No zero-area mesh faces','zero_area_meshes','count',0)
requirement('WINDOWS','Sampled window edges and glass panes have a near shell hit','window_ray_failures','count',0)
requirement('ROUTES','Static 0.60m body / 2.20m headroom samples pass in the model, with explicit stepping allowance','clearance_failures','count',0)
requirement('BERTHS','Ten authored berth positions; not Minecraft sleeping acceptance','berths','count',10)
requirement('TERMINALS','Six reserved terminal positions; no working network claimed','terminal_positions','count',6)
requirement('MATERIALS','Nineteen original custom material textures, no third-party pixels','original_textures','count',19)
requirement('LAYER-SPLIT','Render cells keep opaque paint and transparent glazing separate','separate_render_layers','bool',True)
requirement('EXPORT-SEAMS','Cell boundary positions snap to exact 0/1 after serialization','boundary_snap_violations','count',0)
requirement('GAME-WALK','Four full-client stair routes without flight, grounded at each upper landing','native_walk_passed','bool',True,'engineering')
requirement('ME-COLD','Real powered AE2 network survives controlled body relocation and clean cold reload','native_me_cold_passed','bool',True,'engineering')
requirement('GAME-SEAMS','No exterior sky cracks after actual Minecraft export and rendering','native_seams_passed','bool',True,'engineering')
req.append(dict(id='SEA-SPEED',text='Measured actual sea propulsion reaches at least 50 km/h',method='demonstration',required_for=['engineering'],acceptance=dict(metric='actual_speed_kmh',unit='km/h',operator='min',value=50)))
requirement('LIVE-NAV','Bridge navigation displays show real server data in EREZ UI','live_navigation_verified','bool',True,'engineering')
m['requirements']=req
m['assumptions']=[dict(id='GAME-ADAPTATION',text='Layout, details, mechanisms and material appearance are adapted, not manufacturer production data.',state='accepted',rationale='Owner requested a real vessel basis and a playable adaptation.',required_for=['concept'])]
m['risks']=[dict(id='RUNTIME-OPEN',description='Propulsion, boarding, sleeping, all-route collision and multiplayer remain unaccepted.',state='open',rationale='Four native stair routes and six real ME screens do not prove sea stability, speed or complete gameplay.',required_for=['engineering'])]
specs=[('DESIGN','tools/build_design.py','source'),('SCENE-BUILDER','tools/build_scene.py','source'),('INSPECTION-SCRIPT','tools/verify_model.py','source'),('RENDER-EXPORTER','tools/export_render_cells.py','source'),('GEOMETRY','source/ne01.json','source'),('PARAMETERS','source/parameters.json','source'),('TEXTURES','source/texture_manifest.json','source'),('SOURCES','source/sources.json','source'),('DECISIONS','source/OWNER_DECISIONS.md','source'),('ASSEMBLY','assembly/ne01.blend','assembly'),('BOM','docs/bom.csv','bom'),('MANUAL','docs/assembly.md','instructions'),('GLB','exports/ne01.glb','export'),('RENDER-CELLS','exports/minecraft/ne01_render_cells.json','export'),('MODEL-CHECK','reports/MODEL_INSPECTION.json','report'),('RENDER-CHECK','reports/RENDER_EXPORT.json','report')]
for mat in data['materials']:specs.append(('TEX-'+mat['name'].upper(),mat['file'],'source'))
specs.extend([('DECISIONS-A06','source/OWNER_A06.md','source'),('COLLISION-EXPORTER','tools/export_collision.py','source'),('RUNTIME-EXPORTER','tools/export_runtime.py','source'),('COLLISION-CELLS','exports/minecraft/ne01_collision_cells.json','export'),('RUNTIME-CELLS','exports/minecraft/ne01_runtime.json','export'),('NATIVE-A06','reports/NATIVE_A06.json','report')])
m['artifacts']=[dict(id=id,path=path,role=role,required_for=['concept']) for id,path,role in specs];m['calculations']=[];m['evidence']=[]
(ROOT/'project.json').write_text(json.dumps(m,indent=2)+'\n')
manual="""# NE01 - reproduction and inspection

Geometry authority: source/ne01.json, generated by tools/build_design.py.
Blender coordinates are metres; one unit equals one Minecraft block. Project
manifest requirements use explicit mm conversion. Hull: 29.44 x 7.32 metres.
Interior arrangement and exterior details are a playable adaptation of N96.

assembly/ne01.blend contains packed original textures. Frame 1/81 is closed;
frame 41 demonstrates open doors and gangways. Animation is prescribed geometry,
not a contact solver or a game mechanism. exports/ne01.glb preserves the meshes,
materials and articulated demonstration.

Reproduce: run tools/build_design.py with Python, then tools/build_scene.py in
background Blender. Inspect using tools/verify_model.py; export render cells
using tools/export_render_cells.py. Keep the owner's foreground Blender untouched.

The hull is continuous. Window panes extend underneath closed ring frames.
Float32 cell boundary vertices are snapped to exact 0/1 before serialization.
See reports/MODEL_INSPECTION.json and RENDER_EXPORT.json for measured checks.
Static sampled clearance is not a continuous swept path or native collision proof.

Ten berths are modeled; sleeping remains unimplemented. Six ME screens use real
AE2 managed nodes and native menus with finite power and shared cell storage.
See reports/NATIVE_A06.json for the exact tested native routes and cold reload.
Native tests pause physics and deliberately relocate the body; these operations
do not demonstrate sea propulsion or stability. Minimum 50 km/h and live bridge
navigation displays remain required. No production world is modified here.
"""
(ROOT/'docs/assembly.md').write_text(manual,encoding='utf8')
print(json.dumps(dict(revision=m['revision'],requirements=len(req),artifacts=len(specs))))
