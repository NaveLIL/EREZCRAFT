import fs from 'node:fs';
import {skinPng} from 'file:///D:/repos/erezcraft/apps/auth-gateway/skins.mjs';
const root='D:/repos/Blender/creations/28_kizya_skin';
const data=fs.readFileSync(root+'/exports/Kizya_Pink_PK02_slim_64x64.png');
const accepted=skinPng(data.toString('base64'),'slim');
if(!accepted.equals(data))throw new Error('EREZ ID skin validation changed pixel-file bytes');
const result={revision:'PK02',actual_erez_id_validator_passed:true,variant:'slim',dimensions:[64,64],bytes:data.length,validator_preserves_bytes:true,account_skin_changed:false};
fs.writeFileSync(root+'/reports/EREZ_ID_SKIN_CHECK.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
