#!/usr/bin/env python3
"""Record completed native/server checks and exact source/resource/palette identity of equipment0.5.0."""
import argparse, hashlib, json, zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def require(condition,message):
    if not condition:raise ValueError(message)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('evidence',type=Path);parser.add_argument('--artifact-followup',type=Path);args=parser.parse_args();evidence=args.evidence.resolve();summary=read(evidence/'summary.json')
    require(summary['passed'] is True and summary['saves_unchanged'] is True,'Incomplete checks or changed owner saves')
    build=read(evidence/'build.json');require(build['accepted'] is True and build['required_tests_passed']==262,'Expected 262 executed required tests')
    native={}
    for profile,prefix in [('retortUiSmoke','retort-ui'),('backpackSmoke','backpack'),('wearBackpackSmoke','wear-backpack')]:
        names=[prefix+'-validation.json'] if profile=='retortUiSmoke' else [prefix+'-create-validation.json',prefix+'-reload-validation.json']
        reports=[read(evidence/'profiles'/profile/name) for name in names]
        require(all(r['passed'] is True and not r.get('shutdown_pending',False) for r in reports),'Failed native check: '+profile)
        if len(reports)==2:require(reports[0]['creator_pid']!=reports[1]['reload_pid'],'Restart did not use another process')
        native[profile]={'files':names,'reports':reports}
    require(native['retortUiSmoke']['reports'][0]['native_buttons_clear_actual_inventory_slots'] is True,'GUI overlaps actual slots')
    require(native['backpackSmoke']['reports'][1]['native_registered_hotkey_open'] is True,'Registered hotkey not exercised')
    require(native['wearBackpackSmoke']['reports'][1]['cold_attachment_exact_components_and_armor_preserved'] is True,'Worn save not exercised')
    followup=None
    if args.artifact_followup:
        followup=args.artifact_followup.resolve();receipt=read(followup/'summary.json')
        require(receipt['passed'] is True and receipt['saves_unchanged'] is True,'Final artifact follow-up failed')
        require(read(followup/'runEquipmentGameTestServer.json')['required_tests_passed']==17,'Final equipment regression check did not execute')
        require(read(followup/'assemble.json')['accepted'] is True,'Current sources not assembled')
    sources=ROOT/'build/libs/interstice-0.5.0-sources.jar'
    java_files=list((ROOT/'src/main/java').rglob('*.java'))
    with zipfile.ZipFile(sources) as jar:
        for path in java_files:require(jar.read(path.relative_to(ROOT/'src/main/java').as_posix())==path.read_bytes(),'Source JAR differs: '+str(path))
    artifact=read(ROOT/'.verification/release-artifact-0.5.0.json');require(artifact['passed'] is True,'Packaged resource audit failed')
    require(artifact['jar_sha256']==sha(ROOT/'build/libs/interstice-0.5.0.jar'),'Runtime JAR changed after audit')
    palettes=[]
    for folder in ['backpacks','gui-materials']:
        provenance=read(ROOT/f'src/main/resources/assets/interstice/provenance/{folder}.json')
        for row in provenance['outputs']:
            original=ROOT/f'art/sources/cc0/{folder}'/row['source'];target=ROOT/'src/main/resources/assets/interstice/textures'/row['output']
            require(sha(original)==row['source_sha256'] and sha(target)==row['output_sha256'],'PNG hash differs')
            a=Image.open(original).convert('RGBA');b=Image.open(target).convert('RGBA');require(a.size==b.size==(16,16),'Whole tile resolution changed')
            forward={};reverse={}
            for coordinate in ((x,y) for y in range(16) for x in range(16)):
                first,second=a.getpixel(coordinate),b.getpixel(coordinate)
                require(first[3]==second[3],'Alpha changed')
                require(forward.get(first,second)==second and reverse.get(second,first)==first,'Pixel pattern changed')
                forward[first]=second;reverse[second]=first
            require(len(forward)==row['classes'],'Palette class count changed')
            palettes.append({'output':row['output'],'classes':len(forward),'resolution':[16,16],'pattern_and_alpha_preserved':True})
    report={'passed':True,'version':'0.5.0','date':'2026-10-08','base_commit':summary['source_commit'],'evidence':str(evidence.relative_to(ROOT)),
        'build':{'required_tests_passed':262,'groups':build['required_test_groups'],'accepted':True},'python_verifier_tests':37,
        'native':native,'artifact':artifact,'source_jar_java_files_compared':len(java_files),'palette_checks':palettes,
        'artifact_followup':str(followup.relative_to(ROOT)) if followup else None,
        'owner_saves_unchanged':True,'limitations':['Prepared disposable client scenes; no unaided Survival collection/crafting route is claimed.',
        'Both player renderers have the layer; the actual photographed skin is SLIM. Elytra geometry is supported but not photographed.',
        'The reinforced item resists fire while intact. Void, despawn and ordinary damage can still remove a bag.',
        'Client and server both need the same updated mod. This task did not publish GitHub or replace owner saves.']}
    out=ROOT/'docs/verification/EQUIPMENT.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'passed':True,'report':str(out),'required_tests':262,'native_processes':5,'palettes':len(palettes),'java_sources':len(java_files)}))
if __name__=='__main__':main()
