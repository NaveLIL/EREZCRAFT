"""Coordinate disposable native trials; never stop an existing game."""
from pathlib import Path
import json,os,sys,time
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'.verification/native-skat-trial-lease.json'
action,pid=sys.argv[1],int(sys.argv[2])
if action=='acquire':
    pending=p.with_name('native-skat-trial-lease-'+str(pid)+'.tmp')
    pending.write_text(json.dumps(dict(pid=pid,expires=int((time.time()+1200)*1000))),encoding='utf8')
    os.replace(pending,p)
elif action=='release':
    if p.exists() and json.loads(p.read_text(encoding='utf8'))['pid']==pid:p.unlink()
else:raise ValueError(action)
