"""Inspect persisted FULL chunks and compare block/biome data across a cold start.

Read-only on the world. Run snapshot with the owned dedicated server stopped.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zlib


class Nbt:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def read(self, n):
        result = self.data[self.pos:self.pos + n]
        self.pos += n
        assert len(result) == n
        return result

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.read(self.number('H')).decode('utf-8')

    def payload(self, tag):
        if tag in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[tag])
        if tag == 8:
            return self.string()
        if tag == 9:
            kind, size = self.number('B'), self.number('i')
            assert 0 <= size <= 1000000
            return [self.payload(kind) for _ in range(size)]
        if tag == 10:
            result = {}
            while True:
                kind = self.number('B')
                if kind == 0:
                    return result
                name = self.string()
                result[name] = self.payload(kind)
        if tag in [7, 11, 12]:
            size = self.number('i')
            assert 0 <= size <= 10000000
            width = {7: 1, 11: 4, 12: 8}[tag]
            return {'array_tag': tag, 'size': size, 'bytes': self.read(size * width).hex()}
        raise ValueError('Unexpected NBT tag')


def chunk(region, x, z):
    path = region / f'r.{x // 32}.{z // 32}.mca'
    with path.open('rb') as stream:
        stream.seek(((x % 32) + (z % 32) * 32) * 4)
        location = int.from_bytes(stream.read(4), 'big')
        assert location >> 8, f'Chunk not persisted: {x}, {z}'
        stream.seek((location >> 8) * 4096)
        size = int.from_bytes(stream.read(4), 'big')
        assert stream.read(1) == b'\x02', 'Expected local zlib chunk'
        reader = Nbt(zlib.decompress(stream.read(size - 1)))
    assert reader.number('B') == 10
    reader.string()
    result = reader.payload(10)
    assert result['Status'] == 'minecraft:full', result['Status']
    assert (result['xPos'], result['zPos']) == (x, z)
    sections = [{k: s[k] for k in ['Y', 'block_states', 'biomes'] if k in s} for s in result['sections']]
    return {'status': result['Status'], 'geometry_sha256': hashlib.sha256(json.dumps(sections, sort_keys=True).encode()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('world', type=Path)
    parser.add_argument('report', type=Path)
    parser.add_argument('--compare', type=Path)
    args = parser.parse_args()
    regions = {'minecraft:overworld': args.world / 'region', 'interstice:islands_v6': args.world / 'dimensions/interstice/islands_v6/region'}
    rows = {}
    for dimension, path in regions.items():
        rows[dimension] = {f'{x},{z}': chunk(path, x, z) for x, z in [(8, 8), (-8, -8), (16, 16), (-16, 16), (24, -24), (-24, -24)]}
    if args.compare:
        assert rows == json.loads(args.compare.read_text())['chunks'], 'Persisted block/biome data changed'
    args.report.write_text(json.dumps({'passed': True, 'cold_compare': bool(args.compare), 'chunks': rows}, indent=2) + '\n')
    print('PERSISTED_FULL_CHUNKS_VERIFIED', len(regions) * 6)


if __name__ == '__main__':
    main()
