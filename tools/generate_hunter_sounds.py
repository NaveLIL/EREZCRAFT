"""Original deterministic EREZ Dev creature audio, no samples (CC0-1.0)."""
from pathlib import Path
import hashlib
import json
import sys
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / '.verification/audio-python'))
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/interstice'
OUT = ASSETS / 'sounds/vault_hunter'
OUT.mkdir(parents=True, exist_ok=True)
files = {}
for i, (name, duration, base) in enumerate([
    ('ambient', .8, 520), ('alert', .72, 760), ('windup', .28, 1150),
    ('attack', .22, 430), ('hurt', .36, 920), ('death', 1.2, 360)
]):
    rng = np.random.default_rng(231023 + i)
    t = np.arange(round(44100 * duration)) / 44100
    progress = t / duration
    # Aperiodic filtered breath and irregular clicks, without a motor oscillator.
    frequency = np.fft.rfftfreq(len(t), 1 / 44100)
    spectrum = np.fft.rfft(rng.normal(size=len(t)))
    shaping = np.exp(-.5*((frequency-base)/(base*.7))**2)
    shaping += .25*np.exp(-.5*((frequency-base*3)/(base*1.1))**2)
    shaping *= 1-np.exp(-(frequency/150)**4)
    breath = np.fft.irfft(spectrum*shaping,n=len(t))
    breath /= max(.001,np.max(np.abs(breath)))
    knots=np.linspace(0,duration,11)
    texture=np.interp(t,knots,rng.uniform(.25,1,len(knots)))
    envelope=np.sin(np.pi*progress)**.8
    if name in ('windup','attack','hurt'):envelope=np.minimum(t/.008,1)*np.exp(-progress*4)
    voice=breath*texture*envelope
    for onset in rng.uniform(.02,duration*.7,3 if name in ('alert','windup') else 1):
        dt=np.maximum(t-onset,0)
        click=rng.normal(size=len(t))*np.exp(-dt/.008)*(t>=onset)
        voice+=click*.08
    data=np.tanh(voice*1.3)
    data*=.65/max(.001,np.max(np.abs(data)))
    path = OUT / (name + '.ogg')
    sf.write(path, data, 44100, format='OGG', subtype='VORBIS')
    decoded, rate = sf.read(path)
    assert len(decoded) == len(data) and rate == 44100 and np.isfinite(decoded).all() and np.max(np.abs(decoded)) < 1
    files[path.name] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'seconds': duration, 'sample_rate': rate, 'channels': 1}
(ASSETS/'provenance/vault-hunter-audio.json').write_text(json.dumps({
    'creator': 'EREZ Dev', 'license': 'CC0-1.0', 'origin': 'Original deterministic aperiodic breath and dry transient synthesis, no sampled recordings', 'revision': 2,
    'generator': 'tools/generate_hunter_sounds.py', 'files': files
}, indent=2)+'\n', encoding='utf-8')
sounds = json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
for name in ['ambient', 'alert', 'windup', 'attack', 'hurt', 'death']:
    sounds['vault_hunter.'+name] = {'subtitle': 'subtitles.interstice.vault_hunter.'+name, 'sounds': [{'name': 'interstice:vault_hunter/'+name}]}
(ASSETS/'sounds.json').write_text(json.dumps(sounds, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
