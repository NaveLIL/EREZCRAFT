"""SK03: owner-requested closed forehold/transom and usable boarding/stair routes.
Game-art units are Minecraft blocks; this is not a manufacturing model.
"""
from pathlib import Path
import copy,json,re,hashlib
ROOT=Path(__file__).resolve().parents[1]
original=ROOT/'source/skat_SK02.json'
model=json.loads(original.read_text(encoding='utf-8'))
parts=model['parts'];old={p['name']:copy.deepcopy(p) for p in parts}
root='SKAT_SK03'
for g in model['groups']:
 if g['name']=='SKAT_SK02':g['name']=root
 if g['parent']=='SKAT_SK02':g['parent']=root
new_group='19_SK03_forehold_and_transom'
boarding='20_SK03_boarding'
model['groups'] += [dict(name=new_group,parent=root,pivot=[0,0,0],role='static'),dict(name=boarding,parent=root,pivot=[0,0,0],role='static')]
def cube(name,c,size,material='graphite',group=new_group,tag=None,rotation=(0,0,0)):
 parts.append(dict(name=name,shape='cube',center=list(c),size=list(size),material=material,group=group,tag=tag,rotation=list(rotation)))
def beam(name,a,b,width=.08,material='copper',group=new_group,tag=None):
 parts.append(dict(name=name,shape='beam',a=list(a),b=list(b),width=width,depth=width,material=material,group=group,tag=tag))
def panel(name,points,offset,material='graphite',group=new_group):
 vertices=[list(v) for v in points]+[[v[i]+offset[i] for i in range(3)] for v in points]
 parts.append(dict(name=name,shape='solid',vertices=vertices,faces=[[0,1,2,3],[7,6,5,4],[0,4,5,1],[1,5,6,2],[2,6,7,3],[3,7,4,0]],material=material,group=group))

# Correct both diagonal plinths: their long axes now join front and side walls.
for p in parts:
 if p['name'].startswith('Cabin_corner_plinth_'):p['rotation'][2]*=-1

# Separate the working stair hatch from the decorative cargo inspection panels.
parts[:]=[p for p in parts if not re.match(r'Hold_stair_[0-9]+$',p['name']) and not re.match(r'Hatch_[A-Za-z]+_2_',p['name']) and p['name'] not in {'Deck_plate_3_11','Deck_plate_4_11','Deck_plate_3_12','Deck_plate_4_12'} and not re.match(r'Deck_fastener_[34]_12_',p['name'])]
for p in parts:
 if p['name'].startswith('Stair_hatch_frame_side_'):
  p['center'][1]=6.875;p['size'][1]=5.05
 elif p['name']=='Stair_hatch_frame_end_6.17':
  p['name']='Stair_hatch_frame_end_4.45';p['center'][1]=4.45
 elif p['name']=='Stair_hatch_leaf':
  p['center'][1]=6.90;p['size'][1]=4.80
 elif p['name']=='Stair_hatch_handle':p['center'][1]=4.85
 if p['group']=='16_Cargo_crane':
  for key in ['center','a','b']:
   if key in p:p[key][1]+=.80;p[key][0]-=.80
for i in range(11):
 top=1.74+i*.23;previous=1.60 if i==0 else top-.23;y=5.425+i*.35
 cube(f'SK03_stair_tread_{i}',(0,y,top-.08),(1.60,.35,.16),'steel','06_Hold_interior')
 cube(f'SK03_stair_riser_{i}',(0,y-.175,(previous+top)/2),(1.60,.06,top-previous),'steel','06_Hold_interior')
for p in parts:
 if p['name'].startswith('Hold_stair_handhold_'):
  side=-1 if p['name'].endswith('-1') else 1;p['a']=[side*.90,5.25,2.55];p['b']=[side*.90,9.3,4.92]
for side in [-1,1]:beam(f'SK03_stair_stringer_{side}',(side*.76,5.25,1.53),(side*.76,9.3,3.90),.10,'graphite','06_Hold_interior')
for side in [-1,1]:
 cube(f'SK03_hatch_sill_{side}',(side*1.12,6.875,4.00),(.10,5.05,.10),'steel','08_Deck_hatches')
 cube(f'SK03_hatch_entry_marker_{side}',(side*.82,9.56,3.985),(.20,.42,.025),'ivory','08_Deck_hatches')

# A hollow, sloped forepeak closes the dark opening under the windscreen.
# It expands the hold through the existing centre opening in the forward bulkhead.
cube('SK03_forehold_floor',(0,-4.47,1.52),(4.10,4.74,.16))
cube('SK03_forepeak_bilge',(0,-5.15,.59),(4.10,6.10,.14))
panel('SK03_forepeak_glacis',[(-2.04,-8.18,.66),(2.04,-8.18,.66),(2.04,-6.67,3.79),(-2.04,-6.67,3.79)],(0,.14,0))
for side in [-1,1]:
 x=side*2.04
 panel(f'SK03_forepeak_cheek_{side}',[(x,-8.18,.66),(x,-2.13,.66),(x,-2.13,3.87),(x,-6.67,3.79)],(-side*.14,0,0))
 cube(f'SK03_forehold_inner_lamp_{side}',(side*1.89,-4.35,3.68),(.06,3.6,.07),'cyan')
 # Repeated armour strips follow the rake; the centre remains a clean chevron.
 for j in range(4):
  z=.98+j*.68;y=-8.18+(z-.66)*1.51/3.13-.035
  beam(f'SK03_glacis_seam_{side}_{j}',(side*.12,y,z),(side*1.94,y,z),.065,'steel')
  if j==2:beam(f'SK03_glacis_belt_{side}',(side*.10,y-.018,z+.13),(side*1.9,y-.018,z+.13),.10,'teal')
 for j in [0,2]:
  z=1.08+j*.68;y=-8.18+(z-.66)*1.51/3.13-.075
  cube(f'SK03_glacis_bolt_{side}_{j}',(side*1.82,y,z),(.10,.07,.10),'brass')
beam('SK03_bow_centre_spine',(0,-8.24,.72),(0,-6.73,3.78),.14,'copper')
cube('SK03_forepeak_upper_join',(0,-6.61,3.86),(4.12,.30,.18),'steel')
for y in [-5.8,-4.4,-3.0]:cube(f'SK03_forehold_aisle_marker_{y}',(0,y,1.615),(.38,.48,.018),'ivory')

# Close the rear gap with a matching reinforced transom and floor, not a floating fascia.
cube('SK03_aft_hold_floor',(0,10.49,1.52),(4.10,3.38,.16))
cube('SK03_transom_lower',(0,12.20,1.38),(4.18,.20,2.06))
cube('SK03_transom_upper',(0,12.20,3.15),(4.18,.20,1.48))
for side in [-1,1]:
 cube(f'SK03_aft_inner_wall_{side}',(side*2.04,10.50,2.23),(.14,3.36,3.16))
 cube(f'SK03_transom_rib_{side}',(side*1.76,12.33,2.14),(.12,.10,3.20),'copper')
 cube(f'SK03_transom_belt_{side}',(side*.96,12.335,2.48),(1.76,.06,.23),'teal')
 cube(f'SK03_transom_lamp_{side}',(side*1.48,12.37,3.48),(.34,.08,.14),'lamp')
cube('SK03_transom_service_recess',(0,12.34,2.36),(1.20,.05,1.15),'rubber')
cube('SK03_transom_service_cover',(0,12.39,2.36),(1.05,.06,1.00),'steel')
for side in [-1,1]:cube(f'SK03_service_latch_{side}',(side*.42,12.45,2.35),(.08,.08,.30),'brass')

# Board from either shore-facing side. Ladder planes lie inside their climbable
# Minecraft cells; the deck railing has an actual 1.3-block clear entry.
cutlo,cuthi=.10,1.40
revised=[]
for p in parts:
 if p['group']=='13_Deck_rails' and p['name'].startswith(('Rail_post_','Rail_foot_')) and cutlo<p['center'][1]<cuthi:continue
 if p['group']=='13_Deck_rails' and p['shape']=='beam' and abs(p['a'][0])>4.9 and abs(p['b'][0])>4.9 and p['a'][1]!=p['b'][1]:
  a,b=p['a'],p['b'];lo,hi=sorted([a[1],b[1]])
  if lo<cuthi and hi>cutlo:
   def at(y):
    t=(y-a[1])/(b[1]-a[1]);return [a[k]+(b[k]-a[k])*t for k in range(3)]
   for suffix,x,y in [('bow',lo,min(hi,cutlo)),('aft',max(lo,cuthi),hi)]:
    if y>x:
     q=copy.deepcopy(p);q['name']+='_'+suffix;q['a']=at(x);q['b']=at(y);revised.append(q)
   continue
 revised.append(p)
parts[:]=revised
for side in [-1,1]:
 x=side*5.60;tag='boarding_ladder'
 for y in [.29,1.21]:
  cube(f'SK03_boarding_stile_{side}_{y}',(x,y,2.58),(.12,.10,3.90),'steel',boarding,tag)
  beam(f'SK03_boarding_grip_{side}_{y}',(x,y,4.53),(side*4.97,y,4.74),.10,'copper',boarding,tag)
 for i in range(14):cube(f'SK03_boarding_rung_{side}_{i}',(x,.75,.68+i*.26),(.14,.86,.075),'steel',boarding,tag)
 cube(f'SK03_boarding_low_step_{side}',(side*5.50,.75,1.09),(.36,1.02,.12),'graphite',boarding,tag)
 cube(f'SK03_boarding_deck_marker_{side}',(side*4.74,.75,3.985),(.52,.72,.025),'ivory',boarding)
 cube(f'SK03_boarding_entry_lamp_{side}',(side*5.12,1.50,4.92),(.20,.18,.16),'cyan',boarding)

# Thin real ceiling fixtures illuminate the aisle instead of hiding behind cargo.
lighting='21_SK03_hold_ceiling_lights'
model['groups'].append(dict(name=lighting,parent=root,pivot=[0,0,0],role='static'))
for side in [-1,1]:
 for bay,y,length,z in [('main',.9,6.8,3.67),('fore',-4.1,3.6,3.70)]:
  cube(f'SK03_ceiling_light_case_{bay}_{side}',(side*.92,y,z),(.16,length,.08),'graphite',lighting)
  cube(f'SK03_ceiling_light_strip_{bay}_{side}',(side*.92,y,z-.06),(.08,length-.08,.04),'cyan',lighting)

model.update(name='Skat SK03',revision='SK03',authoritative_geometry='source/skat_SK03.json',parts=parts,
 design_change='Owner-requested closed forehold/transom, corrected corner plinths, distinct accessible stair hatch and two boarding ladders.',
 cargo_aisle={'x':[-1.25,1.25],'y':[-6.10,4.90],'z':[1.64,3.70]},
 stair_reserved_bay={'x':[-1.0,1.0],'y':[5.25,9.35],'z':[1.6,4.0]},
 boarding_routes=[{'side':s,'centre':[s*5.60,.75,2.58],'deck_height':3.97} for s in [-1,1]],
 extra_forehold_clear_floor={'x':[-1.75,1.75],'y':[-6.10,-2.20],'z':1.60})
assert len(parts)==len({p['name'] for p in parts})
(ROOT/'source/skat_SK03.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
manifest=json.loads((ROOT/'source/texture_manifest_SK02.json').read_text())
(ROOT/'source/texture_manifest_SK03.json').write_text(json.dumps(manifest,indent=2)+'\n')
requirements={'revision':'SK03','units':'Minecraft blocks, game art',
 'requests':['Correct mirrored corner plinths','Close forward/rear openings with coherent armour','Add usable forehold floor','Provide visible shore boarding','One non-overlapping stair hatch; standing descent/ascent','Thin ceiling lights above cargo aisle','Park crane hook clear of the standing stair envelope when the ship turns'],
 'preserved':['SK02 sources','24 cargo inventories / 1296 slots','Cabin / rotors / main hull footprint and twin stepped bows','Original 12 palette-adapted CC0 textures'],
 'source_SK02_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),
 'parts':len(parts),'removed_or_changed_original_parts':[n for n,p in old.items() if not any(p==q for q in parts)],
 'runtime_checks_pending':True,'physical_manufacturing_claim':False}
(ROOT/'reports/REQUIREMENTS_SK03.json').write_text(json.dumps(requirements,indent=2)+'\n')
print(json.dumps({'revision':'SK03','parts':len(parts),'forehold_clear_floor_area':3.5*3.9,'boarding_ladders':2}))
