#!/usr/bin/env python3
"""Collect completed native/GT/artifact receipts; copy original game frames without alteration."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def main():
    full=ROOT/'.verification/20261008T141439.229342Z-agriculture-release-first'
    focused=ROOT/'.verification/20261008T145059.852763Z-agriculture-guide-api-fixed'
    native=ROOT/'.verification/20261008T141427.514716Z-agriculture-native-api-fixed'
    props=ROOT/'.verification/20261008T145112.262071Z-agriculture-props-api-fixed'
    assert read(full/'build.json')['required_tests_passed']==237
    assert read(focused/'runAgricultureGameTestServer.json')['required_tests_passed']==24
    assert all(read(p/'summary.json')['passed'] for p in [full,focused,native,props])
    artifact=read(ROOT/'.verification/release-artifact-0.4.0.json');assets=read(ROOT/'.verification/agriculture-resource-audit.json')
    assert artifact['passed'] and assets['passed']
    target=ROOT/'art/generated/agriculture';target.mkdir(parents=True,exist_ok=True)
    screenshots=[]
    for location,profile,files in [(native,'agricultureSmoke',['agriculture-ready-crops.png','agriculture-retort-running.png','agriculture-food-purification.png','agriculture-purified-meal.png']),
                                   (props,'agriculturePropsSmoke',['agriculture-props-clear.png','agriculture-props-dark.png'])]:
        for name in files:shutil.copy2(location/'profiles'/profile/'screenshots'/name,target/name);screenshots.append(str((target/name).relative_to(ROOT)))
    report={'version':'0.4.0','passed':True,'jar_sha256':artifact['jar_sha256'],'jar':'build/libs/interstice-0.4.0.jar',
            'unique_required_game_tests_passed':238,'full_build_game_tests':237,'final_agriculture_group':24,
            'test_count_explanation':'Full237 passed; after data-only construction discovery additions, focused group24 replaces prior agriculture23. Other214 tests unchanged.',
            'python_verifier_tests':33,'models_checked':artifact['models_checked'],'resources_compared':artifact['resources_compared'],
            'ready_png_palettes_checked':assets['palette_count'],'source_files':assets['source_count'],
            'full_build_receipt':str(full.relative_to(ROOT)),'final_data_and_package_receipt':str(focused.relative_to(ROOT)),
            'native_cold_receipt':str(native.relative_to(ROOT)),'native_props_receipt':str(props.relative_to(ROOT)),
            'native_cold_create':read(native/'profiles/agricultureSmoke/agriculture-create-validation.json'),
            'native_cold_reload':read(native/'profiles/agricultureSmoke/agriculture-reload-validation.json'),
            'native_props':read(props/'profiles/agriculturePropsSmoke/agriculture-props-validation.json'),
            'screenshots':screenshots,'user_saves_unchanged_in_final_runs':all(read(p/'summary.json')['saves_unchanged'] is True for p in [full,focused,native,props]),
            'scope':'Prepared disposable Survival inventory/platform/display and supplemental flour. Ordinary client interactions, actual processing and eating, cold restart. Prop gallery is Creative. Not autonomous Survival progression.',
            'not_implemented':['native race/transformation','potato and additional crop families','root stew','gas mask','automatic fluid pipe networks','direct wall-lantern attachment']}
    (ROOT/'docs/verification/AGRICULTURE.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(f'0.4.0:238 unique GTs,33 Python checks,3 native client JVMs; SHA {artifact["jar_sha256"]}')
if __name__=='__main__':main()
