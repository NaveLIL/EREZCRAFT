import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { readZip } from './zip.mjs';

export const sha256 = data => createHash('sha256').update(data).digest('hex');
export const parseJson = data => JSON.parse(data.toString('utf8').replace(/^\uFEFF/, ''));

export async function audit(modsDirectory) {
  const jars = [], languages = new Map(), books = [], extra = [];
  function scan(bytes, origin, depth = 0) {
    const entries = readZip(bytes);
    for (const [name, read] of entries) {
      const match = name.match(/^assets\/([^/]+)\/lang\/en_us\.json$/);
      if (match) {
        const ns = match[1], en = parseJson(read());
        const ruReader = entries.get(name.replace('en_us.json', 'ru_ru.json'));
        const ru = ruReader ? parseJson(ruReader()) : {};
        if (languages.has(ns)) {
          const previous = languages.get(ns);
          if (JSON.stringify(previous.en) !== JSON.stringify(en) || JSON.stringify(previous.ru) !== JSON.stringify(ru)) {
            throw new Error(`Conflicting bundled translations: ${ns}`);
          }
          previous.origins.push(origin);
        } else languages.set(ns, { ns, origins: [origin], en, ru });
      }
      if (/\/patchouli_books\/.+\/en_us\/.+\.json$/.test(name)) {
        const target = name.replace('/en_us/', '/ru_ru/');
        books.push({ origin, source: name, target, en: parseJson(read()), ru: entries.has(target) ? parseJson(entries.get(target)()) : null });
      }
      if (name.endsWith('.md') && /\/ae2guide\/|\/mi_guidebook\//.test(name) && !/\/_/.test(name)) {
        const folder = name.includes('/ae2guide/') ? '/ae2guide/' : '/mi_guidebook/';
        const target = name.replace(folder, folder + '_ru_ru/');
        books.push({ origin, source: name, target, en: read().toString('utf8'), ru: entries.has(target) ? entries.get(target)().toString('utf8') : null });
      }
      if (/\/lang\/.+(\.lang|\.properties)$/.test(name) || /^(shaders\/lang\/)/.test(name)) extra.push({ origin, path: name });
      if (/^META-INF\/jarjar\/.+\.jar$/.test(name)) {
        if (depth >= 4) throw new Error('Unexpected nested JAR depth');
        scan(read(), origin + '!' + name, depth + 1);
      }
    }
  }
  for (const name of (await readdir(modsDirectory)).filter(n => n.endsWith('.jar')).sort()) {
    const bytes = await readFile(path.join(modsDirectory, name));
    jars.push({ file: name, bytes: bytes.length, sha256: sha256(bytes) });
    scan(bytes, name);
  }
  return { schema: 1, jars, languages: [...languages.values()].sort((a, b) => a.ns.localeCompare(b.ns)), books, extra };
}
