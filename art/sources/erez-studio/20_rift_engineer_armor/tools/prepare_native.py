from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
src=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8');src=src[:src.index('    @SubscribeEvent public static void tick(ClientTickEvent.Post event)')]
src=src.replace('ArmorPlayerGallery','EngineerNative').replace('"gallery-"','"engineer-"').replace('armor-player-gallery.json','engineer-native.json').replace('armor-gallery','engineer-native')
src=src.replace('"EREZ_Silver","EREZ_Steel","EREZ_Magen"','"EngineerWide","EngineerSlim","EngineerTwin"')
a=src.index('    private static void equip(');b=src.index('    private static void reset(',a)
src=src[:a]+'''    private static void equip(ServerPlayer p,String tier){for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,new ItemStack(EngineerArmor.item(kind(slot))));p.inventoryMenu.broadcastChanges();}
'''+src[b:]
a=src.index('    private static void inspect(');b=src.index('    private static void skinFixtures(',a)
d=json.loads((ROOT/'source/armor_E12.json').read_text(encoding='utf-8'));h=hashlib.sha256((ROOT/'source/armor_E12.json').read_bytes()).hexdigest();ah=hashlib.sha256((ROOT/'source/armor_E12_atlas.png').read_bytes()).hexdigest()
src=src[:a]+f'''    private static void inspect(Minecraft mc){{
        require(EngineerArmorModel.sourceHash().equals("{h}"),"Exact Blender mesh and UV bytes required");
        require(EngineerArmorModel.sourcePartCount()=={len(d['parts'])}&&EngineerArmorModel.sourceFaceCount()=={sum(len(p['faces']) for p in d['parts'])},"No simplified game geometry");
        for(int i=0;i<3;i++)for(var slot:RealmArmor.SLOTS)require(actor(mc,i).getItemBySlot(slot).is(EngineerArmor.item(kind(slot))),"Tracked armor slot "+slot);
        long submitted=0;for(var slot:RealmArmor.SLOTS)submitted+=EngineerArmorClient.model(slot).renderedVertices;require(submitted>0,"Native armor renderer submitted geometry");
        require(EngineerArmorModel.lastPinnedError<.001F,"Waist anchoring");require(EngineerArmorModel.lastPhysicalPenetration<.01F,"Posed leg contact: "+EngineerArmorModel.lastPhysicalPenetration);
        REPORT.addProperty("loaded_mesh_sha256",EngineerArmorModel.sourceHash());REPORT.addProperty("source_parts",EngineerArmorModel.sourcePartCount());REPORT.addProperty("source_faces",EngineerArmorModel.sourceFaceCount());REPORT.addProperty("submitted_vertices_last_wearer",submitted);
        REPORT.addProperty("native_cloth_waist_error",EngineerArmorModel.lastPinnedError);REPORT.addProperty("native_cloth_contact_penetration",EngineerArmorModel.lastPhysicalPenetration);
        require(actor(mc,0).getSkin().model()==PlayerSkin.Model.WIDE&&actor(mc,1).getSkin().model()==PlayerSkin.Model.SLIM,"Wide/slim required");
    }}
'''+src[b:]
src=src.replace('private static int phase,ticks;','private static int phase,ticks,motionFrame;')
src+=(ROOT/'tools/native_tail.java.txt').read_text(encoding='utf-8')
(CAND/'src/smoke/java/pro/erez/interstice/client/EngineerNative.java').write_text(src,encoding='utf-8')
(ROOT/'reports/native_expected.json').write_text(json.dumps({'revision':'E12','mesh_sha256':h,'atlas_sha256':ah,'parts':len(d['parts']),'faces':sum(len(p['faces']) for p in d['parts'])},indent=2),encoding='utf-8')
print('Engineer native fixture prepared')
