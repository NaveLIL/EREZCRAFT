from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/sk01')
base=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8')
base=base[:base.index('    @SubscribeEvent public static void tick(ClientTickEvent.Post event)')]
base=base.replace('ArmorPlayerGallery','AvatarNative').replace('"gallery-"','"avatar-"').replace('armor-player-gallery.json','avatar-native.json').replace('armor-gallery','avatar-native')
# Every fixture uses the intended classic wide-arm model; no account skin is changed.
start=base.index('    private static void skinFixtures(');end=base.index('    private static void shot(',start)
base=base[:start]+(ROOT/'tools/native_skin.java.txt').read_text(encoding='utf-8')+'\n'+base[end:]
start=base.index('    private static void inspect(');end=base.index('    private static byte[]',start)
base=base[:start]+base[end:]
base=base.replace('equip(player,TIERS[index]);reset(player,index);','reset(player,index);')
base+=(ROOT/'tools/native_tail.java.txt').read_text(encoding='utf-8')
out=CAND/'src/smoke/java/pro/erez/interstice/client/AvatarNative.java';out.write_text(base,encoding='utf-8')
print(out)
