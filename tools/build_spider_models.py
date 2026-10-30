"""Exact selected studio cuboids, pivots and face UVs; lossless atlas packing only."""
from pathlib import Path
import base64,hashlib,io,json,math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'art/fauna/erez-spiders'
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
    assert data['resolution']=={'width':128,'height':128}
    groups={g['uuid']:g for g in data['groups']};elements={e['uuid']:e for e in data['elements']}
    atlas=Image.new('RGBA',(512,768));textures=[]
    for i,t in enumerate(data['textures']):
        raw=base64.b64decode(t['source'].split(',',1)[1]);im=Image.open(io.BytesIO(raw)).convert('RGBA')
        assert im.size==(128,128)
        x,y=i%4*128,i//4*128;atlas.paste(im,(x,y))
        assert atlas.crop((x,y,x+128,y+128)).tobytes()==im.tobytes()
        textures.append({'name':t['name'],'source_png_sha256':sha(raw),'rgba_sha256':sha(im.tobytes()),'origin':[x,y]})
    assert len(textures)==22
    base={}
    for anim in data['animations']:
        if anim['name']!='idle':continue
        for a in anim['animators'].values():
            for frame in a.get('keyframes',[]):
                if frame['channel']=='rotation' and number(frame['time'])==0:
                    base[a['name']]=[number(frame['data_points'][0].get(axis,0)) for axis in 'xyz']
    bones=[];parts=[]
    def walk(node,parent=None):
        group=groups[node['uuid']];origin=group['origin'];p=groups[parent]['origin'] if parent else [0,24,0]
        rest=base.get(group['name'],[0,0,0]);rot=[math.radians(group['rotation'][i]+rest[i])*(-1 if i!=1 else 1) for i in range(3)]
        bones.append({'name':group['name'],'parent':groups[parent]['name'] if parent else None,'offset':[origin[0]-p[0],-(origin[1]-p[1])-(5 if parent is None else 0),origin[2]-p[2]],'rotation':rot})
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
    assert len(bones)==53 and len(parts)==309 and sum(len(p['faces']) for p in parts)==1854
    return {'source_sha256':sha(source),'atlas':{'width':512,'height':768},'bones':bones,'parts':parts},atlas,textures,data

def main():
    provenance={'selection':json.loads((SOURCE/'selection.json').read_text(encoding='utf8')),'upstream':'art/sources/cc0/sea_spider/sources.json','authoring':'EREZCRAFT studio project23; private photographic references are excluded','pixel_operation':'Lossless copy of each unchanged RGBA atlas into a larger atlas; no scaling, painting, palette changes or alpha changes','variants':{}}
    skeleton=None;animations=None
    for variant,stem in SELECTED.items():
        model,atlas,textures,data=compile_model(SOURCE/(stem+'.bbmodel'))
        if skeleton is None:skeleton=model['bones'];animations=data['animations']
        assert skeleton==model['bones'] and animations==data['animations']
        path=ASSET/'models/entity/spider'/f'{variant}.json';write(path,model)
        image=ASSET/'textures/entity/spider'/f'{stem.lower()}.png';image.parent.mkdir(parents=True,exist_ok=True);atlas.save(image)
        provenance['variants'][variant]={'source':str((SOURCE/(stem+'.bbmodel')).relative_to(ROOT)).replace('\\','/'),'revision':stem,'source_sha256':model['source_sha256'],'mesh_sha256':sha(path.read_bytes()),'atlas_sha256':sha(image.read_bytes()),'bones':53,'cuboids':309,'quads':1854,'textures':textures}
    write(ASSET/'provenance/spider-studio.json',provenance)
    print('SPIDER_MODELS: 3 selected variants, 309 cuboids / 1854 quads each, exact RGBA preserved')

if __name__=='__main__':main()
