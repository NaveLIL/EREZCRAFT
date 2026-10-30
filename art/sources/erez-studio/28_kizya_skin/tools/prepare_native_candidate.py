from pathlib import Path
import shutil,json,hashlib
ROOT=Path(__file__).resolve().parents[1];GAME=Path('D:/repos/Minecraft/EREZCRAFT');BASE=GAME/'.verification/sk01';DEST=GAME/'.verification/kizya28'
if not DEST.exists():
    DEST.mkdir()
    for name in ['src/main','src/smoke','src/avatarCompat','gradle']:
        shutil.copytree(BASE/name,DEST/name,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
    for name in ['build.gradle','gradle.properties','settings.gradle','settings.gradle.kts']:
        if (BASE/name).exists():shutil.copy2(BASE/name,DEST/name)
    source=(BASE/'build.gradle').read_text(encoding='utf8')
    source=source.replace('erez_avatar_compat','erez_cpm_guard').replace("archiveBaseName='erez-avatar-compat';archiveVersion='0.1.0'","archiveBaseName='erez-cpm-guard';archiveVersion='0.2.0'")
    source=source.replace('D:/repos/Blender/creations/17_rift_engineer_skin/exports','D:/repos/Blender/creations/28_kizya_skin/exports').replace('erez-avatar-compat-0.1.0.jar','erez-cpm-guard-0.2.0.jar')
    (DEST/'build.gradle').write_text(source,encoding='utf8')
# Fresh isolated guard artifact; never load or install the retired 0.1.0 JAR.
compat=DEST/'src/avatarCompat'
for p in compat.rglob('*'):
    if p.is_file() and p.suffix in ['.java','.json','.toml']:
        s=p.read_text(encoding='utf8').replace('erez_avatar_compat','erez_cpm_guard').replace('0.1.0','0.2.0')
        p.write_text(s,encoding='utf8')
old=(BASE/'src/smoke/java/pro/erez/interstice/client/AvatarNative.java').read_text(encoding='utf8')
s=old.replace('public final class AvatarNative','public final class KizyaNative').replace('private AvatarNative()','private KizyaNative()')
s=s.replace('private static boolean skinsFixed;','private static boolean skinsFixed; private static int vanillaShots;')
s=s.replace('if(skinsFixed)for(int i=0;i<3;i++)','if(mc.getOverlay()!=null)return;\n            if(skinsFixed)for(int i=0;i<3;i++)')
s=s.replace('private static int phase,ticks;','private static int phase,ticks; private static float observerYaw,observerPitch;')
s=s.replace('private static void camera(Minecraft mc,double x,double eyeY,double z,float yaw,float pitch){','private static void camera(Minecraft mc,double x,double eyeY,double z,float yaw,float pitch){\n        observerYaw=yaw;observerPitch=pitch;')
s=s.replace('if(mc.getOverlay()!=null)return;','if(mc.getOverlay()!=null)return;\n            mc.mouseHandler.releaseMouse();mc.player.setYRot(observerYaw);mc.player.yRotO=observerYaw;mc.player.setXRot(observerPitch);mc.player.xRotO=observerPitch;')
s=s.replace('D:/repos/Blender/creations/17_rift_engineer_skin','D:/repos/Blender/creations/28_kizya_skin').replace('avatar-native.json','kizya-native.json').replace('"avatar-"','"kizya-"')
start=s.index('        var e=new com.tom.cpm.shared.editor.Editor();',s.index('private static void compileAvatar'))
end=s.index('    @SuppressWarnings("unchecked") private static com.tom.cpm.shared.definition.ModelDefinition definition',start)
s=s[:start]+'''        var file=com.tom.cpm.shared.io.ModelFile.load(ART.resolve("exports/Kizya_Pink_PK02.cpmmodel").toFile());avatarData=file.getDataBlock();
        var metadata=JsonParser.parseString(Files.readString(ART.resolve("reports/CPM_FORMAT_CHECK.json"))).getAsJsonObject();
        for(var entry:metadata.getAsJsonObject("runtime_element_ids").entrySet())ELEMENTS.put(entry.getKey(),entry.getValue().getAsInt());
        require(ELEMENTS.size()==139,"Painted PK02 element count");require(avatarData.length<30*1024,"Self-contained model packet under CPM local limit");
        REPORT.addProperty("model_elements",ELEMENTS.size());REPORT.addProperty("cpm_packet_bytes",avatarData.length);REPORT.addProperty("self_contained_model",true);
    }
'''+s[end:]
replacements={'respirator_bridge':'hair_crown','belt':'front_lock_-1_0_0','left_coat_panel_-2.65':'left_pinafore_shorts','left_boot':'left_shoe'}
for a,b in replacements.items():s=s.replace('"'+a+'"','"'+b+'"')
s=s.replace('visibility(mc,i,"left_pinafore_shorts",false);shot(mc,"elytra-backpacks")','visibility(mc,i,"back_lock_00_0",false);shot(mc,"elytra-backpacks")')
bind='''    private static void paintedVanillaSkin(Minecraft mc)throws Exception {
        var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"kizya_painted_fixture");
        try(var input=Files.newInputStream(ART.resolve("exports/Kizya_Pink_PK02_slim_64x64.png"))){
            var image=com.mojang.blaze3d.platform.NativeImage.read(input);require(image.getWidth()==64&&image.getHeight()==64,"Painted Java skin dimensions");
            mc.getTextureManager().register(key,new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        var cache=AbstractClientPlayer.class.getDeclaredField("playerInfo");cache.setAccessible(true);
        for(int i=0;i<3;i++){
            var info=mc.getConnection().getPlayerInfo(profiles[i]);require(info!=null,"Fixture player info");
            var skin=new PlayerSkin(key,null,null,null,PlayerSkin.Model.SLIM,true);
            var fixture=new net.minecraft.client.multiplayer.PlayerInfo(info.getProfile(),false){@Override public PlayerSkin getSkin(){return skin;}@Override public GameType getGameMode(){return info.getGameMode();}};
            cache.set(actor(mc,i),fixture);
        }
        REPORT.addProperty("painted_64_skin_loaded",true);REPORT.addProperty("local_skin_fixture_not_account_login",true);
    }
'''
s=s.replace('    private static void compileAvatar()',bind+'    private static void compileAvatar()')
s=s.replace('                if(!skinsFixed){skinFixtures(mc);skinsFixed=true;return;}','''                if(vanillaShots==0){paintedVanillaSkin(mc);vanillaShots=1;focus(mc,false);return;}
                if(vanillaShots==1){shot(mc,"vanilla-64-front");vanillaShots=2;focus(mc,true);return;}
                if(vanillaShots==2){shot(mc,"vanilla-64-back");vanillaShots=3;focus(mc,false);return;}
                if(!skinsFixed){lineup(mc);skinFixtures(mc);skinsFixed=true;return;}''')
# Existing archived harness stays disabled; only the new class listens to this run.
p=DEST/'src/smoke/java/pro/erez/interstice/client/AvatarNative.java';p.write_text(p.read_text(encoding='utf8').replace('"interstice.progressionSmoke"','"interstice.retiredAvatarSmoke"'),encoding='utf8')
(DEST/'src/smoke/java/pro/erez/interstice/client/KizyaNative.java').write_text(s,encoding='utf8')
record={'candidate':str(DEST),'owner_profile_used':False,'source_baseline':'isolated sk01 validation source snapshot; not a public build',
    'new_guard_version':'erez-cpm-guard 0.2.0','retired_guard_jar_used':False,'production_dependency_changed':False,
    'main_source_hashes':{p.relative_to(DEST/'src/main').as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (DEST/'src/main').rglob('*') if p.is_file()}}
(ROOT/'reports/NATIVE_CANDIDATE.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'candidate':str(DEST),'profile_used':False}))
