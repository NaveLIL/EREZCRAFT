from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];P=ROOT/'preview/WT11';P.mkdir(exist_ok=True);S=P/'native/worktools';f=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
im=Image.open(ROOT/'preview/WT10/native/worktools/worktools-first-person-riftsteel_shovel.png');assert im.size==(1920,1080)
o=Image.new('RGB',(1280,770),'#15212b');d=ImageDraw.Draw(o);d.text((16,8),'Первое лицо — сохранено без изменений из WT10',font=f(27),fill='white');o.paste(im.resize((1280,720),Image.Resampling.LANCZOS),(0,50));o.save(P/'first_shovel_both_WT11.png')
o=Image.new('RGB',(1200,970),'#15212b');d=ImageDraw.Draw(o);d.text((16,7),'WT11 — хват в ладони, инструмент выходит из кулака',font=f(26),fill='white')
for i,(body,side,label) in enumerate([('wide','right','Steve / правая'),('wide','left','Steve / левая'),('slim','right','Alex / правая'),('slim','left','Alex / левая')]):
 im=Image.open(S/('worktools-palm-'+body+'-'+side+'.png'));assert im.size==(1920,1080);box=(530,420,1110,850) if side=='right' else (810,420,1390,850)
 x=10+(i%2)*600;y=50+(i//2)*460;d.text((x,y),label,font=f(22),fill='#bddbe8');o.paste(im.crop(box),(x,y+29))
o.save(P/'palm_comparison_WT11.png')
# Whole-body views use the same crop and uniform scale in all four frames.
o=Image.new('RGB',(1200,650),'#15212b');d=ImageDraw.Draw(o)
for i,(body,side,label) in enumerate([('wide','right','Steve / правая'),('wide','left','Steve / левая'),('slim','right','Alex / правая'),('slim','left','Alex / левая')]):
 im=Image.open(S/('worktools-palm-'+body+'-'+side+'.png')).crop((520,180,1400,1020));x=i*300;d.text((x+8,5),label,font=f(20),fill='white');o.paste(im.resize((300,286),Image.Resampling.LANCZOS),(x,40))
o=o.crop((0,0,1200,326));o.save(P/'palm_full_body_WT11.png');print('WT11 native figures saved')
