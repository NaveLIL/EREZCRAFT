import { inflateRawSync, deflateRawSync } from 'node:zlib';

export function readZip(buffer) {
  let end = buffer.length - 22;
  const lower = Math.max(0, buffer.length - 65557);
  while (end >= lower && buffer.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < lower) throw new Error('ZIP end record not found');
  if (buffer.readUInt16LE(end + 4) || buffer.readUInt16LE(end + 6)) throw new Error('Multi-disk ZIP unsupported');
  let cursor = buffer.readUInt32LE(end + 16);
  const count = buffer.readUInt16LE(end + 10);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    if (buffer.readUInt32LE(cursor) !== 0x02014b50) throw new Error('Invalid ZIP central directory');
    const flags = buffer.readUInt16LE(cursor + 8);
    const method = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const size = buffer.readUInt32LE(cursor + 24);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const offset = buffer.readUInt32LE(cursor + 42);
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
    if (flags & 1 || ![0, 8].includes(method)) throw new Error(`Unsupported ZIP entry: ${name}`);
    // Some upstream JARs repeat META-INF license files; localized resources must be unique.
    if (entries.has(name) && !name.startsWith('META-INF/')) throw new Error(`Duplicate ZIP entry: ${name}`);
    entries.set(name, () => {
      if (buffer.readUInt32LE(offset) !== 0x04034b50) throw new Error(`Invalid local header: ${name}`);
      const start = offset + 30 + buffer.readUInt16LE(offset + 26) + buffer.readUInt16LE(offset + 28);
      const data = buffer.subarray(start, start + compressedSize);
      const result = method === 8 ? inflateRawSync(data) : data;
      if (result.length !== size) throw new Error(`ZIP size mismatch: ${name}`);
      return result;
    });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// Stable order and timestamp: rebuilding the same sources produces identical bytes.
export function writeZip(files) {
  const local = [], central = [];
  let offset = 0;
  for (const [name, data] of [...files].sort(([a], [b]) => a.localeCompare(b, 'en'))) {
    if (name.startsWith('/') || name.includes('..') || name.includes('\\')) throw new Error(`Unsafe archive path: ${name}`);
    const bytes = Buffer.from(data), filename = Buffer.from(name), compressed = deflateRawSync(bytes, { level: 9 });
    const crc = crc32(bytes);
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50);
    header.writeUInt16LE(20, 4); header.writeUInt16LE(0x800, 6); header.writeUInt16LE(8, 8);
    header.writeUInt16LE(33, 12); header.writeUInt32LE(crc, 14);
    header.writeUInt32LE(compressed.length, 18); header.writeUInt32LE(bytes.length, 22);
    header.writeUInt16LE(filename.length, 26);
    local.push(header, filename, compressed);
    const directory = Buffer.alloc(46);
    directory.writeUInt32LE(0x02014b50);
    directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6);
    directory.writeUInt16LE(0x800, 8); directory.writeUInt16LE(8, 10); directory.writeUInt16LE(33, 14);
    directory.writeUInt32LE(crc, 16); directory.writeUInt32LE(compressed.length, 20);
    directory.writeUInt32LE(bytes.length, 24); directory.writeUInt16LE(filename.length, 28);
    directory.writeUInt32LE(offset, 42);
    central.push(directory, filename);
    offset += header.length + filename.length + compressed.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50);
  end.writeUInt16LE(files.size, 8); end.writeUInt16LE(files.size, 10);
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, end]);
}
