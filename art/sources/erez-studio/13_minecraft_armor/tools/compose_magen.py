from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
ROOT=Path(__file__).resolve().parents[1]
def font(size,bold=False):return ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf' if bold else 'C:/Windows/Fonts/arial.ttf',size)
def put(canvas,name,x,y,w,h):
    im=ImageOps.contain(Image.open(ROOT/'preview'/name).convert('RGB'),(w,h),Image.Resampling.LANCZOS)
    canvas.paste(im,(x+(w-im.width)//2,y+(h-im.height)//2))
im=Image.new('RGB',(1800,1040),'#25343a');d=ImageDraw.Draw(im)
d.text((35,23),'МЕЖДУМОРЬЕ · силовая броня «МАГЕН»',font=font(32,True),fill='#e2eeec')
d.text((35,73),'Закрытая маска · тяжёлые пластины · гравитационный накопитель · звезда Давида',font=font(23),fill='#b6cdd1')
for i,(file,label) in enumerate([('aegis_front.png','Спереди'),('aegis_proposed.png','Объём и сочленения'),('aegis_back.png','Накопитель поля')]):
    put(im,file,20+i*595,145,575,790);d.text((35+i*595,116),label,font=font(23,True),fill='#e2eeec')
d.text((35,978),'A03 · геометрия Blender; проверка игры фиксируется в отчёте отдельно.',font=font(22),fill='#b6cdd1')
im.save(ROOT/'preview/magen_review_A03.png')
im=Image.new('RGB',(1500,940),'#25343a');d=ImageDraw.Draw(im)
d.text((30,20),'МЕЖДУМОРЬЕ · три ступени брони',font=font(31,True),fill='#e2eeec')
for i,(file,name,stats) in enumerate([('riftsilver_proposed.png','Разломное серебро','20 защиты / 12 твёрдости'),('riftsteel_proposed.png','Рифтсталь','24 защиты / 16 твёрдости'),('aegis_proposed.png','Маген','30 защиты / 20 твёрдости')]):
    x=10+i*495;d.text((x+20,78),name,font=font(25,True),fill='#e2eeec');put(im,file,x,120,485,710)
    d.text((x+20,850),stats,font=font(23),fill='#b6cdd1')
im.save(ROOT/'preview/three_armor_sets_A03.png')
print(ROOT/'preview/magen_review_A03.png')
