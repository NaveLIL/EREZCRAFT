import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { audit, parseJson, sha256 } from './audit.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [mods, reportPath, packArgument, profileArgument] = process.argv.slice(2);
const pack = path.resolve(packArgument || path.join(root, 'distribution/resourcepacks/erezcraft-ru'));
const profile = parseJson(await readFile(profileArgument || path.join(root, 'distribution/localization/ru/profile.json')));
if (!mods) throw new Error('Usage: node tools/localization/verify.mjs <mods-directory> [report.json]');
const inventory = await audit(mods);
const patches = new Map(), packFiles = [];
async function walk(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const filename = path.join(directory, entry.name);
    if (entry.isDirectory()) await walk(filename);
    else packFiles.push(path.relative(pack, filename).replaceAll('\\', '/'));
  }
}
await walk(pack);
const errors = [], rows = [];
const signature = s => [...s.matchAll(/%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAtTn%]|\{\d+\}/g)].map(m => m[0]).sort();
const formatting = s => (s.match(/§[0-9a-fk-or]/gi) || []).sort();
const digest = object => sha256(JSON.stringify(Object.fromEntries(Object.entries(object).sort(([a], [b]) => a.localeCompare(b, 'en')))));
const markup = text => [...text.matchAll(/<\/?[A-Z][^>]*>/g)].map(m => m[0].replace(/\s+/g, ' ').trim());
function technical(object) {
  if (Array.isArray(object)) return object.map(technical);
  if (object && typeof object === 'object') return Object.fromEntries(Object.entries(object).filter(([k]) => !['name', 'text', 'title', 'description'].includes(k)).map(([k, v]) => [k, technical(v)]));
  return object;
}
function headingAnchors(text) {
  return [...text.matchAll(/^#{1,6} (.+)$/gm)].map(m => m[1].trim().toLowerCase().replace(/\s+/g, '-'));
}

const metadata = parseJson(await readFile(path.join(pack, 'pack.mcmeta')));
if (metadata.pack?.pack_format !== 34) errors.push('Expected Minecraft 1.21.1 resource pack format 34');
for (const file of packFiles) {
  if (/^data\//.test(file) || /\/en_us\//.test(file) || /\/lang\/en_us\.json$/.test(file) || /\.(jar|class|png|ogg)$/i.test(file)) errors.push(`Unexpected non-localization resource: ${file}`);
  const match = file.match(/^assets\/([^/]+)\/lang\/ru_ru\.json$/);
  if (match) patches.set(match[1], parseJson(await readFile(path.join(pack, file))));
}

for (const jar of profile.jars) {
  const actual = inventory.jars.find(j => j.file === jar.file);
  if (!actual || actual.sha256 !== jar.sha256) errors.push(`Mod changed since translation audit: ${jar.file}`);
}
for (const jar of inventory.jars) if (!profile.jars.some(j => j.file === jar.file)) errors.push(`Unaudited mod: ${jar.file}`);
for (const namespace of patches.keys()) if (!inventory.languages.some(l => l.ns === namespace)) errors.push(`Unknown namespace: ${namespace}`);

let translatedKeys = 0, missingBefore = 0, missingAfter = 0;
for (const language of inventory.languages) {
  if (profile.languages[language.ns]?.english_sha256 !== digest(language.en)) errors.push(`English language source changed: ${language.ns}`);
  const overlay = patches.get(language.ns) || {};
  const missing = Object.keys(language.en).filter(k => !(k in language.ru) || (/[\p{L}\p{N}]/u.test(language.en[k]) && !language.ru[k]?.trim()));
  const remaining = missing.filter(k => !(k in overlay));
  missingBefore += missing.length;
  missingAfter += remaining.length;
  if (remaining.length) errors.push(`Missing ${language.ns}: ${remaining.join(', ')}`);
  for (const [key, translated] of Object.entries(overlay)) {
    const extra = profile.extra_keys.find(e => e.namespace === language.ns && e.key === key);
    const english = language.en[key] ?? extra?.en;
    if (typeof english !== 'string' || typeof translated !== 'string') {
      errors.push(`Invalid or unknown language entry: ${language.ns}/${key}`);
      continue;
    }
    if (JSON.stringify(signature(english)) !== JSON.stringify(signature(translated))) errors.push(`Changed placeholders: ${language.ns}/${key}`);
    if (JSON.stringify(formatting(english)) !== JSON.stringify(formatting(translated))) errors.push(`Changed formatting: ${language.ns}/${key}`);
    if (english.trim() && !translated.trim()) errors.push(`Empty translation: ${language.ns}/${key}`);
    if (translated.includes('\uFFFD')) errors.push(`Invalid Unicode: ${language.ns}/${key}`);
    if (/^block\./.test(key) && /вверх колонны|грибs|Песокy/.test(translated)) errors.push(`Known mistranslation: ${language.ns}/${key}`);
    translatedKeys++;
  }
  rows.push({ namespace: language.ns, origins: language.origins, english_keys: Object.keys(language.en).length, added_or_corrected: Object.keys(overlay).length, missing_before: missing.length, missing_after: remaining.length });
}

let markdownPages = 0, patchouliPages = 0;
for (const book of inventory.books) {
  const target = path.join(pack, book.target);
  const exists = packFiles.includes(book.target);
  if (!book.ru && !exists) { errors.push(`Missing book translation: ${book.target}`); continue; }
  if (!exists) continue;
  const bytes = await readFile(target);
  if (typeof book.en === 'string') {
    const russian = bytes.toString('utf8');
    if (JSON.stringify(markup(book.en)) !== JSON.stringify(markup(russian))) errors.push(`Changed interactive guide markup: ${book.target}`);
    for (const anchor of headingAnchors(book.en)) {
      const escaped = anchor.replaceAll('&', '&amp;').replaceAll('"', '&quot;');
      if (!russian.includes(`<a name="${escaped}"></a>`)) errors.push(`Missing original heading anchor: ${book.target}#${anchor}`);
    }
    markdownPages++;
  } else {
    const russian = parseJson(bytes);
    if (JSON.stringify(technical(book.en)) !== JSON.stringify(technical(russian))) errors.push(`Changed book mechanics/identifiers: ${book.target}`);
    const links = object => [...JSON.stringify(object).matchAll(/\$\(l:([^)]*)\)/g)].map(m => m[1]).sort();
    if (JSON.stringify(links(book.en)) !== JSON.stringify(links(russian))) errors.push(`Changed Patchouli link: ${book.target}`);
    patchouliPages++;
  }
}

const result = { schema: 1, status: errors.length ? 'FAIL' : 'PASS', checked_profile: profile.name, minecraft: '1.21.1', jars: inventory.jars.length, namespaces: inventory.languages.length, overlay_namespaces: patches.size, translated_keys: translatedKeys, missing_before: missingBefore, missing_after: missingAfter, markdown_pages: markdownPages, patchouli_pages: patchouliPages, originals_unchanged: errors.every(e => !e.startsWith('Mod changed') && !e.startsWith('Unaudited')), checks: ['Pinned mod SHA-256', 'English source digests', 'Missing language keys', 'Placeholder counts and indices', 'Minecraft formatting codes', 'UTF-8 and empty entries', 'Original guide component markup', 'Original heading anchors', 'Patchouli technical fields and links', 'No English/gameplay/binary asset overrides'], errors, languages: rows, limitations: ['Does not detect arbitrary text hardcoded in mod classes.', 'Does not prove visual fit or live client resource-pack activation.', 'Building Gadgets Patchouli content is included for compatible readers; no new reader mod is installed.'] };
if (reportPath) {
  await mkdir(path.dirname(path.resolve(reportPath)), { recursive: true });
  await writeFile(reportPath, JSON.stringify(result, null, 2) + '\n');
}
console.log(JSON.stringify({ ...result, languages: undefined }));
if (errors.length) process.exitCode = 1;
