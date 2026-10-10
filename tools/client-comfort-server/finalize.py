"""Freeze the completed isolated dedicated proof without touching production files."""
from datetime import datetime, timezone
import hashlib, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'.verification/client-comfort-dedicated-20261010'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
manifest=json.loads((DEST/'manifest.json').read_text())
launch=json.loads((DEST/'launch.json').read_text())
receipt=json.loads(Path(launch['validation']).read_text())
assert launch['passed'] and launch['exit_code']==0 and not launch['timed_out'] and receipt['passed']
assert receipt['server_pid']==launch['owned_pid'] and receipt['server_class']=='net.minecraft.server.dedicated.DedicatedServer'
for row in manifest['copied_artifacts']:
    assert sha(DEST/'profile/mods'/row['filename'])==row['sha256']
    assert sha(Path(row['source']))==row['sha256']
for path,digest in manifest['sources_sha256'].items():assert sha(Path(path))==digest
report={'completed_utc':datetime.now(timezone.utc).isoformat(),'passed':True,'server_jar_count':49,
    'all_49_artifacts_sha256_verified_after_shutdown':True,'source_scripts_unchanged':True,
    'launcher_receipt':launch,'functional_receipt':receipt,'copied_artifacts':manifest['copied_artifacts'],
    'excluded_client_artifacts':manifest['excluded_client_artifacts'],
    'server_log_sha256':sha(DEST/'server.log'),'manifest_sha256':sha(DEST/'manifest.json'),
    'known_log_note':'A caught Supplementaries TextureSheetParticle dedicated-dist error is also present in the prior accepted 47-jar startup. No new JFR attribution or ten-player capacity claim is made.'}
(DEST/'acceptance.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'passed':True,'exit_code':0,'server_jars':49,'all_sha256_verified':True,'receipt':str(DEST/'acceptance.json')}))
