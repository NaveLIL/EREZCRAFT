"""Save handoff snapshots without touching the main agent's unfinished code."""
from pathlib import Path
import subprocess,json,hashlib,shutil,zipfile,datetime
ROOT=Path(__file__).resolve().parents[1];CREATIONS=ROOT.parent;GAME=Path('D:/repos/Minecraft/EREZCRAFT')
DEST=GAME/'art/sources/erez-studio-handoff';LOCAL=GAME/'art/generated/erez-studio-handoff';LOCAL.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
def git(*args):return subprocess.check_output(['git',*args],cwd=GAME).decode('utf-8').strip()
assert not git('diff','--cached','--name-only'),'Existing staged files must remain separate'
before={p:sha(GAME/p) for p in git('diff','--name-only','--diff-filter=AM').splitlines() if (GAME/p).is_file()}
snapshot={}
def copy(file,relative):
    out=DEST/relative;out.parent.mkdir(parents=True,exist_ok=True)
    if out.exists():assert out.read_bytes()==file.read_bytes(),str(out)
    else:shutil.copy2(file,out)
    snapshot[out.relative_to(GAME).as_posix()]=sha(out)

# T01 lacked a source/code folder; reconstruct its narrow source/resource delta from its recorded baseline.
p18=CREATIONS/'18_expedition_tools';cand=GAME/'.verification/et01';baseline=read(p18/'reports/baseline.json')['files'];baseline={p.replace('\\','/'):h for p,h in baseline.items()}
for file in (cand/'src/main').rglob('*'):
    if file.is_file():
        rel=file.relative_to(cand).as_posix()
        if baseline.get(rel)!=sha(file):copy(file,Path(p18.name)/'source/code'/rel)
for name in ('README.md','reports/game_review.json','reports/baseline.json'):
    file=p18/name
    if file.exists():copy(file,Path(p18.name)/name)

for name in ('19_realm_worktools','20_rift_engineer_armor'):
    project=CREATIONS/name
    for file in (project/'source/code').rglob('*'):
        if file.is_file():copy(file,Path(name)/file.relative_to(project))
    for rel in ('README.md','reports/game_review.json','reports/package.json','reports/baseline.json','reports/game_baseline.json','source/gameplay_changes.patch','source/game_changes.patch'):
        file=project/rel
        if file.exists():copy(file,Path(name)/rel)

for rel in ('source/armor_E11.json','source/armor_E11_parameters.json','source/armor_E11_atlas.png','source/engineer_gameplay_proposal.json','assembly/EREZ_Rift_Engineer_Armor_E11.blend','exports/EREZ_Rift_Engineer_Armor_E11.glb','reports/GAMEPLAY_PROPOSAL.md','reports/HANDOFF_TO_MAIN_AGENT.md','reports/gameplay_design_review.json','reports/review_verification.json','reports/blender_E11.json','reports/native/run_07/run.json','reports/native/run_07/EngineerNative.java','reports/gametests/runProgressionGameTestServer.json','reports/gametests/source-sha256.json','reports/cloth/run_05/result.json','reports/cloth/run_05/run.json','preview/engineer_E11_front_back.png','preview/native/E11/engineer-wide-close.png','preview/native/E11/engineer_motion_E11.mp4'):
    copy(ROOT/rel,Path(ROOT.name)/rel)
for file in (ROOT/'source/materials').glob('*'):
    if file.is_file():copy(file,Path(ROOT.name)/file.relative_to(ROOT))
for file in (ROOT/'source').glob('palette_E11_*.png'):copy(file,Path(ROOT.name)/file.relative_to(ROOT))

# Full private local archive retains all nine authored Minecraft projects, including historic/rejected work.
# Third-party runtime dependencies and interpreter caches can be restored separately; original folders stay intact.
projects=[p for p in sorted(CREATIONS.iterdir()) if p.is_dir() and any(p.name.startswith(str(i)+'_') for i in range(12,21))]
files=[f for p in projects for f in p.rglob('*') if f.is_file() and not any(s in f.parts for s in ('.deps','.video_deps','__pycache__')) and f.suffix!='.pyc']
assert shutil.disk_usage(LOCAL).free>sum(f.stat().st_size for f in files)+100_000_000,'Insufficient space for a complete local archive'
archive=LOCAL/'EREZ_STUDIO_2026-10-27_FULL.zip';temporary=archive.with_suffix('.zip.tmp');inventory={}
with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=1) as z:
    for file in files:
        rel=file.relative_to(CREATIONS).as_posix();inventory[rel]={'bytes':file.stat().st_size,'sha256':sha(file)};z.write(file,rel)
    z.writestr('ARCHIVE_MANIFEST.json',json.dumps(inventory,ensure_ascii=False,indent=2))
with zipfile.ZipFile(temporary) as z:assert z.testzip() is None
temporary.replace(archive)

doc=(ROOT/'reports/HANDOFF_TO_MAIN_AGENT.md').read_text(encoding='utf-8')
doc+='\n## Локальное сохранение передачи\n\nGit-снимок исходников для узкого слияния: `art/sources/erez-studio-handoff/` в этом репозитории. Полный приватный архив проектов12–20: `art/generated/erez-studio-handoff/EREZ_STUDIO_2026-10-27_FULL.zip` (локальный, намеренно игнорируется Git). Внутри есть `ARCHIVE_MANIFEST.json` с хешем каждого файла. Зависимости Python/FFmpeg и кэши интерпретатора не включены; исходные папки Blender сохранены полностью. Старые архивы/кандидаты являются историей: при работе ориентироваться на E11/D02 и актуальные отчёты, не устанавливать отклонённый скин.\n'
(GAME/'docs/HANDOFF_EREZ_STUDIO.md').write_text(doc,encoding='utf-8')
manifest={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'primary_head_before_handoff':git('rev-parse','HEAD'),'canonical_art_root':str(CREATIONS),'latest_visual':'E11','latest_mechanics_design':'D02_closed_breathing_and_realm_harvesting','mechanics_implemented':False,'latest_jar_sha256':sha(ROOT/'exports/interstice-0.7.0-preview.24-engineer.jar'),'archive':{'path':str(archive),'sha256':sha(archive),'bytes':archive.stat().st_size,'files':len(files),'git_ignored':True},'snapshot_files':snapshot,'existing_modified_file_sha256':before,'primary_code_changes_applied':False,'server_or_launcher_changed':False}
for p,h in before.items():assert sha(GAME/p)==h,('Primary WIP changed',p)
(GAME/'docs/verification/EREZ_STUDIO_HANDOFF.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'reports/local_handoff.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'snapshot_files':len(snapshot),'archive_bytes':archive.stat().st_size,'archived_files':len(files),'existing_wip_files_preserved':len(before),'handoff':str(GAME/'docs/HANDOFF_EREZ_STUDIO.md')}))
