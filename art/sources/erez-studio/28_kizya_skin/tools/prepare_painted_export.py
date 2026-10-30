"""Native Blender image format export and UV baking for Java's fixed 64x64 skin layout."""
from pathlib import Path
import bpy,json,zipfile,io,hashlib
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'source/avatar_PK02.json').read_text(encoding='utf8'))
raw=bpy.data.images.load(str(ROOT/'source/painted_sheet_PK02_original.png'));original_size=list(raw.size)
N=data['config']['skinSize']['x'];raw.scale(N,N);raw.filepath_raw=str(ROOT/'source/avatar_atlas_PK02.png');raw.file_format='PNG';raw.save()
# The fixed vanilla UV export samples the same painted faces, without adding new art.
pixels=list(raw.pixels[:]);output=[0.0]*(64*64*4)
def region(u,v,w,h,d):
    return {'north':(u+d,v+d,u+d+w,v+d+h),'south':(u+2*d+w,v+d,u+2*d+2*w,v+d+h),
        'west':(u,v+d,u+d,v+d+h),'east':(u+d+w,v+d,u+2*d+w,v+d+h),'up':(u+d,v,u+d+w,v+d),'down':(u+d+w,v,u+d+2*w,v+d)}
targets={'head':region(0,0,8,8,8),'body':region(16,16,8,12,4),'right_arm':region(40,16,3,12,4),'left_arm':region(32,48,3,12,4),'right_leg':region(0,16,4,12,4),'left_leg':region(16,48,4,12,4)}
for bone,faces in targets.items():
    for side,(x0,y0,x1,y1) in faces.items():
        src=data['base_face_uv'][bone][side]
        for y in range(y0,y1):
            for x in range(x0,x1):
                sx=int(src['sx']+(x-x0+.5)/(x1-x0)*(src['ex']-src['sx']));sy=int(src['sy']+(y-y0+.5)/(y1-y0)*(src['ey']-src['sy']))
                s=((N-1-sy)*N+sx)*4;t=((63-y)*64+x)*4
                output[t:t+4]=pixels[s:s+3]+[1.0]
skin=bpy.data.images.new('Kizya / Java slim 64x64',64,64,alpha=True);skin.pixels[:]=output;skin.filepath_raw=str(ROOT/'exports/Kizya_Pink_PK02_slim_64x64.png');skin.file_format='PNG';skin.save()
# Pack only the face islands actually used by the 3D avatar. This removes unused
# illustration-sheet noise and produces a small game atlas without repainting.
packed=[0.0]*(128*128*4);cursor=[1,1,0];mapping={}
for p in data['parts']:
    for face,r in p['uv'].items():
        key=tuple(r[k] for k in ['sx','sy','ex','ey'])
        if key not in mapping:
            a,b,c,d=key;w=max(2,round((c-a)/2));h=max(2,round((d-b)/2))
            if cursor[0]+w+1>128:cursor[0]=1;cursor[1]+=cursor[2]+2;cursor[2]=0
            assert cursor[1]+h+1<=128,'Packed painted atlas overflow'
            xx,yy=cursor[:2];cursor[0]+=w+2;cursor[2]=max(cursor[2],h)
            for j in range(h):
                for i in range(w):
                    sx=int(a+(i+.5)*(c-a)/w);sy=int(b+(j+.5)*(d-b)/h);s=((N-1-sy)*N+sx)*4;t=((127-(yy+j))*128+xx+i)*4
                    packed[t:t+4]=pixels[s:s+4]
            mapping[key]={'sx':xx,'sy':yy,'ex':xx+w,'ey':yy+h,'rot':'0','autoUV':False}
        p['uv'][face]=dict(mapping[key])
data['painted_source_face_uv']=data['base_face_uv'];data['config']['skinSize']={'x':128,'y':128}
byid={p['id']:p for p in data['parts']}
for group in data['config']['elements']:
    for p in group['children']:p['faceUV']=byid[p['storeID']]['uv']
final=bpy.data.images.new('Kizya / packed painted game atlas',128,128,alpha=True);final.pixels[:]=packed;final.filepath_raw=str(ROOT/'source/avatar_atlas_PK02.png');final.file_format='PNG';final.save()
(ROOT/'source/avatar_PK02.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(ROOT/'source/PACKED_UV_LAYOUT.json').write_text(json.dumps([{'source':list(k),'packed':v} for k,v in mapping.items()],indent=2)+'\n')
with zipfile.ZipFile(ROOT/'exports/Kizya_Pink_PK02.cpmproject','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('config.json',json.dumps(data['config']));z.write(ROOT/'source/avatar_atlas_PK02.png','skin.png')
    for name,a in data['animations'].items():z.writestr('animations/'+name,json.dumps(a))
report={'revision':'PK02','original_painted_sheet_size':original_size,'painted_atlas_size':[128,128],'packed_painted_regions':len(mapping),'java_skin_size':[64,64],'skin_type':'slim',
    'operation':'UV repacking and resolution conversion of new painted artwork; no semantic repainting or borrowed block textures','account_skin_changed':False,
    'standard_skin_sha256':hashlib.sha256((ROOT/'exports/Kizya_Pink_PK02_slim_64x64.png').read_bytes()).hexdigest()}
(ROOT/'reports/PAINTED_EXPORT.json').write_text(json.dumps(report,indent=2)+'\n');print('PAINTED_SKIN_EXPORTED',json.dumps(report))
