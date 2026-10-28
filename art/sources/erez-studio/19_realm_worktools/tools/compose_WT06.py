from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib
ROOT=Path(__file__).resolve().parents[1];P=ROOT/'preview/WT06';P.mkdir(exist_ok=True)
NEW=P/'native/comparison';OLD=ROOT/'preview/WT05/native/comparison'
f=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
W=420;PAD=18;HEADER=98;FOOT=38;CROP=(540,180,1380,1020)
rows=[]
for kind,title in [('shovel','Лопата'),('hoe','Мотыга'),('pickaxe','Кирка'),('axe','Топор')]:
 out=Image.new('RGB',(3*W+4*PAD,HEADER+W+FOOT),'#15212b');d=ImageDraw.Draw(out);d.text((PAD,10),title+' — один игрок, одна камера',font=f(28),fill='#eff8ff')
 for i,(folder,item,label) in enumerate([(NEW,'iron_'+kind,'Ваниль'),(OLD,'riftsteel_'+kind,'До / WT05'),(NEW,'riftsteel_'+kind,'Сейчас / WT06')]):
  with Image.open(folder/('reference-held-'+item+'.png')) as im:
   assert im.size==(1920,1080);im=im.crop(CROP).resize((W,W),Image.Resampling.LANCZOS)
  x=PAD+i*(W+PAD);out.paste(im,(x,HEADER));d.text((x,57),label,font=f(23),fill='#bddbe8')
 d.text((PAD,HEADER+W+6),'Одинаковые обрезка и масштаб кадров. Ширина геометрии WT06 сохранена.',font=f(17),fill='#b4cbd4')
 out.save(P/('comparison_'+kind+'_WT06.png'));rows.append((kind,out))
combo=Image.new('RGB',(rows[0][1].width,rows[0][1].height*2+12),'#15212b');combo.paste(rows[0][1],(0,0));combo.paste(rows[1][1],(0,rows[0][1].height+12));combo.save(P/'comparison_shovel_hoe_WT06.png')
for kind,title in [('shovel','Лопата'),('hoe','Мотыга'),('pickaxe','Кирка'),('axe','Топор')]:
 out=Image.new('RGB',(1280,430),'#15212b');d=ImageDraw.Draw(out);d.text((15,5),title+' — первое лицо, одинаковые камера и FOV 70',font=f(25),fill='white')
 for i,(item,label) in enumerate([('iron_'+kind,'Ваниль'),('riftsteel_'+kind,'WT06')]):
  im=Image.open(NEW/('reference-first-'+item+'.png'));assert im.size==(1920,1080);out.paste(im.resize((640,360),Image.Resampling.LANCZOS),(i*640,70));d.text((i*640+15,38),label,font=f(21),fill='#bddbe8')
 out.save(P/('first_'+kind+'_WT06.png'))
print('Native comparison figures saved')
