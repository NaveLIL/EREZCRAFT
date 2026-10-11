"""Original EREZ Dev procedural creature audio; no sampled recordings.
Reproduce with numpy and soundfile0.13.1 (libvorbis). Output mono Vorbis.
"""
from pathlib import Path
import hashlib,json,sys
import numpy as np
sys.path.insert(0,str(Path('.verification/audio-python').resolve()))
import soundfile as sf

root=Path(__file__).resolve().parents[1]
out=root/'src/main/resources/assets/interstice/sounds/upper_ghast'
out.mkdir(parents=True,exist_ok=True)
files={}
for index,(name,duration,base) in enumerate([('ambient',1.7,68),('hurt',.55,110),('death',1.6,82),('warn',1.1,58),('shoot',.38,145),('splash',.55,180)]):
    rng=np.random.default_rng(71011+index);t=np.arange(int(44100*duration))/44100
    frequency=base*(1+.23*np.sin(t*7))*(1+.4*t/duration if name=='warn' else 1-.45*t/duration)
    phase=np.cumsum(frequency)*2*np.pi/44100
    voice=.5*np.sin(phase)+.22*np.sin(phase*2.03)+.13*np.sin(phase*3.7)
    noise=rng.normal(0,1,len(t));filtered=np.convolve(noise,np.ones(11)/11,mode='same')
    envelope=np.sin(np.pi*np.minimum(t/.06,1)/2)**2*np.maximum(0,1-t/duration)**.7
    if name in ('shoot','splash'):voice=.15*voice+.85*filtered
    else:voice=voice+.23*filtered
    data=np.tanh(voice*1.4)*envelope*.72
    path=out/(name+'.ogg');sf.write(path,data,44100,format='OGG',subtype='VORBIS')
    files[path.name]={'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'seconds':duration,'sample_rate':44100,'channels':1,'peak':float(np.max(np.abs(data)))}
    decoded,rate=sf.read(path);assert rate==44100 and len(decoded)==len(data) and np.max(np.abs(decoded))<1
p=root/'src/main/resources/assets/interstice/provenance/upper-ghast-audio.json'
p.write_text(json.dumps({'creator':'EREZ Dev','license':'CC0-1.0','origin':'original deterministic procedural synthesis; no samples','generator':'tools/generate_ghast_sounds.py','files':files},indent=2)+'\n')
