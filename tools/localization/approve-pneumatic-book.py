"""Compile explicitly post-edited paragraphs; never import unreviewed MT drafts."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / '.verification/mod-candidates-20261024'
SOURCE = ROOT / 'distribution/localization/industry-ru'


def read(path):
    return json.loads(path.read_text('utf-8-sig'))


def main():
    index_path = WORK / 'pnc-review-index.json'
    index = read(index_path)
    reviewed = {}
    indices = set()
    protected = re.compile(r'\$\((?:l:|k:|item:|command:)[^)]+\)')
    for path in sorted(WORK.glob('postedit-*.json')):
        if not re.fullmatch(r'postedit-\d+\.json', path.name):
            continue
        for raw, russian in read(path).items():
            position = int(raw)
            assert position not in indices, f'Duplicate paragraph {position}'
            english = index[position][0]
            assert re.search('[\u0400-\u04ff]', russian) or (
                english == russian and english in {'Immersive Engineering', 'ComputerCraft',
                    'CraftTweaker', 'Thaumcraft', 'Apotheosis', 'Curios'}), position
            assert Counter(protected.findall(english)) == Counter(protected.findall(russian)), (
                position, 'changed link, keybind or command')
            substitutions=re.compile(r'\$\{[^}]+\}')
            assert Counter(substitutions.findall(english)) == Counter(substitutions.findall(russian)), (
                position, 'changed variable interpolation')
            code_calls=re.compile(r'(?<![\w\u0400-\u04ff])[A-Za-z_]\w*\([^()]*\)')
            # Only compare highlighted executable examples, excluding Patchouli macros.
            code=lambda text:' '.join(re.findall(r'\$\(#800\)(.*?)/\$',text))
            assert Counter(code_calls.findall(code(english))) == Counter(code_calls.findall(code(russian))), (
                position, 'changed API example')
            assert '\ufffd' not in russian, (position, 'replacement character')
            assert '$(' not in re.sub(r'\$\([^)]*\)', '', russian), (position, 'broken macro')
            assert english not in reviewed or reviewed[english] == russian, position
            reviewed[english] = russian
            indices.add(position)
    destination = SOURCE / 'pneumatic-postedit-reviewed.json'
    destination.write_text(json.dumps(reviewed, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    receipt = {
        'index_sha256': hashlib.sha256(index_path.read_bytes()).hexdigest(),
        'approved_indices': sorted(indices),
        'unique_paragraphs': len(reviewed),
        'source_sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
        'unreviewed_drafts_imported': False,
    }
    (WORK / 'postedit-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', 'utf-8')
    print('APPROVED_PARAGRAPHS', len(reviewed))


if __name__ == '__main__':
    main()
