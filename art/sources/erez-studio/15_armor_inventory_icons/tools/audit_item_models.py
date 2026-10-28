"""Read-only visual inventory of the accepted mod; no production asset edits."""
from pathlib import Path
import collections,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
jar=ROOT/'exports/interstice-0.7.0-preview.20-icons.jar'
with zipfile.ZipFile(jar) as z:
    files=set(z.namelist());prefix='assets/interstice/models/'
    models={n[len(prefix):-5]:json.loads(z.read(n)) for n in files if n.startswith(prefix) and n.endswith('.json')}
    lang=json.loads(z.read('assets/interstice/lang/ru_ru.json'))
    def resolve(name,seen=()):
        if name in seen:return {'category':'parent_cycle','chain':[*seen,name]}
        d=models.get(name)
        if d is None:return {'category':'missing_local_parent','parent':name}
        if d.get('loader'):return {'category':'custom_3d','loader':d['loader'],'quads':len(d.get('quads',[]))}
        if d.get('elements'):return {'category':'box_3d','elements':len(d['elements'])}
        parent=d.get('parent','')
        if parent.startswith('interstice:'):return {**resolve(parent.split(':',1)[1],(*seen,name)),'inherited_from':parent}
        if parent in ['minecraft:item/generated','minecraft:item/handheld']:return {'category':'flat_sprite','parent':parent}
        if parent=='minecraft:item/written_book':return {'category':'vanilla_book','parent':parent}
        if parent=='minecraft:item/template_spawn_egg':return {'category':'spawn_egg','parent':parent}
        return {'category':'other','parent':parent}
    rows=[]
    for name,model in sorted(models.items()):
        if not name.startswith('item/'):continue
        key=name[5:];rows.append({'id':'interstice:'+key,'name_ru':lang.get('item.interstice.'+key,lang.get('block.interstice.'+key)),
            **resolve(name),'textures':model.get('textures',{}),'overrides':len(model.get('overrides',[])),
            'auxiliary_model':key.startswith('tide_indicator_')})
    counts=dict(collections.Counter(r['category'] for r in rows))
priorities=[
 {'priority':0,'ids':['upper_sea_ghast_spawn_egg'],'finding':'Registered in StrongRealmFauna but item JSON absent; recent native log also reports missing model. Technical repair, not a Blender sculpt.'},
 {'priority':1,'ids':['tether_spool'],'finding':'Flat model references the plant_fiber block texture. Strong mismatch between spool purpose and silhouette.','proposal':'Distinct spool, axle, wound line and hook; restrained vanilla detail.'},
 {'priority':1,'ids':['lift_controller','structure_surveyor'],'finding':'Controller has only three boxes; surveyor reuses the rift_lens model. Candidates for recognizable handheld instruments.','proposal':'Grip, inset controls, visible field core and a distinct surveyor silhouette.'},
 {'priority':1,'ids':['echo_rift_anchor'],'finding':'A flat overlay of vanilla ender_eye and echo_shard.','proposal':'Original anomalous anchor with a restrained frame around its core.'},
 {'priority':1,'ids':['field_backpack','expedition_backpack','rift_backpack'],'finding':'Flat inventory sprites; a separate existing 3D BackpackLayer supplies the wearable shape. Not missing wearable models.','proposal':'Unify wearable geometry and item miniatures, then distinguish the three tiers by functional shapes.'},
 {'priority':2,'ids':[tier+'_'+tool for tier in ['riftsilver','riftsteel'] for tool in ['pickaxe','axe','shovel','hoe']],'finding':'Existing geometry is only two to four boxes per item; all are already 3D.','proposal':'Improve handles, sockets, head silhouette and differentiate exploration from combat/industrial alloy.'},
 {'priority':2,'ids':['tide_indicator'],'finding':'Flat sprite with sixteen state-dependent overrides. Already functional.','proposal':'Dimensional barometer housing while preserving all sixteen needle states.'},
 {'priority':2,'ids':['rift_lens'],'finding':'Flat lens sprite, also reused by the structure surveyor.','proposal':'Volumetric lens/rim with deliberate optical/anomalous appearance.'},
 {'priority':3,'ids':['ballast_belt','protective_coating'],'finding':'Existing five-box belt and four-box coating container.','proposal':'Better buckles, weights, applicator and fluid-level readability.'},
 {'priority':3,'ids':['blank_module','magnet_module_tier1','feeder_module_tier1','compression_module_tier1','aegis_optics','aegis_control_unit'],'finding':'Flat sprites; useful style refinement after the frequently held equipment.','proposal':'Shared connector and distinct magnetic, feeding, compression, optical and control silhouettes.'}
]
result={'audited_jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'item_model_json_count':len(rows),'resolved_categories':counts,
    'count_note':'Model files are not the item registry: the total includes seventeen helper models for barometer states. No new client run was performed for this audit.',
    'criteria':'Geometry and source inspection. Flatness or a low box count alone does not prove an art defect; redesign priorities are editorial recommendations.',
    'already_approved':['riftsilver/steel armor and Magen','Kotomyaf without muzzle or tail','Original riftsteel sword retained by user preference','New riftsteel longsword'],
    'priorities':priorities,'models':rows,'game_files_modified':False}
out=ROOT/'reports/item_visual_audit.json';out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps({'models':len(rows),'categories':counts,'report':str(out)},ensure_ascii=False))
