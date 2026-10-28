"""Collect real execution receipts and audit the preserved original/unrelated files."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');C=GAME/'.verification/ls03'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
before=read(ROOT/'reports/integration_baseline/source_sha256.json')
candidate=read(ROOT/'reports/candidate.json')
allowed=set(candidate['changed_existing'])|{'docs/HANDOFF.md'}
unexpected=[name for name,digest in before.items() if name not in allowed and sha(GAME/name)!=digest]
assert not unexpected,unexpected
pngs=[name for name in before if name.startswith('src/main/resources/assets/interstice/textures/') and name.endswith('.png')]
assert all(sha(GAME/name)==before[name] for name in pngs)
old_model='src/main/resources/assets/interstice/models/item/riftsteel_sword.json'
old_recipe='src/main/resources/data/interstice/recipe/riftsteel_sword.json'
assert all(sha(GAME/name)==before[name] for name in (old_model,old_recipe))
components=C/'.verification/20261027T035921.492540Z-longsword-components-fixed'
native=C/'.verification/20261027T040135.612048Z-longsword-native'
assert read(components/'summary.json')['passed'] and read(native/'summary.json')['passed']
component_receipt=read(components/'runProgressionGameTestServer.json')
native_receipt=read(native/'runProgressionNativeSmoke.json')
package=read(ROOT/'reports/package.json')
profiles=C/'.verification/ep-0e7a7c66/progressionNative'
proofs={mode:read(profiles/f'progression-{mode}.json') for mode in ('create','reload')}
assert all(p['passed'] and p['distant_horizons_loaded'] and p['models_checked']==19 and p['longsword_baked_quads']==704 for p in proofs.values())
assert proofs['create']['pid']!=proofs['reload']['pid'] and proofs['reload']['cold_distinct_swords']
jar=Path(package['jar'])
with zipfile.ZipFile(jar) as z:
    # Git's Windows checkout may change JSON line endings; model contents must match.
    assert json.loads(z.read('assets/interstice/models/item/riftsteel_sword.json'))==read(GAME/old_model)
    assert z.read('assets/interstice/models/item/riftsteel_longsword.json')==(GAME/'src/main/resources/assets/interstice/models/item/riftsteel_longsword.json').read_bytes()
    recipe=json.loads(z.read('data/interstice/recipe/riftsteel_longsword.json'))
    assert sum(row.count('M') for row in recipe['pattern'])==4
shots=ROOT/'preview/native';shots.mkdir(exist_ok=True)
for filename in ('progression-create-equipment.png','progression-create-longsword-firstperson.png',
                 'progression-create-longsword-thirdperson.png','progression-create-original-sword-firstperson.png'):
    shutil.copy2(profiles/'screenshots'/filename,shots/filename)
report={'status':'local_verified_candidate','item_ids':['interstice:riftsteel_sword','interstice:riftsteel_longsword'],
        'balance':{'original':{'damage':7,'ingots':2},'longsword':{'damage':11,'ingots':4},
                   'both':{'attack_speed':1.6,'durability':1792,'native_sticks':1,'reach':'unchanged'}},
        'source_commit':candidate['base_commit'],'isolated_candidate':str(C),
        'component_tests':{'passed':True,'required_tests_passed':component_receipt.get('required_tests_passed'),
                           'receipt':str(components/'runProgressionGameTestServer.json')},
        'native_tests':{'passed':True,'receipt':str(native/'runProgressionNativeSmoke.json'),'proofs':proofs},
        'packaging':package,
        'preservation':{'unrelated_baseline_files_preserved':True,'unexpected_changes':unexpected,
                        'original_sword_model_and_recipe_byte_identical':True,'existing_texture_pngs_unchanged':len(pngs),
                        'source_audit_baseline':str(ROOT/'reports/integration_baseline/source_sha256.json')},
        'limits':['Prepared native test world, not unassisted Survival balance acceptance.',
                  'Production server, launcher catalogue and owner game profiles were not updated.',
                  'Final JAR differs from native-tested payload only in its mod-version string; entry equality verified.']}
for target in (ROOT/'reports/integration.json',GAME/'docs/verification/LONGSWORD.json'):
    target.parent.mkdir(exist_ok=True,parents=True)
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'jar':jar.name,'component_tests':report['component_tests'],'native_passed':True,
                  'original_unchanged':True,'unrelated_files_preserved':True,'pngs_preserved':len(pngs)},ensure_ascii=False))
