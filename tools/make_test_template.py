"""Generate an empty 16-cube vanilla NBT GameTest template, without external packages."""
import gzip
import struct
from pathlib import Path

def utf(text):
    value = text.encode('utf-8')
    return struct.pack('>H', len(value)) + value

def integer(name, value):
    return b'\x03' + utf(name) + struct.pack('>i', value)

def list_tag(name, kind, values):
    return b'\x09' + utf(name) + bytes([kind]) + struct.pack('>i', len(values)) + b''.join(values)

payload = b'\x0a' + utf('') + integer('DataVersion', 3955)
payload += list_tag('size', 3, [struct.pack('>i', 16)] * 3)
payload += list_tag('palette', 10, [b'\x08' + utf('Name') + utf('minecraft:air') + b'\x00'])
payload += list_tag('blocks', 10, []) + list_tag('entities', 10, []) + b'\x00'
path = Path(__file__).resolve().parents[1] / 'src/main/resources/data/interstice/structure/empty.nbt'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(gzip.compress(payload, mtime=0))
