from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'tools/verify_review.py'
s=p.read_text(encoding='utf-8').replace('E04','E06')
s=s.replace('Visual prototype; no in-game implementation or performance/motion verification','Game evidence is recorded separately in reports/native; this file validates authoring assets')
s=s.replace('Render uses the declared mild A-stance, not a tested player animation','Blender render uses a mild A-stance; native poses and lighting differ')
s=s.replace('No game stats, recipes, server or launcher changes','Iron statistics are a temporary visual-test baseline; no recipes, deployment or launcher changes')
p.write_text(s,encoding='utf-8')
