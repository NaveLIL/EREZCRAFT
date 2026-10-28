from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
for name in ('tools/native_skin.java.txt','tools/save_blender.py'):
    p=ROOT/name;p.write_text(p.read_text(encoding='utf-8').replace('S02','S03'),encoding='utf-8')
