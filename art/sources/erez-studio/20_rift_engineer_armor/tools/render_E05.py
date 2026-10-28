from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=(ROOT/'tools/build_blender.py').read_text(encoding='utf-8');s=s[s.index('# Re-import only the frozen mesh:'):]
s=s.replace('E04','E05')
prefix='''from pathlib import Path
import bpy,json,math,hashlib
from mathutils import Vector,Euler,Matrix
ROOT=Path(__file__).resolve().parents[1]
sha=lambda b:hashlib.sha256(b).hexdigest()
author=json.loads((ROOT/'source/armor_E05.json').read_text(encoding='utf-8'))
frozen=author['parts'];data=author;ATLAS=author['atlas']['width'];cursor=[0,201,0]
for obj in list(bpy.data.objects):bpy.data.objects.remove(obj,do_unlink=True)
'''
exec(compile(prefix+s,str(ROOT/'tools/render_E05.py'),'exec'),globals())
