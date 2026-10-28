from pathlib import Path
r=Path(__file__).resolve().parent
for filename in ('build_game_resources.py','run_cloth_probe.py'):
    p=r/filename
    p.write_text(p.read_text(encoding='utf-8').replace('E05','E06'),encoding='utf-8')
p=r/'EngineerClothProbe.java';s=p.read_text(encoding='utf-8')
s=s.replace('if(n.contains(', 'if(q.get("bone").getAsString().endsWith("_leg") && (n.contains(',1)
s=s.replace('n.contains("waist_bridge"))points.add(q);','n.contains("waist_bridge")))points.add(q);')
p.write_text(s,encoding='utf-8')
