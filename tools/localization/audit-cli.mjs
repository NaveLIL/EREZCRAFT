import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { audit } from './audit.mjs';
const [mods, output] = process.argv.slice(2);
if (!mods || !output) throw new Error('Usage: node tools/localization/audit-cli.mjs <mods-directory> <audit.json>');
const result = await audit(mods);
await mkdir(path.dirname(path.resolve(output)), { recursive: true });
await writeFile(output, JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify({ jars: result.jars.length, namespaces: result.languages.length, missing: result.languages.reduce((n, l) => n + Object.keys(l.en).filter(k => !(k in l.ru)).length, 0), booksWithoutRussian: result.books.filter(b => !b.ru).length }));
