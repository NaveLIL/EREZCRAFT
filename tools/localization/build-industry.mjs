// Rebuild the accepted pack from versioned editable sources, without local caches.
import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {writeZip,readZip} from './zip.mjs';
import {sha256} from './audit.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const source=path.join(root,'distribution/localization/industry-ru');
const acceptance=JSON.parse(await readFile(path.join(source,'acceptance.json'),'utf8'));
if(acceptance.status!=='accepted'||acceptance.unresolved_translations!==0)throw new Error('Localization is not accepted');
const files=new Map();
async function add(directory,relative=''){
 for(const entry of await readdir(path.join(directory,relative),{withFileTypes:true})){
  const name=relative?relative+'/'+entry.name:entry.name;
  if(entry.isSymbolicLink())throw new Error('Unexpected source link');
  if(entry.isDirectory())await add(directory,name);
  else if(entry.isFile())files.set(name,await readFile(path.join(directory,name)));
 }
}
await add(path.join(root,'distribution/resourcepacks/erezcraft-ru'));
await add(path.join(source,'pack'));
const bytes=writeZip(files),digest=sha256(bytes);
if(digest!==acceptance.resource_pack_sha256||bytes.length!==acceptance.resource_pack_bytes)throw new Error('Sources differ from accepted archive');
const unpacked=readZip(bytes);
if(unpacked.size!==files.size)throw new Error('ZIP roundtrip count mismatch');
for(const [name,value] of files)if(!unpacked.get(name)().equals(value))throw new Error('ZIP roundtrip mismatch: '+name);
const output=path.resolve(process.argv[2]||path.join(root,'build/distributions/erezcraft-industry-ru-mc1.21.1.zip'));
await mkdir(path.dirname(output),{recursive:true});await writeFile(output,bytes);
console.log(JSON.stringify({status:'PASS',entries:files.size,bytes:bytes.length,sha256:digest,output}));
