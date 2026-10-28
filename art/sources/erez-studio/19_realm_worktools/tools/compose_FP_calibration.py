from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
p=Path(r'D:/repos/Minecraft/EREZCRAFT/.verification/s30p/studioWorktools/screenshots');out=Path(r'D:/repos/Blender/creations/19_realm_worktools/preview/WT07');out.mkdir(exist_ok=True)
f=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
names=['vanilla-shovel']+['candidate-'+str(i) for i in range(9)]
o=Image.new('RGB',(1920,500),'#15212b');d=ImageDraw.Draw(o)
for i,n in enumerate(names):
 im=Image.open(p/('reference-first-'+n+'.png'));assert im.size==(1920,1080);x=(i%5)*384;y=(i//5)*250;o.paste(im.resize((384,216),Image.Resampling.LANCZOS),(x,y+34));d.text((x+10,y+4),n,font=f(20),fill='white')
o.save(out/'calibration_grid.png')
print('grid saved')
