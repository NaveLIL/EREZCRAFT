from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
for filename in ('build_game_resources.py','prepare_native.py','native_tail.java.txt'):
    p=ROOT/'tools'/filename;p.write_text(p.read_text(encoding='utf-8').replace('E07','E08'),encoding='utf-8')
p=ROOT/'tools/run_native.py';p.write_text(p.read_text(encoding='utf-8').replace('native/E07','native/E08'),encoding='utf-8')
p=ROOT/'tools/verify_review.py';s=p.read_text(encoding='utf-8').replace('E06','E08').replace("'reports/blender.json'","'reports/blender_E08.json'");p.write_text(s,encoding='utf-8')
