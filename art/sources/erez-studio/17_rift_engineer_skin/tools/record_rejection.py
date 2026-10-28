from pathlib import Path
import datetime,json
ROOT=Path(__file__).resolve().parents[1]
decision={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'status':'3D S02/S03 rejected by owner; discontinued, not for installation or release',
 'user_reason':'The real 3D avatar is not close to the liked visual reference; owner prefers 2D skins',
 'new_direction':'Standard 2D skins. Review actual texture on an ordinary Minecraft player model, not illustrative concept art.',
 'server_changed':False,'launcher_release_published':False,'account_skin_changed':False,
 'historical_artifacts':'Preserved for reference; not approved deliverables'}
(ROOT/'reports/OWNER_DECISION.json').write_text(json.dumps(decision,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
p=ROOT/'source/requirements.json';r=json.loads(p.read_text(encoding='utf-8'));r.update(revision='3D direction rejected; stopped',target='Future work: actual standard 2D Minecraft skin',visual_direction_approved=False,approval='S01 illustration liked, but actual S02/S03 rejected; do not publish or install')
p.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
p=ROOT/'reports/delivery_preparation.json';r=json.loads(p.read_text());r.update(status='Withdrawn after owner rejected 3D direction',release_allowed=False);p.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8')
p=ROOT/'README.md';text=p.read_text(encoding='utf-8');p.write_text('# Отклонено владельцем — 3D-направление закрыто\n\nS02/S03 не приняты. Эти файлы сохранены как история и не предназначены для установки или выпуска. На сервер, в лаунчер и в аккаунт владельца ничего не устанавливалось. Следующее направление — обычные 2D-скины с показом настоящей текстуры на стандартной модели игрока. Решение: reports/OWNER_DECISION.json.\n\n---\n\n'+text,encoding='utf-8')
p=ROOT/'reports/visual-review.html';s=p.read_text(encoding='utf-8').replace('<main>','<main><p style="color:#ffb2a9;font-weight:bold">ОТКЛОНЕНО ВЛАДЕЛЬЦЕМ. 3D-направление закрыто; эти материалы не предназначены для выпуска.</p>');p.write_text(s,encoding='utf-8')
print('3D direction withdrawn; no live changes to undo')
