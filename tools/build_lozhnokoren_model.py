"""Exact selected studio cuboids, pivots and face UVs; lossless atlas packing only."""
from pathlib import Path
import base64,hashlib,io,json,math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'art/fauna/lozhnokoren'
ASSET=ROOT/'src/main/resources/assets/interstice'
SELECTED={'lurker':'lurker_LS04','skirmisher':'skirmisher_SK01','spitter':'spitter_SP02'}
# The face order/UV convention is the same as project23/tools/build_scene.py.
FACES={
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)]}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8',newline='\n')
def sha(data):return hashlib.sha256(data).hexdigest()
def number(value):return float(str(value).strip())
def rotate(p,angles):
    x,y,z=p
    a,b,c=[math.radians(number(v))*(-1 if i!=1 else 1) for i,v in enumerate(angles)]
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    return [x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c),z]
def compile_model(path):
    source=path.read_bytes();data=json.loads(source)
    assert data['resolution']=={'width':16,'height':16}
    groups={g['uuid']:g for g in data['groups']};elements={e['uuid']:e for e in data['elements']}
    atlas=Image.new('RGBA',(256,128));textures=[]
    for i,t in enumerate(data['textures']):
        raw=base64.b64decode(t['source'].split(',',1)[1]);im=Image.open(io.BytesIO(raw)).convert('RGBA')
        assert im.size==(16,16)
        x,y=i%4*64,i//4*64
        for dy in range(0,64,16):
            for dx in range(0,64,16):atlas.paste(im,(x+dx,y+dy))
        assert atlas.crop((x,y,x+16,y+16)).tobytes()==im.tobytes()
        textures.append({'name':t['name'],'source_png_sha256':sha(raw),'rgba_sha256':sha(im.tobytes()),'origin':[x,y]})
    assert len(textures)==7
    base={}
    bones=[];parts=[]
    def walk(node,parent=None):
        group=groups[node['uuid']];origin=group['origin'];p=groups[parent]['origin'] if parent else [0,24,0]
        rest=base.get(group['name'],[0,0,0]);rot=[math.radians(group['rotation'][i]+rest[i])*(-1 if i!=1 else 1) for i in range(3)]
        bones.append({'name':group['name'],'parent':groups[parent]['name'] if parent else None,'offset':[origin[0]-p[0],-(origin[1]-p[1]),origin[2]-p[2]],'rotation':rot})
        for child in node['children']:
            if isinstance(child,dict):walk(child,node['uuid']);continue
            element=elements[child]
            assert element.get('export',True) and element['type']=='cube'
            points=[];faces=[];lo,hi=element['from'],element['to']
            for side,bits in FACES.items():
                face=element['faces'].get(side)
                if not face or face.get('texture') is None:continue
                assert face.get('rotation',0)==0
                start=len(points)
                for corner in bits:
                    raw=[(lo,hi)[bit][axis] for axis,bit in enumerate(corner)]
                    v=[raw[0]-origin[0],-(raw[1]-origin[1]),raw[2]-origin[2]]
                    if element.get('rotation'):
                        pivot=element['origin'];pivot=[pivot[0]-origin[0],-(pivot[1]-origin[1]),pivot[2]-origin[2]]
                        local=rotate([v[i]-pivot[i] for i in range(3)],element['rotation'])
                        v=[pivot[i]+local[i] for i in range(3)]
                    points.append(v)
                u,v,uu,vv=face['uv'];tx,ty=textures[int(face['texture'])]['origin']
                uv=[[tx+u,ty+v],[tx+uu,ty+v],[tx+uu,ty+vv],[tx+u,ty+vv]]
                faces.append({'indices':[start+3,start+2,start+1,start],'uv':uv,'texture':int(face['texture']),'source_side':side})
            parts.append({'name':element['name'],'uuid':element['uuid'],'bone':group['name'],'vertices':points,'faces':faces})
    walk(data['outliner'][0])
    assert len(bones)==88 and len(parts)==357 and sum(len(p['faces']) for p in parts)==2142
    return {'source_sha256':sha(source),'atlas':{'width':256,'height':128},'bones':bones,'parts':parts},atlas,textures,data

def main():
 model,atlas,textures,data=compile_model(SOURCE/'lozhnokoren_LK01.bbmodel')
 for p in model['parts']:
  for f in p['faces']:
   ox,oy=textures[f['texture']]['origin']
   assert all(ox<=uv[0]<ox+64 and oy<=uv[1]<oy+64 for uv in f['uv'])
 model['animations']=data['animations']
 target=ASSET/'models/entity/lozhnokoren.json';write(target,model)
 image=ASSET/'textures/entity/lozhnokoren_lk01.png';atlas.save(image)
 write(ASSET/'provenance/lozhnokoren.json',{'revision':'LK01','source_project':'Blender/creations/24_lozhnokoren','source_sha256':model['source_sha256'],'mesh_sha256':sha(target.read_bytes()),'atlas_sha256':sha(image.read_bytes()),'cuboids':357,'bones':88,'quads':2142,'textures':textures,'clips':[a['name'] for a in data['animations']],'pixel_operation':'Original seven16x16 RGBA tiles repeated losslessly; UV repetition retained. No painting, scale or palette change.','upstream':'art/fauna/lozhnokoren/ASSET_PROVENANCE.json'})
 print('LK01: 357 rigid cubes,88 bones,2142 quads;7 unchanged tiles,3 unchanged source clips')
if __name__=='__main__':main()
