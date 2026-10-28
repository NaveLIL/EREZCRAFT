from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
R=Path(r'D:/repos/Blender/creations/21_blade_worktools/preview');source=Image.open(R/'blade_worktools_B01.png').convert('RGB');board=Image.new('RGB',(1600,1220),'#17232d');board.paste(source,(0,120));d=ImageDraw.Draw(board);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',28);small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22);d.text((24,14),'Blade / EREZCRAFT — B01',font=font,fill='#e0eaf0');d.text((24,52),'Исходные формы Blade сохранены; адаптирована только палитра',font=small,fill='#b8cbd4');
for x,label in zip([195,595,995,1395],['Кирка','Топор','Лопата','Мотыга']):
 d.text((x,96),label,font=small,fill='#e0eaf0',anchor='mm')
d.text((24,155),'Разломное серебро',font=small,fill='#ede4fc');d.text((24,680),'Рифтсталь',font=small,fill='#c9e6ed');board.save(R/'review_B01.png')
