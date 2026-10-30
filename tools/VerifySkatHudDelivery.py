"""Bounded feature acceptance for the speed HUD and opaque sea/glass ordering."""
from pathlib import Path
import hashlib,json,zipfile,math,shutil
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'.verification/skat-hud-release-20261029'
PROFILE=ROOT/'.verification/sk97n1/studioSkat'
VERSION='0.7.0-preview.100-skat-speed'
read=lambda p:json.loads(Path(p).read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert PROFILE.is_relative_to(ROOT/'.verification') and not (PROFILE/'skat-owner-profile').exists()
speed=read(PROFILE/'skat-speed.json');glass=read(PROFILE/'skat-glass.json')
assert speed['version']==glass['version']==VERSION
assert speed['passed_runtime'] and speed['hud_captured'] and .05<speed['hud_max_speed_mps']<=64
assert speed['structure_cells_checked']==1509 and speed['cargo_containers']==24
assert glass['passed'] and glass['process_exit_code']==0 and glass['glass']['passed']
assert glass['dh_loaded'] and glass['glass']['dry_hull_cells']>0 and glass['glass']['normal_capture_no_night_vision']
assert len(glass['glass']['cases'])==3 and glass['glass']['restored_views']>0
assert 'enableShaders=false' in (PROFILE/'config/iris.properties').read_text()
units=read(STAGE/'units-raw.json');assert units['passed']==8 and units['units']=='m/s'
jar=ROOT/f'build/libs/interstice-{VERSION}.jar';base=ROOT/'build/libs/interstice-0.7.0-preview.98-skat-server.jar'
with zipfile.ZipFile(base) as old,zipfile.ZipFile(jar) as new:
 old_names=set(old.namelist());new_names=set(new.namelist())
 changed=[n for n in sorted(old_names&new_names) if old.read(n)!=new.read(n)]
 added=sorted(new_names-old_names);removed=sorted(old_names-new_names)
 assert not removed
 allowed={'META-INF/neoforge.mods.toml','assets/interstice/lang/ru_ru.json','assets/interstice/lang/en_us.json','pro/erez/interstice/ship/SkatHelmEntity.class','pro/erez/interstice/ship/SkatNetwork.class'}
 assert all(n in allowed or n.startswith(('pro/erez/interstice/client/LowerToxinRenderer','pro/erez/interstice/client/SkatClient','pro/erez/interstice/ship/SkatNetwork$')) for n in changed),changed
 assert set(added)=={'pro/erez/interstice/ship/SkatSpeedometer.class','pro/erez/interstice/ship/SkatNetwork$Speed.class'},added
 for lang in ['ru_ru','en_us']:
  path=f'assets/interstice/lang/{lang}.json';a=json.loads(old.read(path));b=json.loads(new.read(path))
  assert all(b[k]==v for k,v in a.items())
  assert set(b)-set(a)=={f'hud.interstice.skat.{k}' for k in ['title','speed','controls','fuel']}
 preserved_assets=sum(n.startswith(('assets/','data/')) and n not in changed for n in old_names)
 matched=0
 for tree in ['build/classes/java/main','build/resources/main']:
  p=ROOT/tree
  for actual in p.rglob('*'):
   if actual.is_file():assert new.read(actual.relative_to(p).as_posix())==actual.read_bytes(),str(actual);matched+=1
photos=[];destination=ROOT/'docs/verification/skat-hud';destination.mkdir(exist_ok=True)
for name in ['skat-speed-hud','skat-glass-port-before','skat-glass-port-after','skat-glass-front-before','skat-glass-front-after','skat-glass-starboard-before','skat-glass-starboard-after','skat-glass-normal-no-night-vision']:
 source=PROFILE/'screenshots'/(name+'.png');target=destination/(name+'.png');shutil.copyfile(source,target)
 photos.append({'file':target.relative_to(ROOT).as_posix(),'sha256':sha(target),'unmodified_capture':True,'night_vision':name!='skat-glass-normal-no-night-vision'})
report={'scope':'Ground-speed HUD and near opaque sea visibility through actual SK03 glass, shaders OFF. Not a new full-ship/Survival/multiplayer acceptance.','candidate':VERSION,'jar_sha256':sha(jar),'native':{'passed':True,'speed':speed,'glass':glass['glass']},'glass_run':glass,'unit_checks':units,'changed_entries':changed,'added_entries':added,'preserved_asset_data_entries':preserved_assets,'runtime_files_match_jar':matched,'screenshots':photos,'production_changed':False,'backups_created':False,'owner_profile_modified':False,'limitations':['Initial disconnection cause unconfirmed: server recorded Disconnected, allow-flight already true, no movement checks disabled.','An expanded native run failed standing stair ascent in unchanged geometry; retained in speed100_01_full-probe.json. This feature-specific acceptance does not close that finding.','Speed captured during short acceleration runs; no steady cruise speed or propulsion balance change.','Complementary was not retested in this patch; owner corrected shader state to OFF.']}
STAGE.mkdir(exist_ok=True);(STAGE/'acceptance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(STAGE/'components.json').write_text(json.dumps({'passed':True,'checks':8,'scope':'pure speedometer units/identity/stale state/teleport contracts'}),encoding='utf8')
info={'passed':True,'allUpdatesIncluded':True,'modVersion':VERSION,'mod_version':VERSION,'jar':str(jar),'jarSha256':sha(jar),'jar_sha256':sha(jar),'native':str(STAGE/'acceptance.json'),'components':str(STAGE/'components.json'),'features':['actual_ground_speed_hud','opaque_ocean_before_translucent_glass']}
(STAGE/'input.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf8');(ROOT/'.verification/skat-hud-release-input.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf8')
(ROOT/'docs/verification/SKAT_HUD_GLASS.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'passed':True,'candidate':VERSION,'jar_sha256':sha(jar),'changed':changed,'added':added,'preserved_asset_data_entries':preserved_assets,'photos':len(photos)}))
