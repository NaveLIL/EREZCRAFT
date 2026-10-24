import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { writeZip, readZip } from './zip.mjs';
import { sha256 } from './audit.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const source = path.resolve(process.argv[3] || path.join(root, 'distribution/resourcepacks/erezcraft-ru'));
const output = path.resolve(process.argv[2] || path.join(root, 'build/distributions/erezcraft-ru-mc1.21.1.zip'));
const files = new Map();
async function walk(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const filename = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Unexpected symlink: ${filename}`);
    if (entry.isDirectory()) await walk(filename);
    else if (entry.isFile()) files.set(path.relative(source, filename).replaceAll('\\', '/'), await readFile(filename));
  }
}
await walk(source);
if (!files.has('pack.mcmeta')) throw new Error('Missing pack.mcmeta');
const archive = writeZip(files);
// Verify the produced archive, rather than just assuming the writer succeeded.
const roundTrip = readZip(archive);
if (roundTrip.size !== files.size) throw new Error('ZIP entry count mismatch');
for (const [name, expected] of files) {
  if (!roundTrip.get(name)().equals(expected)) throw new Error(`ZIP content mismatch: ${name}`);
}
await mkdir(path.dirname(output), { recursive: true });
await writeFile(output, archive);
console.log(JSON.stringify({ file: output, entries: files.size, bytes: archive.length, sha256: sha256(archive) }));
