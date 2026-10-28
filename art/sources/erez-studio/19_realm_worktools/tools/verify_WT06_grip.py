from pathlib import Path
import json,math
root=Path(__file__).resolve().parents[1];old=json.loads((root/'source/worktools_WT04.json').read_text(encoding='utf8'));new=json.loads((root/'source/worktools_WT06.json').read_text(encoding='utf8'))
def point(t,left):
 p=[0,-4.15,0];p=[p[i]*t['scale'][i] for i in range(3)];r=list(t['rotation']);tr=list(t['translation'])
 if left:r[1]*=-1;r[2]*=-1;tr[0]*=-1
 a,b,c=map(math.radians,r);x,y,z=p;x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c);x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b);y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
 return [x+tr[0],y+tr[1],z+tr[2]]
rows=[]
for name,r in new['models'].items():
 for ctx in ['thirdperson_righthand','thirdperson_lefthand']:
  a=point(old['models'][name]['model']['display'][ctx],ctx.endswith('lefthand'));b=point(r['model']['display'][ctx],ctx.endswith('lefthand'));drift=math.dist(a,b);assert drift<.0001,(name,ctx,drift)
  rows.append({'item':name,'context':ctx,'grip_anchor_drift_model_units':drift})
report={'passed':True,'grip_anchor':[8,3.85,8],'maximum_drift_model_units':max(r['grip_anchor_drift_model_units'] for r in rows),'rows':rows}
(root/'reports/WT06/grip_verification.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps({'passed':True,'contexts':len(rows),'maximum_drift':report['maximum_drift_model_units']}))
