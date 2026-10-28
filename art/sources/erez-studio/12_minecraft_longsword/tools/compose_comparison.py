from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
revision=json.loads((ROOT/'source/design.json').read_text(encoding='utf-8-sig'))['revision']
canvas=Image.new('RGB',(1480,900),'#202d33');draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',32)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
draw.text((40,25),'МЕЖДУМОРЬЕ · МЕЧ ИЗ РИФТСТАЛИ',font=font,fill='#e2e8e6')
draw.text((40,75),'Одинаковые масштаб, камера и освещение · рукояти совмещены',font=small,fill='#aabcc4')
for x,name,title in ((25,'before_same_scale.png','БЫЛО · текущая модель'),(755,'after_same_scale.png','ПРЕДЛОЖЕНИЕ · '+revision)):
    image=Image.open(ROOT/'preview'/name).convert('RGB').resize((700,700),Image.Resampling.LANCZOS)
    canvas.paste(image,(x,125))
    draw.text((x+22,137),title,font=small,fill='#e2e8e6')
draw.line((740,130,740,815),fill='#45565d',width=2)
draw.text((40,850),'Предварительный рендер Blender. В мод пока не установлено.',font=small,fill='#aabcc4')
canvas.save(ROOT/'preview/before_after.png')
canvas.save(ROOT/'preview'/('before_after_'+revision.replace('-','')+'.png'))
print(ROOT/'preview/before_after.png')
