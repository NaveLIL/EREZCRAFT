from pathlib import Path
from PIL import Image
import hashlib,json
R=Path(__file__).resolve().parents[1];S=R/'art/materials/lower-ocean-oc01/source';T=R/'src/main/resources/assets/interstice/textures/block';P=R/'src/main/resources/assets/interstice/provenance'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
frames=[];sources=[]
for i in range(16):
 p=S/'frames'/f'loop_{i:02d}.png';image=Image.open(p).convert('RGBA');assert image.size==(640,640);assert image.getextrema()[3]==(255,255)
 frame=Image.new('RGBA',(1280,1280))
 for y in (0,640):
  for x in (0,640):frame.paste(image,(x,y));assert frame.crop((x,y,x+640,y+640)).tobytes()==image.tobytes()
 frames.append(frame);sources.append({'file':str(p.relative_to(R)).replace('\\','/'),'png_sha256':sha(p),'rgba_sha256':hashlib.sha256(image.tobytes()).hexdigest()})
atlas=Image.new('RGBA',(1280,1280*16))
for i,image in enumerate(frames):atlas.paste(image,(0,i*1280))
T.mkdir(parents=True,exist_ok=True);out=T/'heavy_oc01.png';atlas.save(out,optimize=True)
(out.with_suffix('.png.mcmeta')).write_text(json.dumps({'animation':{'frametime':20,'interpolate':True,'width':1280,'height':1280}})+'\n',encoding='utf8')
P.mkdir(parents=True,exist_ok=True);record={'revision':'OC01','source_project':'Blender/creations/26_lower_ocean','author':'EREZCRAFT studio','material_parameters_sha256':sha(S/'material_OC01.json'),'source_build_script_sha256':sha(S/'build_material.py'),'sources':sources,'runtime_png_sha256':sha(out),'frame_size':[1280,1280],'source_tile_size':[640,640],'frames':16,'period_ticks':320,'period_seconds':16,'world_period_blocks':64,'source_texels_per_block':10,'packing':'Each original unlit material frame copied exactly four times; no resize, repaint, colour or alpha change','uv_projection':'u=X-0.5*(Y-(lowerSeaTop+8/9)), v=Z-(Y-(lowerSeaTop+8/9)); one shared world field across faces','sampling':'Quad-local period origin with duplicate tile gutter; exact shared phase across block/chunk boundaries','scope':'Material colour only. Blender roughness, bump and scene lighting are not baked into this albedo. Concept illustration not used as texture.'}
(P/'lower-ocean-oc01.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps({'passed':True,'png_bytes':out.stat().st_size,'frames':16,'sha256':record['runtime_png_sha256']}))
