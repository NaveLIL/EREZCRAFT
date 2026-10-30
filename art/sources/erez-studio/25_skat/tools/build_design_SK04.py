"""SK04 game-art correction: joined skins, planar glazing and two usable deck crates.
One coordinate unit = one Minecraft block; no physical vessel certification.
"""
from pathlib import Path
import json,math,copy,hashlib
ROOT=Path(__file__).resolve().parents[1]
old=json.loads((ROOT/'source/skat_SK03.json').read_text(encoding='utf8'));d=copy.deepcopy(old)
d['revision']='SK04';d['authoritative_geometry']='source/skat_SK04.json';d['design_change']='Close 2–3 cm panel gaps without changing outer limits; planar fitted glazing; two functional deck crates.'
for g in d['groups']:
 if g['name']=='SKAT_SK03':g['name']='SKAT_SK04'
 if g['parent']=='SKAT_SK03':g['parent']='SKAT_SK04'
parts=d['parts'];cuboids=[(i,p) for i,p in enumerate(old['parts']) if p['shape']=='cube' and p.get('rotation',[0,0,0])==[0,0,0] and p['group'] in {'01_Hull_shells','03_Hull_plating_and_ribs','07_Main_cargo_deck','11_Cabin_roof'}]
extensions={};pairs=0
for a,(i,p) in enumerate(cuboids):
 for j,q in cuboids[a+1:]:
  if p['group']!=q['group'] or p['material']!=q['material']:continue
  for axis in range(3):
   if any(abs(p['center'][k]-q['center'][k])>1e-5 for k in range(3) if k!=axis):continue
   gap=abs(p['center'][axis]-q['center'][axis])-(p['size'][axis]+q['size'][axis])/2
   if not .0001<gap<=.035:continue
   sign=1 if q['center'][axis]>p['center'][axis] else -1
   for index,side in [(i,sign),(j,-sign)]:
    row=extensions.setdefault(index,[[0.,0.] for _ in range(3)]);row[axis][1 if side>0 else 0]=max(row[axis][1 if side>0 else 0],gap/2)
   pairs+=1
for index,axes in extensions.items():
 for axis,(lo,hi) in enumerate(axes):parts[index]['size'][axis]+=lo+hi;parts[index]['center'][axis]+=(hi-lo)/2

def solid(name,quad,thickness=.025):
 a,b,c=quad[:3];u=[b[k]-a[k] for k in range(3)];v=[c[k]-a[k] for k in range(3)];n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=math.sqrt(sum(x*x for x in n));n=[x/length*thickness/2 for x in n]
 return {'name':name,'shape':'solid','vertices':[[p[k]-n[k] for k in range(3)] for p in quad]+[[p[k]+n[k] for k in range(3)] for p in quad],'faces':[[0,1,2,3],[7,6,5,4],[0,4,5,1],[1,5,6,2],[2,6,7,3],[3,7,4,0]],'material':'glass','group':'10_Cabin_glass'}
z0,z1=4.755,6.125
def front_y(z):return -6.65+(z-4.78)*(.53/1.30)
def side_x(z):return 3.85-(z-4.81)*(.35/1.22)
def side_y(y,z):return y+(z-4.81)*(.12/1.22)
replacement={}
for i,(a,b) in enumerate(zip([-2.92,-1.4,0,1.4],[-1.4,0,1.4,2.92])):
 replacement['Cabin_windscreen_'+str(i)]=solid('Cabin_windscreen_'+str(i),[[a-.035,front_y(z0),z0],[b+.035,front_y(z0),z0],[b+.035,front_y(z1),z1],[a-.035,front_y(z1),z1]])
for sign in [-1,1]:
 for i,(a,b) in enumerate([(-5.35,-3.95),(-3.95,-2.5)]):
  name=f'Cabin_side_glass_{sign}_{i}';replacement[name]=solid(name,[[sign*side_x(z0),side_y(a-.11,z0),z0],[sign*side_x(z0),side_y(b+.035,z0),z0],[sign*side_x(z1),side_y(b+.035,z1),z1],[sign*side_x(z1),side_y(a-.11,z1),z1]])
 # A planar chamfer meeting the front pane. The widened side frame covers its join.
 a=[sign*2.92,front_y(z0),z0];b=[sign*side_x(z0),side_y(-5.35,z0),z0];c=[sign*2.92,front_y(z1),z1]
 nx=b[1]-a[1];ny=-(b[0]-a[0]);constant=nx*c[0]+ny*c[1];end=[sign*side_x(z1),(constant-nx*sign*side_x(z1))/ny,z1]
 name=f'Cabin_corner_glass_{sign}';replacement[name]=solid(name,[a,b,end,c])
 for suffix,p,q in [('side',b,end),('bottom',a,b),('top',c,end)]:
  parts.append({'name':f'SK04_corner_gasket_{sign}_{suffix}','shape':'beam','a':p,'b':q,'width':.18,'depth':.10,'material':'graphite','group':'09_Sloped_cabin'})
for i,p in enumerate(parts):
 if p['name'] in replacement:parts[i]=replacement[p['name']]
for p in parts:
 if p['name'].startswith('Sample_crate_'):
  d['cargo_modules'].append({'id':'DECK_'+p['name'].rsplit('_',1)[-1],'center':p['center'],'size':p['size'],'target_slots':54,'implemented_inventory':False,'deck':True})
d['target_capacity']=26*54
(ROOT/'source/skat_SK04.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
report={'revision':'SK04','base_sha256':hashlib.sha256((ROOT/'source/skat_SK03.json').read_bytes()).hexdigest(),'joined_pairs':pairs,'changed_panels':len(extensions),'glazing_panels':len(replacement),'cargo_modules':len(d['cargo_modules']),'outer_limits_not_extended_by_panel_joining':True,'physical_certification':False}
(ROOT/'reports/DESIGN_SK04.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
