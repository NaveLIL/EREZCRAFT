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
    ('ambient', 1.35, 56), ('alert', 1.05, 96), ('windup', .32, 140),
    ('attack', .23, 80), ('hurt', .45, 125), ('death', 1.7, 72)
]):
    rng = np.random.default_rng(231023 + i)
    t = np.arange(round(44100 * duration)) / 44100
    progress = t / duration
    frequency = base * (1 - .4 * progress) * (1 + .08 * np.sin(t * 28))
    if name == 'windup':
        frequency = base * (1 + progress)
    phase = np.cumsum(frequency) * (2 * np.pi / 44100)
    voice = .5 * np.sin(phase) + .23 * np.sin(phase * 2.01) + .15 * np.sin(phase * 4.03)
    noise = np.convolve(rng.normal(size=len(t)), np.ones(7) / 7, mode='same')
    rasp = noise * (.45 + .35 * np.sin(t * 18)**2)
    if name in ('attack', 'windup'):
        voice = .15 * voice + 1.1 * rasp
    else:
        voice += .65 * rasp
    envelope = np.minimum(t / .025, 1) * (1-progress)**.8
    if name == 'ambient':
        envelope *= .6 + .4 * np.sin(t * 6)**2
    data = np.tanh(voice * 1.8) * envelope * .66
    path = OUT / (name + '.ogg')
    sf.write(path, data, 44100, format='OGG', subtype='VORBIS')
    decoded, rate = sf.read(path)
    assert len(decoded) == len(data) and rate == 44100 and np.isfinite(decoded).all() and np.max(np.abs(decoded)) < 1
    files[path.name] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'seconds': duration, 'sample_rate': rate, 'channels': 1}
(ASSETS/'provenance/vault-hunter-audio.json').write_text(json.dumps({
    'creator': 'EREZ Dev', 'license': 'CC0-1.0', 'origin': 'Original deterministic synthesis, no sampled recordings',
    'generator': 'tools/generate_hunter_sounds.py', 'files': files
}, indent=2)+'\n', encoding='utf-8')
sounds = json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
for name in ['ambient', 'alert', 'windup', 'attack', 'hurt', 'death']:
    sounds['vault_hunter.'+name] = {'subtitle': 'subtitles.interstice.vault_hunter.'+name, 'sounds': [{'name': 'interstice:vault_hunter/'+name}]}
(ASSETS/'sounds.json').write_text(json.dumps(sounds, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
