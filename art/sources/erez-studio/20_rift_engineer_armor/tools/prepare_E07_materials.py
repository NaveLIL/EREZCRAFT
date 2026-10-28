from pathlib import Path
import shutil,json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];dest=ROOT/'source/materials'
original=Path('C:/Users/Dimon/.codex/generated_images/01a172d7-c399-7483-af8b-6fd3ca197b1d/exec-c5652a65-4bab-4ddd-989c-12caabd096f8.png')
shutil.copy2(original,dest/'materials_E07_generated.png');src=Image.open(original).convert('RGBA');records=[]
for material,(ox,oy) in {'metal':(0,0),'leather':(1,0),'rubber':(0,1),'lining':(1,1)}.items():
    tile=Image.new('RGBA',(32,32));tile.putdata([src.getpixel((int((ox+(x+.5)/32)*src.width/2),int((oy+(y+.5)/32)*src.height/2))) for y in range(32) for x in range(32)])
    p=dest/('E07_'+material+'_pixels32.png');tile.save(p);records.append(dict(material=material,file=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
(dest/'generation_E07.json').write_text(json.dumps(dict(method='Built-in image_gen',prompt='generation_E07_prompt.txt',original='materials_E07_generated.png',original_sha256=hashlib.sha256(original.read_bytes()).hexdigest(),normalization='Sample 32x32 logical pixel centers in each of four generated quadrants; preserve original layouts',tiles=records),indent=2)+'\n',encoding='utf-8')
print('E07 material assets saved with prompt and provenance')
