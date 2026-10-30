from pathlib import Path
from PIL import Image
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
check=json.loads((ROOT/'reports/SCENE_CHECK.json').read_text());assert check['saved_scene_reopened']
png=ROOT/'preview/two_seas_BN01_banner.png';im=Image.open(png);assert im.size==(3200,1400)
# Format conversion only. Scene composition and all lettering were rendered by Blender.
im.convert('RGB').save(ROOT/'exports/two_seas_BN01_banner.jpg',quality=96,subsampling=0)
files=['preview/two_seas_BN01_banner.png','exports/two_seas_BN01_banner.jpg','assembly/two_seas_BN01.blend',
    'source/banner_BN01.json','source/erezcraft-emblem.png','tools/build_banner.py','reports/SCENE_CHECK.json','README.md']
report={'revision':'BN01','medium':'Native Blender scene with editable geometry and typography','resolution':[3200,1400],
    'imagegen_used':False,'jpeg_is_format_conversion_only':True,'published':False,
    'files':[{'file':f,'bytes':(ROOT/f).stat().st_size,'sha256':hashlib.sha256((ROOT/f).read_bytes()).hexdigest()} for f in files]}
(ROOT/'reports/ARTIFACTS.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({'revision':'BN01','resolution':im.size,'files':len(files),'native_blender':True}))
