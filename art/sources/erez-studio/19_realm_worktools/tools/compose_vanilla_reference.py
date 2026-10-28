from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib
ROOT=Path(__file__).resolve().parents[1];P=ROOT/'preview/vanilla_reference';R=ROOT/'reports/vanilla_reference'
font_path=r'C:/Windows/Fonts/segoeui.ttf';font=lambda n:ImageFont.truetype(font_path,n)
run=json.loads((R/'run.json').read_text(encoding='utf8'));assert run['passed']
CROP=(540,180,1380,1020);CELL=420;PAD=18;HEADER=100;FOOT=42
labels=['Ваниль — железо','WT04 — серебро','WT04 — рифтсталь'];tiers=['iron','riftsilver','riftsteel'];titles={'pickaxe':'Кирка','axe':'Топор','shovel':'Лопата','hoe':'Мотыга'}
metrics={r['item']:r for r in run['proof']['native_metrics']};rows=[];cards={}
for kind in titles:
 card=Image.new('RGB',(3*CELL+4*PAD,HEADER+CELL+FOOT),(20,29,38));d=ImageDraw.Draw(card);d.text((PAD,10),titles[kind]+' — один игрок и одна камера',font=font(27),fill=(236,244,248))
 for i,tier in enumerate(tiers):
  name='reference-held-'+tier+'_'+kind+'.png';src=P/name
  with Image.open(src) as im:
   assert im.size==(1920,1080);pic=im.crop(CROP).resize((CELL,CELL),Image.Resampling.LANCZOS)
  x=PAD+i*(CELL+PAD);d.text((x,57),labels[i],font=font(22),fill=(193,218,229));card.paste(pic,(x,HEADER))
 d.text((PAD,HEADER+CELL+8),'Одинаковые кадрирование и масштаб; модели показаны в своих игровых положениях.',font=font(17),fill=(168,191,205))
 file=P/('comparison_'+kind+'.png');card.save(file);cards[kind]=card
 base=metrics['iron_'+kind]['THIRD_PERSON_RIGHT_HAND']['opaque_geometry_diameter_model_units'];steel=metrics['riftsteel_'+kind]['THIRD_PERSON_RIGHT_HAND']['opaque_geometry_diameter_model_units']
 rows.append({'kind':kind,'vanilla_display_diameter':base,'WT04_riftsteel_display_diameter':steel,'WT04_percent_of_vanilla':round(100*steel/base,1),'same_body_same_camera':True})
combo=Image.new('RGB',(cards['shovel'].width,cards['shovel'].height+cards['hoe'].height+12),(20,29,38));combo.paste(cards['shovel'],(0,0));combo.paste(cards['hoe'],(0,cards['shovel'].height+12));combo.save(P/'comparison_shovel_hoe.png')
report={'passed':True,'purpose':'Native vanilla-vs-WT04 proportional reference; no production model changes','rows':rows,'same_crop_for_every_held_frame':CROP,'same_resize_for_every_frame':[CELL,CELL],'native_screenshots':len(run['screenshots']),'models_changed':False,'measurement_note':'Maximum pairwise distance between opaque-boundary baked vertices after actual display transform; a whole-tool linear-size measure, not shaft length, pixel area, or block reach.','native_evidence':'run.json','static_vanilla_provenance':'static-reference.json','figures':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in P.glob('comparison_*.png')}}
(R/'summary.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report['rows']))
