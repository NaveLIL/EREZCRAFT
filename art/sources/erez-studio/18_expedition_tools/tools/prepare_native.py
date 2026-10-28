from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/et01')
s=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8');s=s[:s.index('    @SubscribeEvent public static void tick(ClientTickEvent.Post event)')]
s=s.replace('ArmorPlayerGallery','ToolsNative').replace('"gallery-"','"tools-"').replace('armor-player-gallery.json','tools-native.json').replace('armor-gallery','tools-native')
start=s.index('    private static void equip(');end=s.index('    private static void reset(',start)
s=s[:start]+'''    private static final String[] TOOLS={"tether_spool","lift_controller","structure_surveyor"};
    private static net.minecraft.world.item.Item tool(int i){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,TOOLS[i]));}
    private static void equip(ServerPlayer p,String tier){int i=Arrays.asList(TIERS).indexOf(tier);for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(i)));p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();}
'''+s[end:]
s=s.replace('p.stopUsingItem();p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);','p.stopUsingItem();')
s+=(ROOT/'tools/native_tail.java.txt').read_text(encoding='utf-8')
(CAND/'src/smoke/java/pro/erez/interstice/client/ToolsNative.java').write_text(s,encoding='utf-8')
print('Native tool fixture prepared')
