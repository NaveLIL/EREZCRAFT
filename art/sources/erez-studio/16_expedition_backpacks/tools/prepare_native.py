from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/bp01')
old=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ai01/src/main/java/pro/erez/interstice/client/BackpackLayer.java').read_text(encoding='utf-8')
(CAND/'src/smoke/java/pro/erez/interstice/client/LegacyBackpackLayer.java').write_text(old.replace('BackpackLayer','LegacyBackpackLayer'),encoding='utf-8')
base=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8')
base=base[:base.index('    @SubscribeEvent public static void tick(ClientTickEvent.Post event)')]
base=base.replace('ArmorPlayerGallery','BackpackNative')
base=base.replace('{"riftsilver","riftsteel","aegis"}','{"riftsilver","riftsteel","aegis","aegis","aegis","aegis","aegis","aegis","aegis","aegis"}')
base=base.replace('{"EREZ_Silver","EREZ_Steel","EREZ_Magen"}','{"Pack_Field","Pack_Expedition","Pack_Rift","Pack_Bench3","Pack_Bench4","Pack_Bench5","Pack_Bench6","Pack_Bench7","Pack_Bench8","Pack_Bench9"}')
base=base.replace('{34.5,32,29.5}','{34.5,32,29.5,38,39.8,41.6,43.4,45.2,47,48.8}')
base=base.replace('p.inventoryMenu.broadcastChanges();','BackpackHarness.set(p,new ItemStack(tier.equals("aegis")?ExpeditionEquipment.RIFT_BACKPACK.get():tier.equals("riftsteel")?ExpeditionEquipment.EXPEDITION_BACKPACK.get():ExpeditionEquipment.FIELD_BACKPACK.get()));p.inventoryMenu.broadcastChanges();')
base=base.replace('"gallery-"','"backpacks-"').replace('armor-player-gallery.json','backpacks-native.json').replace('armor-gallery','backpacks-native')
base=base.replace('201.02-1.62,27.8,Set.of(),-29.75F','201.02-1.62,36.2,Set.of(),-150.25F')
# Frame benchmark swaps only the owned client's render layer, never a runtime/server setting.
extra=(ROOT/'tools/native_tail.java.txt').read_text(encoding='utf-8')
out=CAND/'src/smoke/java/pro/erez/interstice/client/BackpackNative.java';out.write_text(base+extra,encoding='utf-8')
target=ROOT/'tools/native_java';target.mkdir(exist_ok=True)
for name in ['BackpackNative.java','LegacyBackpackLayer.java']:(target/name).write_bytes((out.parent/name).read_bytes())
print(out)
