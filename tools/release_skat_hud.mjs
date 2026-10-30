import {readFile,lstat,realpath,mkdir,copyFile,writeFile,link} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {createHash,createPrivateKey,createPublicKey} from 'node:crypto';
import {publishPack,decodeSigned,sha256,safePath} from 'file:///D:/repos/erezcraft/tools/publish-pack.mjs';

const keyFile=path.resolve('.local/release-signing.key');
const output=path.resolve('.local/files-release/public');
const requireCondition=(value,message)=>{if(!value)throw new Error(message);};
async function privateKeyPolicy() {
  const stat=await lstat(keyFile);
  requireCondition(stat.isFile()&&!stat.isSymbolicLink(),'Release key must be a regular private file');
  const canonical=await realpath(keyFile);
  const keyMatches=process.platform==='win32'?canonical.toLowerCase()===keyFile.toLowerCase():canonical===keyFile;
  requireCondition(keyMatches&&!canonical.startsWith(output+path.sep),'Release key must stay outside public output and links');
  if(process.platform==='win32') {
    const identity=spawnSync('whoami.exe',['/user','/fo','csv','/nh'],{windowsHide:true,encoding:'utf8',timeout:30000});
    const currentSid=identity.stdout?.match(/"(S-[0-9]+(?:-[0-9]+)+)"\s*$/)?.[1];
    requireCondition(identity.status===0&&!identity.error&&currentSid,'Could not identify the release operator SID');
    const policy=`$ErrorActionPreference='Stop';
      $taskReleaseAcl=Get-Acl -LiteralPath $env:EREZCRAFT_RELEASE_KEY_POLICY_PATH;
      $taskAllowedSids=@($env:EREZCRAFT_RELEASE_OPERATOR_SID,'S-1-5-18','S-1-5-32-544','SY','BA');
      $taskUnsafe=0;
      foreach($taskAce in ($taskReleaseAcl.Sddl -split '\\(')){
        if($taskAce -match '^(A|OA|XA|ZA);'){
          if($taskAce -match '^A;[^;]*;[^;]*;[^;]*;[^;]*;([^)]*)\\)'){
            if($taskAllowedSids -notcontains $Matches[1]){$taskUnsafe++}
          }else{$taskUnsafe++}
        }
      }
      @{protected=$taskReleaseAcl.AreAccessRulesProtected;unsafeAllows=$taskUnsafe}|ConvertTo-Json -Compress`;
    let response=spawnSync('pwsh.exe',['-NoProfile','-NonInteractive','-Command',policy],
      {windowsHide:true,encoding:'utf8',timeout:30000,env:{...process.env,EREZCRAFT_RELEASE_KEY_POLICY_PATH:keyFile,
        EREZCRAFT_RELEASE_OPERATOR_SID:currentSid}});
    if(response.error?.code==='ENOENT')response=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',policy],
      {windowsHide:true,encoding:'utf8',timeout:30000,env:{...process.env,EREZCRAFT_RELEASE_KEY_POLICY_PATH:keyFile,
        EREZCRAFT_RELEASE_OPERATOR_SID:currentSid}});
    requireCondition(response.status===0&&!response.error,'Could not verify private release key permissions: '+String(response.error?.message??response.stderr).slice(0,1500));
    let acl;try{acl=JSON.parse(response.stdout.trim());}catch{throw new Error('Release key permissions result was invalid');}
    requireCondition(acl.protected===true&&acl.unsafeAllows===0,'Release key ACL must allow only current user, SYSTEM and Administrators with inheritance disabled');
  } else {
    requireCondition((stat.mode&0o077)===0,'Release key permissions must exclude other users');
  }
}

async function writePrepared(file,bytes){try{await writeFile(file,bytes,{flag:'wx'});}catch(error){if(error.code!=='EEXIST'||!Buffer.from(await readFile(file)).equals(Buffer.from(bytes)))throw error;}}
// Signed pack-only delta. Input and isolated acceptance live outside the public tree.
const project=path.resolve('D:/repos/Minecraft/EREZCRAFT');
const input=JSON.parse(await readFile(path.join(project,'.verification/skat-hud-release-input.json'),'utf8'));
const stage=path.join(project,'.verification/skat-hud-release-20261029');
const jar=path.resolve(input.jar);
requireCondition(input.passed===true && input.allUpdatesIncluded===true && input.modVersion==='0.7.0-preview.100-skat-speed','Candidate acceptance missing');
requireCondition(sha256(await readFile(jar))===input.jarSha256,'Candidate JAR changed');
requireCondition(JSON.parse(await readFile(input.components,'utf8')).passed===true,'Unaccepted component evidence');
const accepted=JSON.parse(await readFile(input.native,'utf8'));requireCondition(accepted.native.passed&&accepted.native.glass.passed&&accepted.jar_sha256===input.jarSha256,'Unaccepted native evidence');
const config=JSON.parse(await readFile('config/launcher.downloads.json','utf8'));
const publicKey=createPublicKey({key:Buffer.concat([Buffer.from('302a300506032b6570032100','hex'),Buffer.from(config.publicKey,'base64')]),format:'der',type:'spki'});
const get=async url=>{const r=await fetch(url,{signal:AbortSignal.timeout(30000)});requireCondition(r.ok,'Public metadata request failed');return Buffer.from(await r.arrayBuffer());};
const catalogBytes=await get(config.catalogUrl);
requireCondition(catalogBytes.equals(await readFile(path.join(output,'catalog.json'))),'Local/public base differ');
const catalog=decodeSigned(catalogBytes,publicKey);requireCondition(catalog.sequence===23,'Live channel moved');
const server=catalog.servers.find(s=>s.id==='archipelago');
requireCondition(server.playable && server.releaseId==='techmagic-0-1-0-alpha-23-skat-ocean-fauna' && server.address==='mc.erezcraft.com:25565','Unexpected base server');
const manifestBytes=await get(server.manifestUrl);requireCondition(sha256(manifestBytes)===server.manifestSha256,'Base manifest digest mismatch');
const old=decodeSigned(manifestBytes,publicKey);
requireCondition(old.sequence===23 && old.files.length===89 && old.files.filter(r=>r.path.startsWith('mods/')).length===79,'Unexpected base pack');
requireCondition((await lstat(stage)).isDirectory(),'Prepared tide release stage required');
await writePrepared(path.join(stage,'base-catalog.json'),catalogBytes);
await writePrepared(path.join(stage,'base-manifest.json'),manifestBytes);
const source=path.join(stage,'client100');await mkdir(source,{recursive:true});
const base=path.join(project,'.verification/updates-release-20261029/client98');
const oldJar='mods/interstice-0.7.0-preview.98-skat-server.jar', markerPath='config/erezcraft-pack.json';
for(const row of old.files){
 requireCondition(sha256(await readFile(path.join(base,row.path)))===row.sha256,'Base stage changed: '+row.path);
 if(row.path===oldJar)continue;
 await mkdir(path.dirname(path.join(source,row.path)),{recursive:true});if(row.path===markerPath)await copyFile(path.join(base,row.path),path.join(source,row.path));else try{await link(path.join(base,row.path),path.join(source,row.path));}catch(error){requireCondition(error.code==='EEXIST'&&sha256(await readFile(path.join(source,row.path)))===row.sha256,'Prepared source differs: '+row.path);}
}
await copyFile(jar,path.join(source,'mods',path.basename(jar)));
const marker=JSON.parse(await readFile(path.join(source,markerPath),'utf8'));
requireCondition(marker.deliveryRelease===23,'Unexpected base marker');marker.deliveryRelease=24;marker.packVersion='0.1.0-alpha.24';
await writeFile(path.join(source,markerPath),JSON.stringify(marker,null,2)+'\n');
const externalDownloads=Object.fromEntries(old.files.filter(r=>r.path!==oldJar && new URL(r.url).origin==='https://cdn.modrinth.com').map(r=>[r.path,r.url]));
const descriptor={serverId:'archipelago',releaseId:'techmagic-0-1-0-alpha-24-skat-hud-glass',sequence:24,
 expiresAt:Math.floor(Date.now()/1000)+30*86400,minecraft:old.minecraft,loader:old.loader,loaderVersion:old.loaderVersion,
 name:server.name,description:'Междуморье: спидометр Ската и видимость нижнего моря через стекло рубки.',
 tag:server.tag,address:server.address,playable:true,managedConfig:old.files.filter(r=>r.path.startsWith('config/')&&r.policy==='managed').map(r=>r.path),externalDownloads};
try{descriptor.expiresAt=JSON.parse(await readFile(path.join(stage,'descriptor.json'),'utf8')).expiresAt;}catch(error){if(error.code!=='ENOENT')throw error;}
await writePrepared(path.join(stage,'descriptor.json'),JSON.stringify(descriptor,null,2)+'\n');
await privateKeyPolicy();
const key=createPrivateKey(await readFile(keyFile));requireCondition(createPublicKey(key).export({type:'spki',format:'der'}).subarray(-32).toString('base64')===config.publicKey,'Wrong release key');
const result=await publishPack({source,output,keyFile,baseUrl:'https://files.erezcraft.com/',descriptor});
requireCondition(result.catalog.sequence===24 && result.manifest.sequence===24 && result.manifest.files.length===89 && result.manifest.files.filter(r=>r.path.startsWith('mods/')).length===79,'Unexpected candidate closure');
for(const row of old.files){if(row.path===oldJar||row.path===markerPath)continue;requireCondition(JSON.stringify(result.manifest.files.find(r=>r.path===row.path))===JSON.stringify(row),'Unrelated bytes, URL or policy changed: '+row.path);}
const changed=result.manifest.files.filter(r=>!old.files.some(o=>JSON.stringify(o)===JSON.stringify(r)));requireCondition(changed.length===2,'Expected only own JAR and marker');
await writeFile(path.join(stage,'publication.json'),JSON.stringify({sourceSequence:23,sequence:24,releaseId:descriptor.releaseId,oldCatalogSha256:sha256(catalogBytes),newCatalogSha256:sha256(await readFile(path.join(output,'catalog.json'))),files:89,clientJars:79,unchangedBaseFiles:87,removed:[oldJar],changed,stage},null,2)+'\n');
console.log('SIGNED_UPDATES_SEQUENCE24_READY; 78 existing JARs and localisation unchanged');
