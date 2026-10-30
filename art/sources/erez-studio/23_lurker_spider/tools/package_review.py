from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib,shutil,argparse
parser=argparse.ArgumentParser();parser.add_argument('--revision',default='LS02');parser.add_argument('--baseline',default='original')
args=parser.parse_args();ROOT=Path(__file__).resolve().parents[1];REV=args.revision
reference=Path('D:/repos/erez/.codex-remote-attachments/01a18166-1151-7823-b315-5c455d286cdd/2459eb1d-d7a6-4e4d-bfce-f2a8082b6892')
(ROOT/'references').mkdir(exist_ok=True)
photos=[]
for p in sorted(reference.glob('*.jpg')):
    target=ROOT/'references'/p.name;shutil.copyfile(p,target)
    photos.append({'file':str(target.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
                   'source':'User-supplied photograph, visual anatomy reference only; not a texture or distributable game asset'})
(ROOT/'references/sources.json').write_text(json.dumps(photos,indent=2)+'\n',encoding='utf8')
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',30)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
baseline_label='Исходный большой паук' if args.baseline=='original' else args.baseline+' — предыдущая версия'
tiles=[(baseline_label,f'lurker_original_{REV}_quarter.png'),
       (REV+' — длинные ноги сохранены',f'lurker_{REV}_quarter.png'),
       (REV+' — спереди',f'lurker_{REV}_front.png'),
       (REV+' — сверху',f'lurker_{REV}_top.png')]
sheet=Image.new('RGB',(1600,1420),(26,30,36));draw=ImageDraw.Draw(sheet)
draw.text((30,20),'EREZCRAFT / большой паук / '+REV,font=font,fill=(231,235,239))
subtitle='Лапы толще на 18%, кончики на 12%. Длина ног, тело, текстура и анимации сохранены.' if REV=='LS03' else 'Blender-прототип: пропорции по фото, прежние длины ног и пиксельная текстура 128×128'
if REV=='LS04':subtitle='Окраска по фото: тёмное тело, медно-рыжие колени. Геометрия LS03 и положение глаз сохранены.'
draw.text((30,62),subtitle,font=small,fill=(174,189,198))
for i,(label,filename) in enumerate(tiles):
    x=(i%2)*800;y=110+(i//2)*650
    draw.text((x+24,y),label,font=font,fill=(223,228,233))
    image=Image.open(ROOT/'preview'/filename).convert('RGB');image=image.resize((800,600),Image.Resampling.LANCZOS)
    sheet.paste(image,(x,y+43))
sheet.save(ROOT/f'preview/lurker_{REV}_review.jpg',quality=94)
model=json.loads((ROOT/f'source/lurker_{REV}.bbmodel').read_text(encoding='utf8'))
report=json.loads((ROOT/f'reports/design_{REV}.json').read_text())
atlas=Image.open(ROOT/'source/cave_rift_spider_lurker.png').convert('RGBA')
for entry in model['textures']:
    texture_path=ROOT/'source'/entry['name']
    if texture_path.is_file():
        texture=Image.open(texture_path).convert('RGBA')
        assert texture.size==atlas.size and texture.getchannel('A').tobytes()==atlas.getchannel('A').tobytes()
original_ids=set()
original=json.loads(Path(report['source']).read_text(encoding='utf8'))
original_ids={e['uuid'] for e in original['elements']}
new=[e for e in model['elements'] if e['uuid'] not in original_ids]
assert len({e['uuid'] for e in model['elements']})==len(model['elements'])
assert model['groups']==original['groups'] and model['animations']==original['animations']
for e in model['elements']:
    for face in e['faces'].values():
        u,v,uu,vv=face['uv']
        if abs(uu-u)<1 and abs(vv-v)<1:
            assert atlas.getpixel((int((u+uu)/2),int((v+vv)/2)))[3]==255,(e['name'],face)
artifacts={}
for relative in [f'source/lurker_{REV}.bbmodel',f'assembly/lurker_{REV}.blend',f'exports/lurker_{REV}.glb',f'preview/lurker_{REV}_review.jpg']:
    p=ROOT/relative;assert p.is_file() and p.stat().st_size>100
    artifacts[relative]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
checks={'revision':REV,'files':artifacts,'unique_element_ids':True,'new_bristle_and_eye_uv_samples_opaque':True,
        'comparison_baseline':args.baseline,
        'original_groups_and_animations_preserved':True,'leg_segment_count':len(report['leg_lengths']),
        'all_leg_longitudinal_extents_preserved':report['leg_lengths_all_preserved'],
        'original_texture_bytes_preserved':report['texture_bytes_identical'],
        'texture_alpha_and_size_preserved':True,
        'geometry_identical_to_baseline':report.get('geometry_identical_to_LS03'),
        'eye_positions_identical_to_baseline':report.get('eyes_positions_identical'),
        'reference_photos':len(photos),'game_verified':False}
(ROOT/f'reports/artifacts_{REV}.json').write_text(json.dumps(checks,indent=2)+'\n',encoding='utf8')
print(json.dumps(checks))
