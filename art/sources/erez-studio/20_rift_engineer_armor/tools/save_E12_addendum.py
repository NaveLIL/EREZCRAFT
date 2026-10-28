from pathlib import Path
import json,shutil,hashlib,subprocess
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT')
DEST=GAME/'art/sources/erez-studio-handoff/20_rift_engineer_armor_E12'
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
review=read(ROOT/'reports/game_review.json');assert review['revision']=='E12' and review['native']['passed']
snapshot={}
files=[p for p in (ROOT/'source/code').rglob('*') if p.is_file()]
files += [ROOT/p for p in ('README.md','source/armor_E12.json','source/armor_E12_parameters.json','source/armor_E12_atlas.png','source/engineer_gameplay_proposal.json','source/game_changes.patch','assembly/EREZ_Rift_Engineer_Armor_E12.blend','exports/EREZ_Rift_Engineer_Armor_E12.glb','reports/game_review.json','reports/package.json','reports/belt_E12_verification.json','reports/review_verification.json','reports/blender_E12.json','reports/GAMEPLAY_PROPOSAL.md','reports/gameplay_design_review.json','reports/native/run_08/run.json','reports/native/run_08/EngineerNative.java','preview/engineer_E12_front_back.png','preview/native/E12/engineer-wide-close.png','preview/native/E12/engineer_motion_E12.mp4','tools/build_E12.py','tools/verify_belt_E12.py')]
for file in files:
    rel=file.relative_to(ROOT);out=DEST/rel;out.parent.mkdir(parents=True,exist_ok=True)
    if out.exists():assert out.read_bytes()==file.read_bytes(),str(out)
    else:shutil.copy2(file,out)
    snapshot[out.relative_to(GAME).as_posix()]=sha(out)
local=GAME/'art/generated/erez-studio-handoff/EREZ_ENGINEER_E12_ADDENDUM.zip';shutil.copy2(ROOT/'exports/EREZ_Rift_Engineer_Armor_E12_review.zip',local)
doc='''# Дополнение к передаче — E12, кожаный пояс

После исходного коммита ef399dd владелец заметил, что пояс выглядит тканевым. Материал уже был кожаным, но поверхность перекрывали перемычки пальто. В E12 пояс выведен наружу, пряжка, подсумки и задние крепления перенесены к новой поверхности. Механика и геометрия пол сохранены. Передняя звезда по-прежнему удалена, задняя сохранена.

**Актуальная внешность: E12. Новый отдельный JAR: `interstice-0.7.0-preview.25-engineer-belt.jar`.** E11/JAR24 и исходная передача сохраняются как предыдущий проверенный снимок. Кандидат `.verification/ea01` теперь содержит E12/preview.25.

Канонические файлы: `D:/repos/Blender/creations/20_rift_engineer_armor/`:

- `source/armor_E12.json` — авторитетная геометрия; рядом параметры и атлас.
- `assembly/EREZ_Rift_Engineer_Armor_E12.blend` — реальный Blender; `exports/` — GLB, JAR25, sources и новый review.zip.
- `reports/native/run_08/run.json` — прошедший полноценный клиент с DH/106 модами; `preview/native/E12/` — исходные кадры и видео.
- `reports/belt_E12_verification.json` — пояс находится снаружи перемычек, изменены только19 деталей поясного узла, полы не изменены.
- `reports/game_review.json` — итоговые хеши и привязка к предыдущим43 GameTest с неизменными269 runtime Java-исходниками. Новых механик этот прогон не подтверждает.

Git-снимок для интеграции: `art/sources/erez-studio-handoff/20_rift_engineer_armor_E12/`. Локальный архив дополнения: `art/generated/erez-studio-handoff/EREZ_ENGINEER_E12_ADDENDUM.zip`, намеренно игнорируется Git. Исходный полный архив проектов12–20 остаётся снимком до этой правки и дополняется новым архивом E12.

**D02 не изменён:** самозарядка, нейроинтерфейс и замкнутое дыхание по-прежнему спроектированы, но не реализованы. Предметы пока имеют железные тестовые характеристики. Основной runtime-код, фауна, миры, сервер и лаунчер не менялись.

'''
doc+='JAR25 SHA256: `'+review['jar_sha256']+'`.\n'
(GAME/'docs/HANDOFF_EREZ_STUDIO_E12.md').write_text(doc,encoding='utf-8')
entry=GAME/'docs/HANDOFF_EREZ_STUDIO.md'
clean=subprocess.run(['git','diff','--quiet','HEAD','--',str(entry.relative_to(GAME))],cwd=GAME).returncode==0
entry_updated=False
if clean:
    text=entry.read_text(encoding='utf-8');entry.write_text('> **Дополнение E12:** актуальная версия с исправленным кожаным поясом —preview.25-engineer-belt. Сначала прочитать [HANDOFF_EREZ_STUDIO_E12.md](HANDOFF_EREZ_STUDIO_E12.md). Ниже сохранена первоначальная передача E11/D02.\n\n'+text,encoding='utf-8');entry_updated=True
manifest={'revision':'E12','version':review['version'],'jar_sha256':review['jar_sha256'],'native_passed':True,'native_evidence':'reports/native/run_08/run.json','geometry_fix':review['belt_fix'],'snapshot_files':snapshot,'local_addendum':{'path':str(local),'sha256':sha(local),'bytes':local.stat().st_size},'old_E11_preserved':True,'D02_gameplay_implemented':False,'main_entry_pointer_updated':entry_updated}
(GAME/'docs/verification/EREZ_STUDIO_E12.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'reports/local_handoff_E12.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
paths=['docs/HANDOFF_EREZ_STUDIO_E12.md','docs/verification/EREZ_STUDIO_E12.json','art/sources/erez-studio-handoff/20_rift_engineer_armor_E12']
if entry_updated:paths.append('docs/HANDOFF_EREZ_STUDIO.md')
subprocess.run(['git','add','--',*paths],cwd=GAME,check=True)
args=['git'];ident=subprocess.run(['git','var','GIT_AUTHOR_IDENT'],cwd=GAME,capture_output=True)
if ident.returncode:args+=['-c','user.name=Codex','-c','user.email=codex@localhost']
result=subprocess.run(args+['commit','--only','-m','Save E12 leather belt fix and handoff addendum','--',*paths],cwd=GAME,capture_output=True,text=True,encoding='utf-8',errors='replace');assert result.returncode==0,result.stderr
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=GAME,text=True).strip()
(ROOT/'reports/local_handoff_E12_commit.json').write_text(json.dumps({'commit':commit,'no_push':True,'paths':paths},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'commit':commit,'snapshot_files':len(snapshot),'native_passed':True,'primary_runtime_unchanged':True,'old_E11_preserved':True}))
