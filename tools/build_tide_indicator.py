"""Thin TI01 pocket barometer: existing 16 dial textures, standard Minecraft geometry."""
from pathlib import Path
import argparse,copy,hashlib,json,zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/interstice'
SIDES=('north','south','east','west','up','down')
DONOR=ROOT/'art/sources/mit/mb3d-clock'
ARCHIVE=DONOR/'MB-3D Items Pack 2.4.0.zip'
FACE_VERTICES={
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)]}

def align_with_clock(elements):
    """Match the donor's face-up clock axes, including its 180-degree dial heading."""
    mapping={'down':'north','up':'south','north':'down','south':'up','east':'west','west':'east'}
    result=[]
    for original in elements:
        e=copy.deepcopy(original);a=original['from'];b=original['to']
        e['from']=[16-b[0],a[2],a[1]];e['to']=[16-a[0],b[2],b[1]];e['faces']={}
        for side,face in original['faces'].items():
            target=mapping[side]
            mapped=[(1-x,z,y) for x,y,z in FACE_VERTICES[side]]
            order=[mapped.index(p) for p in FACE_VERTICES[target]]
            rotation=next(r for r in range(4) if order==[(i+r)%4 for i in range(4)])
            nf=copy.deepcopy(face);nf['rotation']=(face.get('rotation',0)+90*rotation)%360
            e['faces'][target]=nf
        result.append(e)
    return result

def clock_display():
    assert hashlib.sha512(ARCHIVE.read_bytes()).hexdigest()=='25b2041fe532797d8999fc14951b049f0dcac7d16e1603b6e328cb593567f8bb8b963daa7a2037635dfa2f5a6c908fff3c1e305b2c2c06813147b5726b9da115'
    with zipfile.ZipFile(ARCHIVE) as z:
        return json.loads(z.read('assets/minecraft/models/item/display/clock.json'))['display']

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def cube(name,a,b,material):
    return {'name':name,'from':a,'to':b,
            'faces':{side:{'texture':material,'uv':[0,0,16,16]} for side in SIDES}}
def build(output):
    images=[Image.open(ASSETS/f'textures/item/tide_indicator_{i:02}.png').convert('RGBA') for i in range(16)]
    assert all(im.size==(16,16) for im in images)
    assert all(im.getchannel('A').tobytes()==images[0].getchannel('A').tobytes() for im in images)
    mask=[[images[0].getpixel((x,y))[3]>0 for x in range(16)] for y in range(16)]
    elements=[];pixel=.75
    # Exact original alpha outline, including the tiny suspension loop. Merge vertical runs.
    spans={}
    for y,row in enumerate(mask):
        x=0
        while x<16:
            if not row[x]:x+=1;continue
            left=x
            while x<16 and row[x]:x+=1
            spans.setdefault((left,x),[]).append(y)
    for (left,right),rows in spans.items():
        start=last=rows[0]
        for y in rows[1:]+[100]:
            if y==last+1:last=y;continue
            elements.append(cube('thin_case', [2+left*pixel,2+(15-last)*pixel,7.59],
                                 [2+right*pixel,2+(16-start)*pixel,8.39], '#case'))
            start=last=y
    # The untouched complete dial sits slightly below its original one-pixel perimeter.
    elements.append({'name':'recessed_dial','from':[2,2,8.41],'to':[14,14,8.41],
                     'faces':{'south':{'texture':'#dial','uv':[0,0,16,16]}}})
    rim=0
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:continue
            if all(0<=xx<16 and 0<=yy<16 and mask[yy][xx] for xx,yy in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]):continue
            # A 0.06-unit raised edge, sampled from the original texture at the same pixel.
            e=cube('fine_rim',[2+x*pixel,2+(15-y)*pixel,8.40],
                   [2+(x+1)*pixel,2+(16-y)*pixel,8.47],'#case')
            e['faces']['south']={'texture':'#dial','uv':[x,y,x+1,y+1]}
            elements.append(e);rim+=1
    elements=align_with_clock(elements)
    donor=clock_display()
    display={
        'gui':{'rotation':[90,180,0],'translation':[0,0,0],'scale':[1.25]*3},
        'ground':{'translation':[0,2,0],'scale':[.45]*3},
        'fixed':copy.deepcopy(donor['fixed'])}
    # Missing left-hand transforms inherit right-hand transforms in the donor.
    # Explicitly materialize that fallback; Minecraft applies its normal mirroring.
    for view in ('firstperson','thirdperson'):
        for hand in ('righthand','lefthand'):
            display[f'{view}_{hand}']=copy.deepcopy(donor.get(f'{view}_{hand}',donor[f'{view}_righthand']))
    base={'parent':'minecraft:block/block','gui_light':'front','ambientocclusion':False,
          'render_type':'minecraft:cutout', 'credit':'EREZCRAFT TI02; clock hand poses: MB-3D Items Pack / mbanders / MIT',
          'textures':{'case':'interstice:item/expedition_tools/brass','dial':'interstice:item/tide_indicator_00','particle':'#case'},
          'elements':elements,'display':display}
    overrides=[{'predicate':{'interstice:tide_angle':i/16},'model':f'interstice:item/tide_indicator_{i:02}'} for i in range(16)]
    models={'tide_indicator_base':base,'tide_indicator':{'parent':'interstice:item/tide_indicator_base','overrides':overrides}}
    models.update({f'tide_indicator_{i:02}':{'parent':'interstice:item/tide_indicator_base',
                                         'textures':{'dial':f'interstice:item/tide_indicator_{i:02}'}} for i in range(16)})
    target=output/'src/main/resources/assets/interstice'
    for name,model in models.items():write(target/f'models/item/{name}.json',model)
    source={'revision':'TI02','units':'16 model units per Minecraft block',
            'scope':'Visual geometry only; no mechanics, recipes, tide timing or new PNGs',
            'maximum_case_depth':.88,'depth_axis':'y','nominal_dial_width':12,'maximum_depth_to_nominal_width':.88/12,
            'axis_alignment':'Rigid Ry(180) Rx(-90) about [8,8,8]; matches donor up-face heading; preserves all textured corners',
            'hand_pose_source':{'project':'jZM0fp9Q','version':'eL9owlE1','name':'MB-3D Items Pack 2.4.0',
                                'author':'mbanders','license':'MIT','url':'https://modrinth.com/resourcepack/mb3d-items-pack',
                                'archive_sha256':digest(ARCHIVE),'model':'assets/minecraft/models/item/display/clock.json',
                                'display':donor,'hand_values_modified':False},
            'case_outline':'Unmodified alpha silhouette of original 16x16 dial, including loop',
            'dial_textures':{f'tide_indicator_{i:02}':digest(ASSETS/f'textures/item/tide_indicator_{i:02}.png') for i in range(16)},
            'case_texture':{'reference':'interstice:item/expedition_tools/brass',
                            'sha256':digest(ASSETS/'textures/item/expedition_tools/brass.png'),
                            'origin':'Existing T01 palette-only iron-block material; unchanged'},
            'rim_pixels':rim,'quads':sum(len(e['faces']) for e in elements),'models':models}
    provenance=copy.deepcopy(source);provenance.pop('models')
    write(target/'provenance/tide-indicator-ti01.json',provenance)
    license_file=DONOR/'LICENSE-MIT.txt'
    (target/'licenses').mkdir(parents=True,exist_ok=True)
    (target/'licenses/MB3D-MIT.txt').write_bytes(license_file.read_bytes())
    return source

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output-root',type=Path,default=ROOT);p.add_argument('--design',type=Path)
    args=p.parse_args();design=build(args.output_root)
    if args.design:write(args.design,design)
    print(json.dumps({'passed':True,'revision':'TI02','depth':design['maximum_case_depth'],'quads':design['quads'],'states':16,'new_pngs':0}))
