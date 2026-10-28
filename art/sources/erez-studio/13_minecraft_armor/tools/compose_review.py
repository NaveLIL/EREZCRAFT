from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
ROOT=Path(__file__).resolve().parents[1]
def font(size,bold=False):return ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf' if bold else 'C:/Windows/Fonts/arial.ttf',size)
def canvas(width,height,title,subtitle):
    result=Image.new('RGB',(width,height),'#25343a');d=ImageDraw.Draw(result)
    d.text((35,22),title,font=font(30,True),fill='#e3e9e8')
    d.text((35,66),subtitle,font=font(21),fill='#b7c7cd');return result
def put(result,path,x,y,width,height):
    im=ImageOps.contain(Image.open(ROOT/'preview'/path).convert('RGB'),(width,height),Image.Resampling.LANCZOS)
    result.paste(im,(x+(width-im.width)//2,y+(height-im.height)//2))
overview=canvas(1800,850,'МЕЖДУМОРЬЕ · ОБЪЁМНАЯ БРОНЯ / A01','Рыцарские формы + экспедиционные детали · одинаковые масштаб и освещение')
d=ImageDraw.Draw(overview)
entries=[('riftsilver_original.png','Серебро · сейчас'),('riftsilver_proposed.png','Серебро · предложение'),
         ('riftsteel_original.png','Рифтсталь · сейчас'),('riftsteel_proposed.png','Рифтсталь · предложение')]
for index,(path,title) in enumerate(entries):
    x=15+index*448;put(overview,path,x,137,430,635)
    d.text((x+10,108),title,font=font(21,True),fill='#e3e9e8')
d.text((35,807),'Рендер Blender. Игровые предметы и характеристики пока не менялись.',font=font(21),fill='#b7c7cd')
overview.save(ROOT/'preview/armor_comparison_A01.png')
for tier,ru in [('riftsteel','РИФТСТАЛЬ'),('riftsilver','РАЗЛОМНОЕ СЕРЕБРО')]:
    review=canvas(1400,1060,ru+' · A01','Предложение объёмной формы · вид спереди и в три четверти')
    put(review,tier+'_front.png',20,115,670,820);put(review,tier+'_proposed.png',710,115,670,820)
    ImageDraw.Draw(review).text((35,985),'Модель для согласования; в Minecraft ещё не установлена.',font=font(23),fill='#b7c7cd')
    review.save(ROOT/'preview'/f'{tier}_review_A01.png')
print(ROOT/'preview/armor_comparison_A01.png')
